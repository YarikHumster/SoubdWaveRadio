package com.yaros.RadioUrl.helpers

import android.content.Context
import com.yaros.RadioUrl.Keys
import com.yaros.RadioUrl.core.Collection


object ImportHelper {


    fun removeDefaultStationImageUris(context: Context) {
        val collection: Collection = FileHelper.readCollection(context)
        collection.stations.forEach { station ->
            if (station.image == Keys.LOCATION_DEFAULT_STATION_IMAGE) {
                station.image = String()
            }
            if (station.smallImage == Keys.LOCATION_DEFAULT_STATION_IMAGE) {
                station.smallImage = String()
            }
        }
        CollectionHelper.saveCollection(context, collection, async = false)
    }

}
