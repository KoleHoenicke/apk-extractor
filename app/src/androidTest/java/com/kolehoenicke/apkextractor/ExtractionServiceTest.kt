package com.kolehoenicke.apkextractor

import android.content.Intent
import android.content.Context
import android.provider.DocumentsContract
import androidx.core.content.edit
import androidx.documentfile.provider.DocumentFile
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.kolehoenicke.apkextractor.data.AppCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ExtractionServiceTest {
    @Test fun consecutiveExportsCompleteWithoutLosingTheNextForegroundStart() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assertFalse(ExtractionSession.state.value.isActive)
        val journal = context.getSharedPreferences("last_export", Context.MODE_PRIVATE)
        val originalResult = journal.getString("result", null)
        val tree = DocumentsContract.buildTreeDocumentUri("com.kolehoenicke.apkextractor.test.exports", "root")
        com.kolehoenicke.apkextractor.data.grantExportTestTree()
        val app = AppCatalog(context).loadPackages(listOf(context.packageName)).single()
        val originalBytes = File(context.applicationInfo.sourceDir).readBytes()
        val exported = mutableListOf<android.net.Uri>()
        try {
            ActivityScenario.launch<MainActivity>(createNewWindowIntent(context)).use { scenario ->
                repeat(12) {
                    val completed = async(start = CoroutineStart.UNDISPATCHED) {
                        withTimeout(20_000) { ExtractionSession.events.first() }
                    }
                    scenario.onActivity { assertTrue(ExtractionService.start(it, listOf(app), tree)) }
                    val result = completed.await() as UiEvent.ExportFinished
                    exported += result.files.map { it.uri }
                    assertTrue(result.failures.toString(), result.failures.isEmpty())
                    assertEquals(1, result.files.size)
                    assertFalse(ExtractionSession.state.value.isActive)
                    assertArrayEquals(originalBytes, context.contentResolver.openInputStream(result.files.single().uri)!!.use { it.readBytes() })
                    assertEquals(result.files.single().uri, ExportResultStore(context).read()!!.files.single().uri)
                }
            }
        } finally {
            exported.forEach { DocumentFile.fromSingleUri(context, it)?.delete() }
            ExtractionSession.clear()
            journal.edit(commit = true) {
                if (originalResult == null) remove("result") else putString("result", originalResult)
            }
            ExtractionNotifications(context).cancel()
        }
    }

    @Test fun completionCanImmediatelyStartTheNextExportOnMain() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertFalse(ExtractionSession.state.value.isActive)
        val journal = context.getSharedPreferences("last_export", Context.MODE_PRIVATE)
        val originalResult = journal.getString("result", null)
        val tree = DocumentsContract.buildTreeDocumentUri("com.kolehoenicke.apkextractor.test.exports", "root")
        com.kolehoenicke.apkextractor.data.grantExportTestTree()
        val app = AppCatalog(context).loadPackages(listOf(context.packageName)).single()
        val originalBytes = File(context.applicationInfo.sourceDir).readBytes()
        val results = mutableListOf<UiEvent.ExportFinished>()
        val count = 24
        try {
            ActivityScenario.launch<MainActivity>(createNewWindowIntent(context)).use { scenario ->
                val completed = async(Dispatchers.Main.immediate, start = CoroutineStart.UNDISPATCHED) {
                    withTimeout(90_000) {
                        ExtractionSession.events.take(count).collect { event ->
                            val result = event as UiEvent.ExportFinished
                            results += result
                            assertFalse(ExtractionSession.state.value.isActive)
                            // No file I/O or UI round-trip between completion and the next
                            // request. The completion collector itself starts the next job.
                            if (results.size < count) {
                                assertTrue(ExtractionService.start(context, listOf(app), tree))
                            }
                        }
                    }
                }
                scenario.onActivity { assertTrue(ExtractionService.start(it, listOf(app), tree)) }
                completed.await()
                assertEquals(count, results.size)
                results.forEach { result ->
                    assertTrue(result.failures.toString(), result.failures.isEmpty())
                    assertEquals(1, result.files.size)
                    assertArrayEquals(originalBytes, context.contentResolver
                        .openInputStream(result.files.single().uri)!!.use { it.readBytes() })
                }
                assertEquals(results.last().files.single().uri,
                    ExportResultStore(context).read()!!.files.single().uri)
            }
        } finally {
            context.stopService(Intent(context, ExtractionService::class.java))
            results.flatMap { it.files }.forEach { DocumentFile.fromSingleUri(context, it.uri)?.delete() }
            ExtractionSession.clear()
            journal.edit(commit = true) {
                if (originalResult == null) remove("result") else putString("result", originalResult)
            }
            ExtractionNotifications(context).cancel()
        }
    }


    @Test fun cancellingAnActiveBatchAllowsTheNextExportToComplete() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assertFalse(ExtractionSession.state.value.isActive)
        val journal = context.getSharedPreferences("last_export", Context.MODE_PRIVATE)
        val originalResult = journal.getString("result", null)
        val tree = DocumentsContract.buildTreeDocumentUri("com.kolehoenicke.apkextractor.test.exports", "root")
        com.kolehoenicke.apkextractor.data.grantExportTestTree()
        val catalog = AppCatalog(context)
        val packages = context.packageManager.getInstalledApplications(0).map { it.packageName }
        val batch = catalog.loadPackages(packages).sortedByDescending { it.totalBytes }.take(8)
        assertTrue("A real batch is needed to cancel during copying", batch.size >= 3)
        val app = catalog.loadPackages(listOf(context.packageName)).single()
        val exported = mutableListOf<android.net.Uri>()
        try {
            ActivityScenario.launch<MainActivity>(createNewWindowIntent(context)).use { scenario ->
                instrumentation.waitForIdleSync()
                val screenshot = instrumentation.uiAutomation.takeScreenshot()
                File(context.getExternalFilesDir(null), "crash-fix-normal-app.png").outputStream().use {
                    screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
                screenshot.recycle()
                val cancelled = async(start = CoroutineStart.UNDISPATCHED) {
                    withTimeout(30_000) { ExtractionSession.events.first() as UiEvent.ExportFinished }
                }
                val copying = async(start = CoroutineStart.UNDISPATCHED) {
                    withTimeout(30_000) { ExtractionSession.state.first { snapshot ->
                        snapshot.apps.any { it.progress > 0f && it.progress < 1f }
                    } }
                }
                scenario.onActivity { assertTrue(ExtractionService.start(it, batch, tree)) }
                copying.await()
                scenario.onActivity {
                    it.startService(Intent(it, ExtractionService::class.java)
                        .setAction(ExtractionService.ActionCancel))
                }
                val interrupted = cancelled.await()
                exported += interrupted.files.map { it.uri }
                assertFalse(ExtractionSession.state.value.isActive)
                assertTrue("Cancelled apps should remain retryable", interrupted.failures.isNotEmpty())
                assertEquals(batch.size, interrupted.requestedCount)
                val completed = async(start = CoroutineStart.UNDISPATCHED) {
                    withTimeout(30_000) { ExtractionSession.events.first() as UiEvent.ExportFinished }
                }
                scenario.onActivity { assertTrue(ExtractionService.start(it, listOf(app), tree)) }
                val result = completed.await()
                exported += result.files.map { it.uri }
                assertTrue(result.failures.toString(), result.failures.isEmpty())
                assertEquals(1, result.files.size)
                assertFalse(ExtractionSession.state.value.isActive)
                assertArrayEquals(File(context.applicationInfo.sourceDir).readBytes(),
                    context.contentResolver.openInputStream(result.files.single().uri)!!.use { it.readBytes() })
            }
        } finally {
            context.stopService(Intent(context, ExtractionService::class.java))
            exported.forEach { DocumentFile.fromSingleUri(context, it)?.delete() }
            ExtractionSession.clear()
            journal.edit(commit = true) {
                if (originalResult == null) remove("result") else putString("result", originalResult)
            }
            ExtractionNotifications(context).cancel()
        }
    }

}
