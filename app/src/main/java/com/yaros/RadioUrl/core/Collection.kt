package com.yaros.RadioUrl.core

import android.os.Parcelable
import androidx.annotation.Keep
import com.google.gson.annotations.Expose
import com.yaros.RadioUrl.Keys
import kotlinx.parcelize.Parcelize
import java.util.*

@Keep
@Parcelize
data class Collection(
    @Expose val version: Int = Keys.CURRENT_COLLECTION_CLASS_VERSION_NUMBER,
    @Expose var stations: MutableList<Station> = mutableListOf(),
    @Expose var modificationDate: Date = Date()
) : Parcelable {

    override fun toString(): String {
        val stringBuilder: StringBuilder = StringBuilder()
        stringBuilder.append("Format version: $version\n")
        stringBuilder.append("Number of stations in collection: ${stations.size}\n\n")
        stations.forEach {
            stringBuilder.append("$it\n")
        }
        return stringBuilder.toString()
    }

    fun deepCopy(): Collection {
        val stationsCopy: MutableList<Station> = mutableListOf()

        stations.forEach { stationsCopy.add(it.deepCopy()) }
        return Collection(
            version = version,
            stations = stationsCopy,
            modificationDate = modificationDate
        )
    }
}
