package com.yaros.RadioUrl

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.TaskStackBuilder
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.media.audiofx.AudioEffect
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.service.media.MediaBrowserService.BrowserRoot.EXTRA_RECENT
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Metadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultAllocator
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.yaros.RadioUrl.core.Collection
import com.yaros.RadioUrl.helpers.AudioHelper
import com.yaros.RadioUrl.helpers.CollectionHelper
import com.yaros.RadioUrl.helpers.FileHelper
import com.yaros.RadioUrl.helpers.NetworkHelper
import com.yaros.RadioUrl.helpers.PreferencesHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Date

@UnstableApi
class PlayerService : MediaLibraryService() {
    private val TAG: String = PlayerService::class.java.simpleName
    private lateinit var player: Player
    private lateinit var mediaLibrarySession: MediaLibrarySession
    private lateinit var sleepTimer: CountDownTimer
    var sleepTimerTimeRemaining: Long = 0L
    private var sleepTimerEndTime: Long = 0L
    private val librarySessionCallback = CustomMediaLibrarySessionCallback()
    private var collection: Collection = Collection()
    private lateinit var metadataHistory: MutableList<String>
    private var bufferSizeMultiplier: Int = PreferencesHelper.loadBufferSizeMultiplier()
    private var playbackRestartCounter: Int = 0
    private var playLastStation: Boolean = false
    private var manuallyCancelledSleepTimer = false
    private val notificationId = 154
    private val notificationChannelId = "pscid"

    override fun onCreate() {
    super.onCreate()
    Timber.tag(TAG).d("onCreatePlayerService: Start")  // Логирование для отслеживания

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && ContextCompat.isBackgroundRestricted(this)) {
        Timber.tag(TAG).e("App is in restricted background state, cannot start foreground service")
        return
    }

