package com.yaros.RadioUrl.helpers

import android.content.Context
import android.util.Log
import com.yaros.RadioUrl.Keys
import com.yaros.RadioUrl.core.Collection
import com.yaros.RadioUrl.core.Station
import com.yaros.RadioUrl.ui.search.RadioBrowserResult
import com.yaros.RadioUrl.ui.search.RadioBrowserSearch
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import timber.log.Timber

class UpdateHelper(
    private val context: Context,
    private val updateHelperListener: UpdateHelperListener,
    private var collection: Collection
) : RadioBrowserSearch.RadioBrowserSearchListener {


    private val TAG: String = UpdateHelper::class.java.simpleName

    private var radioBrowserSearchCounter: Int = 0
    private var remoteStationLocationsList: MutableList<String> = mutableListOf()

    interface UpdateHelperListener {
        fun onStationUpdated(
            collection: Collection,
            positionPriorUpdate: Int,
            positionAfterUpdate: Int
        )
    }


    override fun onRadioBrowserSearchResults(results: Array<RadioBrowserResult>) {
        if (results.isNotEmpty()) {
            CoroutineScope(IO).launch {
                val station: Station = results[0].toStation()
                val deferred: Deferred<NetworkHelper.ContentType> =
                    async(Dispatchers.Default) { NetworkHelper.detectContentTypeSuspended(station.getStreamUri()) }
                val contentType: NetworkHelper.ContentType = deferred.await()
                station.streamContent = contentType.type
                val positionPriorUpdate =
                    CollectionHelper.getStationPositionFromRadioBrowserStationUuid(
                        collection,
                        station.radioBrowserStationUuid
                    )
                collection = CollectionHelper.updateStation(context, collection, station)
                val positionAfterUpdate: Int =
                    CollectionHelper.getStationPositionFromRadioBrowserStationUuid(
                        collection,
                        station.radioBrowserStationUuid
                    )
                withContext(Main) {
                    updateHelperListener.onStationUpdated(
                        collection,
                        positionPriorUpdate,
                        positionAfterUpdate
                    )
                }
                radioBrowserSearchCounter--
                if (radioBrowserSearchCounter == 0 && remoteStationLocationsList.isNotEmpty()) {
                    DownloadHelper.downloadPlaylists(
                        context,
                        remoteStationLocationsList.toTypedArray()
                    )
                }
            }
        }
    }


    fun updateCollection() {
        PreferencesHelper.saveLastUpdateCollection()
        collection.stations.forEach { station ->
            when {
                station.radioBrowserStationUuid.isNotEmpty() -> {
                    radioBrowserSearchCounter++
                    downloadFromRadioBrowser(station.radioBrowserStationUuid)
                }
                station.remoteStationLocation.isNotEmpty() -> {
                    remoteStationLocationsList.add(station.remoteStationLocation)
                }
                else -> {
                    Timber.tag(TAG).w("Unable to update station: ${station.name}.")
                }
            }
        }
        if (radioBrowserSearchCounter == 0) {
            DownloadHelper.downloadPlaylists(context, remoteStationLocationsList.toTypedArray())
        }
    }


    private fun downloadFromRadioBrowser(radioBrowserStationUuid: String) {
        val radioBrowserSearch = RadioBrowserSearch(this)
        radioBrowserSearch.searchStation(context, radioBrowserStationUuid, Keys.SEARCH_TYPE_BY_UUID)
    }

}
