package com.kolehoenicke.apkextractor

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogRefreshTest {
    @Test fun supersededRefreshDoesNotPublishCancellationAsAnError() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val errors = ConcurrentLinkedQueue<String>()
        val store = ViewModelStore()
        val observer = CoroutineScope(Job() + Dispatchers.Unconfined)
        lateinit var model: MainViewModel
        instrumentation.runOnMainSync {
            model = MainViewModel(instrumentation.targetContext.applicationContext as Application)
            store.put("catalog", model)
            observer.launch { model.state.collect { it.loadError?.let(errors::add) } }
            // Replaces an in-flight load twice, as package broadcasts / returning to an XR
            // Activity can do. Cancellation must not overwrite the latest refresh's state.
            model.refresh()
            model.refresh()
        }
        try {
            val deadline = System.nanoTime() + 30_000_000_000L
            while (model.state.value.loading && System.nanoTime() < deadline) Thread.sleep(20)
            assertTrue("The latest catalog load must finish", !model.state.value.loading)
            assertTrue("No cancellation error should be visible: $errors", errors.isEmpty())
        } finally {
            observer.cancel()
            instrumentation.runOnMainSync { store.clear() }
        }
    }
}