    try {
        val notification = createNotification(notificationChannelId)
        startForeground(notificationId, notification)
    } catch (e: ForegroundServiceStartNotAllowedException) {
        Timber.tag(TAG).e(e, "Failed to start foreground service")
        Toast.makeText(this, "Failed to start foreground service", Toast.LENGTH_LONG).show()
        // Альтернативные способы уведомления пользователя
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, notification)
    }

    collection = FileHelper.readCollection(this)
    LocalBroadcastManager.getInstance(application).registerReceiver(
        collectionChangedReceiver,
        IntentFilter(Keys.ACTION_COLLECTION_CHANGED)
    )
    Timber.tag(TAG).d("onCreate: Initializing player and session")
    initializePlayer()
    initializeSession()
    val notificationProvider = CustomNotificationProvider()
    setMediaNotificationProvider(notificationProvider)
    metadataHistory = PreferencesHelper.loadMetadataHistory()

    val prefs = getSharedPreferences("PermissionPrefs", MODE_PRIVATE)
    prefs.registerOnSharedPreferenceChangeListener(sharedPreferenceChangeListener)
}

    private fun createNotification(channelId: String): Notification {
        // Создание канала уведомлений для Android O и выше
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel = NotificationChannel(
                channelId,
                "PlayerService",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(notificationChannel)
        }

        // Создание основного уведомления
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.player_ready_notification))
            .setContentText(getString(R.string.player_ready_notification_midl))
            .setSmallIcon(R.drawable.ic_notification_app_icon_white_24dp)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    override fun onDestroy() {
        player.removeListener(playerListener)
        player.release()
        mediaLibrarySession.release()
        LocalBroadcastManager.getInstance(application).unregisterReceiver(collectionChangedReceiver)
        val prefs = getSharedPreferences("PermissionPrefs", MODE_PRIVATE)
        prefs.unregisterOnSharedPreferenceChangeListener(sharedPreferenceChangeListener)
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady) {
            stopSelf()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession {
        return mediaLibrarySession
    }

    private fun initializePlayer() {
        val dataSourceFactory = OkHttpDataSource.Factory(NetworkHelper.client)

        // Настройка и инициализация ExoPlayer
        val exoPlayer: ExoPlayer = ExoPlayer.Builder(this).apply {
            setAudioAttributes(AudioAttributes.DEFAULT, true)
            setHandleAudioBecomingNoisy(true)
            setLoadControl(createDefaultLoadControl(bufferSizeMultiplier))
            if (true) {
                setMediaSourceFactory(
                    DefaultMediaSourceFactory(dataSourceFactory)
                        .setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
                )
            }
        }.build()

        // Добавление слушателей
        exoPlayer.addAnalyticsListener(analyticsListener)
        exoPlayer.addListener(playerListener)

        // Обертывание проигрывателя
        player = object : ForwardingPlayer(exoPlayer) {
            override fun getAvailableCommands(): Player.Commands {
                return super.availableCommands.buildUpon()
                    .add(COMMAND_SEEK_TO_NEXT)
                    .add(COMMAND_SEEK_TO_PREVIOUS)
                    .build()
            }

            override fun isCommandAvailable(command: Int): Boolean {
                return availableCommands.contains(command)
            }

            override fun getDuration(): Long {
                return C.TIME_UNSET
            }
        }
    }


    private fun initializeSession() {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = TaskStackBuilder.create(this).run {
            addNextIntent(intent)
            getPendingIntent(
                0,
                if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_IMMUTABLE else PendingIntent.FLAG_CANCEL_CURRENT
            )
        }

        mediaLibrarySession =
            MediaLibrarySession.Builder(this, player, librarySessionCallback).apply {
                setSessionActivity(pendingIntent)
            }.build()
    }

    private fun createDefaultLoadControl(factor: Int): DefaultLoadControl {
        val builder = DefaultLoadControl.Builder()
        builder.setAllocator(DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE))
        builder.setBufferDurationsMs(
            DefaultLoadControl.DEFAULT_MIN_BUFFER_MS * factor,
            DefaultLoadControl.DEFAULT_MAX_BUFFER_MS * factor,
            DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS * factor,
            DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS * factor
            )
        return builder.build()
    }

    private fun adjustBufferingFactor(networkSpeed: Int): Int {
        return when {
            networkSpeed < 100 -> 2 // Low speed
            networkSpeed < 500 -> 1 // Medium speed
            else -> 0 // High speed
        }
    }

    private fun updateBufferingSettings() {
        val networkSpeed = NetworkHelper.getNetworkSpeed(this)
        val newFactor = adjustBufferingFactor(networkSpeed)
        if (newFactor != bufferSizeMultiplier) {
            bufferSizeMultiplier = newFactor
            initializePlayer()
        }
    }

    private fun startSleepTimer(selectedTimeMillis: Long) {
        if (sleepTimerTimeRemaining > 0L && this::sleepTimer.isInitialized) {
            sleepTimer.cancel()
        }

        sleepTimerEndTime = System.currentTimeMillis() + selectedTimeMillis

        sleepTimer = object : CountDownTimer(selectedTimeMillis, 1000) {
            override fun onFinish() {
                Timber.tag(TAG).v("Sleep timer finished. Sweet dreams.")
                sleepTimerTimeRemaining = 0L
                player.stop()
            }

            override fun onTick(millisUntilFinished: Long) {
                sleepTimerTimeRemaining = millisUntilFinished
            }
        }
        sleepTimer.start()
        PreferencesHelper.saveSleepTimerRunning(isRunning = true)
    }

    private fun cancelSleepTimer() {
        if (this::sleepTimer.isInitialized) {
            if (manuallyCancelledSleepTimer) {
                sleepTimerTimeRemaining = 0L
                sleepTimer.cancel()
            }
            manuallyCancelledSleepTimer = false
        }
        PreferencesHelper.saveSleepTimerRunning(isRunning = false)
    }

    fun manuallyCancelSleepTimer() {
        manuallyCancelledSleepTimer = true
        cancelSleepTimer()
    }

    private fun updateMetadata(metadata: String = String()) {
        val metadataString: String = metadata.ifEmpty {
            player.currentMediaItem?.mediaMetadata?.artist.toString()
        }
        if (metadataHistory.contains(metadataString)) {
            metadataHistory.removeAll { it == metadataString }
        }
        metadataHistory.add(metadataString)
        if (metadataHistory.size > Keys.DEFAULT_SIZE_OF_METADATA_HISTORY) {
            metadataHistory.removeAt(0)
        }
        PreferencesHelper.saveMetadataHistory(metadataHistory)
    }

    private fun loadCollection(context: Context) {
        Timber.tag(TAG).v("Loading collection of stations from storage")
        CoroutineScope(Main).launch {
            val deferred: Deferred<Collection> =
                async(Dispatchers.Default) { FileHelper.readCollectionSuspended(context) }
            collection = deferred.await()
//            // special case: trigger metadata view update for stations that have no metadata
//           if (player.isPlaying && station.name == getCurrentMetadata()) {
//                station = CollectionHelper.getStation(collection, station.uuid)
//                updateMetadata(null)
//            }
        }
    }

    private inner class CustomMediaLibrarySessionCallback : MediaLibrarySession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<List<MediaItem>> {
            val updatedMediaItems: List<MediaItem> =
                mediaItems.map { mediaItem ->
                    CollectionHelper.getItem(this@PlayerService, collection, mediaItem.mediaId)
//                    if (mediaItem.requestMetadata.searchQuery != null)
//                        getMediaItemFromSearchQuery(mediaItem.requestMetadata.searchQuery!!)
//                    else MediaItemTree.getItem(mediaItem.mediaId) ?: mediaItem
                }

            return Futures.immediateFuture(updatedMediaItems)

//            val updatedMediaItems = mediaItems.map { mediaItem ->
//                mediaItem.buildUpon().apply {
//                    setUri(mediaItem.requestMetadata.mediaUri)
//                }.build()
//            }
//            return Futures.immediateFuture(updatedMediaItems)
        }

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val connectionResult: MediaSession.ConnectionResult =
                super.onConnect(session, controller)
            val builder: SessionCommands.Builder =
                connectionResult.availableSessionCommands.buildUpon()
            builder.add(SessionCommand(Keys.CMD_START_SLEEP_TIMER, Bundle.EMPTY))
            builder.add(SessionCommand(Keys.CMD_CANCEL_SLEEP_TIMER, Bundle.EMPTY))
            builder.add(SessionCommand(Keys.CMD_REQUEST_SLEEP_TIMER_REMAINING, Bundle.EMPTY))
            builder.add(SessionCommand(Keys.CMD_REQUEST_METADATA_HISTORY, Bundle.EMPTY))
            return MediaSession.ConnectionResult.accept(
                builder.build(),
                connectionResult.availablePlayerCommands
            )
        }

        override fun onSubscribe(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<Void>> {
            val children: List<MediaItem> =
                CollectionHelper.getChildren(this@PlayerService, collection)
            session.notifyChildrenChanged(browser, parentId, children.size, params)
            return Futures.immediateFuture(LibraryResult.ofVoid())
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val children: List<MediaItem> =
                CollectionHelper.getChildren(this@PlayerService, collection)
            return Futures.immediateFuture(LibraryResult.ofItemList(children, params))
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            return if (params?.extras?.containsKey(EXTRA_RECENT) == true) {
                playLastStation = true
                Futures.immediateFuture(
                    LibraryResult.ofItem(
                        CollectionHelper.getRecent(
                            this@PlayerService,
                            collection
                        ), params
                    )
                )
            } else {
                Futures.immediateFuture(
                    LibraryResult.ofItem(
                        CollectionHelper.getRootItem(),
                        params
                    )
                )
            }
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val item: MediaItem = CollectionHelper.getItem(this@PlayerService, collection, mediaId)
            return Futures.immediateFuture(LibraryResult.ofItem(item, /* params = */ null))
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                Keys.CMD_START_SLEEP_TIMER -> {
                    val selectedTimeMillis = args.getLong(Keys.SLEEP_TIMER_DURATION)
                    startSleepTimer(selectedTimeMillis)
                }

                Keys.CMD_CANCEL_SLEEP_TIMER -> {
                    manuallyCancelSleepTimer()
                }

                Keys.CMD_REQUEST_SLEEP_TIMER_REMAINING -> {
                    val resultBundle = Bundle()
                    resultBundle.putLong(Keys.EXTRA_SLEEP_TIMER_REMAINING, sleepTimerTimeRemaining)
                    return Futures.immediateFuture(
                        SessionResult(
                            SessionResult.RESULT_SUCCESS,
                            resultBundle
                        )
                    )
                }

                Keys.CMD_REQUEST_METADATA_HISTORY -> {
                    val resultBundle = Bundle()
                    resultBundle.putStringArrayList(
                        Keys.EXTRA_METADATA_HISTORY,
                        ArrayList(metadataHistory)
                    )
                    return Futures.immediateFuture(
                        SessionResult(
                            SessionResult.RESULT_SUCCESS,
                            resultBundle
                        )
                    )
                }
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }

        override fun onPlayerCommandRequest(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            playerCommand: Int
        ): Int {
            // playerCommand = one of COMMAND_PLAY_PAUSE, COMMAND_PREPARE, COMMAND_STOP, COMMAND_SEEK_TO_DEFAULT_POSITION, COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM, COMMAND_SEEK_TO_PREVIOUS, COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, COMMAND_SEEK_TO_NEXT, COMMAND_SEEK_TO_MEDIA_ITEM, COMMAND_SEEK_BACK, COMMAND_SEEK_FORWARD, COMMAND_SET_SPEED_AND_PITCH, COMMAND_SET_SHUFFLE_MODE, COMMAND_SET_REPEAT_MODE, COMMAND_GET_CURRENT_MEDIA_ITEM, COMMAND_GET_TIMELINE, COMMAND_GET_MEDIA_ITEMS_METADATA, COMMAND_SET_MEDIA_ITEMS_METADATA, COMMAND_CHANGE_MEDIA_ITEMS, COMMAND_GET_AUDIO_ATTRIBUTES, COMMAND_GET_VOLUME, COMMAND_GET_DEVICE_VOLUME, COMMAND_SET_VOLUME, COMMAND_SET_DEVICE_VOLUME, COMMAND_ADJUST_DEVICE_VOLUME, COMMAND_SET_VIDEO_SURFACE, COMMAND_GET_TEXT, COMMAND_SET_TRACK_SELECTION_PARAMETERS or COMMAND_GET_TRACK_INFOS. */
            // emulate headphone buttons
            // start/pause: adb shell input keyevent 85
            // next: adb shell input keyevent 87
            // prev: adb shell input keyevent 88
            when (playerCommand) {
                Player.COMMAND_SEEK_TO_NEXT -> {
                    player.addMediaItem(
                        CollectionHelper.getNextMediaItem(
                            this@PlayerService,
                            collection,
                            player.currentMediaItem?.mediaId ?: String()
                        )
                    )
                    player.prepare()
                    player.play()
                    return SessionResult.RESULT_SUCCESS
                }

                Player.COMMAND_SEEK_TO_PREVIOUS -> {
                    player.addMediaItem(
                        CollectionHelper.getPreviousMediaItem(
                            this@PlayerService,
                            collection,
                            player.currentMediaItem?.mediaId ?: String()
                        )
                    )
                    player.prepare()
                    player.play()
                    return SessionResult.RESULT_SUCCESS
                }

                Player.COMMAND_PREPARE -> {
                    return if (playLastStation) {
                        player.addMediaItem(
                            CollectionHelper.getRecent(
                                this@PlayerService,
                                collection
                            )
                        )
                        player.prepare()
                        playLastStation = false
                        SessionResult.RESULT_SUCCESS
                    } else {
                        super.onPlayerCommandRequest(session, controller, playerCommand)
                    }
                }

                Player.COMMAND_PLAY_PAUSE -> {
                    return if (player.isPlaying) {
                        super.onPlayerCommandRequest(session, controller, playerCommand)
                    } else {
                        player.seekTo(0)
                        SessionResult.RESULT_SUCCESS
                    }
                }
//                Player.COMMAND_PLAY_PAUSE -> {
//                    // override pause with stop, to prevent unnecessary buffering
//                    if (player.isPlaying) {
//                        player.stop()
//                        return SessionResult.RESULT_INFO_SKIPPED
//                    } else {
//                       return super.onPlayerCommandRequest(session, controller, playerCommand)
//                    }
//                }
                else -> {
                    return super.onPlayerCommandRequest(session, controller, playerCommand)
                }
            }
        }
    }

    private inner class CustomNotificationProvider :
        DefaultMediaNotificationProvider(this@PlayerService) {

        override fun getMediaButtons(
            session: MediaSession,
            playerCommands: Player.Commands,
            customLayout: ImmutableList<CommandButton>,
            showPauseButton: Boolean
        ): ImmutableList<CommandButton> {

            val commandButtons: MutableList<CommandButton> = mutableListOf()

            // Команда "Предыдущий трек"
            if (playerCommands.contains(Player.COMMAND_SEEK_TO_PREVIOUS)) {
                commandButtons.add(
                    createCommandButton(
                        Player.COMMAND_SEEK_TO_PREVIOUS,
                        R.drawable.ic_notification_skip_to_previous_36dp
                    )
                )
            }

            // Команда "Плей/Пауза"
            if (playerCommands.contains(Player.COMMAND_PLAY_PAUSE)) {
                val playPauseIcon = getPlayPauseIcon(player.isPlaying)
                commandButtons.add(createCommandButton(Player.COMMAND_PLAY_PAUSE, playPauseIcon))
            }

            // Команда "Следующий трек"
            if (playerCommands.contains(Player.COMMAND_SEEK_TO_NEXT)) {
                commandButtons.add(
                    createCommandButton(
                        Player.COMMAND_SEEK_TO_NEXT,
                        R.drawable.ic_notification_skip_to_next_36dp
                    )
                )
            }

            return ImmutableList.copyOf(commandButtons)
        }

        private fun createCommandButton(command: Int, iconResId: Int): CommandButton {
            return CommandButton.Builder().apply {
                setPlayerCommand(command)
                setIconResId(iconResId)
                setEnabled(true)
            }.build()
        }

        private fun getPlayPauseIcon(isPlaying: Boolean): Int {
            return if (isPlaying) R.drawable.ic_notification_stop_36dp else R.drawable.ic_notification_play_36dp
        }
    }

    private var playerListener: Player.Listener = object : Player.Listener {

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            super.onIsPlayingChanged(isPlaying)
            val currentMediaId: String = player.currentMediaItem?.mediaId ?: String()
            PreferencesHelper.saveIsPlaying(isPlaying)
            PreferencesHelper.saveCurrentStationId(currentMediaId)
            playbackRestartCounter = 0

            collection = CollectionHelper.savePlaybackState(
                this@PlayerService,
                collection,
                currentMediaId,
                isPlaying
            )
            //updatePlayerState(station, playbackState)

            if (isPlaying) {
            } else {
                cancelSleepTimer()
                updateMetadata()
                when (player.playbackState) {
                    Player.STATE_READY -> {
                        // todo
                    }

                    Player.STATE_BUFFERING -> {
                        // todo
                    }

                    Player.STATE_ENDED -> {
                        // todo
                    }

                    Player.STATE_IDLE -> {
                        // todo
                    }
                }
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            super.onPlayWhenReadyChanged(playWhenReady, reason)
            if (!playWhenReady) {
                when (reason) {
                    Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM -> {
                    }

                    else -> {
                        // playback has been paused by user or OS: update media session and save state
                        // PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST or
                        // PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS or
                        // PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY or
                        // PLAY_WHEN_READY_CHANGE_REASON_REMOTE
                        // handlePlaybackChange(PlaybackStateCompat.STATE_PAUSED)
                    }
                }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            super.onPlayerError(error)
            Timber.tag(TAG).d("PlayerError occurred: ${error.errorCodeName}")
            Toast.makeText(
            applicationContext,
            "Error occurred: ${error.errorCodeName}",
            Toast.LENGTH_LONG
            ).show()
        }

        override fun onMetadata(metadata: Metadata) {
            super.onMetadata(metadata)
            updateMetadata(AudioHelper.getMetadataString(metadata))
        }

    }

    private val loadErrorHandlingPolicy: DefaultLoadErrorHandlingPolicy =
    object : DefaultLoadErrorHandlingPolicy() {
        override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
            if (loadErrorInfo.errorCount <= Keys.DEFAULT_MAX_RECONNECTION_COUNT && loadErrorInfo.exception is HttpDataSource.HttpDataSourceException) {
                Toast.makeText(
                    applicationContext,
                    "Failed to connect to the radio station. Retrying...",
                    Toast.LENGTH_LONG
                ).show()
                return Keys.RECONNECTION_WAIT_INTERVAL
            }
            return C.TIME_UNSET
        }

        override fun getMinimumLoadableRetryCount(dataType: Int): Int {
            return Int.MAX_VALUE
        }
    }

    private val collectionChangedReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.hasExtra(Keys.EXTRA_COLLECTION_MODIFICATION_DATE)) {
                val date = Date(intent.getLongExtra(Keys.EXTRA_COLLECTION_MODIFICATION_DATE, 0L))

                if (date.after(collection.modificationDate)) {
                    Timber.tag(TAG).v("PlayerService - reload collection after broadcast received.")
                    loadCollection(context)
                }
            }
        }
    }

    private val sharedPreferenceChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                Keys.PREF_LARGE_BUFFER_SIZE -> {
                    bufferSizeMultiplier = PreferencesHelper.loadBufferSizeMultiplier()
                    if (!player.isPlaying && !player.isLoading) {
                        initializePlayer()
                    }
                }
            }
        }

    private val analyticsListener = object : AnalyticsListener {
        override fun onAudioSessionIdChanged(
            eventTime: AnalyticsListener.EventTime,
            audioSessionId: Int
        ) {
            super.onAudioSessionIdChanged(eventTime, audioSessionId)
            val intent = Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
            intent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId)
            intent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
            intent.putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
            sendBroadcast(intent)
        }
    }
}
