package com.kolehoenicke.apkextractor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewPromptPolicyTest {
    @Test
    fun `review becomes eligible after third successful extraction session`() {
        val first = ReviewPromptPolicy.afterSuccessfulSession(0, requestAttempted = false)
        val second = ReviewPromptPolicy.afterSuccessfulSession(
            first.successfulSessions,
            requestAttempted = false,
        )
        val third = ReviewPromptPolicy.afterSuccessfulSession(
            second.successfulSessions,
            requestAttempted = false,
        )

        assertFalse(first.shouldRequestReview)
        assertFalse(second.shouldRequestReview)
        assertTrue(third.shouldRequestReview)
        assertEquals(3, third.successfulSessions)
    }

    @Test
    fun `review is never requested again after a launch attempt`() {
        val decision = ReviewPromptPolicy.afterSuccessfulSession(
            successfulSessions = 3,
            requestAttempted = true,
        )

        assertFalse(decision.shouldRequestReview)
        assertEquals(3, decision.successfulSessions)
    }
}
