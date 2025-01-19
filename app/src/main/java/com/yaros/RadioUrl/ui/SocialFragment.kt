package com.yaros.RadioUrl.ui

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.yaros.RadioUrl.Keys
import com.yaros.RadioUrl.R
import com.yaros.RadioUrl.helpers.AppThemeHelper
import com.yaros.RadioUrl.helpers.PreferencesHelper

class SocialFragment : Fragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PreferencesHelper.registerPreferenceChangeListener(sharedPreferenceChangeListener)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.activity_social, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val buttonsocial1 = view.findViewById<LinearLayout>(R.id.button_social1)
        val socialImage = view.findViewById<ImageView>(R.id.button_image_social1)
        val buttonSocial = view.findViewById<TextView>(R.id.button_title_social1)
        val buttonDescriptionSocial = view.findViewById<TextView>(R.id.button_description_social1)

        val buttonsocial2 = view.findViewById<LinearLayout>(R.id.buttonsocial2)
        val buttonSocial2 = view.findViewById<ImageView>(R.id.button_imagesocial2)
        val socialImage2 = view.findViewById<TextView>(R.id.button_titlesocial2)
        val buttonDescriptionSocial2 = view.findViewById<TextView>(R.id.button_descriptionsocial2)

        val buttonsocialpay = view.findViewById<LinearLayout>(R.id.buttonsocial3)
        val buttonsocial3 = view.findViewById<ImageView>(R.id.button_imagesocial3)
        val socialImage3 = view.findViewById<TextView>(R.id.button_titlesocial3)
        val buttonDescriotionSocial3 = view.findViewById<TextView>(R.id.button_descriptionsocial3)

        // Set images and titles
        socialImage.setImageResource(R.drawable.ic_tg)
        buttonSocial.text = getString(R.string.pref_tg_title)
        buttonDescriptionSocial.text = getString(R.string.pref_tg_summary)

        buttonSocial2.setImageResource(R.drawable.ic_tg)
        socialImage2.text = getString(R.string.pref_tg_title_bot)
        buttonDescriptionSocial2.text = getString(R.string.pref_tg_summary_bot)

        buttonsocial3.setImageResource(R.drawable.ic_coffee)
        socialImage3.text = getString(R.string.pref_pay_title)
        buttonDescriotionSocial3.text = getString(R.string.pref_pay_summary)

        socialImage.contentDescription = getString(R.string.pref_tg_title)
        socialImage2.contentDescription = getString(R.string.pref_tg_title_bot)
        buttonsocial3.contentDescription = getString(R.string.pref_pay_title)

        // Set click listeners
        buttonsocial1.setOnClickListener {
            openLink("https://www.facebook.com")
        }

        buttonsocial2.setOnClickListener {
           openLink("https://www.twitter.com")
       }

        buttonsocialpay.setOnClickListener {
            openLink("https://www.tbank.ru/cf/8fwehAv657s")
        }

        val locale = requireActivity().resources.configuration.locale
        if (locale.language == "ru") {
            buttonsocialpay.visibility = View.VISIBLE
        } else {
            buttonsocialpay.visibility = View.GONE
        }
    }

    private fun openLink(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        requireActivity().startActivity(intent)
    }

    private val sharedPreferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            Keys.PREF_THEME_SELECTION -> {
                AppThemeHelper.setTheme(PreferencesHelper.loadThemeSelection())
            }
        }
    }
}