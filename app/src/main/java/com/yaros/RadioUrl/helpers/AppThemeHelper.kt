package com.yaros.RadioUrl.helpers

import android.content.Context
import android.content.res.TypedArray
import android.util.Log
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatDelegate
import com.yaros.RadioUrl.Keys
import com.yaros.RadioUrl.R
import timber.log.Timber

object AppThemeHelper {

    private val TAG: String = AppThemeHelper::class.java.simpleName

    private val sTypedValue = TypedValue()

    fun setTheme(nightModeState: String) {
        when (nightModeState) {
            Keys.STATE_THEME_DARK_MODE -> {
                if (AppCompatDelegate.getDefaultNightMode() != AppCompatDelegate.MODE_NIGHT_YES) {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                    Timber.tag(TAG).i("Dark Mode activated.")
                }
            }
            Keys.STATE_THEME_LIGHT_MODE -> {
                if (AppCompatDelegate.getDefaultNightMode() != AppCompatDelegate.MODE_NIGHT_NO) {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                    Timber.tag(TAG).i("Theme: Light Mode activated.")
                }
            }
            Keys.STATE_THEME_FOLLOW_SYSTEM -> {
                if (AppCompatDelegate.getDefaultNightMode() != AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                    Timber.tag(TAG).i("Theme: Follow System Mode activated.")
                }
            }
            else -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                Timber.tag(TAG).i("Theme: Follow System Mode activated.")
            }
        }
    }


    fun getCurrentTheme(context: Context): String {
        return when (PreferencesHelper.loadThemeSelection()) {
            Keys.STATE_THEME_LIGHT_MODE -> context.getString(R.string.pref_theme_selection_mode_light)
            Keys.STATE_THEME_DARK_MODE -> context.getString(R.string.pref_theme_selection_mode_dark)
            else -> context.getString(R.string.pref_theme_selection_mode_device_default)
        }
    }


    @ColorInt
    fun getColor(context: Context, @AttrRes resource: Int): Int {
        val a: TypedArray = context.obtainStyledAttributes(sTypedValue.data, intArrayOf(resource))
        val color = a.getColor(0, 0)
        a.recycle()
        return color
    }

}
