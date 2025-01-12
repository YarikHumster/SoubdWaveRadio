package com.yaros.RadioUrl.ui

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import com.google.android.play.core.review.ReviewInfo
import com.google.android.play.core.review.ReviewManagerFactory
import ru.rustore.sdk.review.RuStoreReviewManagerFactory
import timber.log.Timber

class ReviewManager(private val context: Context) {

    private val sharedPreferences: SharedPreferences = context.getSharedPreferences("ReviewPrefs", Context.MODE_PRIVATE)
    private val handler = Handler(Looper.getMainLooper())

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
        val installerPackageName = packageManager.getInstallerPackageName(context.packageName)

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
                val flow = reviewManager.launchReviewFlow(context as android.app.Activity, reviewInfo)
                flow.addOnCompleteListener {
                    // Отметить, что экран оценки был показан
                    sharedPreferences.edit().putBoolean(KEY_REVIEW_SHOWN, true).apply()
                }
            }
        }
    }

    private fun showRuStoreReview() {
        val ruStoreReviewManager = RuStoreReviewManagerFactory.create(context)
        ruStoreReviewManager.requestReviewFlow()
            .addOnSuccessListener { reviewInfo ->
                ruStoreReviewManager.launchReviewFlow(reviewInfo)
                    .addOnSuccessListener {
                        // Отметить, что экран оценки был показан
                        sharedPreferences.edit().putBoolean(KEY_REVIEW_SHOWN, true).apply()
                    }
            }
            .addOnFailureListener { exception ->
                // Обработка ошибки
                Timber.tag("ReviewManager").e(exception, "Failed to request review flow")
            }
    }

    enum class InstallSource {
        GOOGLE_PLAY, RUSTORE, UNKNOWN
    }
}