package com.kolehoenicke.apkextractor

import android.content.ActivityNotFoundException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalActivitiesTest {
    @Test fun missingHandlerIsRecoverable() {
        assertFalse(tryLaunchExternalActivity { throw ActivityNotFoundException() })
    }
    @Test fun deniedHandlerIsRecoverable() {
        assertFalse(tryLaunchExternalActivity { throw SecurityException() })
    }
    @Test fun successfulLaunchRunsOnce() {
        var launches = 0
        assertTrue(tryLaunchExternalActivity { launches++ })
        assertTrue(launches == 1)
    }
    @Test(expected = IllegalStateException::class)
    fun programmingErrorsAreNotHidden() {
        tryLaunchExternalActivity { throw IllegalStateException() }
    }
}
