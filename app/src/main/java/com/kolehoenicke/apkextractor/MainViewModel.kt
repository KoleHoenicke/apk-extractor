package com.kolehoenicke.apkextractor

import android.app.Application
import android.net.Uri
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
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

class MainViewModel @JvmOverloads constructor(
    application: Application,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
) : AndroidViewModel(application) {
    private val catalog = AppCatalog(application)
    private val folderStore = OutputFolderStore(application)
    private val eventsChannel = Channel<UiEvent>(Channel.BUFFERED)
    private var refreshJob: Job? = null
    private var ownsExtraction = false
    private var resultRevision = 0

    private val resultStore = ExportResultStore(application)
    private val preferences = application.getSharedPreferences("preferences", android.content.Context.MODE_PRIVATE)
    private val initialFolder = folderStore.get()
    private val _state = MutableStateFlow(
        AppUiState(
            outputFolder = initialFolder,
            sort = readSort(),
            lastResult = resultStore.read(),
            query = savedStateHandle["query"] ?: "",
            filter = runCatching { AppFilter.valueOf(savedStateHandle["filter"] ?: "User") }
                .getOrDefault(AppFilter.User),
            selectedPackages = savedStateHandle.get<ArrayList<String>>("selection")?.toSet().orEmpty(),
        ),
    )
    val state: StateFlow<AppUiState> = _state.asStateFlow()
    val events = eventsChannel.receiveAsFlow()
    private val sortListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "sort" || key == null) {
            _state.update { it.copy(sort = readSort()) }
        }
    }

    private fun readSort() = runCatching {
        AppSort.valueOf(preferences.getString("sort", "Name")!!)
    }.getOrDefault(AppSort.Name)

    init {
        preferences.registerOnSharedPreferenceChangeListener(sortListener)
        viewModelScope.launch {
            folderStore.observe().collect { folder ->
                _state.update { it.copy(outputFolder = folder) }
                refreshFolderName(folder)
            }
        }
        viewModelScope.launch {
            val revision = resultRevision
            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                if (!ExtractionSession.state.value.isActive) resultStore.recoverInterrupted()
                resultStore.read()
            }
            if (revision == resultRevision) _state.update { it.copy(lastResult = result) }
        }
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
                resultRevision++
                if (folderStore.get() == null) {
                    _state.update { it.copy(outputFolder = null, outputFolderName = null) }
                }
                _state.update { it.copy(lastResult = event as? UiEvent.ExportFinished) }
                // All windows see the result; only the initiating window shows completion UI
                // and records a successful session for the in-app review prompt.
                if (ownsExtraction) {
                    ownsExtraction = false
                    eventsChannel.send(event)
                }
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
                    persistSelection()
                }
                .onFailure { failure ->
                    // A newer refresh owns the UI. Cancellation is not a catalog failure.
                    if (failure is kotlinx.coroutines.CancellationException) throw failure
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
        savedStateHandle["filter"] = filter.name
        _state.update { it.copy(filter = filter) }
    }

    fun setQuery(query: String) {
        savedStateHandle["query"] = query
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
        persistSelection()
    }

    fun toggleSelection(packageName: String) {
        if (_state.value.isExporting) return
        _state.update { current ->
            val selected = current.selectedPackages.toMutableSet()
            if (!selected.add(packageName)) selected.remove(packageName)
            current.copy(selectedPackages = selected)
        }
        persistSelection()
    }

    fun clearSelection() {
        _state.update { it.copy(selectedPackages = emptySet()) }
        persistSelection()
    }

    fun setSelection(packageNames: Set<String>) {
        if (_state.value.isExporting) return
        _state.update { current ->
            val installed = current.apps.mapTo(mutableSetOf(), InstalledApp::packageName)
            current.copy(selectedPackages = packageNames.intersect(installed))
        }
        persistSelection()
    }

    private fun persistSelection() {
        savedStateHandle["selection"] = ArrayList(_state.value.selectedPackages)
    }

    fun extract(apps: List<InstalledApp>): Boolean {
        val folder = _state.value.outputFolder ?: return false
        if (_state.value.isExporting || ExtractionSession.state.value.isActive) return false
        ownsExtraction = ExtractionService.start(getApplication(), apps, folder)
        if (!ownsExtraction) {
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
        return ownsExtraction
    }

    override fun onCleared() {
        preferences.unregisterOnSharedPreferenceChangeListener(sortListener)
        super.onCleared()
    }

}
