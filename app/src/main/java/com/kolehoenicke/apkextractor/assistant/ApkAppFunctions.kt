package com.kolehoenicke.apkextractor.assistant

import android.app.PendingIntent
import androidx.annotation.RequiresApi
import androidx.appfunctions.AppFunctionDeclaration
import androidx.appfunctions.AppFunctionElementNotFoundException
import androidx.appfunctions.AppFunctionInvalidArgumentException
import androidx.appfunctions.AppFunctionLimitExceededException
import androidx.appfunctions.AppFunctionNotSupportedException
import androidx.appfunctions.AppFunctionSerializable
import androidx.appfunctions.AppFunctionService
import androidx.appfunctions.AppFunctionServiceEntryPoint
import com.kolehoenicke.apkextractor.ExportResultStore
import com.kolehoenicke.apkextractor.ExtractionSession
import com.kolehoenicke.apkextractor.data.AppCatalog
import com.kolehoenicke.apkextractor.data.InstalledApp
import com.kolehoenicke.apkextractor.data.OutputFolderStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Android MCP tools use the installed catalog and existing export UI. They never start an export or share files. */
@RequiresApi(36)
@AppFunctionServiceEntryPoint(serviceName = "ApkAppFunctionService", appFunctionXmlFileName = "apk_app_functions")
abstract class BaseApkAppFunctionService : AppFunctionService() {
    /**
     * Finds installed apps using exact local metadata, without exporting or changing the UI.
     * Translate natural-language requests into structured criteria, for example "non-system apps
     * updated this week" becomes appType=user and updatedSinceMillis at the start of that week.
     * For a named app, search first and use the returned exact package identifier in prepareExport.
     * If multiple apps match a name, ask the user which one they mean. Never guess package identifiers.
     *
     * @param criteria Filters combined with AND. Sizes include the base APK and every installed split.
     * @param offset Zero-based result offset, default 0. Results are ordered by package identifier.
     * @param limit Number of results, default 50, maximum 100. Use totalMatches to detect more results.
     * @return Matching installed-app metadata and the full match count. Does not contain APKs or private app data.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun searchInstalledApps(
        criteria: AssistantAppQuery,
        offset: Int? = null,
        limit: Int? = null,
    ): AssistantAppSearchResult = withContext(Dispatchers.IO) {
        validate(criteria)
        val start = offset ?: 0
        val count = limit ?: 50
        if (start < 0 || count !in 1..100) {
            throw AppFunctionInvalidArgumentException("offset must be nonnegative and limit must be between 1 and 100.")
        }
        val apps = matchingApps(criteria)
        AssistantAppSearchResult(apps.drop(start).take(count).map(InstalledApp::toAssistantApp), apps.size)
    }

    /**
     * Prepares an export selection for the user to review in APK Extractor's existing UI.
     * Does not export, send, upload, or share anything. Open the returned reviewIntent only when
     * the user asks to continue. The user must tap Extract in APK Extractor to start copying files.
     * For a named app, first use searchInstalledApps and pass the exact package identifier.
     * Resolve relative dates in the user's timezone before supplying updatedSinceMillis.
     * The complete matching selection is prepared, independent of search pagination. No silent truncation.
     *
     * @param criteria Selection filters combined with AND. At most 500 apps can be prepared at once.
     * @return Selected counts and a single-use review intent valid for 15 minutes. Installation is rechecked on opening.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun prepareExport(criteria: AssistantAppQuery): AssistantPreparedExport = withContext(Dispatchers.IO) {
        validate(criteria)
        if (ExtractionSession.state.value.isActive) {
            throw AppFunctionNotSupportedException("An export is already running. Check getExportStatus and wait before preparing another selection.")
        }
        val apps = matchingApps(criteria)
        if (criteria.packageNames.isNotEmpty()) {
            val matched = apps.mapTo(mutableSetOf(), InstalledApp::packageName)
            if (!matched.containsAll(criteria.packageNames)) {
                throw AppFunctionElementNotFoundException("Some requested packages are no longer installed or do not match the filters. Search again.")
            }
        }
        if (apps.isEmpty()) throw AppFunctionElementNotFoundException("No installed apps match these criteria.")
        if (apps.size > AssistantAppQuery.MaxPreparedApps) {
            throw AppFunctionLimitExceededException("More than 500 apps match. Narrow the selection; no selection was prepared.")
        }
        ensureActive()
        val token = AssistantExportDraftStore(this@BaseApkAppFunctionService).create(apps.map(InstalledApp::packageName))
        AssistantPreparedExport(
            selectedCount = apps.size,
            totalBytes = apps.sumOf(InstalledApp::totalBytes),
            splitAppCount = apps.count(InstalledApp::isSplit),
            needsFolderSelection = OutputFolderStore(this@BaseApkAppFunctionService).get() == null,
            reviewIntent = assistantExportPendingIntent(this@BaseApkAppFunctionService, token),
        )
    }

    /**
     * Reads current export progress and the most recent completed result. Does not change state,
     * recover interrupted files, or grant access to exported files. Progress is between 0 and 1.
     * An idle state does not mean the latest export succeeded; inspect the saved and failed counts.
     *
     * @return Running app progress and the latest completed counts, or null counts when no result exists.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun getExportStatus(): AssistantExportStatus = withContext(Dispatchers.IO) {
        val active = ExtractionSession.state.value
        val result = ExportResultStore(this@BaseApkAppFunctionService).read()
        AssistantExportStatus(
            isExporting = active.isActive,
            overallProgress = active.overallProgress,
            activeApps = active.apps.map { AssistantAppProgress(it.packageName, it.label, it.progress) },
            lastRequestedCount = result?.requestedCount,
            lastSavedCount = result?.files?.size,
            lastFailedCount = result?.failures?.size,
        )
    }

    private suspend fun matchingApps(criteria: AssistantAppQuery): List<InstalledApp> {
        val apps = AppCatalog(this).load()
        return apps.filter { app ->
            kotlin.coroutines.coroutineContext.ensureActive()
            criteria.matches(app)
        }.sortedBy(InstalledApp::packageName)
    }

    private fun validate(criteria: AssistantAppQuery) {
        try {
            criteria.validate()
        } catch (failure: IllegalArgumentException) {
            throw AppFunctionInvalidArgumentException(failure.message)
        }
    }
}

/** Metadata of one installed app. APKs contain installed code only, not private user data or a publisher's AAB. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class AssistantInstalledApp(
    /** Display name. Multiple apps may have the same name. */
    val label: String,
    /** Exact installed package identifier. */
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val isSystemApp: Boolean,
    /** Combined size of the base APK and all installed splits, in bytes. */
    val totalBytes: Long,
    /** Number of installed APK files. More than one means export produces a ZIP, not a single installable APK. */
    val apkCount: Int,
    /** Last update time in Unix epoch milliseconds. */
    val lastUpdateTimeMillis: Long,
)

/** One page of matching apps. totalMatches may exceed apps.size. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class AssistantAppSearchResult(val apps: List<AssistantInstalledApp>, val totalMatches: Int)

/** A prepared selection only. No export has started. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class AssistantPreparedExport(
    val selectedCount: Int,
    val totalBytes: Long,
    val splitAppCount: Int,
    val needsFolderSelection: Boolean,
    /** Open for user review within 15 minutes. The user must tap Extract to start the export. */
    val reviewIntent: PendingIntent,
)

/** Current progress and the most recent completed result. Nullable result counts mean no completed result is recorded. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class AssistantExportStatus(
    val isExporting: Boolean,
    val overallProgress: Float,
    val activeApps: List<AssistantAppProgress>,
    val lastRequestedCount: Int?,
    val lastSavedCount: Int?,
    val lastFailedCount: Int?,
)

/** Progress of one currently exporting app, between 0 and 1. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class AssistantAppProgress(val packageName: String, val label: String, val progress: Float)

private fun InstalledApp.toAssistantApp() = AssistantInstalledApp(
    label, packageName, versionName, versionCode, isSystemApp, totalBytes, apkFiles.size, lastUpdateTime,
)
