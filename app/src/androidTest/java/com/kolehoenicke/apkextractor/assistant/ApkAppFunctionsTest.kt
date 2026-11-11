package com.kolehoenicke.apkextractor.assistant

import android.app.PendingIntent
import android.content.Intent
import androidx.appfunctions.AppFunctionData
import androidx.appfunctions.AppFunctionElementNotFoundException
import androidx.appfunctions.AppFunctionInvalidArgumentException
import androidx.appfunctions.AppFunctionManager
import androidx.appfunctions.AppFunctionNotSupportedException
import androidx.appfunctions.AppFunctionSearchSpec
import androidx.appfunctions.ExecuteAppFunctionRequest
import androidx.appfunctions.ExecuteAppFunctionResponse
import androidx.appfunctions.metadata.AppFunctionMetadata
import androidx.appfunctions.metadata.AppFunctionObjectTypeMetadata
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.kolehoenicke.apkextractor.ExtractionSession
import com.kolehoenicke.apkextractor.MainActivity
import com.kolehoenicke.apkextractor.MainViewModel
import com.kolehoenicke.apkextractor.data.AppCatalog
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Exercises the generated schema, serialization, platform registry, and real bound service together. */
@SdkSuppress(minSdkVersion = 36)
class ApkAppFunctionsTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val manager = requireNotNull(AppFunctionManager.getInstance(context))
    private lateinit var metadata: List<AppFunctionMetadata>

    @Before fun discoverFunctions() = runBlocking {
        assertFalse("Run without a real export in progress", ExtractionSession.state.value.isActive)
        withTimeout(30_000) {
            do {
                metadata = manager.searchAppFunctions(AppFunctionSearchSpec(packageNames = setOf(context.packageName)))
                if (metadata.size < 3) delay(200)
            } while (metadata.size < 3)
        }
    }

    @After fun clearSimulatedProgress() { ExtractionSession.clear() }

    @Test fun registeredSearchReturnsActualInstalledMetadataAndSupportsPagination() = runBlocking {
        assertEquals(3, metadata.size)
        assertTrue(metadata.all { !it.description.isNullOrBlank() })
        val actual = AppCatalog(context).load().first { it.packageName == context.packageName }
        val result = success(call(ApkAppFunctionService.FUNCTION_ID_SEARCH_INSTALLED_APPS) {
            setAppFunctionData("criteria", criteria(ApkAppFunctionService.FUNCTION_ID_SEARCH_INSTALLED_APPS) {
                setString("appType", "all")
                setStringList("packageNames", listOf(context.packageName))
                setBoolean("splitApks", false)
            })
        })
        assertEquals(1, result.getInt("totalMatches"))
        val app = result.getAppFunctionDataList("apps")!!.single()
        assertEquals(actual.packageName, app.getString("packageName"))
        assertEquals(actual.versionCode, app.getLong("versionCode"))
        assertEquals(actual.totalBytes, app.getLong("totalBytes"))
        assertEquals(actual.apkFiles.size, app.getInt("apkCount"))
        assertEquals(actual.lastUpdateTime, app.getLong("lastUpdateTimeMillis"))

        val pageOne = searchPage(0)
        val pageTwo = searchPage(1)
        assertEquals(pageOne.getInt("totalMatches"), pageTwo.getInt("totalMatches"))
        assertTrue(pageOne.getInt("totalMatches") > 1)
        assertNotEquals(pageOne.getAppFunctionDataList("apps")!!.single().getString("packageName"),
            pageTwo.getAppFunctionDataList("apps")!!.single().getString("packageName"))
        assertFalse(ExtractionSession.state.value.isActive)
    }

    @Test fun optionalCriteriaDefaultsWorkAndInvalidRangesReturnTypedErrors() = runBlocking {
        val function = ApkAppFunctionService.FUNCTION_ID_SEARCH_INSTALLED_APPS
        val result = success(call(function) { setAppFunctionData("criteria", criteria(function) {}) })
        assertTrue(result.getAppFunctionDataList("apps")!!.all { !it.getBoolean("isSystemApp") })
        val error = call(function) {
            setAppFunctionData("criteria", criteria(function) {
                setLong("minimumBytes", 300)
                setLong("maximumBytes", 100)
            })
        }
        assertTrue(error is ExecuteAppFunctionResponse.Error && error.error is AppFunctionInvalidArgumentException)
    }

    @Test fun preparationReturnsReviewActionAndExistingActivityShowsSelectionWithoutExporting() = runBlocking<Unit> {
        // The same app supports multiple desktop tasks. Give this lifecycle assertion its own clean task.
        val launch = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        ActivityScenario.launch<MainActivity>(launch).use { scenario ->
            lateinit var model: MainViewModel
            lateinit var activity: MainActivity
            scenario.onActivity { activity = it; model = ViewModelProvider(it)[MainViewModel::class.java] }
            withTimeout(15_000) { model.state.first { !it.loading } }
            scenario.onActivity { model.setQuery("unchanged until review") }
            val result = success(call(ApkAppFunctionService.FUNCTION_ID_PREPARE_EXPORT) {
                setAppFunctionData("criteria", criteria(ApkAppFunctionService.FUNCTION_ID_PREPARE_EXPORT) {
                    setStringList("packageNames", listOf(context.packageName))
                    setString("appType", "all")
                })
            })
            assertEquals(1, result.getInt("selectedCount"))
            assertEquals("unchanged until review", model.state.value.query)
            assertTrue(model.state.value.selectedPackages.isEmpty())
            assertFalse(ExtractionSession.state.value.isActive)
            val review = requireNotNull(result.getParcelable("reviewIntent", PendingIntent::class.java))
            assertTrue(review.isImmutable)
            try {
                review.send()
                withTimeout(15_000) { model.state.first { it.selectedPackages == setOf(context.packageName) } }
                assertEquals("", model.state.value.query)
                assertFalse(ExtractionSession.state.value.isActive)
                assertThrows(PendingIntent.CanceledException::class.java) { review.send() }
            } finally {
                // onNewIntent updates Activity.intent. ActivityScenario filters lifecycle events
                // against its original intent, so restore that intent before scenario teardown.
                instrumentation.runOnMainSync { activity.intent = launch }
            }
        }
    }

    @Test fun missingExplicitPackageCannotPrepareAPartialSelection() = runBlocking {
        val function = ApkAppFunctionService.FUNCTION_ID_PREPARE_EXPORT
        val result = call(function) {
            setAppFunctionData("criteria", criteria(function) {
                setString("appType", "all")
                setStringList("packageNames", listOf(context.packageName, "com.example.not.installed"))
            })
        }
        assertTrue(result is ExecuteAppFunctionResponse.Error && result.error is AppFunctionElementNotFoundException)
        assertFalse(ExtractionSession.state.value.isActive)
    }

    @Test fun activeExportReportsProgressAndRefusesAnotherPreparedSelection() = runBlocking {
        val app = AppCatalog(context).load().first { it.packageName == context.packageName }
        assertTrue(ExtractionSession.begin(listOf(app)))
        ExtractionSession.updateProgress(app.packageName, 0.4f)
        val status = success(call(ApkAppFunctionService.FUNCTION_ID_GET_EXPORT_STATUS) {})
        assertTrue(status.getBoolean("isExporting"))
        assertEquals(0.4f, status.getFloat("overallProgress"), 0.0001f)
        assertEquals(app.packageName, status.getAppFunctionDataList("activeApps")!!.single().getString("packageName"))
        val function = ApkAppFunctionService.FUNCTION_ID_PREPARE_EXPORT
        val refused = call(function) { setAppFunctionData("criteria", criteria(function) {}) }
        assertTrue(refused is ExecuteAppFunctionResponse.Error && refused.error is AppFunctionNotSupportedException)
    }

    private suspend fun searchPage(offset: Int): AppFunctionData {
        val function = ApkAppFunctionService.FUNCTION_ID_SEARCH_INSTALLED_APPS
        return success(call(function) {
            setAppFunctionData("criteria", criteria(function) { setString("appType", "all") })
            setInt("offset", offset)
            setInt("limit", 1)
        })
    }

    private fun criteria(function: String, fill: AppFunctionData.Builder.() -> Unit): AppFunctionData {
        val schema = metadata.single { it.id == function }
        val type = schema.components.dataTypes.getValue(AssistantAppQuery::class.java.name) as AppFunctionObjectTypeMetadata
        return AppFunctionData.Builder(type, schema.components).apply(fill).build()
    }

    private suspend fun call(function: String, fill: AppFunctionData.Builder.() -> Unit): ExecuteAppFunctionResponse {
        val schema = metadata.single { it.id == function }
        val parameters = AppFunctionData.Builder(schema.parameters, schema.components).apply(fill).build()
        return withTimeout(15_000) {
            manager.executeAppFunction(ExecuteAppFunctionRequest(context.packageName, function, parameters))
        }
    }

    private fun success(response: ExecuteAppFunctionResponse): AppFunctionData {
        assertTrue("Expected success, got $response", response is ExecuteAppFunctionResponse.Success)
        return requireNotNull((response as ExecuteAppFunctionResponse.Success).returnValue
            .getAppFunctionData(ExecuteAppFunctionResponse.Success.PROPERTY_RETURN_VALUE))
    }
}
