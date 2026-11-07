package com.kolehoenicke.apkextractor

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.edit
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.kolehoenicke.apkextractor.data.AppFilter
import com.kolehoenicke.apkextractor.data.AppSort
import com.kolehoenicke.apkextractor.data.OutputFolderStore
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

class MultiWindowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val stores = mutableListOf<ViewModelStore>()
    private val folderStore = OutputFolderStore(context)
    private val preferences = context.getSharedPreferences("preferences", Context.MODE_PRIVATE)
    private val journal = context.getSharedPreferences("last_export", Context.MODE_PRIVATE)
    private var originalFolder: Uri? = null
    private var originalSort: String? = null
    private var originalResult: String? = null

    @Before fun rememberSettings() {
        assertFalse("Run when no real export is active", ExtractionSession.state.value.isActive)
        originalFolder = folderStore.get()
        originalSort = preferences.getString("sort", null)
        originalResult = journal.getString("result", null)
    }

    @After fun restoreSettings() {
        main { stores.forEach(ViewModelStore::clear) }
        ExtractionSession.clear()
        context.getSharedPreferences("output", Context.MODE_PRIVATE).edit(commit = true) {
            if (originalFolder == null) remove("folder_uri") else putString("folder_uri", originalFolder.toString())
        }
        preferences.edit(commit = true) { if (originalSort == null) remove("sort") else putString("sort", originalSort) }
        journal.edit(commit = true) {
            if (originalResult == null) remove("result") else putString("result", originalResult)
        }
        instrumentation.uiAutomation.dropShellPermissionIdentity()
        assertEquals(originalFolder, OutputFolderStore(context).get())
    }

    @Test fun separateTasksKeepSearchFilterAndSelectionIndependent() {
        ActivityScenario.launch<MainActivity>(createNewWindowIntent(context)).use { first ->
            ActivityScenario.launch<MainActivity>(createNewWindowIntent(context)).use { second ->
                var firstTask = -1
                var secondTask = -1
                lateinit var firstModel: MainViewModel
                lateinit var secondModel: MainViewModel
                first.onActivity { firstTask = it.taskId; firstModel = ViewModelProvider(it)[MainViewModel::class.java] }
                second.onActivity { secondTask = it.taskId; secondModel = ViewModelProvider(it)[MainViewModel::class.java] }
                assertNotEquals(firstTask, secondTask)
                loaded(firstModel); loaded(secondModel)
                first.onActivity {
                    firstModel.setQuery("Extractor")
                    firstModel.setFilter(AppFilter.All)
                    firstModel.startSelection(context.packageName)
                }
                assertEquals("", secondModel.state.value.query)
                assertEquals(AppFilter.User, secondModel.state.value.filter)
                assertTrue(secondModel.state.value.selectedPackages.isEmpty())
                second.onActivity { secondModel.setFilter(AppFilter.System) }
                assertEquals(AppFilter.All, firstModel.state.value.filter)
                instrumentation.waitForIdleSync()
                capture("desktop-multiple-windows.png")
            }
        }
    }

    @Test fun exportFolderAndSortChangesReachBothWindows() {
        val first = model(); val second = model()
        val folder = DocumentsContract.buildTreeDocumentUri("com.kolehoenicke.apkextractor.test.exports", "root")
        main { first.setOutputFolder(folder); first.setSort(AppSort.RecentlyUpdated) }
        runBlocking { withTimeout(10_000) {
            second.state.first { it.outputFolder == folder && it.sort == AppSort.RecentlyUpdated }
        } }
        folderStore.clear()
        runBlocking { withTimeout(10_000) {
            first.state.first { it.outputFolder == null }
            second.state.first { it.outputFolder == null }
        } }
    }

    @Test fun eachWindowRestoresItsOwnSearchFilterAndSelection() {
        val saved = SavedStateHandle()
        val original = model(saved)
        main {
            original.setQuery("Extractor")
            original.setFilter(AppFilter.All)
            original.startSelection(context.packageName)
        }
        val restored = model(SavedStateHandle(mapOf(
            "query" to saved.get<String>("query"),
            "filter" to saved.get<String>("filter"),
            "selection" to saved.get<ArrayList<String>>("selection"),
        )))
        val other = model()
        assertEquals("Extractor", restored.state.value.query)
        assertEquals(AppFilter.All, restored.state.value.filter)
        assertEquals(setOf(context.packageName), restored.state.value.selectedPackages)
        assertEquals("", other.state.value.query)
        assertEquals(AppFilter.User, other.state.value.filter)
        assertTrue(other.state.value.selectedPackages.isEmpty())
    }

    @Test fun windowsShareProgressAndRejectOverlappingExportSessions() {
        val first = model(); val second = model()
        val app = first.state.value.apps.first { it.packageName == context.packageName }
        assertTrue(ExtractionSession.begin(listOf(app)))
        assertFalse(ExtractionSession.begin(listOf(app)))
        ExtractionSession.updateProgress(app.packageName, 0.5f)
        runBlocking { withTimeout(10_000) {
            first.state.first { it.exportProgressByPackage[app.packageName] == 0.5f }
            second.state.first { it.exportProgressByPackage[app.packageName] == 0.5f }
        } }
        val result = UiEvent.ExportFinished(emptyList(), 1, listOf(ExportFailure(app.label, "Test", app.packageName)))
        runBlocking { ExtractionSession.finish(result); withTimeout(10_000) {
            first.state.first { !it.isExporting && it.lastResult == result }
            second.state.first { !it.isExporting && it.lastResult == result }
        } }
    }

    @Test fun onlyInitiatingWindowReceivesCompletionUiForARealExport() = runBlocking {
        val first = model(); val second = model()
        val tree = DocumentsContract.buildTreeDocumentUri("com.kolehoenicke.apkextractor.test.exports", "root")
        com.kolehoenicke.apkextractor.data.grantExportTestTree()
        main { first.setOutputFolder(tree) }
        withTimeout(10_000) { second.state.first { it.outputFolder == tree } }
        val app = first.state.value.apps.first { it.packageName == context.packageName }
        val ownerEvent = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(15_000) { first.events.first() } }
        val otherEvent = async(start = CoroutineStart.UNDISPATCHED) { withTimeoutOrNull(2_000) { second.events.first() } }
        main { assertTrue(first.extract(listOf(app))) }
        val result = ownerEvent.await() as UiEvent.ExportFinished
        try {
            assertTrue(result.failures.isEmpty())
            assertEquals(1, result.files.size)
            withTimeout(10_000) { second.state.first { it.lastResult == result && !it.isExporting } }
            assertNull(otherEvent.await())
        } finally {
            result.files.forEach { DocumentFile.fromSingleUri(context, it.uri)?.delete() }
        }
    }

    @Test fun openingAnotherWindowCannotRecoverAnActiveExportJournal() {
        val model = model()
        val app = model.state.value.apps.first()
        val store = ExportResultStore(context)
        val uri = Uri.parse("content://example/export.apk")
        assertTrue(ExtractionSession.begin(listOf(app)))
        store.begin(listOf(app), uri)
        store.created(app.packageName, uri)
        val activeJournal = journal.getString("result", null)
        store.recoverInterrupted()
        assertEquals(activeJournal, journal.getString("result", null))
        // The owning service still needs to finalize cancellation or failure while the
        // process-wide progress snapshot is active.
        store.recoverInterrupted(recoverActiveSession = true)
        assertEquals(app.packageName, store.read()!!.failures.single().packageName)
    }

    private fun model(saved: SavedStateHandle = SavedStateHandle()): MainViewModel {
        lateinit var model: MainViewModel
        main {
            val store = ViewModelStore().also(stores::add)
            model = ViewModelProvider(store, object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    MainViewModel(context.applicationContext as Application, saved) as T
            })[MainViewModel::class.java]
        }
        loaded(model)
        return model
    }

    private fun loaded(model: MainViewModel) = runBlocking {
        withTimeout(10_000) { model.state.first { !it.loading && it.apps.isNotEmpty() } }
    }

    private fun main(action: () -> Unit) = instrumentation.runOnMainSync(action)

    private fun capture(name: String) {
        instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
            File(context.cacheDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
