package com.kolehoenicke.apkextractor

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory

internal data class ReviewPromptDecision(
    val successfulSessions: Int,
    val shouldRequestReview: Boolean,
)

internal object ReviewPromptPolicy {
    const val SuccessfulSessionsBeforeReview = 3

    fun afterSuccessfulSession(
        successfulSessions: Int,
        requestAttempted: Boolean,
    ): ReviewPromptDecision {
        val updatedCount = (successfulSessions + 1).coerceAtMost(SuccessfulSessionsBeforeReview)
        return ReviewPromptDecision(
            successfulSessions = updatedCount,
            shouldRequestReview = !requestAttempted &&
                updatedCount >= SuccessfulSessionsBeforeReview,
        )
    }
}

/**
 * Requests Google's native Play review sheet after repeated successful use.
 *
 * Play controls whether the sheet is actually shown. The app never displays a
 * custom pre-prompt, never interrupts an extraction, and permanently stops
 * requesting after Play accepts one launch attempt.
 */
internal class InAppReviewCoordinator(
    private val activity: ComponentActivity,
    private val reviewManager: ReviewManager = ReviewManagerFactory.create(activity),
) {
    private val preferences = activity.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
    private var requestInFlight = false

    @Synchronized
    fun onSuccessfulExtractionSession() {
        val decision = ReviewPromptPolicy.afterSuccessfulSession(
            successfulSessions = preferences.getInt(SuccessfulSessionsKey, 0),
            requestAttempted = preferences.getBoolean(RequestAttemptedKey, false),
        )
        preferences.edit { putInt(SuccessfulSessionsKey, decision.successfulSessions) }

        if (!decision.shouldRequestReview || requestInFlight ||
            !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        ) {
            return
        }

        requestInFlight = true
        reviewManager.requestReviewFlow().addOnCompleteListener { requestTask ->
            if (!requestTask.isSuccessful) {
                requestInFlight = false
                return@addOnCompleteListener
            }

            preferences.edit { putBoolean(RequestAttemptedKey, true) }
            reviewManager.launchReviewFlow(activity, requestTask.result)
                .addOnCompleteListener { requestInFlight = false }
        }
    }

    private companion object {
        const val PreferencesName = "in_app_review"
        const val SuccessfulSessionsKey = "successful_extraction_sessions"
        const val RequestAttemptedKey = "request_attempted"
    }
}
