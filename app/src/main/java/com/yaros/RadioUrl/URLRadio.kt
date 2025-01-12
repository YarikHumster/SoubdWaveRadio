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