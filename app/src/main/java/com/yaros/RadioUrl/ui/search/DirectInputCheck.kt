package com.yaros.RadioUrl.ui.search

import android.content.Context
import android.webkit.URLUtil
import android.widget.Toast
import com.yaros.RadioUrl.R
import com.yaros.RadioUrl.core.Station
import com.yaros.RadioUrl.helpers.CollectionHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.GregorianCalendar


data class IcecastMetadata(
    val title: String?
)
class DirectInputCheck(private var directInputCheckListener: DirectInputCheckListener) {

    interface DirectInputCheckListener {
        fun onDirectInputCheck(stationList: MutableList<Station>) {
        }
    }


    private var lastCheckedAddress: String = String()


    fun checkStationAddress(context: Context, query: String) {
        if (URLUtil.isValidUrl(query)) {
            val stationList: MutableList<Station> = mutableListOf()
            CoroutineScope(IO).launch {
                stationList.addAll(CollectionHelper.createStationsFromUrl(query, lastCheckedAddress))
                lastCheckedAddress = query
                withContext(Main) {
                    if (stationList.isNotEmpty()) {
                        directInputCheckListener.onDirectInputCheck(stationList)
                    } else {
                        Toast.makeText(context, R.string.toastmessage_station_not_valid, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }


    private suspend fun extractIcecastMetadata(streamUri: String): IcecastMetadata {
        return withContext(IO) {
            val client = OkHttpClient()
            val request = Request.Builder()
                .url(streamUri)
                .build()

            val response = client.newCall(request).execute()
            val icecastHeaders = response.headers

            val title = icecastHeaders["icy-name"]

            IcecastMetadata(title?.takeIf { it.isNotEmpty() } ?: streamUri)
        }
    }


    private suspend fun updateStationWithIcecastMetadata(station: Station, icecastMetadata: IcecastMetadata) {
        withContext(Dispatchers.Default) {
            station.name = icecastMetadata.title.toString()
        }
    }

    suspend fun processIcecastStream(streamUri: String, stationList: MutableList<Station>) {
        val icecastMetadata = extractIcecastMetadata(streamUri)
        val station = Station(
            name = icecastMetadata.title.toString(),
            streamUris = mutableListOf(streamUri),
            modificationDate = GregorianCalendar.getInstance().time,
        )
        updateStationWithIcecastMetadata(station, icecastMetadata)
        if (lastCheckedAddress != streamUri) {
            stationList.add(station)
        }
        lastCheckedAddress = streamUri
    }

}