package com.yaros.RadioUrl.helpers

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.widget.Toast
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.net.toUri
import com.yaros.RadioUrl.Keys
import com.yaros.RadioUrl.MainActivity
import com.yaros.RadioUrl.R
import com.yaros.RadioUrl.core.Station


object ShortcutHelper {

    fun placeShortcut(context: Context, station: Station) {
        if (ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
            val shortcut: ShortcutInfoCompat = ShortcutInfoCompat.Builder(context, station.name)
                .setShortLabel(station.name)
                .setLongLabel(station.name)
                .setIcon(createShortcutIcon(context, station.image, station.imageColor))
                .setIntent(createShortcutIntent(context, station.uuid))
                .build()
            ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
            Toast.makeText(context, R.string.toastmessage_shortcut_created, Toast.LENGTH_LONG)
                .show()
        } else {
            Toast.makeText(context, R.string.toastmessage_shortcut_not_created, Toast.LENGTH_LONG)
                .show()
        }
    }


    private fun createShortcutIntent(context: Context, stationUuid: String): Intent {
        val shortcutIntent = Intent(context, MainActivity::class.java)
        shortcutIntent.action = Keys.ACTION_START
        shortcutIntent.putExtra(Keys.EXTRA_STATION_UUID, stationUuid)
        shortcutIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        shortcutIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        return shortcutIntent
    }


    private fun createShortcutIcon(
        context: Context,
        stationImage: String,
        stationImageColor: Int
    ): IconCompat {
        val stationImageBitmap: Bitmap =
            ImageHelper.getScaledStationImage(context, stationImage.toUri(), 192)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            IconCompat.createWithAdaptiveBitmap(
                ImageHelper.createSquareImage(
                    context,
                    stationImageBitmap,
                    stationImageColor,
                    192,
                    true
                )
            )
        } else {
            IconCompat.createWithAdaptiveBitmap(
                ImageHelper.createSquareImage(
                    context,
                    stationImageBitmap,
                    stationImageColor,
                    192,
                    false
                )
            )
        }
    }

}
