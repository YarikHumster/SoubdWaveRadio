package com.yaros.RadioUrl.helpers

import android.app.Application
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ProcessLifecycleOwner
import com.yandex.mobile.ads.appopenad.AppOpenAd
import com.yandex.mobile.ads.appopenad.AppOpenAdEventListener
import com.yandex.mobile.ads.appopenad.AppOpenAdLoadListener
import com.yandex.mobile.ads.appopenad.AppOpenAdLoader
import com.yandex.mobile.ads.banner.BannerAdSize
import com.yandex.mobile.ads.banner.BannerAdView
import com.yandex.mobile.ads.common.AdError
import com.yandex.mobile.ads.common.AdRequest
import com.yandex.mobile.ads.common.AdRequestConfiguration
import com.yandex.mobile.ads.common.AdRequestError
import com.yandex.mobile.ads.common.ImpressionData
import com.yandex.mobile.ads.common.MobileAds
import com.yandex.mobile.ads.instream.MobileInstreamAds
import com.yaros.RadioUrl.R
import timber.log.Timber
import kotlin.math.roundToInt

class AdManager(application: Application) {
    private var activity: AppCompatActivity? = null
    private var appOpenAd: AppOpenAd? = null
    private var adRequest: AdRequest
    private var bannerAd: BannerAdView? = null

    init {
        MobileAds.initialize(application) {
            MobileInstreamAds.setAdGroupPreloading(true)
            MobileAds.enableLogging(true)

            val processLifecycleObserver = DefaultProcessLifecycleObserver(
                onProcessCaseForeground = ::showAppOpenAd
            )
            ProcessLifecycleOwner.get().lifecycle.addObserver(processLifecycleObserver)
        }

        adRequest = AdRequest.Builder().build()
    }

    private inner class AdEventListener : AppOpenAdEventListener {
        override fun onAdShown() {
            loadAppOpenAd()
            Timber.tag("AppAdOpen").d("Ad has been shown.")
        }

        override fun onAdFailedToShow(adError: AdError) {
            clearAppOpenAd()
            loadAppOpenAd()
            Timber.tag("AppAdOpen").e("Failed to show ad: ${adError.description}")
        }

        override fun onAdDismissed() {
            clearAppOpenAd()
            Timber.tag("AppAdOpen").d("Ad dismissed.")
        }

        override fun onAdClicked() {
            Timber.tag("AppAdOpen").d("Ad clicked.")
        }

        override fun onAdImpression(impressionData: ImpressionData?) {
            Timber.tag("AppAdOpen").d("Ad impression: $impressionData")
        }
    }

    fun initBannerAd(view: View) {
        bannerAd = view.findViewById(R.id.bannerAdView)
        bannerAd?.apply {
            setAdUnitId("R-M-4490409-1")
        }
    }

    fun loadBannerAd() {
        activity?.let { activity ->
            bannerAd?.apply {
                val displayMetrics = activity.resources.displayMetrics
                val screenWidthPixels = displayMetrics.widthPixels
                val adWidth = (screenWidthPixels / displayMetrics.density).roundToInt()
                val screenHeight = displayMetrics.heightPixels / displayMetrics.density.toInt()
                val maxAdHeight = screenHeight / 12
                setAdSize(BannerAdSize.inlineSize(activity, adWidth, maxAdHeight))
                loadAd(adRequest)
                Timber.tag("BannerAd").d("Banner ad loaded.")
            }
        }
    }

    private fun loadAppOpenAd() {
        activity?.let { activity ->
            val appOpenAdLoader = AppOpenAdLoader(activity)
            val adUnitId = "R-M-4490409-4"
            val adRequestConfig = AdRequestConfiguration.Builder(adUnitId).build()

            appOpenAdLoader.run {
                setAdLoadListener(object : AppOpenAdLoadListener {
                    override fun onAdLoaded(appOpenAd: AppOpenAd) {
                        this@AdManager.appOpenAd = appOpenAd
                        Timber.tag("AppOpenAd").d("App open ad loaded.")
                    }

                    override fun onAdFailedToLoad(error: AdRequestError) {
                        Timber.tag("AppOpenAd").e("Failed to load app open ad: ${error.description}")
                    }
                })
                loadAd(adRequestConfig)
            }
        }
    }

    private fun showAppOpenAd() {
        activity?.let { activity ->
            appOpenAd?.apply {
                setAdEventListener(AdEventListener())
                show(activity)
                Timber.tag("AppOpenAd").d("Attempting to show app open ad.")
            }
        }
    }

    private fun clearAppOpenAd() {
        appOpenAd?.setAdEventListener(null)
        appOpenAd = null
        Timber.tag("AppOpenAd").d("App open ad cleared.")
    }

    fun destroyBannerAd() {
        bannerAd?.destroy()
        Timber.tag("BannerAd").d("Banner ad destroyed.")
    }

    fun setActivity(activity: AppCompatActivity) {
        this.activity = activity
        loadAppOpenAd()
    }
}
