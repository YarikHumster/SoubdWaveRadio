package com.yaros.RadioUrl.helpers

import android.util.Log
import androidx.media3.common.Metadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.metadata.icy.IcyHeaders
import androidx.media3.extractor.metadata.icy.IcyInfo
import com.yaros.RadioUrl.Keys
import timber.log.Timber
import kotlin.math.min


object AudioHelper {


    private val TAG: String = AudioHelper::class.java.simpleName

    @UnstableApi
    fun getMetadataString(metadata: Metadata): String {
        var metadataString = String()
        for (i in 0 until metadata.length()) {
            when (val entry = metadata.get(i)) {
                is IcyInfo -> {
                    metadataString = entry.title.toString()
                }

                is IcyHeaders -> {
                    Timber.tag(TAG).i("%s%s", "%s - ", "icyHeaders:" + entry.name, entry.genre)
                }

                else -> {
                    Timber.tag(TAG)
                        .w("Unsupported metadata received (type = ${entry.javaClass.simpleName})")
                }
            }
        }
        if (metadataString.isNotEmpty()) {
            metadataString = metadataString.substring(0, min(metadataString.length, Keys.DEFAULT_MAX_LENGTH_OF_METADATA_ENTRY))
        }
        return metadataString
    }


}
