package com.kolehoenicke.apkextractor.assistant

import android.app.Application
import android.content.ContextWrapper
import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.kolehoenicke.apkextractor.ExtractionSession
import com.kolehoenicke.apkextractor.MainActivity
import com.kolehoenicke.apkextractor.MainViewModel
import com.kolehoenicke.apkextractor.data.AppFilter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.UUID

class AssistantExportHandoffTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val stores = mutableListOf<ViewModelStore>()
    private val temporaryDirectories = mutableListOf<File>()

    @Before fun requireIdle() { assertFalse(ExtractionSession.state.value.isActive) }
    @After fun cleanUp() {
        instrumentation.runOnMainSync { stores.forEach(ViewModelStore::clear) }
        ExtractionSession.clear()
        temporaryDirectories.forEach { it.deleteRecursively() }
    }

    @Test fun draftsSurviveStoreRecreationAreSingleUseExpireAndAreBounded() {
        // Instrumentation runs as the target UID, so isolate inside a private target-owned directory.
        val directory = File(context.cacheDir, "assistant-drafts-test-${UUID.randomUUID()}")
            .also { check(it.mkdirs()); temporaryDirectories += it }
        val isolatedContext = object : ContextWrapper(context) {
            override fun getNoBackupFilesDir(): File = directory
        }
        var now = 1_000L
        val store = AssistantExportDraftStore(isolatedContext) { now }
        val token = store.create(listOf("com.example.one", "com.example.one"))
        assertEquals(listOf("com.example.one"), AssistantExportDraftStore(isolatedContext) { now }.consume(token))
        assertNull(store.consume(token))
        val expired = store.create(listOf("com.example.expired"))
        now += 15 * 60 * 1000
        assertNull(store.consume(expired))
        val tokens = (1..9).map { store.create(listOf("com.example.$it")) }
        assertNull(store.consume(tokens.first()))
        assertEquals(listOf("com.example.9"), store.consume(tokens.last()))
        tokens.drop(1).dropLast(1).forEach { store.consume(it) }
    }

    @Test fun reviewRevalidatesInstallationAndClearsFiltersWithoutExporting() {
        val model = model()
        loaded(model)
        val token = AssistantExportDraftStore(context).create(listOf(context.packageName, "com.example.uninstalled"))
        instrumentation.runOnMainSync {
            model.setQuery("unrelated")
            model.setFilter(AppFilter.System)
            model.reviewAssistantExport(token)
        }
        runBlocking { withTimeout(15_000) {
            model.state.first { it.selectedPackages == setOf(context.packageName) }
        } }
        assertEquals("", model.state.value.query)
        assertEquals(AppFilter.All, model.state.value.filter)
        assertFalse(model.state.value.isExporting)
        assertFalse(ExtractionSession.state.value.isActive)
    }

    @Test fun unknownAndReplayedTokensCannotOverwriteSelection() {
        val model = model()
        loaded(model)
        val token = AssistantExportDraftStore(context).create(listOf(context.packageName))
        instrumentation.runOnMainSync { model.reviewAssistantExport(token) }
        runBlocking { withTimeout(15_000) { model.state.first { it.selectedPackages.isNotEmpty() } } }
        instrumentation.runOnMainSync {
            model.clearSelection()
            model.setQuery("keep this search")
        }
        review(model, token)
        review(model, "00000000-0000-0000-0000-000000000000")
        assertTrue(model.state.value.selectedPackages.isEmpty())
        assertEquals("keep this search", model.state.value.query)
    }

    @Test fun activeExportKeepsItsSelectionWhenReviewArrives() {
        val model = model()
        loaded(model)
        val app = model.state.value.apps.first { it.packageName == context.packageName }
        assertTrue(ExtractionSession.begin(listOf(app)))
        val token = AssistantExportDraftStore(context).create(listOf(app.packageName))
        review(model, token)
        runBlocking { withTimeout(15_000) { model.state.first { it.isExporting } } }
        instrumentation.waitForIdleSync()
        assertTrue(model.state.value.selectedPackages.isEmpty())
        assertTrue(ExtractionSession.state.value.isActive)
    }

    @Test fun pendingIntentUsesExistingSelectionUiAndRotationDoesNotReplayIt() {
        val token = AssistantExportDraftStore(context).create(listOf(context.packageName))
        val pending = assistantExportPendingIntent(context, token)
        assertTrue(pending.isImmutable)
        ActivityScenario.launch<MainActivity>(createAssistantExportIntent(context, token)).use { scenario ->
            lateinit var model: MainViewModel
            scenario.onActivity { model = ViewModelProvider(it)[MainViewModel::class.java] }
            runBlocking { withTimeout(15_000) { model.state.first { it.selectedPackages == setOf(context.packageName) } } }
            scenario.onActivity { model.clearSelection() }
            scenario.recreate()
            scenario.onActivity {
                assertTrue(ViewModelProvider(it)[MainViewModel::class.java].state.value.selectedPackages.isEmpty())
            }
            assertFalse(ExtractionSession.state.value.isActive)
        }
        pending.cancel()
    }

    @Test fun unrelatedIntentsAreNotAssistantRequests() {
        assertNull(assistantExportToken(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://example.com/prepare-export"))))
        assertNull(assistantExportToken(createAssistantExportIntent(context, "bad")))
        assertNull(assistantExportToken(Intent()))
    }

    private fun model(): MainViewModel {
        lateinit var model: MainViewModel
        instrumentation.runOnMainSync {
            model = MainViewModel(context.applicationContext as Application, SavedStateHandle())
            stores += ViewModelStore().also { it.put("model", model) }
        }
        return model
    }

    private fun loaded(model: MainViewModel) = runBlocking {
        withTimeout(15_000) { model.state.first { !it.loading && it.apps.isNotEmpty() } }
    }

    private fun review(model: MainViewModel, token: String) {
        lateinit var job: Job
        instrumentation.runOnMainSync { job = model.reviewAssistantExport(token) }
        runBlocking { withTimeout(15_000) { job.join() } }
    }
}
