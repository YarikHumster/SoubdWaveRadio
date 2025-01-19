package com.yaros.RadioUrl.core.collection

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.Group
import androidx.core.net.toUri
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.yaros.RadioUrl.Keys
import com.yaros.RadioUrl.R
import com.yaros.RadioUrl.core.Collection
import com.yaros.RadioUrl.core.Station
import com.yaros.RadioUrl.helpers.CollectionHelper
import com.yaros.RadioUrl.helpers.FileHelper
import com.yaros.RadioUrl.helpers.ImageHelper
import com.yaros.RadioUrl.helpers.NetworkHelper
import com.yaros.RadioUrl.helpers.PreferencesHelper
import com.yaros.RadioUrl.helpers.ShortcutHelper
import com.yaros.RadioUrl.helpers.UiHelper
import com.yaros.RadioUrl.helpers.UpdateHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Collections
import java.util.Locale

class CollectionAdapter(
    private val context: Context,
    private val collectionAdapterListener: CollectionAdapterListener
) : RecyclerView.Adapter<RecyclerView.ViewHolder>(), UpdateHelper.UpdateHelperListener {

    private lateinit var collectionViewModel: CollectionViewModel
    private var collection: Collection = Collection()
    private var editStationsEnabled: Boolean = PreferencesHelper.loadEditStationsEnabled()
    private var editStationStreamsEnabled: Boolean = PreferencesHelper.loadEditStreamUrisEnabled()
    private var expandedStationUuid: String = PreferencesHelper.loadStationListStreamUuid()
    private var expandedStationPosition: Int = -1
    var isExpandedForEdit: Boolean = false

    interface CollectionAdapterListener {
        fun onPlayButtonTapped(stationUuid: String)
        fun onAddNewButtonTapped()
        fun onChangeImageButtonTapped(stationUuid: String)
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        collectionViewModel =
            ViewModelProvider(context as AppCompatActivity)[CollectionViewModel::class.java]
        observeCollectionViewModel(context as LifecycleOwner)
        PreferencesHelper.registerPreferenceChangeListener(sharedPreferenceChangeListener)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        PreferencesHelper.unregisterPreferenceChangeListener(sharedPreferenceChangeListener)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            Keys.VIEW_TYPE_ADD_NEW -> {
                val v = LayoutInflater.from(parent.context)
                    .inflate(R.layout.card_add_new_station, parent, false)
                AddNewViewHolder(v)
            }
            else -> {
                val v = LayoutInflater.from(parent.context)
                    .inflate(R.layout.card_station, parent, false)
                StationViewHolder(v)
            }
        }
    }

    fun onItemMove(fromPosition: Int, toPosition: Int) {
        if (isExpandedForEdit) {
            return
        }
        val stationList = collection.stations
        val stationCount = stationList.size
        if (fromPosition !in 0 until stationCount || toPosition !in 0 until stationCount) {
            return
        }
        val fromStation = stationList[fromPosition]
        val toStation = stationList[toPosition]
        if (fromStation.starred != toStation.starred) {
            return
        }
        Collections.swap(stationList, fromPosition, toPosition)
        expandedStationPosition =
            if (fromPosition == expandedStationPosition) toPosition else expandedStationPosition
        notifyItemMoved(fromPosition, toPosition)
    }


    fun onItemDismiss(position: Int) {
        collection.stations.removeAt(position)
        notifyItemRemoved(position)
    }


    fun saveCollectionAfterDragDrop() {
        CollectionHelper.saveCollection(context, collection)
    }


    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is AddNewViewHolder -> {
                val addNewViewHolder: AddNewViewHolder = holder
                addNewViewHolder.addNewStationView.setOnClickListener {
                    collectionAdapterListener.onAddNewButtonTapped()
                }
            }

            is StationViewHolder -> {
                val station: Station = collection.stations[position]
                val stationViewHolder: StationViewHolder = holder
                setStarredIcon(stationViewHolder, station)
                setStationName(stationViewHolder, station)
                setStationImage(stationViewHolder, station)
                setStationButtons(stationViewHolder, station)
                setEditViews(stationViewHolder, station)
                when (expandedStationPosition) {
                    position -> {
                        stationViewHolder.stationNameView.isVisible = false
                        stationViewHolder.playButtonView.isGone = true
                        stationViewHolder.stationStarredView.isGone = true
                        stationViewHolder.editViews.isVisible = true
                        if (editStationStreamsEnabled) {
                            stationViewHolder.stationUriEditView.isVisible = true
                            stationViewHolder.stationUriEditView.imeOptions =
                                EditorInfo.IME_ACTION_DONE
                        } else {
                            stationViewHolder.stationUriEditView.isGone = true
                            stationViewHolder.stationNameEditView.imeOptions =
                                EditorInfo.IME_ACTION_DONE
                        }
                    }
                    else -> {
                        stationViewHolder.stationNameView.isVisible = true
                        stationViewHolder.stationStarredView.isVisible = station.starred
                        stationViewHolder.editViews.isGone = true
                        stationViewHolder.stationUriEditView.isGone = true
                    }
                }
            }
        }
    }

    override fun onStationUpdated(
        collection: Collection,
        positionPriorUpdate: Int,
        positionAfterUpdate: Int
    ) {
        if (positionPriorUpdate != positionAfterUpdate && positionPriorUpdate != -1 && positionAfterUpdate != -1) {
            notifyItemMoved(positionPriorUpdate, positionAfterUpdate)
            notifyItemChanged(positionPriorUpdate)
        }
        notifyItemChanged(positionAfterUpdate)
    }

    private fun setStationName(stationViewHolder: StationViewHolder, station: Station) {
        stationViewHolder.stationNameView.text = station.name
    }

    private fun setEditViews(stationViewHolder: StationViewHolder, station: Station) {
        stationViewHolder.stationNameEditView.setText(station.name, TextView.BufferType.EDITABLE)
        stationViewHolder.stationUriEditView.setText(
            station.getStreamUri(),
            TextView.BufferType.EDITABLE
        )
        stationViewHolder.stationUriEditView.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                handleStationUriInput(stationViewHolder, s, station.getStreamUri())
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        stationViewHolder.cancelButton.setOnClickListener {
            val position: Int = stationViewHolder.adapterPosition
            toggleEditViews(position, station.uuid)
            UiHelper.hideSoftKeyboard(context, stationViewHolder.stationNameEditView)
        }
        stationViewHolder.saveButton.setOnClickListener {
            val position: Int = stationViewHolder.adapterPosition
            toggleEditViews(position, station.uuid)
            saveStation(
                station,
                position,
                stationViewHolder.stationNameEditView.text.toString(),
                stationViewHolder.stationUriEditView.text.toString()
            )
            UiHelper.hideSoftKeyboard(context, stationViewHolder.stationNameEditView)
        }
        stationViewHolder.placeOnHomeScreenButton.setOnClickListener {
            val position: Int = stationViewHolder.adapterPosition
            ShortcutHelper.placeShortcut(context, station)
            toggleEditViews(position, station.uuid)
            UiHelper.hideSoftKeyboard(context, stationViewHolder.stationNameEditView)
        }
        stationViewHolder.stationImageChangeView.setOnClickListener {
            val position: Int = stationViewHolder.adapterPosition
            collectionAdapterListener.onChangeImageButtonTapped(station.uuid)
            stationViewHolder.adapterPosition
            toggleEditViews(position, station.uuid)
            UiHelper.hideSoftKeyboard(context, stationViewHolder.stationNameEditView)
        }
    }

    private fun toggleEditViews(position: Int, stationUuid: String) {
        when (stationUuid) {
            expandedStationUuid -> {
                isExpandedForEdit = false
                saveStationListExpandedState()
                notifyItemChanged(position)
            }

            else -> {
                isExpandedForEdit = true
                val previousExpandedStationPosition: Int = expandedStationPosition
                if (previousExpandedStationPosition > -1 && previousExpandedStationPosition < collection.stations.size)
                    notifyItemChanged(previousExpandedStationPosition)
                saveStationListExpandedState(position, stationUuid)
                notifyDataSetChanged()
            }
        }
    }

    private fun setStarredIcon(stationViewHolder: StationViewHolder, station: Station) {
        when (station.starred) {
            true -> {
                if (station.imageColor != -1) {
                    stationViewHolder.stationStarredView.setColorFilter(station.imageColor)
                }
                stationViewHolder.stationStarredView.isVisible = true
            }
            false -> stationViewHolder.stationStarredView.isGone = true
        }
    }

    private fun setStationImage(stationViewHolder: StationViewHolder, station: Station) {
        if (station.imageColor != -1) {
            stationViewHolder.stationImageView.setBackgroundColor(station.imageColor)
        }
        stationViewHolder.stationImageView.setImageBitmap(
            ImageHelper.getStationImage(
                context,
                station.smallImage
            )
        )
        stationViewHolder.stationImageView.contentDescription =
            "${context.getString(R.string.descr_player_station_image)}: ${station.name}"
    }

    private fun setStationButtons(stationViewHolder: StationViewHolder, station: Station) {
        when (station.isPlaying) {
            true -> stationViewHolder.playButtonView.visibility = View.VISIBLE
            false -> stationViewHolder.playButtonView.visibility = View.INVISIBLE
        }
        stationViewHolder.stationCardView.setOnClickListener {
            collectionAdapterListener.onPlayButtonTapped(station.uuid)
        }
        stationViewHolder.playButtonView.setOnClickListener {
            collectionAdapterListener.onPlayButtonTapped(station.uuid)
        }
        stationViewHolder.stationNameView.setOnClickListener {
            collectionAdapterListener.onPlayButtonTapped(station.uuid)
        }
        stationViewHolder.stationStarredView.setOnClickListener {
            collectionAdapterListener.onPlayButtonTapped(station.uuid)
        }
        stationViewHolder.stationImageView.setOnClickListener {
            collectionAdapterListener.onPlayButtonTapped(station.uuid)
        }
        stationViewHolder.playButtonView.setOnLongClickListener {
            if (editStationsEnabled) {
                val position: Int = stationViewHolder.adapterPosition
                toggleEditViews(position, station.uuid)
                return@setOnLongClickListener true
            } else {
                return@setOnLongClickListener false
            }
        }
        stationViewHolder.stationNameView.setOnLongClickListener {
            if (editStationsEnabled) {
                val position: Int = stationViewHolder.adapterPosition
                toggleEditViews(position, station.uuid)
                return@setOnLongClickListener true
            } else {
                return@setOnLongClickListener false
            }
        }
        stationViewHolder.stationStarredView.setOnLongClickListener {
            if (editStationsEnabled) {
                val position: Int = stationViewHolder.adapterPosition
                toggleEditViews(position, station.uuid)
                return@setOnLongClickListener true
            } else {
                return@setOnLongClickListener false
            }
        }
        stationViewHolder.stationImageView.setOnLongClickListener {
            if (editStationsEnabled) {
                val position: Int = stationViewHolder.adapterPosition
                toggleEditViews(position, station.uuid)
                return@setOnLongClickListener true
            } else {
                return@setOnLongClickListener false
            }
        }
    }

    private fun handleStationUriInput(
        stationViewHolder: StationViewHolder,
        s: Editable?,
        streamUri: String
    ) {
        if (editStationStreamsEnabled) {
            val input: String = s.toString()
            if (input == streamUri) {
                stationViewHolder.saveButton.isEnabled = true
            } else {
                stationViewHolder.saveButton.isEnabled = false
                if (input.startsWith("http")) {
                    CoroutineScope(IO).launch {
                        val deferred: Deferred<NetworkHelper.ContentType> =
                            async(Dispatchers.Default) {
                                NetworkHelper.detectContentTypeSuspended(input)
                            }
                        val contentType: String =
                            deferred.await().type.lowercase(Locale.getDefault())
                        if (Keys.MIME_TYPES_MPEG.contains(contentType) or
                            Keys.MIME_TYPES_OGG.contains(contentType) or
                            Keys.MIME_TYPES_AAC.contains(contentType) or
                            Keys.MIME_TYPES_HLS.contains(contentType)
                        ) {
                            withContext(Main) {
                                stationViewHolder.saveButton.isEnabled = true
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: List<Any>
    ) {

        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position)
        } else if (holder is StationViewHolder) {
            collection.stations[holder.adapterPosition]
            for (data in payloads) {
                when (data as Int) {
                    Keys.HOLDER_UPDATE_COVER -> {
                        // todo implement
                    }
                    Keys.HOLDER_UPDATE_NAME -> {
                        // todo implement
                    }
                    Keys.HOLDER_UPDATE_PLAYBACK_STATE -> {
                        // todo implement
                    }
                    Keys.HOLDER_UPDATE_PLAYBACK_PROGRESS -> {
                        // todo implement
                    }
                    Keys.HOLDER_UPDATE_DOWNLOAD_STATE -> {
                        // todo implement
                    }
                }
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (isPositionFooter(position)) {
            true -> Keys.VIEW_TYPE_ADD_NEW
            false -> Keys.VIEW_TYPE_STATION
        }
    }

    override fun getItemCount(): Int {
        return collection.stations.size + 1
    }

    fun removeStation(context: Context, position: Int) {
        val newCollection = collection.deepCopy()
        CollectionHelper.deleteStationImages(context, newCollection.stations[position])
        newCollection.stations.removeAt(position)
        collection = newCollection
        notifyItemRemoved(position)
        CollectionHelper.saveCollection(context, newCollection)
    }

    fun toggleStarredStation(context: Context, position: Int) {
        notifyItemChanged(position)
        val stationUuid: String = collection.stations[position].uuid
        collection.stations[position].apply { starred = !starred }
        collection = CollectionHelper.sortCollection(collection)
        notifyItemMoved(position, CollectionHelper.getStationPosition(collection, stationUuid))
        CollectionHelper.saveCollection(context, collection)
    }

    private fun saveStation(
        station: Station,
        position: Int,
        stationName: String,
        streamUri: String
    ) {
        collection.stations.forEach {
            if (it.uuid == station.uuid) {
                if (stationName.isNotEmpty()) {
                    it.name = stationName
                    it.nameManuallySet = true
                }
                if (streamUri.isNotEmpty()) {
                    it.streamUris[0] = streamUri
                }
            }
        }
        collection = CollectionHelper.sortCollection(collection)
        val newPosition: Int = CollectionHelper.getStationPosition(collection, station.uuid)
        if (position != newPosition && newPosition != -1) {
            notifyItemMoved(position, newPosition)
            notifyItemChanged(position)
        }
        CollectionHelper.saveCollection(context, collection)
    }

    private fun isPositionFooter(position: Int): Boolean {
        return position == collection.stations.size
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun updateRecyclerView(oldCollection: Collection, newCollection: Collection) {
        collection = newCollection
        if (oldCollection.stations.size == 0 && newCollection.stations.size > 0) {
            notifyDataSetChanged()
        } else {
            val diffResult =
                DiffUtil.calculateDiff(CollectionDiffCallback(oldCollection, newCollection), true)
            diffResult.dispatchUpdatesTo(this@CollectionAdapter)
        }
    }

    private fun saveStationListExpandedState(
        position: Int = -1,
        stationStreamUri: String = String()
    ) {
        expandedStationUuid = stationStreamUri
        expandedStationPosition = position
        PreferencesHelper.saveStationListStreamUuid(expandedStationUuid)
    }

    private fun observeCollectionViewModel(owner: LifecycleOwner) {
        collectionViewModel.collectionLiveData.observe(owner) { newCollection ->
            updateRecyclerView(collection, newCollection)
        }
    }

    private val sharedPreferenceChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                Keys.PREF_EDIT_STATIONS -> editStationsEnabled =
                    PreferencesHelper.loadEditStationsEnabled()
                Keys.PREF_EDIT_STREAMS_URIS -> editStationStreamsEnabled =
                    PreferencesHelper.loadEditStreamUrisEnabled()
            }
        }

    private inner class AddNewViewHolder(listItemAddNewLayout: View) :
        RecyclerView.ViewHolder(listItemAddNewLayout) {
        val addNewStationView: ExtendedFloatingActionButton =
            listItemAddNewLayout.findViewById(R.id.card_add_new_station)
    }

    private inner class StationViewHolder(stationCardLayout: View) :
        RecyclerView.ViewHolder(stationCardLayout) {
        val stationCardView: CardView = stationCardLayout.findViewById(R.id.station_card)
        val stationImageView: ImageView = stationCardLayout.findViewById(R.id.station_icon)
        val stationNameView: TextView = stationCardLayout.findViewById(R.id.station_name)
        val stationStarredView: ImageView = stationCardLayout.findViewById(R.id.starred_icon)
        val playButtonView: ImageView = stationCardLayout.findViewById(R.id.playback_button)
        val editViews: Group = stationCardLayout.findViewById(R.id.default_edit_views)
        val stationImageChangeView: ImageView =
            stationCardLayout.findViewById(R.id.change_image_view)
        val stationNameEditView: TextInputEditText =
            stationCardLayout.findViewById(R.id.edit_station_name)
        val stationUriEditView: TextInputEditText =
            stationCardLayout.findViewById(R.id.edit_stream_uri)
        val placeOnHomeScreenButton: MaterialButton =
            stationCardLayout.findViewById(R.id.place_on_home_screen_button)
        val cancelButton: MaterialButton = stationCardLayout.findViewById(R.id.cancel_button)
        val saveButton: MaterialButton = stationCardLayout.findViewById(R.id.save_button)
    }

    private inner class CollectionDiffCallback(
        val oldCollection: Collection,
        val newCollection: Collection
    ) : DiffUtil.Callback() {

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldStation: Station = oldCollection.stations[oldItemPosition]
            val newStation: Station = newCollection.stations[newItemPosition]
            return oldStation.uuid == newStation.uuid
        }

        override fun getOldListSize(): Int {
            return oldCollection.stations.size
        }

        override fun getNewListSize(): Int {
            return newCollection.stations.size
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldStation: Station = oldCollection.stations[oldItemPosition]
            val newStation: Station = newCollection.stations[newItemPosition]
            if (oldStation.isPlaying != newStation.isPlaying) return false
            if (oldStation.uuid != newStation.uuid) return false
            if (oldStation.starred != newStation.starred) return false
            if (oldStation.name != newStation.name) return false
            if (oldStation.stream != newStation.stream) return false
            if (oldStation.remoteImageLocation != newStation.remoteImageLocation) return false
            if (oldStation.remoteStationLocation != newStation.remoteStationLocation) return false
            if (!oldStation.streamUris.containsAll(newStation.streamUris)) return false
            if (oldStation.imageColor != newStation.imageColor) return false
            if (FileHelper.getFileSize(context, oldStation.image.toUri()) != FileHelper.getFileSize(
                    context,
                    newStation.image.toUri()
                )
            ) return false
            return FileHelper.getFileSize(
                context,
                oldStation.smallImage.toUri()
            ) == FileHelper.getFileSize(context, newStation.smallImage.toUri())
        }
    }
}