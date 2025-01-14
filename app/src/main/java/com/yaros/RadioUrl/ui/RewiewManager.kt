package com.yaros.RadioUrl.ui

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.google.android.play.core.review.ReviewInfo
import com.google.android.play.core.review.ReviewManagerFactory
import ru.rustore.sdk.review.RuStoreReviewManagerFactory
import timber.log.Timber
import com.google.firebase.analytics.FirebaseAnalytics

class ReviewManager(private val context: Context) {

    private val sharedPreferences: SharedPreferences = context.getSharedPreferences("ReviewPrefs", Context.MODE_PRIVATE)
    private val handler = Handler(Looper.getMainLooper())
    private val firebaseAnalytics = FirebaseAnalytics.getInstance(context)

    companion object {
        private const val KEY_REVIEW_SHOWN = "review_shown"
        private const val KEY_INSTALL_TIME = "install_time"
        private const val HOUR_IN_MILLIS = 60 * 60 * 1000L
    }

    fun initialize() {
        if (!sharedPreferences.contains(KEY_INSTALL_TIME)) {
            sharedPreferences.edit().putLong(KEY_INSTALL_TIME, System.currentTimeMillis()).apply()
        }

        scheduleReviewCheck()
    }

    private fun scheduleReviewCheck() {
        handler.postDelayed({
            if (shouldShowReview()) {
                showReview()
            }
        }, HOUR_IN_MILLIS)
    }

    private fun shouldShowReview(): Boolean {
        val installTime = sharedPreferences.getLong(KEY_INSTALL_TIME, 0)
        val currentTime = System.currentTimeMillis()
        val timeSinceInstall = currentTime - installTime

        return !sharedPreferences.getBoolean(KEY_REVIEW_SHOWN, false) && timeSinceInstall >= HOUR_IN_MILLIS
    }

    private fun showReview() {
        when (getInstallSource()) {
            InstallSource.GOOGLE_PLAY -> showGooglePlayReview()
            InstallSource.RUSTORE -> showRuStoreReview()
            InstallSource.UNKNOWN -> {
                // Обработка неизвестного источника установки
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun getInstallSource(): InstallSource {
        val packageManager = context.packageManager
        val installerPackageName = packageManager.getInstallerPackageName(context.packageName) ?: ""

        return when (installerPackageName) {
            "com.android.vending" -> InstallSource.GOOGLE_PLAY
            "ru.rustore.app" -> InstallSource.RUSTORE
            else -> InstallSource.UNKNOWN
        }
    }

    private fun showGooglePlayReview() {
        val reviewManager = ReviewManagerFactory.create(context)
        val requestReviewFlow = reviewManager.requestReviewFlow()
        requestReviewFlow.addOnCompleteListener { request ->
            if (request.isSuccessful) {
                val reviewInfo: ReviewInfo = request.result
                logReviewShownEvent("Google Play")
                val flow = reviewManager.launchReviewFlow(context as android.app.Activity, reviewInfo)
                flow.addOnCompleteListener {
                    sharedPreferences.edit().putBoolean(KEY_REVIEW_SHOWN, true).apply()
                    logReviewCompletedEvent("Google Play")
                }
            } else {
                logReviewErrorEvent("Google Play", request.exception ?: Exception("Unknown error"))
            }
        }
        requestReviewFlow.addOnFailureListener { exception ->
            logReviewErrorEvent("Google Play", exception)
        }
    }

    private fun showRuStoreReview() {
        val ruStoreReviewManager = RuStoreReviewManagerFactory.create(context)
        ruStoreReviewManager.requestReviewFlow()
            .addOnSuccessListener { reviewInfo ->
                logReviewShownEvent("RuStore")
                ruStoreReviewManager.launchReviewFlow(reviewInfo)
                    .addOnSuccessListener {
                        sharedPreferences.edit().putBoolean(KEY_REVIEW_SHOWN, true).apply()
                        logReviewCompletedEvent("RuStore")
                    }
                    .addOnFailureListener { exception ->
                        logReviewErrorEvent("RuStore", exception as Exception)
                    }
            }
            .addOnFailureListener { exception ->
                logReviewErrorEvent("RuStore", exception as Exception)
            }
    }

    private fun logReviewShownEvent(source: String) {
        val bundle = Bundle()
        bundle.putString(FirebaseAnalytics.Param.CONTENT_TYPE, "review_shown")
        bundle.putString("source", source)
        firebaseAnalytics.logEvent(FirebaseAnalytics.Event.SELECT_PROMOTION, bundle)
    }

    private fun logReviewCompletedEvent(source: String) {
        val bundle = Bundle()
        bundle.putString(FirebaseAnalytics.Param.CONTENT_TYPE, "review_completed")
        bundle.putString("source", source)
        firebaseAnalytics.logEvent(FirebaseAnalytics.Event.SELECT_PROMOTION, bundle)
    }

    private fun logReviewDeclinedEvent(source: String) {
        val bundle = Bundle()
        bundle.putString(FirebaseAnalytics.Param.CONTENT_TYPE, "review_declined")
        bundle.putString("source", source)
        firebaseAnalytics.logEvent("review_declined", bundle)
    }

    private fun logReviewErrorEvent(source: String, exception: Exception) {
        val bundle = Bundle()
        bundle.putString(FirebaseAnalytics.Param.VALUE, exception.javaClass.name)
        bundle.putString(FirebaseAnalytics.Param.VALUE, exception.message)
        bundle.putString("source", source)
        firebaseAnalytics.logEvent("review_error", bundle)
    }

    enum class InstallSource {
        GOOGLE_PLAY, RUSTORE, UNKNOWN
    }
}
