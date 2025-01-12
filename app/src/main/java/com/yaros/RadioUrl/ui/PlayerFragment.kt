package com.yaros.RadioUrl.ui

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Parcelable
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.RequestQueue
import com.android.volley.toolbox.Volley
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.MaterialTimePicker.INPUT_MODE_KEYBOARD
import com.google.android.material.timepicker.TimeFormat
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.yaros.RadioUrl.Keys
import com.yaros.RadioUrl.PlayerService
import com.yaros.RadioUrl.R
import com.yaros.RadioUrl.core.collection.CollectionAdapter
import com.yaros.RadioUrl.core.collection.CollectionViewModel
import com.yaros.RadioUrl.core.Collection
import com.yaros.RadioUrl.core.Station
import com.yaros.RadioUrl.core.dialogs.AddStationDialog
import com.yaros.RadioUrl.core.dialogs.FindStationDialog
import com.yaros.RadioUrl.core.dialogs.YesNoDialog
import com.yaros.RadioUrl.core.extensions.cancelSleepTimer
import com.yaros.RadioUrl.core.extensions.play
import com.yaros.RadioUrl.core.extensions.playStreamDirectly
import com.yaros.RadioUrl.core.extensions.requestMetadataHistory
import com.yaros.RadioUrl.core.extensions.requestSleepTimerRemaining
import com.yaros.RadioUrl.core.extensions.startSleepTimer
import com.yaros.RadioUrl.helpers.BackupHelper
import com.yaros.RadioUrl.helpers.CollectionHelper
import com.yaros.RadioUrl.helpers.DownloadHelper
import com.yaros.RadioUrl.helpers.NetworkHelper
import com.yaros.RadioUrl.helpers.PreferencesHelper
import com.yaros.RadioUrl.helpers.UiHelper
import com.yaros.RadioUrl.helpers.UpdateHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@UnstableApi
class PlayerFragment : Fragment(),
    SharedPreferences.OnSharedPreferenceChangeListener,
    FindStationDialog.FindStationDialogListener,
    AddStationDialog.AddStationDialogListener,
    CollectionAdapter.CollectionAdapterListener,
    YesNoDialog.YesNoDialogListener {

    private val TAG: String = PlayerFragment::class.java.simpleName

    private lateinit var collectionViewModel: CollectionViewModel
    private lateinit var layout: LayoutHolder
    private lateinit var collectionAdapter: CollectionAdapter
    private lateinit var controllerFuture: ListenableFuture<MediaController>
    private lateinit var pickSingleMediaLauncher: ActivityResultLauncher<PickVisualMediaRequest>
    private lateinit var queue: RequestQueue
    private val controller: MediaController?
        get() = if (controllerFuture.isDone) controllerFuture.get() else null
    private var collection: Collection = Collection()
    private var playerState: PlayerState = PlayerState()
    private var listLayoutState: Parcelable? = null
    private val handler: Handler = Handler(Looper.getMainLooper())
    private var tempStationUuid: String = String()
    private var itemTouchHelper: ItemTouchHelper? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requireActivity().onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (isEnabled && this@PlayerFragment::layout.isInitialized && !layout.minimizePlayerIfExpanded()) {
                        isEnabled = false
                        activity?.onBackPressedDispatcher?.onBackPressed()
                    }
                }
            })

        queue = Volley.newRequestQueue(requireActivity())

        playerState = PreferencesHelper.loadPlayerState()

        collectionViewModel = ViewModelProvider(this)[CollectionViewModel::class.java]

        collectionAdapter = CollectionAdapter(
            activity as Context,
            this as CollectionAdapter.CollectionAdapterListener
        )

        listLayoutState = savedInstanceState?.getParcelable<Parcelable>(Keys.KEY_SAVE_INSTANCE_STATE_STATION_LIST)

        pickSingleMediaLauncher =
            registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { imageUri ->
                if (imageUri == null) {
                    Snackbar.make(requireView(), R.string.toastalert_failed_picking_media, Snackbar.LENGTH_LONG).show()
                } else {
                    collection = CollectionHelper.setStationImageWithStationUuid(
                        activity as Context,
                        collection,
                        imageUri,
                        tempStationUuid,
                        imageManuallySet = true
                    )
                    tempStationUuid = String()
                }
            }

        Handler(Looper.getMainLooper()).postDelayed({ context?.let {  } }, 5000)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val rootView: View = inflater.inflate(R.layout.fragment_player, container, false)
        layout = LayoutHolder(rootView)

        initializeViews()

        (activity as AppCompatActivity).supportActionBar?.hide()

        (activity as AppCompatActivity).window.navigationBarColor = ContextCompat.getColor(requireActivity(),
            R.color.player_sheet_background
        )

        itemTouchHelper = ItemTouchHelper(ItemTouchHelperCallback())
        itemTouchHelper?.attachToRecyclerView(layout.recyclerView)

        return rootView
    }

    inner class ItemTouchHelperCallback : ItemTouchHelper.Callback() {

        override fun isLongPressDragEnabled() = !collectionAdapter.isExpandedForEdit

        override fun isItemViewSwipeEnabled() = true

        override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
            if (viewHolder.itemViewType == Keys.VIEW_TYPE_ADD_NEW) {
                return 0
            }

            val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN
            val swipeFlags = ItemTouchHelper.START or ItemTouchHelper.END
            return makeMovementFlags(dragFlags, swipeFlags)
        }

        override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
            val fromPosition = viewHolder.bindingAdapterPosition
            val toPosition = target.bindingAdapterPosition
            collectionAdapter.onItemMove(fromPosition, toPosition)
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            val position = viewHolder.bindingAdapterPosition
            collectionAdapter.onItemDismiss(position)
        }

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            collectionAdapter.saveCollectionAfterDragDrop()
        }
    }

    override fun onStart() {
        super.onStart()
        initializeController()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (this::layout.isInitialized) {
            listLayoutState = layout.layoutManager.onSaveInstanceState()
            outState.putParcelable(Keys.KEY_SAVE_INSTANCE_STATE_STATION_LIST, listLayoutState)
        }
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        activity?.volumeControlStream = AudioManager.STREAM_MUSIC
        playerState = PreferencesHelper.loadPlayerState()
//        setupPlaybackControls()
        updatePlayerViews()
        updateStationListState()
        togglePeriodicSleepTimerUpdateRequest()
        observeCollectionViewModel()
        handleNavigationArguments()
//        handleStartIntent()
        PreferencesHelper.registerPreferenceChangeListener(this as SharedPreferences.OnSharedPreferenceChangeListener)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(periodicSleepTimerUpdateRequestRunnable)
        PreferencesHelper.unregisterPreferenceChangeListener(this as SharedPreferences.OnSharedPreferenceChangeListener)

    }

    override fun onStop() {
        super.onStop()
        releaseController()
    }

    override fun onDestroy() {
        super.onDestroy()
        queue.cancelAll(TAG)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == Keys.PREF_ACTIVE_DOWNLOADS) {
            layout.toggleDownloadProgressIndicator()
        }
        if (key == Keys.PREF_PLAYER_METADATA_HISTORY) {
            requestMetadataUpdate()
        }
    }

    override fun onFindStationDialog(station: Station) {
        if (station.streamContent.isNotEmpty() && station.streamContent != Keys.MIME_TYPE_UNSUPPORTED) {
            activity?.let { context ->
                collection = CollectionHelper.addStation(context, collection, station)
            }
        } else {
            CoroutineScope(IO).launch {
                val contentType: NetworkHelper.ContentType = NetworkHelper.detectContentType(station.getStreamUri())
                station.streamContent = contentType.type
                withContext(Main) {
                    collection = CollectionHelper.addStation(activity as Context, collection, station)
                }
            }
        }
    }

    override fun onAddStationDialog(station: Station) {
        if (station.streamContent.isNotEmpty() && station.streamContent != Keys.MIME_TYPE_UNSUPPORTED) {
            collection = CollectionHelper.addStation(activity as Context, collection, station)
        }
    }

    override fun onPlayButtonTapped(stationUuid: String) {
        if (controller?.isPlaying == true && stationUuid == playerState.stationUuid) {
            controller?.pause()
        } else {
            activity?.let { context ->
                // Предполагаем, что CollectionHelper.getStation(collection, stationUuid) не возвращает null
                CollectionHelper.getStation(collection, stationUuid)?.let { station ->
                    controller?.play(context, station)
                }
            }
        }
    }

    override fun onAddNewButtonTapped() {
        FindStationDialog(activity as Activity, this as FindStationDialog.FindStationDialogListener).show()
    }

    override fun onChangeImageButtonTapped(stationUuid: String) {
        tempStationUuid = stationUuid
        pickImage()
    }

    override fun onYesNoDialog(
        type: Int,
        dialogResult: Boolean,
        payload: Int,
        payloadString: String
    ) {
        super.onYesNoDialog(type, dialogResult, payload, payloadString)
        when (type) {
            Keys.DIALOG_REMOVE_STATION -> {
                when (dialogResult) {
                    true -> collectionAdapter.removeStation(activity as Context, payload)
                    false -> collectionAdapter.notifyItemChanged(payload)
                }
            }
            Keys.DIALOG_RESTORE_COLLECTION -> {
                when (dialogResult) {
                    true -> BackupHelper.restore(requireView(), activity as Context, payloadString.toUri())
                    false -> {
                    }
                }
            }
        }
    }

    private fun initializeController() {
        controllerFuture = MediaController.Builder(
            activity as Context,
            SessionToken(
                activity as Context,
                ComponentName(activity as Context, PlayerService::class.java)
            )
        ).buildAsync()
        controllerFuture.addListener({ setupController() }, MoreExecutors.directExecutor())
    }

    private fun releaseController() {
        MediaController.releaseFuture(controllerFuture)
    }

    private fun setupController() {
        val controller: MediaController = this.controller ?: return
        controller.addListener(playerListener)
        requestMetadataUpdate()
        handleStartIntent()
    }

    private fun initializeViews() {
        layout.recyclerView.adapter = collectionAdapter

        val swipeToDeleteHandler = object : UiHelper.SwipeToDeleteCallback(activity as Context) {
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val adapterPosition: Int = viewHolder.bindingAdapterPosition
                val dialogMessage =
                    "${getString(R.string.dialog_yes_no_message_remove_station)}\n\n- ${collection.stations[adapterPosition].name}"
                YesNoDialog(this@PlayerFragment as YesNoDialog.YesNoDialogListener).show(
                    context = activity as Context,
                    type = Keys.DIALOG_REMOVE_STATION,
                    messageString = dialogMessage,
                    yesButton = R.string.dialog_yes_no_positive_button_remove_station,
                    payload = adapterPosition
                )
            }
        }
        val swipeToDeleteItemTouchHelper = ItemTouchHelper(swipeToDeleteHandler)
        swipeToDeleteItemTouchHelper.attachToRecyclerView(layout.recyclerView)

        val swipeToMarkStarredHandler =
            object : UiHelper.SwipeToMarkStarredCallback(activity as Context) {
                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                    val adapterPosition: Int = viewHolder.bindingAdapterPosition
                    collectionAdapter.toggleStarredStation(activity as Context, adapterPosition)
                }
            }
        val swipeToMarkStarredItemTouchHelper = ItemTouchHelper(swipeToMarkStarredHandler)
        swipeToMarkStarredItemTouchHelper.attachToRecyclerView(layout.recyclerView)

        layout.sheetSleepTimerStartButtonView.setOnClickListener {
            when (controller?.isPlaying) {
                true -> {
                    val timePicker = MaterialTimePicker.Builder()
                        .setTimeFormat(TimeFormat.CLOCK_24H)
                        .setHour(0)
                        .setMinute(1)
                        .setInputMode(INPUT_MODE_KEYBOARD)
                        .build()

                    timePicker.addOnPositiveButtonClickListener {
                        val selectedTimeMillis = (timePicker.hour * 60 * 60 * 1000L) + (timePicker.minute * 60 * 1000L) + 1000
                        playerState.sleepTimerRunning = true
                        controller?.startSleepTimer(selectedTimeMillis)
                        togglePeriodicSleepTimerUpdateRequest()
                    }

                    timePicker.show(requireActivity().supportFragmentManager, "tag")
                }
                else -> Snackbar.make(
                    requireView(),
                    R.string.toastmessage_sleep_timer_unable_to_start,
                    Snackbar.LENGTH_SHORT
                ).show()
            }
        }

        layout.sheetSleepTimerCancelButtonView.setOnClickListener {
            playerState.sleepTimerRunning = false
            controller?.cancelSleepTimer()
            togglePeriodicSleepTimerUpdateRequest()
        }

    }

    private fun updatePlayerViews() {
        var station = Station()
        if (playerState.stationUuid.isNotEmpty()) {
            station = CollectionHelper.getStation(collection, playerState.stationUuid)
        } else if (collection.stations.isNotEmpty()) {
            station = collection.stations[0]
            playerState.stationUuid = station.uuid
        }
        layout.togglePlayButton(playerState.isPlaying)
        layout.updatePlayerViews(activity as Context, station, playerState.isPlaying)

        layout.playButtonView.setOnClickListener {
            onPlayButtonTapped(playerState.stationUuid)
        }
    }

    private fun updateStationListState() {
        if (listLayoutState != null) {
            layout.layoutManager.onRestoreInstanceState(listLayoutState)
        }
    }

    private fun requestSleepTimerUpdate() {
        val resultFuture: ListenableFuture<SessionResult>? =
            controller?.requestSleepTimerRemaining()
        resultFuture?.addListener(Runnable {
            val timeRemaining: Long = resultFuture.get().extras.getLong(Keys.EXTRA_SLEEP_TIMER_REMAINING)
            layout.updateSleepTimer(activity as Context, timeRemaining)
        }, MoreExecutors.directExecutor())
    }

    private fun requestMetadataUpdate() {
        val resultFuture: ListenableFuture<SessionResult>? = controller?.requestMetadataHistory()
        resultFuture?.addListener(Runnable {
            val metadata: ArrayList<String>? = resultFuture.get().extras.getStringArrayList(Keys.EXTRA_METADATA_HISTORY)
            layout.updateMetadata(metadata?.toMutableList())
        }, MoreExecutors.directExecutor())
    }

    private fun pickImage() {
        pickSingleMediaLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    private fun handleStartIntent() {
        if ((activity as Activity).intent.action != null) {
            when ((activity as Activity).intent.action) {
                Keys.ACTION_SHOW_PLAYER -> handleShowPlayer()
                Intent.ACTION_VIEW -> handleViewIntent()
                Keys.ACTION_START -> handleStartPlayer()
            }
        }
        (activity as Activity).intent.action = ""
    }

    private fun handleShowPlayer() {
        Timber.tag(TAG).i("Tap on notification registered.")
    }

    private fun handleViewIntent() {
        val intentUri: Uri? = (activity as Activity).intent.data
        if (intentUri != null) {
            CoroutineScope(IO).launch {
                val stationList: MutableList<Station> = mutableListOf()
                val scheme: String = intentUri.scheme ?: String()
                if (scheme.startsWith("http")) {
                    Timber.tag(TAG).i("Transistor was started to handle a web link.")
                    stationList.addAll(CollectionHelper.createStationsFromUrl(intentUri.toString()))
                }
                else if (scheme.startsWith("content")) {
                    Timber.tag(TAG).i("Transistor was started to handle a local audio playlist.")
                    stationList.addAll(CollectionHelper.createStationListFromContentUri(activity as Context, intentUri))
                }
                withContext(Main) {
                    if (stationList.isNotEmpty()) {
                        AddStationDialog(activity as Activity, stationList, this@PlayerFragment as AddStationDialog.AddStationDialogListener).show()
                    } else {
                        Toast.makeText(context, R.string.toastmessage_station_not_valid, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun handleStartPlayer() {
        val intent: Intent = (activity as Activity).intent
        if (intent.hasExtra(Keys.EXTRA_START_LAST_PLAYED_STATION)) {
            controller?.play(activity as Context, CollectionHelper.getStation(collection, playerState.stationUuid))
        } else if (intent.hasExtra(Keys.EXTRA_STATION_UUID)) {
            val uuid: String = intent.getStringExtra(Keys.EXTRA_STATION_UUID) ?: String()
            controller?.play(activity as Context, CollectionHelper.getStation(collection, uuid))
        } else if (intent.hasExtra(Keys.EXTRA_STREAM_URI)) {
            val streamUri: String = intent.getStringExtra(Keys.EXTRA_STREAM_URI) ?: String()
            controller?.playStreamDirectly(streamUri)
        }
    }

    private fun togglePeriodicSleepTimerUpdateRequest() {
        handler.removeCallbacks(periodicSleepTimerUpdateRequestRunnable)
        handler.postDelayed(periodicSleepTimerUpdateRequestRunnable, 0)
    }

    private fun observeCollectionViewModel() {
        collectionViewModel.collectionLiveData.observe(this) {
            collection = it
////            // updates current station in player views
////            playerState = PreferencesHelper.loadPlayerState()
//            // get station
//            val station: Station = CollectionHelper.getStation(collection, playerState.stationUuid)
//            // update player views
//            layout.updatePlayerViews(activity as Context, station, playerState.isPlaying)
////            // handle start intent
////            handleStartIntent()
////            // handle navigation arguments
////            handleNavigationArguments()
        }
        collectionViewModel.collectionSizeLiveData.observe(this) {
            layout.toggleOnboarding(activity as Context, collection.stations.size)
            updatePlayerViews()
            CollectionHelper.exportCollectionM3u(activity as Context, collection)
            CollectionHelper.exportCollectionPls(activity as Context, collection)
        }
    }

    private fun handleNavigationArguments() {
        val updateCollection: Boolean =
            arguments?.getBoolean(Keys.ARG_UPDATE_COLLECTION, false) ?: false
        val updateStationImages: Boolean =
            arguments?.getBoolean(Keys.ARG_UPDATE_IMAGES, false) ?: false
        val restoreCollectionFileString: String? = arguments?.getString(Keys.ARG_RESTORE_COLLECTION)

        if (updateCollection) {
            arguments?.putBoolean(Keys.ARG_UPDATE_COLLECTION, false)
            val updateHelper = UpdateHelper(activity as Context, collectionAdapter, collection)
            updateHelper.updateCollection()
        }
        if (updateStationImages) {
            arguments?.putBoolean(Keys.ARG_UPDATE_IMAGES, false)
            DownloadHelper.updateStationImages(activity as Context)
        }
        if (!restoreCollectionFileString.isNullOrEmpty()) {
            arguments?.putString(Keys.ARG_RESTORE_COLLECTION, null)
            when (collection.stations.isNotEmpty()) {
                true -> {
                    YesNoDialog(this as YesNoDialog.YesNoDialogListener).show(
                        context = activity as Context,
                        type = Keys.DIALOG_RESTORE_COLLECTION,
                        messageString = getString(R.string.dialog_restore_collection_replace_existing),
                        payloadString = restoreCollectionFileString
                    )
                }
                false -> {
                    BackupHelper.restore(
                        requireView(),
                        activity as Context,
                        restoreCollectionFileString.toUri()
                    )
                }
            }
        }
    }

    private val periodicSleepTimerUpdateRequestRunnable: Runnable = object : Runnable {
        override fun run() {
            requestSleepTimerUpdate()
            handler.postDelayed(this, 500)
        }
    }

    private var playerListener: Player.Listener = object : Player.Listener {

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            super.onMediaItemTransition(mediaItem, reason)
            playerState.stationUuid = mediaItem?.mediaId ?: String()
            updatePlayerViews()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            super.onIsPlayingChanged(isPlaying)
            playerState.isPlaying = isPlaying
            layout.animatePlaybackButtonStateTransition(activity as Context, isPlaying)

            if (isPlaying) {
                layout.showPlayer(activity as Context)
                layout.showBufferingIndicator(buffering = false)
            } else {
                if (controller?.playWhenReady == true) {
                    layout.showBufferingIndicator(buffering = true)
                } else {
                    layout.showBufferingIndicator(buffering = false)
                }
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            super.onPlayWhenReadyChanged(playWhenReady, reason)

            if (playWhenReady && controller?.isPlaying == false) {
                layout.showBufferingIndicator(buffering = true)
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            super.onPlayerError(error)
            layout.togglePlayButton(false)
            layout.showBufferingIndicator(false)
            Toast.makeText(activity, R.string.toastmessage_connection_failed, Toast.LENGTH_LONG).show()
        }
    }
}