package com.yaros.RadioUrl.ui

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.yaros.RadioUrl.helpers.AppThemeHelper
import com.yaros.RadioUrl.helpers.PreferencesHelper
import android.widget.Button
import androidx.navigation.fragment.findNavController
import com.yaros.RadioUrl.Keys
import com.yaros.RadioUrl.R

class MenuFragment : Fragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PreferencesHelper.registerPreferenceChangeListener(sharedPreferenceChangeListener)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_menu, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Найти включенный макет
        val placeholderLayout = view.findViewById<View>(R.id.placeholder)
        if (placeholderLayout != null) {
            val settingsButton: Button = placeholderLayout.findViewById(R.id.settingsButton)
            settingsButton.setOnClickListener {
                performSettings()
            }
        }
        val placeholderLayouts = view.findViewById<View>(R.id.placeholdetwo)
        if (placeholderLayouts != null) {
            val favoriteButton: Button = placeholderLayouts.findViewById(R.id.favoriteButton)
            favoriteButton.setOnClickListener{
                performFavorite()
            }
        }
    }

    private fun performSettings() {
        findNavController().navigate(R.id.action_navigation_menu_to_settingsFragment)
    }
    private fun performFavorite(){
        findNavController().navigate(R.id.action_navigation_menu_to_favoriteFragment)
    }

    private val sharedPreferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            Keys.PREF_THEME_SELECTION -> {
                AppThemeHelper.setTheme(PreferencesHelper.loadThemeSelection())
            }
        }
    }
}
