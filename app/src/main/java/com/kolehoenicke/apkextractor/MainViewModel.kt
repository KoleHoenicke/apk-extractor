package com.kolehoenicke.apkextractor

import android.app.Application
import android.net.Uri
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kolehoenicke.apkextractor.data.AppCatalog
import com.kolehoenicke.apkextractor.data.AppSort
import com.kolehoenicke.apkextractor.data.sortApps
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
    val sort: AppSort = AppSort.Name,
    val lastResult: UiEvent.ExportFinished? = null,
    val outputFolderName: String? = null,
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
        get() = sortApps(filterApps(apps, filter, query), sort)

    val isExporting: Boolean get() = exportingPackages.isNotEmpty()
}

sealed interface UiEvent {
    data class ExportFinished(
        val files: List<ExportedFile>,
        val requestedCount: Int,
        val failures: List<ExportFailure>,
        val outputFolder: Uri? = null,
    ) : UiEvent
}

data class ExportFailure(val appLabel: String, val reason: String, val packageName: String = "")

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val catalog = AppCatalog(application)
    private val folderStore = OutputFolderStore(application)
    private val eventsChannel = Channel<UiEvent>(Channel.BUFFERED)
    private var refreshJob: Job? = null

    private val resultStore = ExportResultStore(application)
    private val preferences = application.getSharedPreferences("preferences", android.content.Context.MODE_PRIVATE)
    private val initialFolder = folderStore.get()
    private val _state = MutableStateFlow(
        AppUiState(
            outputFolder = initialFolder,
            sort = runCatching { AppSort.valueOf(preferences.getString("sort", "Name")!!) }.getOrDefault(AppSort.Name),
            lastResult = resultStore.read(),
        ),
    )
    val state: StateFlow<AppUiState> = _state.asStateFlow()
    val events = eventsChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                if (!ExtractionSession.state.value.isActive) resultStore.recoverInterrupted()
                resultStore.read()
            }
            _state.update { it.copy(lastResult = result) }
        }
        refreshFolderName(initialFolder)
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
                    _state.update { it.copy(outputFolder = null, outputFolderName = null) }
                }
                _state.update { it.copy(lastResult = event as? UiEvent.ExportFinished) }
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

    fun setSort(sort: AppSort) {
        preferences.edit { putString("sort", sort.name) }
        _state.update { it.copy(sort = sort) }
    }

    private fun refreshFolderName(uri: Uri?) {
        viewModelScope.launch {
            val name = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                uri?.let { runCatching {
                    androidx.documentfile.provider.DocumentFile.fromTreeUri(getApplication(), it)?.name
                }.getOrNull() }
            }
            _state.update { if (it.outputFolder == uri) it.copy(outputFolderName = name) else it }
        }
    }

    fun setFilter(filter: AppFilter) {
        _state.update { it.copy(filter = filter) }
    }

    fun setQuery(query: String) {
        _state.update { it.copy(query = query) }
    }

    fun setOutputFolder(uri: Uri) {
        folderStore.set(uri)
        _state.update { it.copy(outputFolder = uri) }
        refreshFolderName(uri)
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
                val event = UiEvent.ExportFinished(
                        files = emptyList(),
                        requestedCount = apps.size,
                        failures = apps.map { app ->
                            ExportFailure(
                                appLabel = app.label,
                                packageName = app.packageName,
                                reason = getApplication<Application>()
                                    .getString(R.string.error_start_extraction),
                            )
                        },
                    )
                _state.update { it.copy(lastResult = event) }
                eventsChannel.send(event)
            }
        }
    }

}
