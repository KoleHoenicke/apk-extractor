package com.kolehoenicke.apkextractor

import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kolehoenicke.apkextractor.data.ExportedFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShareIntentsTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun singleApkUsesNativeSendIntentWithReadGrant() {
        val uri = Uri.parse("content://example/app.apk")
        val chooser = createShareChooserIntent(
            context,
            listOf(ExportedFile(uri, "app.apk", isSplitArchive = false)),
        )
        val send = chooser?.let {
            IntentCompat.getParcelableExtra(it, Intent.EXTRA_INTENT, Intent::class.java)
        }

        assertNotNull(send)
        assertEquals(Intent.ACTION_SEND, send?.action)
        assertEquals("application/vnd.android.package-archive", send?.type)
        assertEquals(uri, send?.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java))
        assertTrue(send!!.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(uri, send.clipData?.getItemAt(0)?.uri)
    }

    @Test
    fun mixedFilesUseNativeMultipleShareIntent() {
        val files = listOf(
            ExportedFile(Uri.parse("content://example/app.apk"), "app.apk", false),
            ExportedFile(Uri.parse("content://example/splits.zip"), "splits.zip", true),
        )
        val chooser = createShareChooserIntent(context, files)
        val send = chooser?.let {
            IntentCompat.getParcelableExtra(it, Intent.EXTRA_INTENT, Intent::class.java)
        }

        assertEquals(Intent.ACTION_SEND_MULTIPLE, send?.action)
        assertEquals("application/octet-stream", send?.type)
        assertEquals(2, send?.clipData?.itemCount)
    }

    @Test
    fun noFilesProducesNoShareIntent() {
        assertNull(createShareChooserIntent(context, emptyList()))
    }
}
