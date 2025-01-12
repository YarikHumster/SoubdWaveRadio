package com.yaros.RadioUrl.ui

import android.os.Parcelable
import com.google.gson.annotations.Expose
import kotlinx.parcelize.Parcelize

@Parcelize
data class PlayerState(
    @Expose var stationUuid: String = String(),
    @Expose var isPlaying: Boolean = false,
    @Expose var sleepTimerRunning: Boolean = false
) : Parcelable
