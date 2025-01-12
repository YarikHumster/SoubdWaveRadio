package com.yaros.RadioUrl.helpers

import android.util.Log
import com.yaros.RadioUrl.Keys
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*

object DateTimeHelper {


    private val TAG: String = DateTimeHelper::class.java.simpleName


    private const val pattern: String = "EEE, dd MMM yyyy HH:mm:ss Z"
    private val dateFormat: SimpleDateFormat = SimpleDateFormat(pattern, Locale.ENGLISH)


    fun convertFromRfc2822(dateString: String): Date {
        val date: Date = try {
            dateFormat.parse((dateString)) ?: Keys.DEFAULT_DATE
        } catch (e: Exception) {
            Timber.tag(TAG).w("Unable to parse. Trying an alternative Date format. $e")
            tryAlternativeRfc2822Parsing(dateString)
        }
        return date
    }


    fun convertToRfc2822(date: Date): String {
        val dateFormat = SimpleDateFormat(pattern, Locale.ENGLISH)
        return dateFormat.format(date)
    }


    fun convertToHoursMinutesSeconds(milliseconds: Long, negativeValue: Boolean = false): String {
        val hours: Long = milliseconds / 1000 / 3600
        val minutes: Long = milliseconds / 1000 % 3600 / 60
        val seconds: Long = milliseconds / 1000 % 60
        val hourPart = if (hours > 0) {
            "${hours.toString().padStart(2, '0')}:"
        } else {
            ""
        }

        var timeString =
            "$hourPart${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
        if (negativeValue) {
            timeString = "-$timeString"
        }
        return timeString
    }


    private fun tryAlternativeRfc2822Parsing(dateString: String): Date {
        var date: Date = Keys.DEFAULT_DATE
        try {
            date = SimpleDateFormat("EEE, dd MMM yyyy HH:mm Z", Locale.ENGLISH).parse((dateString))
                ?: Keys.DEFAULT_DATE
        } catch (e: Exception) {
            try {
                Timber.tag(TAG).w("Unable to parse. Trying an alternative Date format. $e")
                date = SimpleDateFormat(
                    "EEE, dd MMM yyyy HH:mm:ss",
                    Locale.ENGLISH
                ).parse((dateString)) ?: Keys.DEFAULT_DATE
            } catch (e: Exception) {
                Timber.tag(TAG).e("Unable to parse. Returning a default date. $e")
            }
        }
        return date
    }

}
