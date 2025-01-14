package com.yaros.RadioUrl

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
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

       PermissionHelper.checkAllPermissions(this)

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
        
        override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PermissionHelper.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS) {
            if (PermissionHelper.isIgnoringBatteryOptimizations(this)) {
                Toast.makeText(this, R.string.battary_no_function, Toast.LENGTH_SHORT).show()
            } else {
                AlertDialog.Builder(this)
                    .setTitle(R.string.battary_no)
                    .setMessage(R.string.battery_no_message)
                    .setPositiveButton(R.string.dialog_yes_no_positive_button_default) { _, _ ->
                        PermissionHelper.openBatteryOptimizationSettings(this)
                    }
                    .setNegativeButton(R.string.dialog_generic_button_cancel) { _, _ ->
                        Toast.makeText(this, R.string.battery_reload, Toast.LENGTH_LONG).show()
                    }
                    .show()
            }
        }
    }
}
