package com.yaros.RadioUrl

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.firebase.analytics.FirebaseAnalytics
import com.yaros.RadioUrl.helpers.AdManager
import com.yaros.RadioUrl.helpers.AppThemeHelper
import com.yaros.RadioUrl.helpers.PreferencesHelper
import com.yaros.RadioUrl.helpers.PreferencesHelper.initPreferences
import timber.log.Timber

class URLRadio : Application() {
    var firebaseAnalytics: FirebaseAnalytics? = null
    private val TAG: String = URLRadio::class.java.simpleName
    lateinit var adManager: AdManager
    private set
    override fun onCreate() {
        super.onCreate()
        Timber.tag(TAG).v("URLRadio application started.")
        initPreferences()
        AppThemeHelper.setTheme(PreferencesHelper.loadThemeSelection())
        adManager = AdManager(this)
        firebaseAnalytics = FirebaseAnalytics.getInstance(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver)

        // Check if the event has been sent already
        if (!PreferencesHelper.isStoreInstallEventSent()) {
            val installerPackage = packageManager.getInstallerPackageName(packageName)
            val storeName = getStoreName(installerPackage)
            firebaseAnalytics?.logEvent("app_install_source") {
                param("store_name", storeName)
            }
            // Set the flag to true
            PreferencesHelper.setStoreInstallEventSent(true)
        }
    }

     private fun getStoreName(installerPackage: String?): String {
        return when (installerPackage) {
            "com.android.vending" -> "Google Play Store"
            "com.amazon.venezia" -> "Amazon Appstore"
            "com.opera.appstore" -> "Opera Mobile Store"
            "com.nokia.installer" -> "Nokia Store"
            "com.asus.android.appstore" -> "ASUS App Store"
            "com.sec.android.app.samsungappstore" -> "Samsung Galaxy Store"
            "com.lenovo.lenovostore" -> "Lenovo App Store"
            else -> "Unknown or direct install"
        }
    }

    var lifecycleObserver: LifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            super.onStart(owner)
            Keys.isForeground = true
        }

        override fun onStop(owner: LifecycleOwner) {
            super.onStop(owner)
            Keys.isForeground = false
            Keys.isPausedFromClick = false
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        Timber.tag(TAG).v("URLRadio application terminated.")
    }

}
