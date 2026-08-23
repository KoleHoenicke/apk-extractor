package com.kolehoenicke.apkextractor

import android.app.Notification
import android.app.NotificationManager
import android.Manifest
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kolehoenicke.apkextractor.data.ExportedFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExtractionNotificationsTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun standardProgressNotificationQualifiesForLiveUpdatePromotion() {
        assumeTrue(
            Build.VERSION.SDK_INT >= 36 &&
                Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA_1,
        )
        val notifications = ExtractionNotifications(context)
        notifications.createChannel()

        val notification = notifications.ongoing(
            snapshot = ExtractionSnapshot(
                apps = listOf(
                    ExtractionAppProgress(
                        packageName = "example",
                        label = "Example",
                        totalBytes = 100,
                        progress = 0.42f,
                    ),
                ),
            ),
            requestPromotion = true,
        )

        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertTrue(notification.hasPromotableCharacteristics())
        assertEquals(42, notification.extras.getInt(Notification.EXTRA_PROGRESS))
        assertEquals(100, notification.extras.getInt(Notification.EXTRA_PROGRESS_MAX))
        assertEquals(false, notification.extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE))
    }

    @Test
    fun systemPromotesPostedLiveUpdateWhenEnabled() {
        assumeTrue(
            Build.VERSION.SDK_INT >= 36 &&
                Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA_1,
        )
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.grantRuntimePermission(
            context.packageName,
            Manifest.permission.POST_NOTIFICATIONS,
        )
        val manager = context.getSystemService(NotificationManager::class.java)
        assumeTrue(manager.canPostPromotedNotifications())
        val notifications = ExtractionNotifications(context)
        notifications.createChannel()

        try {
            notifications.notifyProgress(
                snapshot = ExtractionSnapshot(
                    apps = listOf(
                        ExtractionAppProgress("example", "Example", 100, 0.42f),
                    ),
                ),
                requestPromotion = true,
            )

            var posted = manager.activeNotifications
                .firstOrNull { it.id == ExtractionNotifications.NotificationId }
                ?.notification
            repeat(20) {
                if (posted == null ||
                    posted.flags and Notification.FLAG_PROMOTED_ONGOING == 0
                ) {
                    SystemClock.sleep(50)
                    posted = manager.activeNotifications
                        .firstOrNull { it.id == ExtractionNotifications.NotificationId }
                        ?.notification
                }
            }
            assertTrue(posted != null)
            assertTrue(posted!!.flags and Notification.FLAG_PROMOTED_ONGOING != 0)
        } finally {
            notifications.cancel()
        }
    }

    @Test
    fun finishedNotificationOffersShareWhenFilesWereExported() {
        val notification = ExtractionNotifications(context).finished(
            files = listOf(
                ExportedFile(
                    uri = Uri.parse("content://example/exported.apk"),
                    displayName = "exported.apk",
                    isSplitArchive = false,
                ),
            ),
            requested = 1,
        )

        assertEquals(1, notification.actions.size)
        assertEquals(context.getString(R.string.share), notification.actions.single().title)
        assertTrue(notification.actions.single().actionIntent != null)
    }

    @Test
    fun failedFinishedNotificationDoesNotOfferShare() {
        val notification = ExtractionNotifications(context).finished(
            files = emptyList(),
            requested = 1,
        )

        assertTrue(notification.actions.isNullOrEmpty())
    }
}
