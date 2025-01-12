package com.yaros.RadioUrl.core.extensions

import android.content.Context
import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.ListenableFuture
import com.yaros.RadioUrl.Keys
import com.yaros.RadioUrl.core.Station
import com.yaros.RadioUrl.helpers.CollectionHelper


fun MediaController.startSleepTimer(timerDurationMillis: Long) {
    val bundle = Bundle().apply {
        putLong(Keys.SLEEP_TIMER_DURATION, timerDurationMillis)
    }
    sendCustomCommand(SessionCommand(Keys.CMD_START_SLEEP_TIMER, bundle), bundle)
}


fun MediaController.cancelSleepTimer() {
    sendCustomCommand(SessionCommand(Keys.CMD_CANCEL_SLEEP_TIMER, Bundle.EMPTY), Bundle.EMPTY)
}


fun MediaController.requestSleepTimerRemaining(): ListenableFuture<SessionResult> {
    return sendCustomCommand(
        SessionCommand(Keys.CMD_REQUEST_SLEEP_TIMER_REMAINING, Bundle.EMPTY),
        Bundle.EMPTY
    )
}


fun MediaController.requestMetadataHistory(): ListenableFuture<SessionResult> {
    return sendCustomCommand(
        SessionCommand(Keys.CMD_REQUEST_METADATA_HISTORY, Bundle.EMPTY),
        Bundle.EMPTY
    )
}


fun MediaController.play(context: Context, station: Station) {
    if (isPlaying) pause()
    setMediaItem(CollectionHelper.buildMediaItem(context, station))
    prepare()
    play()
}

fun MediaController.playStreamDirectly(streamUri: String) {
    sendCustomCommand(
        SessionCommand(Keys.CMD_PLAY_STREAM, Bundle.EMPTY),
        bundleOf(Pair(Keys.KEY_STREAM_URI, streamUri))
    )
}