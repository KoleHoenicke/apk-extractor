package com.kolehoenicke.apkextractor.data

import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import androidx.test.platform.app.InstrumentationRegistry
import com.kolehoenicke.apkextractor.ExportResultStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineStart
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.zip.ZipInputStream

class ExportReliabilityTest {
    @org.junit.Before fun allowTestProvider() {
        InstrumentationRegistry.getInstrumentation().uiAutomation.adoptShellPermissionIdentity("android.permission.MANAGE_DOCUMENTS")
        context.contentResolver.call(folder, "grantTestTree", null, null)
        InstrumentationRegistry.getInstrumentation().uiAutomation.dropShellPermissionIdentity()
    }
    @org.junit.After fun restorePermissions() {
        InstrumentationRegistry.getInstrumentation().uiAutomation.dropShellPermissionIdentity()
    }
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val folder = DocumentsContract.buildTreeDocumentUri("com.kolehoenicke.apkextractor.test.exports", "root")
    private fun app(files: List<File>) = InstalledApp("日本語-${UUID.randomUUID()}", "test.export", "1", 1, null, false, files.mapIndexed { i, f -> InstalledApk(f.path, if (i == 0) null else "config.test", f.length()) })
    private fun source() = File.createTempFile("source", ".apk", context.cacheDir).apply { writeBytes(ByteArray(1024 * 1024) { (it % 251).toByte() }) }

    @Test fun singleExportIsByteExactAndResultSurvivesStoreRecreation() = runBlocking<Unit> {
        val source = source()
        val app = app(listOf(source))
        val store = ExportResultStore(context)
        store.begin(listOf(app), folder)
        val file = ApkExporter(context).export(app, folder) {}
        store.finish()
        assertArrayEquals(source.readBytes(), context.contentResolver.openInputStream(file.uri)!!.use { it.readBytes() })
        assertEquals(file, ExportResultStore(context).read()!!.files.single())
        assertEquals(folder, store.read()!!.outputFolder)
        DocumentFile.fromSingleUri(context, file.uri)!!.delete()
        source.delete()
    }

    @Test fun cancellationRemovesPartialAndRecordsRetryablePackage() = runBlocking<Unit> {
        val source = source()
        val app = app(listOf(source))
        val store = ExportResultStore(context)
        store.begin(listOf(app), folder)
        lateinit var job: kotlinx.coroutines.Job
        job = launch(start = CoroutineStart.LAZY) {
            ApkExporter(context).export(app, folder) { if (it > 0) job.cancel() }
        }
        job.start()
        job.join()
        assertTrue(job.isCancelled)
        store.recoverInterrupted()
        assertEquals(app.packageName, store.read()!!.failures.single().packageName)
        assertNull(DocumentFile.fromTreeUri(context, folder)!!.findFile(exportFileName(app)))
        source.delete()
    }

    @Test fun splitArchiveContainsEveryOriginalFileAndHashes() = runBlocking<Unit> {
        val first = source()
        val second = source()
        val app = app(listOf(first, second))
        val store = ExportResultStore(context)
        store.begin(listOf(app), folder)
        val file = ApkExporter(context).export(app, folder) {}
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(context.contentResolver.openInputStream(file.uri)).use { zip ->
            while (true) { val entry = zip.nextEntry ?: break; entries[entry.name] = zip.readBytes() }
        }
        assertArrayEquals(first.readBytes(), entries["base.apk"])
        assertArrayEquals(second.readBytes(), entries[second.name])
        val manifest = org.json.JSONObject(String(entries.getValue("manifest.json")))
        assertEquals(2, manifest.getJSONArray("apks").length())
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(first.readBytes()).joinToString("") { "%02x".format(it.toInt() and 255) }
        assertEquals(digest, manifest.getJSONArray("apks").getJSONObject(0).getString("sha256"))
        store.finish()
        DocumentFile.fromSingleUri(context, file.uri)!!.delete()
        first.delete(); second.delete()
    }

    @Test fun recoveryKeepsCompletedFilesAndRemovesOnlyRecordedPartial() = runBlocking<Unit> {
        val source = source()
        val first = app(listOf(source))
        val second = first.copy(packageName = "test.second")
        val store = ExportResultStore(context)
        store.begin(listOf(first, second), folder)
        val saved = ApkExporter(context).export(first, folder) {}
        val partial = DocumentFile.fromTreeUri(context, folder)!!.createFile("application/zip", "partial-${UUID.randomUUID()}.zip")!!
        store.created(second.packageName, partial.uri)
        ExportResultStore(context).recoverInterrupted()
        val result = store.read()!!
        assertEquals(listOf(saved), result.files)
        assertEquals(second.packageName, result.failures.single().packageName)
        assertTrue(DocumentFile.fromSingleUri(context, saved.uri)!!.exists())
        assertNull(DocumentFile.fromTreeUri(context, folder)!!.findFile(partial.name ?: "missing"))
        DocumentFile.fromSingleUri(context, saved.uri)!!.delete()
        source.delete()
    }
}
