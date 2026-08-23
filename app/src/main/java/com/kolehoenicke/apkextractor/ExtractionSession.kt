package com.kolehoenicke.apkextractor

import com.kolehoenicke.apkextractor.data.InstalledApp
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ExtractionAppProgress(
    val packageName: String,
    val label: String,
    val totalBytes: Long,
    val progress: Float,
)

data class ExtractionSnapshot(
    val apps: List<ExtractionAppProgress> = emptyList(),
) {
    val isActive: Boolean get() = apps.isNotEmpty()
    val packages: Set<String> get() = apps.mapTo(mutableSetOf(), ExtractionAppProgress::packageName)
    val progressByPackage: Map<String, Float>
        get() = apps.associate { it.packageName to it.progress }

    val overallProgress: Float
        get() {
            if (apps.isEmpty()) return 0f
            val knownBytes = apps.sumOf { it.totalBytes.coerceAtLeast(0) }
            return if (knownBytes > 0) {
                apps.sumOf { it.progress.toDouble() * it.totalBytes.coerceAtLeast(0) }
                    .div(knownBytes)
                    .toFloat()
            } else {
                apps.map(ExtractionAppProgress::progress).average().toFloat()
            }.coerceIn(0f, 1f)
        }
}

/** Process-wide handoff between the foreground extraction service and the visible UI. */
object ExtractionSession {
    private val mutableState = MutableStateFlow(ExtractionSnapshot())
    private val mutableEvents = MutableSharedFlow<UiEvent>(extraBufferCapacity = 1)

    val state = mutableState.asStateFlow()
    val events = mutableEvents.asSharedFlow()

    @Synchronized
    fun begin(apps: List<InstalledApp>): Boolean {
        if (mutableState.value.isActive) return false
        mutableState.value = ExtractionSnapshot(
            apps = apps.map { app ->
                ExtractionAppProgress(
                    packageName = app.packageName,
                    label = app.label,
                    totalBytes = app.totalBytes,
                    progress = 0f,
                )
            },
        )
        return true
    }

    fun updateProgress(packageName: String, progress: Float) {
        mutableState.update { current ->
            current.copy(
                apps = current.apps.map { app ->
                    if (app.packageName == packageName) {
                        app.copy(progress = progress.coerceIn(0f, 1f))
                    } else {
                        app
                    }
                },
            )
        }
    }

    suspend fun finish(event: UiEvent.ExportFinished) {
        mutableState.value = ExtractionSnapshot()
        mutableEvents.emit(event)
    }

    fun clear() {
        mutableState.value = ExtractionSnapshot()
    }
}
