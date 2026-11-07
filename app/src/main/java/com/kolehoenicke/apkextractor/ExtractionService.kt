package com.kolehoenicke.apkextractor

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.kolehoenicke.apkextractor.data.ApkExporter
import com.kolehoenicke.apkextractor.data.AppCatalog
import com.kolehoenicke.apkextractor.data.ExportedFile
import com.kolehoenicke.apkextractor.data.InstalledApp
import com.kolehoenicke.apkextractor.data.OutputFolderStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class ExtractionService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var notifications: ExtractionNotifications
    private var extractionJob: Job? = null
    private var lastNotificationAt = 0L
    private var promotionDismissed = false

    override fun onCreate() {
        super.onCreate()
        notifications = ExtractionNotifications(this)
        notifications.createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ActionCancel -> extractionJob?.cancel()
            ActionDismissed -> promotionDismissed = true
            ActionStart -> if (extractionJob?.isActive != true) startExtraction(intent, startId)
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        val interrupted = extractionJob != null
        extractionJob?.cancel()
        serviceScope.cancel()
        // A finished instance may be destroyed after the next instance starts.
        // It must not clear that instance's process-wide export session.
        if (interrupted && ExtractionSession.state.value.isActive) {
            ExtractionSession.clear()
            notifications.cancel()
        }
        super.onDestroy()
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        extractionJob?.cancel()
        super.onTimeout(startId, fgsType)
    }

    private fun startExtraction(intent: Intent, startId: Int) {
        val outputTree = intent.getStringExtra(ExtraOutputTree)?.let(Uri::parse)
        val packageNames = intent.getStringArrayListExtra(ExtraPackages).orEmpty()
        val initial = ExtractionSession.state.value
        if (outputTree == null || packageNames.isEmpty() || !initial.isActive) {
            ExtractionSession.clear()
            stopSelf(startId)
            return
        }

        val notification = notifications.ongoing(initial, requestPromotion = true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                ExtractionNotifications.ForegroundNotificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(ExtractionNotifications.ForegroundNotificationId, notification)
        }

        extractionJob = serviceScope.launch {
            runExtraction(packageNames, outputTree, startId)
        }
    }

    private suspend fun runExtraction(
        packageNames: List<String>,
        outputTree: Uri,
        startId: Int,
    ) {
        val result = try {
            val initial = ExtractionSession.state.value.apps
            val placeholders = initial.map { InstalledApp(it.label, it.packageName, "", 0, null, false, emptyList()) }
            ExportResultStore(this).begin(placeholders, outputTree)
            val appsByPackage = AppCatalog(this).loadPackages(packageNames).associateBy { it.packageName }
            val missing = packageNames.filterNot(appsByPackage::containsKey)
            val apps = packageNames.mapNotNull(appsByPackage::get)
            val exporter = ApkExporter(this)
            val resultStore = ExportResultStore(this)
            val limit = Semaphore(3)
            val attempts = supervisorScope {
                apps.map { app ->
                    async {
                        limit.withPermit {
                            try {
                                val file = exporter.export(app, outputTree) { progress ->
                                    onProgress(app.packageName, progress)
                                }
                                ExportAttempt(app = app, file = file)
                            } catch (failure: Exception) {
                                if (failure is CancellationException) throw failure
                                resultStore.failed(app.packageName, failure.message ?: getString(R.string.error_unknown))
                                ExportAttempt(app = app, failure = failure)
                            }
                        }
                    }
                }.awaitAll()
            }

            val failures = buildList {
                missing.forEach { packageName ->
                    resultStore.failed(packageName, getString(R.string.error_app_no_longer_installed))
                    add(ExportFailure(packageName, getString(R.string.error_app_no_longer_installed), packageName))
                }
                attempts.forEach { attempt ->
                    attempt.failure?.let { failure ->
                        add(
                            ExportFailure(
                                appLabel = attempt.app.label,
                                packageName = attempt.app.packageName,
                                reason = failure.message ?: getString(R.string.error_unknown),
                            ),
                        )
                    }
                }
            }
            if (attempts.any { it.failure is SecurityException }) {
                OutputFolderStore(this).clear()
            }

            val files = attempts.mapNotNull(ExportAttempt::file)
            val event = UiEvent.ExportFinished(
                files = files,
                requestedCount = packageNames.size,
                failures = failures,
                outputFolder = outputTree,
            )
            resultStore.finish()
            event
        } catch (cancellation: CancellationException) {
            withContext(NonCancellable) {
                val store = ExportResultStore(this@ExtractionService)
                runCatching { store.recoverInterrupted(recoverActiveSession = true) }
                finishExtraction(store.read(), startId, cancelled = true)
            }
            throw cancellation
        } catch (failure: Exception) {
            if (failure is SecurityException) OutputFolderStore(this).clear()
            val store = ExportResultStore(this)
            runCatching { store.recoverInterrupted(recoverActiveSession = true) }
            val result = store.read() ?: UiEvent.ExportFinished(
                files = emptyList(),
                requestedCount = packageNames.size,
                failures = packageNames.map { packageName ->
                    ExportFailure(packageName, failure.message ?: getString(R.string.error_unknown), packageName)
                },
                outputFolder = outputTree,
            )
            result
        }
        // Keep finalization outside the recovery catch blocks. Service destruction
        // can cancel the worker while it returns from Main after publishing a result.
        finishExtraction(result, startId)
    }

    private suspend fun finishExtraction(
        result: UiEvent.ExportFinished?,
        startId: Int,
        cancelled: Boolean = false,
    ) = withContext(NonCancellable + Dispatchers.Main.immediate) {
        // onStartCommand also runs on Main. Retire the old job and foreground
        // service before publishing idle state, so an immediate next export
        // cannot be ignored as an already-running job or stopped by this one.
        extractionJob = null
        stopForeground(if (cancelled) STOP_FOREGROUND_REMOVE else STOP_FOREGROUND_DETACH)
        if (cancelled) {
            notifications.cancel()
        } else if (result != null) {
            runCatching { notifications.notifyFinished(result.files, result.requestedCount) }
        }
        stopSelf(startId)
        if (result != null) ExtractionSession.finish(result) else ExtractionSession.clear()
    }

    @Synchronized
    private fun onProgress(packageName: String, progress: Float) {
        ExtractionSession.updateProgress(packageName, progress)
        val now = SystemClock.elapsedRealtime()
        if (progress >= 1f || now - lastNotificationAt >= NotificationUpdateIntervalMillis) {
            lastNotificationAt = now
            notifications.notifyProgress(
                snapshot = ExtractionSession.state.value,
                requestPromotion = !promotionDismissed,
            )
        }
    }

    private data class ExportAttempt(
        val app: InstalledApp,
        val file: ExportedFile? = null,
        val failure: Exception? = null,
    )

    companion object {
        const val ActionStart = "com.kolehoenicke.apkextractor.action.START_EXTRACTION"
        const val ActionCancel = "com.kolehoenicke.apkextractor.action.CANCEL_EXTRACTION"
        const val ActionDismissed = "com.kolehoenicke.apkextractor.action.DISMISS_EXTRACTION"
        private const val ExtraPackages = "packages"
        private const val ExtraOutputTree = "output_tree"
        private const val NotificationUpdateIntervalMillis = 250L

        fun start(context: Context, apps: List<InstalledApp>, outputTree: Uri): Boolean {
            val queue = apps.distinctBy(InstalledApp::packageName)
            if (queue.isEmpty() || !ExtractionSession.begin(queue)) return false

            val intent = Intent(context, ExtractionService::class.java).apply {
                action = ActionStart
                putStringArrayListExtra(
                    ExtraPackages,
                    ArrayList(queue.map(InstalledApp::packageName)),
                )
                putExtra(ExtraOutputTree, outputTree.toString())
            }
            return runCatching {
                ContextCompat.startForegroundService(context, intent)
                true
            }.getOrElse {
                ExtractionSession.clear()
                false
            }
        }
    }
}
