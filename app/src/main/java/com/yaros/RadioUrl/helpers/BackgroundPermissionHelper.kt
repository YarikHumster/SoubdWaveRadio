package com.yaros.RadioUrl

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationManagerCompat

object PermissionHelper {

    private const val PREF_NAME = "PermissionPrefs"
    private const val PREF_NOTIFICATION_DIALOG_SHOWN = "notification_dialog_shown"
    private const val PREF_BACKGROUND_DIALOG_SHOWN = "background_dialog_shown"

    fun checkAllPermissions(activity: Activity) {
        val sharedPreferences = activity.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        if (!hasNotificationPermission(activity) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!sharedPreferences.getBoolean(PREF_NOTIFICATION_DIALOG_SHOWN, false)) {
                requestNotificationPermission(activity)
                sharedPreferences.edit().putBoolean(PREF_NOTIFICATION_DIALOG_SHOWN, true).apply()
            }
        }

        if (!isIgnoringBatteryOptimizations(activity) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!sharedPreferences.getBoolean(PREF_BACKGROUND_DIALOG_SHOWN, false)) {
                showBackgroundPermissionDialog(activity, onAccept = {
                    requestIgnoreBatteryOptimizations(activity)
                    sharedPreferences.edit().putBoolean(PREF_BACKGROUND_DIALOG_SHOWN, true).apply()
                }, onDecline = {
                    sharedPreferences.edit().putBoolean(PREF_BACKGROUND_DIALOG_SHOWN, true).apply()
                })
            }
        }
    }

    private fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        } else {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                notificationManager.areNotificationsEnabled()
            } else {
                true
            }
        }
    }

    @SuppressLint("BatteryLife")
    private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val packageName = context.packageName
        return powerManager.isIgnoringBatteryOptimizations(packageName)
    }

    fun requestIgnoreBatteryOptimizations(activity: Activity) {
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

    fun showBackgroundPermissionDialog(
        activity: Activity,
        onAccept: () -> Unit,
        onDecline: () -> Unit
    ) {
        AlertDialog.Builder(activity)
            .setTitle(R.string.battary_no_function)
            .setMessage(R.string.battari_info)
            .setPositiveButton(R.string.dialog_yes_no_positive_button_default) { _, _ ->
                onAccept()
            }
            .setNegativeButton(R.string.dialog_generic_button_cancel) { _, _ ->
                onDecline()
            }
            .setCancelable(false)
            .show()
    }

    fun requestNotificationPermission(activity: Activity) {
        if (!hasNotificationPermission(activity)) {
            AlertDialog.Builder(activity)
                .setTitle(R.string.notification)
                .setMessage(R.string.notification_info)
                .setPositiveButton(R.string.setting) { _, _ ->
                    val intent = Intent().apply {
                        action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                        putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            putExtra(Settings.EXTRA_CHANNEL_ID, activity.applicationInfo.uid.toString())
                        }
                    }
                    activity.startActivity(intent)
                }
                .setNegativeButton(R.string.dialog_generic_button_cancel, null)
                .show()
        }
    }

    const val REQUEST_IGNORE_BATTERY_OPTIMIZATIONS = 1001
}
