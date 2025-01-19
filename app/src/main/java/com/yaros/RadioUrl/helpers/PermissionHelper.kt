package com.yaros.RadioUrl

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

object PermissionHelper {

    private const val PREF_NAME = "PermissionPrefs"
    private const val PREF_UNIFIED_DIALOG_SHOWN = "unified_dialog_shown"

    fun checkAllPermissions(activity: Activity) {
        val sharedPreferences = activity.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        if (!sharedPreferences.getBoolean(PREF_UNIFIED_DIALOG_SHOWN, false)) {
            showUnifiedPermissionDialog(activity, onAccept = {
                if (!hasNotificationPermission(activity) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    requestNotificationPermission(activity)
                }
                if (!isIgnoringBatteryOptimizations(activity) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    requestIgnoreBatteryOptimizations(activity)
                }
                sharedPreferences.edit().putBoolean(PREF_UNIFIED_DIALOG_SHOWN, true).apply()
            }, onDecline = {
                sharedPreferences.edit().putBoolean(PREF_UNIFIED_DIALOG_SHOWN, true).apply()
            })
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
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
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
            activity.startActivity(intent)
        }
    }

    fun openBatteryOptimizationSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            context.startActivity(intent)
        }
    }

    fun requestNotificationPermission(activity: Activity) {
        if (!hasNotificationPermission(activity)) {
            val intent = Intent().apply {
                action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    putExtra(Settings.EXTRA_CHANNEL_ID, activity.applicationInfo.uid.toString())
                }
            }
            activity.startActivity(intent)
        }
    }

    fun showUnifiedPermissionDialog(
        activity: Activity,
        onAccept: () -> Unit,
        onDecline: () -> Unit
    ) {
        AlertDialog.Builder(activity)
            .setMessage(R.string.notification_info)
            .setTitle(R.string.notification)
            .setPositiveButton(R.string.dialog_yes_no_positive_button_default) { dialog, _ ->
                onAccept()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.dialog_generic_button_cancel) { dialog, _ ->
                onDecline()
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    const val REQUEST_IGNORE_BATTERY_OPTIMIZATIONS = 1001
}
