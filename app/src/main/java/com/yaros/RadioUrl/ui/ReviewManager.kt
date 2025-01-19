package com.yaros.RadioUrl.ui

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import com.google.android.play.core.review.ReviewInfo
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.firebase.analytics.FirebaseAnalytics
import ru.rustore.sdk.review.RuStoreReviewManagerFactory
import timber.log.Timber

class ReviewManager(private val context: Context) {

    private val sharedPreferences: SharedPreferences = context.getSharedPreferences("ReviewPrefs", Context.MODE_PRIVATE)
    private val firebaseAnalytics = FirebaseAnalytics.getInstance(context)

    companion object {
        private const val KEY_REVIEW_SHOWN = "review_shown"
        private const val KEY_INSTALL_TIME = "install_time"
        private const val KEY_LAUNCH_COUNT = "launch_count"
        private const val LAUNCH_THRESHOLD = 10L
    }

    fun initialize() {
        if (!sharedPreferences.contains(KEY_INSTALL_TIME)) {
            sharedPreferences.edit().putLong(KEY_INSTALL_TIME, System.currentTimeMillis()).apply()
        }

        val launchCount = sharedPreferences.getInt(KEY_LAUNCH_COUNT, 0) + 1
        sharedPreferences.edit().putInt(KEY_LAUNCH_COUNT, launchCount).apply()

        if (launchCount >= LAUNCH_THRESHOLD && !sharedPreferences.getBoolean(KEY_REVIEW_SHOWN, false)) {
            showReview()
        }
    }

    private fun showReview() {
        when (getInstallSource()) {
            InstallSource.GOOGLE_PLAY -> showGooglePlayReview()
            InstallSource.RUSTORE -> showRuStoreReview()
            InstallSource.UNKNOWN -> {
                // Handle unknown install source
                Timber.w("Unknown install source, cannot show review")
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun getInstallSource(): InstallSource {
        val packageManager = context.packageManager
        return try {
            val installerPackageName = packageManager.getInstallerPackageName(context.packageName) ?: ""
            when (installerPackageName) {
                "com.android.vending" -> InstallSource.GOOGLE_PLAY
                "ru.rustore.app" -> InstallSource.RUSTORE
                else -> InstallSource.UNKNOWN
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to get installer package name")
            InstallSource.UNKNOWN
        }
    }

    private fun showGooglePlayReview() {
        Timber.d("Requesting Google Play review")
        val reviewManager = ReviewManagerFactory.create(context)
        val requestReviewFlow = reviewManager.requestReviewFlow()
        requestReviewFlow.addOnCompleteListener { request ->
            if (request.isSuccessful) {
                val reviewInfo: ReviewInfo = request.result
                Timber.d("Review flow requested successfully, showing review dialog for Google Play")
                logReviewShownEvent("Google Play")
                val flow = reviewManager.launchReviewFlow(context as android.app.Activity, reviewInfo)
                flow.addOnCompleteListener {
                    Timber.d("Review flow completed, marking review as shown for Google Play")
                    sharedPreferences.edit().putBoolean(KEY_REVIEW_SHOWN, true).apply()
                    logReviewCompletedEvent("Google Play")
                }
            } else {
                Timber.e("Failed to request review flow for Google Play")
                logReviewErrorEvent("Google Play", request.exception ?: Exception("Unknown error"))
            }
        }
        requestReviewFlow.addOnFailureListener { exception ->
            Timber.e(exception, "Failed to request review flow for Google Play")
            logReviewErrorEvent("Google Play", exception)
        }
    }

    private fun showRuStoreReview() {
        Timber.d("Requesting RuStore review")
        val ruStoreReviewManager = RuStoreReviewManagerFactory.create(context)
        ruStoreReviewManager.requestReviewFlow()
            .addOnSuccessListener { reviewInfo ->
                Timber.d("Review flow requested successfully, showing review dialog for RuStore")
                logReviewShownEvent("RuStore")
                ruStoreReviewManager.launchReviewFlow(reviewInfo)
                    .addOnSuccessListener {
                        Timber.d("Review flow completed, marking review as shown for RuStore")
                        sharedPreferences.edit().putBoolean(KEY_REVIEW_SHOWN, true).apply()
                        logReviewCompletedEvent("RuStore")
                    }
                    .addOnFailureListener { exception ->
                        Timber.e(exception, "Failed to launch review flow for RuStore")
                        logReviewErrorEvent("RuStore", exception as Exception)
                    }
            }
            .addOnFailureListener { exception ->
                Timber.e(exception, "Failed to request review flow for RuStore")
                logReviewErrorEvent("RuStore", exception as Exception)
            }
    }

    private fun logReviewShownEvent(source: String) {
        val bundle = Bundle().apply {
            putString(FirebaseAnalytics.Param.CONTENT_TYPE, "review_shown")
            putString("source", source)
        }
        firebaseAnalytics.logEvent(FirebaseAnalytics.Event.SELECT_PROMOTION, bundle)
    }

    private fun logReviewCompletedEvent(source: String) {
        val bundle = Bundle().apply {
            putString(FirebaseAnalytics.Param.CONTENT_TYPE, "review_completed")
            putString("source", source)
        }
        firebaseAnalytics.logEvent(FirebaseAnalytics.Event.SELECT_PROMOTION, bundle)
    }

    private fun logReviewErrorEvent(source: String, exception: Exception) {
        val bundle = Bundle().apply {
            putString(FirebaseAnalytics.Param.VALUE, exception.javaClass.name)
            putString(FirebaseAnalytics.Param.VALUE, exception.message)
            putString("source", source)
        }
        firebaseAnalytics.logEvent("review_error", bundle)
    }

    enum class InstallSource {
        GOOGLE_PLAY, RUSTORE, UNKNOWN
    }
}
