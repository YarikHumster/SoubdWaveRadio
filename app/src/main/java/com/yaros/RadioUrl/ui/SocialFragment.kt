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

        val tgChannelClickble = view.findViewById<LinearLayout>(R.id.tgChannelClickble)
        val tgChannelImage = view.findViewById<ImageView>(R.id.tgChannelImage)
        val tgChannelTitle = view.findViewById<TextView>(R.id.tgChannelTitle)
        val tgChannelSummon = view.findViewById<TextView>(R.id.tgChannelSummon)

        val tgBotClickble = view.findViewById<LinearLayout>(R.id.tgBotClickble)
        val tgBotImage = view.findViewById<ImageView>(R.id.tgBotImage)
        val tgBotTitle = view.findViewById<TextView>(R.id.tgBotTitle)
        val tgBotSummon = view.findViewById<TextView>(R.id.tgBotSummon)

        val payClickable = view.findViewById<LinearLayout>(R.id.payClickable)
        val payImage = view.findViewById<ImageView>(R.id.payImage)
        val payTitle = view.findViewById<TextView>(R.id.payTitle)
        val paySummon = view.findViewById<TextView>(R.id.paySummon)

        // Set images and titles
        tgChannelImage.setImageResource(R.drawable.ic_tg)
        tgChannelTitle.text = getString(R.string.pref_tg_title)
        tgChannelSummon.text = getString(R.string.pref_tg_summary)

        tgBotImage.setImageResource(R.drawable.ic_tg)
        tgBotTitle.text = getString(R.string.pref_tg_title_bot)
        tgBotSummon.text = getString(R.string.pref_tg_summary_bot)

        payImage.setImageResource(R.drawable.ic_coffee)
        payTitle.text = getString(R.string.pref_pay_title)
        paySummon.text = getString(R.string.pref_pay_summary)

        tgChannelImage.contentDescription = getString(R.string.pref_tg_title)
        tgBotImage.contentDescription = getString(R.string.pref_tg_title_bot)
        payImage.contentDescription = getString(R.string.pref_pay_title)

        // Set click listeners
        tgChannelClickble.setOnClickListener {
            openLink("https://www.facebook.com")
        }

        tgBotClickble.setOnClickListener {
           openLink("https://www.twitter.com")
       }

        payClickable.setOnClickListener {
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
