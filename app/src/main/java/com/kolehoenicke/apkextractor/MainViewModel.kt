package com.kolehoenicke.apkextractor

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kolehoenicke.apkextractor.data.AppCatalog
import com.kolehoenicke.apkextractor.data.AppFilter
import com.kolehoenicke.apkextractor.data.ExportedFile
import com.kolehoenicke.apkextractor.data.InstalledApp
import com.kolehoenicke.apkextractor.data.OutputFolderStore
import com.kolehoenicke.apkextractor.data.filterApps
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class AppUiState(
    val apps: List<InstalledApp> = emptyList(),
    val filter: AppFilter = AppFilter.User,
    val query: String = "",
    val loading: Boolean = true,
    val loadError: String? = null,
    val outputFolder: Uri? = null,
    val selectedPackages: Set<String> = emptySet(),
    val exportingPackages: Set<String> = emptySet(),
    val exportProgressByPackage: Map<String, Float> = emptyMap(),
) {
    val visibleApps: List<InstalledApp>
        get() = filterApps(apps, filter, query)

    val isExporting: Boolean get() = exportingPackages.isNotEmpty()
}

sealed interface UiEvent {
    data class ExportFinished(
        val files: List<ExportedFile>,
        val requestedCount: Int,
        val failures: List<ExportFailure>,
    ) : UiEvent
}

data class ExportFailure(val appLabel: String, val reason: String)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val catalog = AppCatalog(application)
    private val folderStore = OutputFolderStore(application)
    private val eventsChannel = Channel<UiEvent>(Channel.BUFFERED)
    private var refreshJob: Job? = null

    private val initialFolder = folderStore.get()
    private val _state = MutableStateFlow(
        AppUiState(outputFolder = initialFolder),
    )
    val state: StateFlow<AppUiState> = _state.asStateFlow()
    val events = eventsChannel.receiveAsFlow()

    init {
        refresh()
        viewModelScope.launch {
            ExtractionSession.state.collectLatest { extraction ->
                _state.update { current ->
                    current.copy(
                        exportingPackages = extraction.packages,
                        exportProgressByPackage = extraction.progressByPackage,
                    )
                }
            }
        }
        viewModelScope.launch {
            ExtractionSession.events.collect { event ->
                if (folderStore.get() == null) {
                    _state.update { it.copy(outputFolder = null) }
                }
                eventsChannel.send(event)
            }
        }
    }

    fun refresh(showLoading: Boolean = true) {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    loading = showLoading || it.apps.isEmpty(),
                    loadError = null,
                )
            }
            runCatching { catalog.load() }
                .onSuccess { apps ->
                    val installedPackages = apps.mapTo(mutableSetOf(), InstalledApp::packageName)
                    _state.update {
                        it.copy(
                            apps = apps,
                            loading = false,
                            selectedPackages = it.selectedPackages.intersect(installedPackages),
                        )
                    }
                }
                .onFailure { failure ->
                    _state.update {
                        it.copy(
                            loading = false,
                            loadError = failure.message
                                ?: getApplication<Application>().getString(R.string.error_loading_apps),
                        )
                    }
                }
        }
    }

    fun refreshSilently() = refresh(showLoading = false)

    fun setFilter(filter: AppFilter) {
        _state.update { it.copy(filter = filter) }
    }

    fun setQuery(query: String) {
        _state.update { it.copy(query = query) }
    }

    fun setOutputFolder(uri: Uri) {
        folderStore.set(uri)
        _state.update { it.copy(outputFolder = uri) }
    }

    fun startSelection(packageName: String) {
        if (_state.value.isExporting) return
        _state.update { it.copy(selectedPackages = setOf(packageName)) }
    }

    fun toggleSelection(packageName: String) {
        if (_state.value.isExporting) return
        _state.update { current ->
            val selected = current.selectedPackages.toMutableSet()
            if (!selected.add(packageName)) selected.remove(packageName)
            current.copy(selectedPackages = selected)
        }
    }

    fun clearSelection() {
        _state.update { it.copy(selectedPackages = emptySet()) }
    }

    fun extract(apps: List<InstalledApp>) {
        val folder = _state.value.outputFolder ?: return
        if (_state.value.isExporting) return
        if (!ExtractionService.start(getApplication(), apps, folder)) {
            viewModelScope.launch {
                eventsChannel.send(
                    UiEvent.ExportFinished(
                        files = emptyList(),
                        requestedCount = apps.size,
                        failures = apps.map { app ->
                            ExportFailure(
                                appLabel = app.label,
                                reason = getApplication<Application>()
                                    .getString(R.string.error_start_extraction),
                            )
                        },
                    ),
                )
            }
        }
    }

}
