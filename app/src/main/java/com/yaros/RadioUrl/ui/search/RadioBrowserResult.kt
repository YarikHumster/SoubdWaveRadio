package com.yaros.RadioUrl.ui.search

import com.google.gson.annotations.Expose
import com.yaros.RadioUrl.Keys
import com.yaros.RadioUrl.core.Station
import java.util.*

data class RadioBrowserResult(
    @Expose val changeuuid: String,
    @Expose val stationuuid: String,
    @Expose val name: String,
    @Expose val url: String,
    @Expose val url_resolved: String,
    @Expose val homepage: String,
    @Expose val favicon: String,
    @Expose val bitrate: Int,
    @Expose val codec: String,
    @Expose val countrycode: String,
    @Expose val language: String,
    @Expose val languagecodes: String,
    @Expose val country: String
) {

    fun toStation(): Station = Station(
        starred = false,
        name = name,
        nameManuallySet = false,
        streamUris = mutableListOf(url_resolved),
        stream = 0,
        streamContent = Keys.MIME_TYPE_UNSUPPORTED,
        homepage = homepage,
        image = String(),
        smallImage = String(),
        imageColor = -1,
        imageManuallySet = false,
        remoteImageLocation = favicon,
        remoteStationLocation = url,
        modificationDate = GregorianCalendar.getInstance().time,
        isPlaying = false,
        radioBrowserStationUuid = stationuuid,
        radioBrowserChangeUuid = changeuuid,
        bitrate = bitrate,
        codec = codec,
        countrycode = countrycode,
        language = language,
        languagecodes = languagecodes,
        country = country
    )
}