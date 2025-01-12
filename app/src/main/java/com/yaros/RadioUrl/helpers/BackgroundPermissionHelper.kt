package com.yaros.RadioUrl

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog

object BackgroundPermissionHelper {

    private const val PREF_NAME = "BackgroundPermissionPref"
    private const val PREF_KEY_DIALOG_SHOWN = "dialog_shown"

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val packageName = context.packageName
        return powerManager.isIgnoringBatteryOptimizations(packageName)
    }

    @SuppressLint("BatteryLife")
    fun requestIgnoreBatteryOptimizations(activity: AppCompatActivity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent().apply {
                action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                data = Uri.parse("package:${activity.packageName}")
            }
            activity.startActivityForResult(intent, REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        }
    }

    fun openBatteryOptimizationSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            context.startActivity(intent)
        }
    }

    fun showBackgroundPermissionDialogIfNeeded(activity: AppCompatActivity, onAccept: () -> Unit, onDecline: () -> Unit) {
        val sharedPrefs = activity.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val dialogShown = sharedPrefs.getBoolean(PREF_KEY_DIALOG_SHOWN, false)

        if (!dialogShown) {
            AlertDialog.Builder(activity)
                .setTitle(R.string.battary_no_function)
                .setMessage(R.string.battari_info)
                .setPositiveButton(R.string.dialog_yes_no_positive_button_default) { _, _ ->
                    sharedPrefs.edit().putBoolean(PREF_KEY_DIALOG_SHOWN, true).apply()
                    onAccept()
                }
                .setNegativeButton(R.string.dialog_generic_button_cancel) { _, _ ->
                    sharedPrefs.edit().putBoolean(PREF_KEY_DIALOG_SHOWN, true).apply()
                    onDecline()
                }
                .setCancelable(false)
                .show()
        }
    }

    const val REQUEST_IGNORE_BATTERY_OPTIMIZATIONS = 1001
}