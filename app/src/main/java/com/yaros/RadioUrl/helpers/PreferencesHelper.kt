package com.yaros.RadioUrl.helpers

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.google.gson.Gson
import com.yaros.RadioUrl.Keys
import com.yaros.RadioUrl.ui.PlayerState
import timber.log.Timber
import java.util.*


object PreferencesHelper {

    private val TAG: String = PreferencesHelper::class.java.simpleName

    private lateinit var sharedPreferences: SharedPreferences

    fun Context.initPreferences() {
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
    }

    fun loadRadioBrowserApiAddress(): String {
        return sharedPreferences.getString(
            Keys.PREF_RADIO_BROWSER_API,
            Keys.RADIO_BROWSER_API_DEFAULT
        ) ?: Keys.RADIO_BROWSER_API_DEFAULT
    }

    fun saveRadioBrowserApiAddress(radioBrowserApi: String) {
        sharedPreferences.edit {
            putString(Keys.PREF_RADIO_BROWSER_API, radioBrowserApi)
        }
    }

    fun saveIsPlaying(isPlaying: Boolean) {
        sharedPreferences.edit {
            putBoolean(Keys.PREF_PLAYER_STATE_IS_PLAYING, isPlaying)
        }
    }

    fun loadStationListStreamUuid(): String {
        return sharedPreferences.getString(Keys.PREF_STATION_LIST_EXPANDED_UUID, String())
            ?: String()
    }

    fun saveStationListStreamUuid(stationUuid: String = String()) {
        sharedPreferences.edit {
            putString(Keys.PREF_STATION_LIST_EXPANDED_UUID, stationUuid)
        }
    }

    fun saveLastUpdateCollection(lastUpdate: Date = Calendar.getInstance().time) {
        sharedPreferences.edit {
            putString(Keys.PREF_LAST_UPDATE_COLLECTION, DateTimeHelper.convertToRfc2822(lastUpdate))
        }
    }

    fun loadCollectionSize(): Int {
        return sharedPreferences.getInt(Keys.PREF_COLLECTION_SIZE, -1)
    }

    fun saveCollectionSize(size: Int) {
        sharedPreferences.edit {
            putInt(Keys.PREF_COLLECTION_SIZE, size)
        }
    }

    fun saveSleepTimerRunning(isRunning: Boolean) {
        sharedPreferences.edit {
            putBoolean(Keys.PREF_PLAYER_STATE_SLEEP_TIMER_RUNNING, isRunning)
        }
    }

    fun loadCollectionModificationDate(): Date {
        val modificationDateString: String =
            sharedPreferences.getString(Keys.PREF_COLLECTION_MODIFICATION_DATE, "") ?: String()
        return DateTimeHelper.convertFromRfc2822(modificationDateString)
    }

    fun saveCollectionModificationDate(lastSave: Date = Calendar.getInstance().time) {
        sharedPreferences.edit {
            putString(
                Keys.PREF_COLLECTION_MODIFICATION_DATE,
                DateTimeHelper.convertToRfc2822(lastSave)
            )
        }
    }

    fun loadActiveDownloads(): String {
        val activeDownloadsString: String =
            sharedPreferences.getString(Keys.PREF_ACTIVE_DOWNLOADS, Keys.ACTIVE_DOWNLOADS_EMPTY)
                ?: Keys.ACTIVE_DOWNLOADS_EMPTY
        Timber.tag(TAG).v("IDs of active downloads: $activeDownloadsString")
        return activeDownloadsString
    }

    fun saveActiveDownloads(activeDownloadsString: String = String()) {
        sharedPreferences.edit {
            putString(Keys.PREF_ACTIVE_DOWNLOADS, activeDownloadsString)
        }
    }

    fun loadPlayerState(): PlayerState {
        return PlayerState().apply {
            stationUuid = sharedPreferences.getString(Keys.PREF_PLAYER_STATE_STATION_UUID, String())
                ?: String()
            isPlaying = sharedPreferences.getBoolean(Keys.PREF_PLAYER_STATE_IS_PLAYING, false)
            sleepTimerRunning =
                sharedPreferences.getBoolean(Keys.PREF_PLAYER_STATE_SLEEP_TIMER_RUNNING, false)
        }
    }

    fun saveCurrentStationId(stationUuid: String) {
        sharedPreferences.edit {
            putString(Keys.PREF_PLAYER_STATE_STATION_UUID, stationUuid)
        }
    }

    fun loadLastPlayedStationUuid(): String {
        return sharedPreferences.getString(Keys.PREF_PLAYER_STATE_STATION_UUID, String())
            ?: String()
    }

    fun saveMetadataHistory(metadataHistory: MutableList<String>) {
        val gson = Gson()
        val json = gson.toJson(metadataHistory)
        sharedPreferences.edit {
            putString(Keys.PREF_PLAYER_METADATA_HISTORY, json)
        }
    }

    fun loadMetadataHistory(): MutableList<String> {
        var metadataHistory: MutableList<String> = mutableListOf()
        val json: String =
            sharedPreferences.getString(Keys.PREF_PLAYER_METADATA_HISTORY, String()) ?: String()
        if (json.isNotEmpty()) {
            val gson = Gson()
            metadataHistory = gson.fromJson(json, metadataHistory::class.java)
        }
        return metadataHistory
    }

    fun registerPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener)
    }

    fun isHouseKeepingNecessary(): Boolean {
        return sharedPreferences.getBoolean(Keys.PREF_ONE_TIME_HOUSEKEEPING_NECESSARY, true)
    }

    fun saveHouseKeepingNecessaryState(state: Boolean = false) {
        sharedPreferences.edit {
            putBoolean(Keys.PREF_ONE_TIME_HOUSEKEEPING_NECESSARY, state)
        }
    }

    fun loadThemeSelection(): String {
        return sharedPreferences.getString(
            Keys.PREF_THEME_SELECTION,
            Keys.STATE_THEME_FOLLOW_SYSTEM
        ) ?: Keys.STATE_THEME_FOLLOW_SYSTEM
    }

    fun loadEditStationsEnabled(): Boolean {
        return sharedPreferences.getBoolean(Keys.PREF_EDIT_STATIONS, true)
    }

    fun saveEditStationsEnabled(enabled: Boolean = false) {
        sharedPreferences.edit {
            putBoolean(Keys.PREF_EDIT_STATIONS, enabled)
        }
    }

    fun loadEditStreamUrisEnabled(): Boolean {
        return sharedPreferences.getBoolean(Keys.PREF_EDIT_STREAMS_URIS, true)
    }

    fun loadLargeBufferSize(): Boolean {
        return sharedPreferences.getBoolean(Keys.PREF_LARGE_BUFFER_SIZE, false)
    }

    fun loadBufferSizeMultiplier(): Int {
        return if (sharedPreferences.getBoolean(Keys.PREF_LARGE_BUFFER_SIZE, false)) {
            Keys.LARGE_BUFFER_SIZE_MULTIPLIER
        } else {
            1
        }
    }

    fun downloadOverMobile(): Boolean {
        return sharedPreferences.getBoolean(
            Keys.PREF_DOWNLOAD_OVER_MOBILE,
            Keys.DEFAULT_DOWNLOAD_OVER_MOBILE
        )
    }
}
