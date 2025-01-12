package com.yaros.RadioUrl

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.media3.common.util.UnstableApi
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.navigateUp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessaging
import com.yaros.RadioUrl.helpers.AdManager
import com.yaros.RadioUrl.helpers.AppThemeHelper
import com.yaros.RadioUrl.helpers.FileHelper
import com.yaros.RadioUrl.helpers.ImportHelper
import com.yaros.RadioUrl.helpers.PreferencesHelper
import com.yaros.RadioUrl.ui.ReviewManager
import timber.log.Timber

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var analytics: FirebaseAnalytics
    private lateinit var adManager: AdManager
    private lateinit var reviewManager: ReviewManager

    @UnstableApi
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        if (PreferencesHelper.isHouseKeepingNecessary()) {
            ImportHelper.removeDefaultStationImageUris(this)
            if (PreferencesHelper.loadCollectionSize() != -1) {
                PreferencesHelper.saveEditStationsEnabled(true)
            }
            PreferencesHelper.saveHouseKeepingNecessaryState()
        }
        analytics = Firebase.analytics
        setContentView(R.layout.activity_main)

        reviewManager = ReviewManager(this)
        reviewManager.initialize()

        if (!hasNotificationPermission(this)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                requestNotificationPermission(this)
            }
        }
        if (!BackgroundPermissionHelper.isIgnoringBatteryOptimizations(this)) {
            BackgroundPermissionHelper.showBackgroundPermissionDialogIfNeeded(
                this,
                onAccept = {
                    BackgroundPermissionHelper.requestIgnoreBatteryOptimizations(this)
                },
                onDecline = {
                    // Здесь можно показать сообщение о том, что некоторые функции могут работать некорректно
                    Toast.makeText(this, "Некоторые функции могут работать некорректно без разрешения", Toast.LENGTH_LONG).show()
                }
            )
        }

        FileHelper.createNomediaFile(getExternalFilesDir(null))
        setSupportActionBar(findViewById(R.id.main_toolbar))
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        val navView: BottomNavigationView = findViewById(R.id.bottomNavigationView)

        appBarConfiguration = AppBarConfiguration(
            setOf(R.id.navigation_home,R.id.navigation_catalog, R.id.navigation_menu )
        )

        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        PreferencesHelper.registerPreferenceChangeListener(sharedPreferenceChangeListener)
        startPlayerService()

        // Инициализация Firebase
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                Timber.tag("TAG").d("Токен устройства: $token")
            } else {
                Timber.tag("TAG").w("Не удалось получить токен.")
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "2222",
                "Channel Name",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
        adManager = (application as URLRadio).adManager
        adManager.setActivity(this)
        val bannerAdView: View = findViewById(R.id.bannerAdView)
        adManager.initBannerAd(bannerAdView)
        adManager.loadBannerAd()
    }


    @UnstableApi
    private fun startPlayerService() {
        val serviceIntent = Intent(this, PlayerService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_graph)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    override fun onResume() {
        super.onResume()
        Timber.tag("ONRESUME").d("OnResume")
        adManager.loadBannerAd()
    }

    override fun onDestroy() {
        super.onDestroy()
        PreferencesHelper.unregisterPreferenceChangeListener(sharedPreferenceChangeListener)
        adManager.destroyBannerAd()
    }

    private val sharedPreferenceChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                Keys.PREF_THEME_SELECTION -> {
                    AppThemeHelper.setTheme(PreferencesHelper.loadThemeSelection())
                }
            }
        }

    private fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        } else {
            val notificationManager =
                context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                notificationManager.areNotificationsEnabled()
            } else {
                // Для версий ниже N, возвращаем true, так как до Android N не было возможности отключить уведомления на уровне системы
                true
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == BackgroundPermissionHelper.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS) {
            if (BackgroundPermissionHelper.isIgnoringBatteryOptimizations(this)) {
                // Разрешение получено
                Toast.makeText(this, R.string.battary_no_function, Toast.LENGTH_SHORT).show()
            } else {
                // Разрешение не получено
                AlertDialog.Builder(this)
                    .setTitle(R.string.battary_no)
                    .setMessage(R.string.battery_no_message)
                    .setPositiveButton(R.string.dialog_yes_no_positive_button_default) { _, _ ->
                        BackgroundPermissionHelper.openBatteryOptimizationSettings(this)
                    }
                    .setNegativeButton(R.string.dialog_generic_button_cancel) { _, _ ->
                        Toast.makeText(this, R.string.battery_reload, Toast.LENGTH_LONG).show()
                    }
                    .show()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun requestNotificationPermission(context: Context) {
        if (!hasNotificationPermission(context)) {
            AlertDialog.Builder(context)
                .setTitle(R.string.notification)
                .setMessage(R.string.notification_info)
                .setPositiveButton(R.string.setting) { _, _ ->
                    val intent = Intent().apply {
                        action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            putExtra(Settings.EXTRA_CHANNEL_ID, context.applicationInfo.uid)
                        }
                    }
                    context.startActivity(intent)
                }
                .setNegativeButton(R.string.dialog_generic_button_cancel, null)
                .show()
        }
    }
}