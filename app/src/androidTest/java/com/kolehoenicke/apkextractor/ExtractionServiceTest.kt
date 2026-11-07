package com.kolehoenicke.apkextractor

import android.content.Context
import android.provider.DocumentsContract
import androidx.core.content.edit
import androidx.documentfile.provider.DocumentFile
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.kolehoenicke.apkextractor.data.AppCatalog
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
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
}
