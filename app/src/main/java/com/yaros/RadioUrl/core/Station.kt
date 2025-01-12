package com.yaros.RadioUrl.core

import android.os.Parcelable
import androidx.annotation.Keep
import com.google.gson.annotations.Expose
import com.yaros.RadioUrl.Keys
import kotlinx.parcelize.Parcelize
import java.util.Date
import java.util.UUID

@Keep
@Parcelize
data class Station(
    @Expose val uuid: String = UUID.randomUUID().toString(),
    @Expose var starred: Boolean = false,
    @Expose var name: String = String(),
    @Expose var nameManuallySet: Boolean = false,
    @Expose var streamUris: MutableList<String> = mutableListOf(),
    @Expose var stream: Int = 0,
    @Expose var streamContent: String = Keys.MIME_TYPE_UNSUPPORTED,
    @Expose var homepage: String = String(),
    @Expose var image: String = String(),
    @Expose var smallImage: String = String(),
    @Expose var imageColor: Int = -1,
    @Expose var imageManuallySet: Boolean = false,
    @Expose var remoteImageLocation: String = String(),
    @Expose var remoteStationLocation: String = String(),
    @Expose var modificationDate: Date = Keys.DEFAULT_DATE,
    @Expose var isPlaying: Boolean = false,
    @Expose var radioBrowserStationUuid: String = String(),
    @Expose var radioBrowserChangeUuid: String = String(),
    @Expose var bitrate: Int = 0,
    @Expose var codec: String = String(),
    @Expose var countrycode: String = String(),
    @Expose var country: String = String(),
    @Expose var language: String = String(),
    @Expose var languagecodes: String = String()
) : Parcelable {

    override fun toString(): String {
        val stringBuilder: StringBuilder = StringBuilder()
        stringBuilder.append("Name: ${name}\n")
        if (streamUris.isNotEmpty()) stringBuilder.append("Stream: ${streamUris[stream]}\n")
        stringBuilder.append("Last Update: ${modificationDate}\n")
        stringBuilder.append("Content-Type: ${streamContent}\n")
        return stringBuilder.toString()
    }

    fun getStreamUri(): String {
        //Log.e("ErrorStreamUris","streamUris: $streamUris, stream: $stream")
        return if (streamUris.isNotEmpty() && stream in streamUris.indices) {
            streamUris[stream]
        } else {
            ""
        }
    }


    fun isValid(): Boolean {
        return uuid.isNotEmpty() && name.isNotEmpty() && streamUris.isNotEmpty() && streamUris[stream].isNotEmpty() && modificationDate != Keys.DEFAULT_DATE && streamContent != Keys.MIME_TYPE_UNSUPPORTED
    }

    fun deepCopy(): Station {
        return Station(
            uuid = uuid,
            starred = starred,
            name = name,
            nameManuallySet = nameManuallySet,
            streamUris = streamUris,
            stream = stream,
            streamContent = streamContent,
            homepage = homepage,
            image = image,
            smallImage = smallImage,
            imageColor = imageColor,
            imageManuallySet = imageManuallySet,
            remoteImageLocation = remoteImageLocation,
            remoteStationLocation = remoteStationLocation,
            modificationDate = modificationDate,
            isPlaying = isPlaying,
            radioBrowserStationUuid = radioBrowserStationUuid,
            radioBrowserChangeUuid = radioBrowserChangeUuid,
            bitrate = bitrate,
            codec = codec,
            countrycode = countrycode,
            language = language,
            languagecodes = languagecodes,
            country = country
        )
    }
}