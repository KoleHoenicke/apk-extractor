@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.kolehoenicke.apkextractor.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExpandedDockedSearchBarWithGap
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.window.core.layout.WindowSizeClass
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_MEDIUM_LOWER_BOUND
import com.kolehoenicke.apkextractor.tryLaunchExternalActivity
import com.kolehoenicke.apkextractor.AppUiState
import com.kolehoenicke.apkextractor.MainViewModel
import com.kolehoenicke.apkextractor.R
import com.kolehoenicke.apkextractor.UiEvent
import com.kolehoenicke.apkextractor.createShareChooserIntent
import com.kolehoenicke.apkextractor.data.AppSort
import com.kolehoenicke.apkextractor.data.AppFilter
import com.kolehoenicke.apkextractor.data.ExportedFile
import com.kolehoenicke.apkextractor.data.InstalledApp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

private data class PendingExtraction(
    val apps: List<InstalledApp>,
    val clearSelectionOnStart: Boolean,
)

private val AdaptiveContentMaxWidth = 840.dp
private val AdaptiveSearchMaxWidth = 720.dp
private val ContentHorizontalPadding = 16.dp

@Composable
fun ApkExtractorApp(
    viewModel: MainViewModel,
    focusSearchRequests: Flow<Unit>,
    onSuccessfulExtractionSession: () -> Unit = {},
    onNewWindow: (() -> Unit)? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val showLaunchError: (Int) -> Unit = { message ->
        scope.launch { snackbarHostState.showSnackbar(resources.getString(message)) }
    }
    var resultVisible by remember { mutableStateOf(false) }
    var folderInfoVisible by remember { mutableStateOf(false) }
    var pendingExtraction by remember { mutableStateOf<PendingExtraction?>(null) }

    val startExtraction: (PendingExtraction) -> Unit = { pending ->
        if (viewModel.extract(pending.apps) && pending.clearSelectionOnStart) viewModel.clearSelection()
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        pendingExtraction?.let(startExtraction)
        pendingExtraction = null
    }

    val requestNotificationsAndExtract: (PendingExtraction) -> Unit = { pending ->
        val preferences = context.getSharedPreferences("notification_permission", Context.MODE_PRIVATE)
        val shouldRequest = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED &&
            !preferences.getBoolean("requested", false)
        if (shouldRequest) {
            preferences.edit { putBoolean("requested", true) }
            pendingExtraction = pending
            if (!tryLaunchExternalActivity {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            ) {
                startExtraction(pending)
                pendingExtraction = null
            }
        } else {
            startExtraction(pending)
            pendingExtraction = null
        }
    }

    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            viewModel.setOutputFolder(uri)
            pendingExtraction?.let(requestNotificationsAndExtract)
        }
        if (uri == null) pendingExtraction = null
    }

    val chooseFolder: (Uri?) -> Unit = { initialUri ->
        if (!tryLaunchExternalActivity { folderLauncher.launch(initialUri) }) {
            pendingExtraction = null
            showLaunchError(R.string.folder_picker_unavailable)
        }
    }

    val extract: (List<InstalledApp>, Boolean) -> Unit = { apps, clearSelectionOnStart ->
        if (state.outputFolder == null) {
            pendingExtraction = PendingExtraction(apps, clearSelectionOnStart)
            chooseFolder(null)
        } else {
            requestNotificationsAndExtract(PendingExtraction(apps, clearSelectionOnStart))
        }
    }

    LaunchedEffect(viewModel, snackbarHostState, resources) {
        viewModel.events.collect { event ->
            when (event) {
                is UiEvent.ExportFinished -> {
                    if (event.requestedCount > 0 &&
                        event.files.size == event.requestedCount &&
                        event.failures.isEmpty()
                    ) {
                        onSuccessfulExtractionSession()
                    }
                    val message = when {
                        event.files.size == event.requestedCount && event.requestedCount == 1 -> {
                            resources.getString(R.string.export_complete, event.files.single().displayName)
                        }
                        event.files.size == event.requestedCount -> {
                            resources.getQuantityString(
                                R.plurals.export_complete_count,
                                event.files.size,
                                event.files.size,
                            )
                        }
                        event.files.isEmpty() && event.requestedCount == 1 -> {
                            val failure = event.failures.single()
                            "${resources.getString(R.string.export_failed, failure.appLabel)}: ${failure.reason}"
                        }
                        event.files.isEmpty() -> {
                            resources.getQuantityString(
                                R.plurals.export_failed_count,
                                event.requestedCount,
                                event.requestedCount,
                            )
                        }
                        else -> {
                            resources.getQuantityString(
                                R.plurals.export_partial,
                                event.requestedCount,
                                event.files.size,
                                event.requestedCount,
                            )
                        }
                    }
                    if (event.failures.isNotEmpty() || event.files.any(ExportedFile::isSplitArchive)) {
                        resultVisible = true
                    }
                    val result = snackbarHostState.showSnackbar(
                        message = message,
                        actionLabel = resources.getString(R.string.view_result),
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) resultVisible = true
                }
            }
        }
    }

    val failedPackages = state.lastResult?.failures?.map { it.packageName }?.toSet().orEmpty()
    val retryApps = state.apps.filter { it.packageName in failedPackages }
    val retryResult: () -> Unit = { resultVisible = false; extract(retryApps, false) }
    val openResultFolder: () -> Unit = { state.lastResult?.outputFolder?.let {
        if (!openExportFolder(context, it)) showLaunchError(R.string.open_folder_unavailable)
    } }
    val shareResult: () -> Unit = { state.lastResult?.let { result ->
        if (!tryLaunchExternalActivity { share(context, result.files) }) showLaunchError(R.string.sharing_unavailable)
    } }

    XrWorkspace(
        supportingPane = {
            XrExportPane(
                state = state,
                onChooseFolder = { pendingExtraction = null; folderInfoVisible = true },
                onOpenFolder = { state.outputFolder?.let {
                    if (!openExportFolder(context, it)) showLaunchError(R.string.open_folder_unavailable)
                } },
                onShowLastResult = { resultVisible = true },
                onShareLastResult = shareResult,
                showResult = resultVisible,
                canRetry = !state.isExporting && retryApps.isNotEmpty(),
                onRetry = retryResult,
                onOpenResultFolder = openResultFolder,
                onCloseResult = { resultVisible = false },
            )
        },
    ) {
        if (folderInfoVisible) {
            ExportFolderDialog(
                folderName = state.outputFolderName,
                onOpen = { state.outputFolder?.let { if (!openExportFolder(context, it)) showLaunchError(R.string.open_folder_unavailable) } },
                onChange = { folderInfoVisible = false; chooseFolder(state.outputFolder) },
                onDismiss = { folderInfoVisible = false; pendingExtraction = null },
            )
        }

        if (resultVisible) state.lastResult?.let { result ->
            if (!LocalXrUi.current.isSpatial || !needsDetailedExportReport(result)) {
                ExportResultDialog(
                    result = result,
                    canRetry = !state.isExporting && retryApps.isNotEmpty(),
                    onRetry = retryResult,
                    onOpenFolder = openResultFolder,
                    onShare = shareResult,
                    onDismiss = { resultVisible = false },
                )
            }
        }

        ApkExtractorScreen(
            state = state,
            snackbarHostState = snackbarHostState,
            onQueryChange = viewModel::setQuery,
            onFilterChange = viewModel::setFilter,
            onRefresh = viewModel::refresh,
            onChooseFolder = {
                pendingExtraction = null
                folderInfoVisible = true
            },
            onExtract = { app -> extract(listOf(app), false) },
            onExtractSelected = { apps -> extract(apps, true) },
            onStartSelection = viewModel::startSelection,
            onToggleSelection = viewModel::toggleSelection,
            onClearSelection = viewModel::clearSelection,
            onSelectionChange = viewModel::setSelection,
            onOpenFolder = { state.outputFolder?.let {
                if (!openExportFolder(context, it)) showLaunchError(R.string.open_folder_unavailable)
            } },
            onSortChange = viewModel::setSort,
            onShowLastResult = { resultVisible = true },
            focusSearchRequests = focusSearchRequests,
            onNewWindow = onNewWindow,
            onShareLastResult = shareResult,
        )
        BackHandler(enabled = resultVisible && LocalXrUi.current.isSpatial &&
            state.lastResult?.let(::needsDetailedExportReport) == true) { resultVisible = false }
    }
}

@Composable
fun ApkExtractorScreen(
    state: AppUiState,
    snackbarHostState: SnackbarHostState,
    onQueryChange: (String) -> Unit,
    onFilterChange: (AppFilter) -> Unit,
    onRefresh: () -> Unit,
    onChooseFolder: () -> Unit,
    onExtract: (InstalledApp) -> Unit,
    onExtractSelected: (List<InstalledApp>) -> Unit,
    onStartSelection: (String) -> Unit,
    onToggleSelection: (String) -> Unit,
    onClearSelection: () -> Unit,
    focusSearchRequests: Flow<Unit>,
    onSortChange: (AppSort) -> Unit = {},
    onShowLastResult: () -> Unit = {},
    onSelectionChange: (Set<String>) -> Unit = {},
    onOpenFolder: () -> Unit = {},
    onNewWindow: (() -> Unit)? = null,
    onShareLastResult: () -> Unit = {},
) {
    val searchBarState = rememberSearchBarState()
    var restoreExpandedSearch by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(searchBarState) {
        if (restoreExpandedSearch) searchBarState.animateToExpanded()
        snapshotFlow { searchBarState.currentValue }.collect {
            restoreExpandedSearch = it == androidx.compose.material3.SearchBarValue.Expanded
        }
    }
    val textFieldState = rememberTextFieldState(state.query)
    val searchFocusRequester = remember { FocusRequester() }
    var searchFieldFocused by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val topBarScrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val useDockedSearch = shouldUseDockedSearch(currentWindowAdaptiveInfoV2().windowSizeClass)
    val searchWidth = adaptiveSearchWidth(LocalWindowInfo.current.containerDpSize.width)
    val showExportPane = !LocalXrUi.current.isSpatial &&
        shouldShowExportPane(currentWindowAdaptiveInfoV2().windowSizeClass)
    val selectionMode = state.selectedPackages.isNotEmpty()
    val selectedApps = remember(state.apps, state.selectedPackages) {
        state.apps.filter { it.packageName in state.selectedPackages }
    }
    var overflowMenuExpanded by remember { mutableStateOf(false) }
    var aboutDialogVisible by remember { mutableStateOf(false) }
    var selectionAnchor by rememberSaveable { mutableStateOf<String?>(null) }
    val selectApp: (String) -> Unit = { packageName ->
        selectionAnchor = packageName
        onToggleSelection(packageName)
    }
    val selectRange: (String) -> Unit = { packageName ->
        if (!state.isExporting) {
            val visible = state.visibleApps.map(InstalledApp::packageName)
            onSelectionChange(state.selectedPackages + selectionRange(visible, selectionAnchor, packageName))
            if (selectionAnchor !in visible) selectionAnchor = packageName
        }
    }
    val selectionShortcuts = Modifier.onPreviewKeyEvent { event ->
        // Leave Ctrl+A in the search field to the text editor.
        if (!(event.isCtrlPressed || event.isMetaPressed)) {
            false
        } else if (event.key == Key.A && (searchFieldFocused ||
            searchBarState.targetValue == androidx.compose.material3.SearchBarValue.Expanded)) {
            false
        } else if (event.key == Key.A || event.key == Key.Enter) {
            if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0 && !state.isExporting) {
                if (event.key == Key.A) {
                    val visible = state.visibleApps.map(InstalledApp::packageName)
                    selectionAnchor = visible.firstOrNull()
                    onSelectionChange(state.selectedPackages + visible)
                } else if (selectedApps.isNotEmpty()) {
                    onExtractSelected(selectedApps)
                }
            }
            true
        } else {
            false
        }
    }

    BackHandler(enabled = selectionMode, onBack = onClearSelection)

    LaunchedEffect(focusSearchRequests) {
        focusSearchRequests.collect {
            searchBarState.animateToExpanded()
            searchFocusRequester.requestFocus()
        }
    }

    LaunchedEffect(textFieldState) {
        snapshotFlow { textFieldState.text.toString() }
            .distinctUntilChanged()
            .collect(onQueryChange)
    }

    val searchInput = @Composable {
        SearchBarDefaults.InputField(
            textFieldState = textFieldState,
            searchBarState = searchBarState,
            onSearch = { scope.launch { searchBarState.animateToCollapsed() } },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { searchFieldFocused = it.isFocused }
                .focusRequester(searchFocusRequester),
            placeholder = { Text(stringResource(R.string.search_apps)) },
            leadingIcon = {
                Icon(
                    painter = painterResource(R.drawable.ic_search),
                    contentDescription = null,
                )
            },
            trailingIcon = if (textFieldState.text.isNotEmpty()) {
                {
                    IconButton(
                        modifier = Modifier.xrActionSize(),
                        onClick = {
                            textFieldState.setTextAndPlaceCursorAtEnd("")
                            onQueryChange("")
                        },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_close),
                            contentDescription = stringResource(R.string.clear_search),
                        )
                    }
                }
            } else {
                null
            },
        )
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .then(selectionShortcuts)
            .nestedScroll(topBarScrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets.safeDrawing.union(WindowInsets.ime),
        topBar = {
            XrAppBar {
                if (selectionMode) {
                    TopAppBar(
                        title = {
                            Text(
                                pluralStringResource(
                                    R.plurals.selected_count,
                                    selectedApps.size,
                                    selectedApps.size,
                                ),
                            )
                        },
                        navigationIcon = {
                            AppActionButton(description = stringResource(R.string.exit_selection), onClick = onClearSelection) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_close),
                                    contentDescription = stringResource(R.string.exit_selection),
                                )
                            }
                        },
                        actions = {
                            XrSpaceModeButton()
                            AppActionButton(
                                description = stringResource(R.string.extract_selected),
                                onClick = { onExtractSelected(selectedApps) },
                                enabled = selectedApps.isNotEmpty() && !state.isExporting,
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_download),
                                    contentDescription = stringResource(R.string.extract_selected),
                                )
                            }
                        },
                        scrollBehavior = if (LocalXrUi.current.isSpatial) null else topBarScrollBehavior,
                    )
                } else {
                    TopAppBar(
                        title = { Text(stringResource(R.string.app_name)) },
                        actions = {
                            XrSpaceModeButton()
                            AppActionButton(
                                description = stringResource(if (state.outputFolder == null) R.string.choose_folder else R.string.change_folder),
                                onClick = onChooseFolder,
                                enabled = !state.isExporting,
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_folder_open),
                                    contentDescription = stringResource(
                                        if (state.outputFolder == null) {
                                            R.string.choose_folder
                                        } else {
                                            R.string.change_folder
                                        },
                                    ),
                                )
                            }
                            Box {
                                AppActionButton(description = stringResource(R.string.more_options), onClick = { overflowMenuExpanded = true }) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_more_vert),
                                        contentDescription = stringResource(R.string.more_options),
                                    )
                                }
                                AppOverflowMenu(
                                    expanded = overflowMenuExpanded,
                                    onDismiss = { overflowMenuExpanded = false },
                                    sort = state.sort,
                                    onSort = onSortChange,
                                    hasResult = state.lastResult != null,
                                    onResult = onShowLastResult,
                                    onAbout = { aboutDialogVisible = true },
                                    onNewWindow = onNewWindow,
                                )
                            }
                        },
                        scrollBehavior = if (LocalXrUi.current.isSpatial) null else topBarScrollBehavior,
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Row(Modifier.widthIn(max = if (showExportPane) 1176.dp else AdaptiveContentMaxWidth).fillMaxSize()) {
                AppList(
                    state = state,
                    contentPadding = innerPadding,
                    header = {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            SearchBar(
                                state = searchBarState,
                                inputField = searchInput,
                                modifier = Modifier.width(searchWidth),
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        AppFilterButtons(
                            selected = state.filter,
                            onSelected = onFilterChange,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = pluralStringResource(
                                R.plurals.app_count,
                                state.visibleApps.size,
                                state.visibleApps.size,
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    },
                    onRefresh = onRefresh,
                    onExtract = onExtract,
                    onStartSelection = { selectionAnchor = it; onStartSelection(it) },
                    onToggleSelection = selectApp,
                    onRangeSelection = selectRange,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .consumeWindowInsets(innerPadding),
                )
                if (showExportPane) {
                    ExportSupportingPane(
                        state = state,
                        onChooseFolder = onChooseFolder,
                        onOpenFolder = onOpenFolder,
                        onShowLastResult = onShowLastResult,
                        onShareLastResult = onShareLastResult,
                        modifier = Modifier.width(336.dp).fillMaxHeight().padding(innerPadding)
                            .padding(start = 16.dp, top = 12.dp, end = 16.dp),
                    )
                }
            }
        }
    }

    val searchResults: @Composable ColumnScope.() -> Unit = {
        SearchResults(
            apps = state.visibleApps,
            exportingPackages = state.exportingPackages,
            exportProgressByPackage = state.exportProgressByPackage,
            selectedPackages = state.selectedPackages,
            onStartSelection = { packageName ->
                scope.launch {
                    searchBarState.animateToCollapsed()
                    selectionAnchor = packageName
                    onStartSelection(packageName)
                }
            },
            onToggleSelection = { packageName ->
                scope.launch {
                    searchBarState.animateToCollapsed()
                    selectApp(packageName)
                }
            },
            onExtract = { app ->
                scope.launch {
                    searchBarState.animateToCollapsed()
                    onExtract(app)
                }
            },
            onRangeSelection = selectRange,
        )
    }

    if (useDockedSearch) {
        ExpandedDockedSearchBarWithGap(
            state = searchBarState,
            inputField = searchInput,
            modifier = Modifier.width(searchWidth),
            content = searchResults,
        )
    } else {
        ExpandedFullScreenSearchBar(
            state = searchBarState,
            inputField = searchInput,
            // Keep the results viewport edge-to-edge. Its scrollable content owns bottom insets.
            windowInsets = {
                WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
            },
            content = searchResults,
        )
    }

    if (aboutDialogVisible) {
        PrivacyAndLicensesDialog(onDismissRequest = { aboutDialogVisible = false })
    }

}

@Composable
private fun PrivacyAndLicensesDialog(onDismissRequest: () -> Unit) {
    XrAlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.privacy_and_licenses)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = stringResource(R.string.privacy_summary),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.android_robot_attribution),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.open_source_attribution),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(modifier = Modifier.xrActionSize(), onClick = onDismissRequest) {
                Text(stringResource(R.string.done))
            }
        },
    )
}

internal fun shouldUseDockedSearch(windowSizeClass: WindowSizeClass): Boolean =
    windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND) &&
        windowSizeClass.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND)

internal fun shouldShowExportPane(windowSizeClass: WindowSizeClass): Boolean =
    windowSizeClass.isWidthAtLeastBreakpoint(1200) &&
        windowSizeClass.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND)

@Composable
internal fun ExportSupportingPane(
    state: AppUiState,
    onChooseFolder: () -> Unit,
    onOpenFolder: () -> Unit,
    onShowLastResult: () -> Unit,
    onShareLastResult: () -> Unit,
    modifier: Modifier,
) {
    val scrollState = rememberScrollState()
    val scrollbar = scrollState.scrollIndicatorState?.let {
        Modifier.appScrollbar(it)
    } ?: Modifier
    Column(modifier.verticalScroll(scrollState).then(scrollbar), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.export_folder), style = MaterialTheme.typography.titleMedium)
                Text(state.outputFolderName ?: stringResource(R.string.no_export_folder),
                    style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.outputFolder != null) {
                        TextButton(modifier = Modifier.xrActionSize(), onClick = onOpenFolder) {
                            Text(stringResource(R.string.open_folder_action))
                        }
                    }
                    TextButton(modifier = Modifier.xrActionSize(), onClick = onChooseFolder, enabled = !state.isExporting) {
                        Text(stringResource(if (state.outputFolder == null) R.string.choose_folder else R.string.change_folder_action))
                    }
                }
            }
        }
        state.lastResult?.let { result ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.last_export), style = MaterialTheme.typography.titleMedium)
                    Text(pluralStringResource(R.plurals.result_saved, result.files.size, result.files.size))
                    if (result.failures.isNotEmpty()) {
                        Text(pluralStringResource(R.plurals.result_failed, result.failures.size, result.failures.size))
                    }
                    if (result.files.isNotEmpty()) ExportFileDragTile(result.files)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(modifier = Modifier.xrActionSize(), onClick = onShowLastResult) { Text(stringResource(R.string.view_result)) }
                        if (result.files.isNotEmpty()) TextButton(modifier = Modifier.xrActionSize(), onClick = onShareLastResult) { Text(stringResource(R.string.share)) }
                    }
                }
            }
        }
    }
}

internal fun adaptiveSearchWidth(windowWidth: Dp) =
    (minOf(windowWidth, AdaptiveContentMaxWidth) - ContentHorizontalPadding * 2)
        .coerceAtLeast(0.dp)
        .coerceAtMost(AdaptiveSearchMaxWidth)

@Composable
private fun AppList(
    state: AppUiState,
    contentPadding: PaddingValues,
    header: @Composable () -> Unit,
    onRefresh: () -> Unit,
    onExtract: (InstalledApp) -> Unit,
    onStartSelection: (String) -> Unit,
    onToggleSelection: (String) -> Unit,
    onRangeSelection: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.loading) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            AppListHeader(header)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                LoadingState()
            }
        }
        return
    }

    val listState = rememberLazyListState()
    val scrollbar = listState.scrollIndicatorState?.let {
        Modifier.appScrollbar(it,
            mainAxisTrackInset = contentPadding.calculateTopPadding() + 8.dp)
    } ?: Modifier
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize().then(scrollbar),
        contentPadding = contentPadding,
    ) {
        item(key = "header") {
            AppListHeader(header)
        }

        when {
            state.loadError != null -> item(key = "error") {
                ErrorState(message = state.loadError, onRefresh = onRefresh)
            }
            state.visibleApps.isEmpty() -> item(key = "empty") { EmptyState() }
            else -> itemsIndexed(
                items = state.visibleApps,
                key = { _, app -> app.packageName },
                contentType = { _, _ -> "app" },
            ) { index, app ->
                val selectionMode = state.selectedPackages.isNotEmpty()
                val selected = app.packageName in state.selectedPackages
                AppRow(
                    app = app,
                    index = index,
                    count = state.visibleApps.size,
                    selected = selected,
                    selectionMode = selectionMode,
                    exporting = app.packageName in state.exportingPackages,
                    exportProgress = state.exportProgressByPackage[app.packageName] ?: 0f,
                    enabled = !state.isExporting,
                    onClick = {
                        if (selectionMode) {
                            onToggleSelection(app.packageName)
                        } else {
                            onExtract(app)
                        }
                    },
                    onLongClick = {
                        if (selectionMode) {
                            onToggleSelection(app.packageName)
                        } else {
                            onStartSelection(app.packageName)
                        }
                    },
                    onContextExtract = { onExtract(app) },
                    onRangeSelection = { onRangeSelection(app.packageName) },
                    modifier = Modifier.padding(
                        horizontal = 16.dp,
                        vertical = ListItemDefaults.SegmentedGap / 2,
                    ),
                )
            }
        }
    }
}

@Composable
private fun AppListHeader(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        content()
    }
}

@Composable
private fun SearchResults(
    apps: List<InstalledApp>,
    exportingPackages: Set<String>,
    exportProgressByPackage: Map<String, Float>,
    selectedPackages: Set<String>,
    onStartSelection: (String) -> Unit,
    onToggleSelection: (String) -> Unit,
    onExtract: (InstalledApp) -> Unit,
    onRangeSelection: (String) -> Unit,
) {
    if (apps.isEmpty()) {
        EmptyState()
        return
    }
    val resultContainerColor = MaterialTheme.colorScheme.surface
    val bottomInsets = WindowInsets.safeDrawing.union(WindowInsets.ime)
        .only(WindowInsetsSides.Bottom)
    val listState = rememberLazyListState()
    val scrollbar = listState.scrollIndicatorState?.let {
        Modifier.appScrollbar(it)
    } ?: Modifier
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .then(scrollbar)
            .consumeWindowInsets(bottomInsets),
        contentPadding = PaddingValues(
            top = 8.dp,
            bottom = 8.dp + bottomInsets.asPaddingValues().calculateBottomPadding(),
        ),
    ) {
        itemsIndexed(
            items = apps,
            key = { _, app -> app.packageName },
            contentType = { _, _ -> "search-result" },
        ) { index, app ->
            val selectionMode = selectedPackages.isNotEmpty()
            AppRow(
                app = app,
                index = index,
                count = apps.size,
                selected = app.packageName in selectedPackages,
                selectionMode = selectionMode,
                exporting = app.packageName in exportingPackages,
                exportProgress = exportProgressByPackage[app.packageName] ?: 0f,
                enabled = exportingPackages.isEmpty(),
                unselectedContainerColor = resultContainerColor,
                onClick = {
                    if (selectionMode) {
                        onToggleSelection(app.packageName)
                    } else {
                        onExtract(app)
                    }
                },
                onLongClick = {
                    if (selectionMode) {
                        onToggleSelection(app.packageName)
                    } else {
                        onStartSelection(app.packageName)
                    }
                },
                onContextExtract = { onExtract(app) },
                onRangeSelection = { onRangeSelection(app.packageName) },
                modifier = Modifier.padding(
                    horizontal = 16.dp,
                    vertical = ListItemDefaults.SegmentedGap / 2,
                ),
            )
        }
    }
}

@Composable
private fun AppFilterButtons(
    selected: AppFilter,
    onSelected: (AppFilter) -> Unit,
) {
    val labels = mapOf(
        AppFilter.User to stringResource(R.string.user_apps),
        AppFilter.System to stringResource(R.string.system_apps),
        AppFilter.All to stringResource(R.string.all_apps),
    )
    val isXr = LocalXrUi.current.isXr
    ButtonGroup(
        overflowIndicator = { menuState ->
            ButtonGroupDefaults.OverflowIndicator(menuState = menuState)
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        val groupScope = this
        AppFilter.entries.forEach { filter ->
            if (isXr) {
                // The built-in DSL uses 48dp targets. Keep Material's toggle and group
                // press animation, with an explicit 56dp target for hand-ray input.
                customItem(
                    buttonGroupContent = {
                        val interactions = remember { MutableInteractionSource() }
                        ToggleButton(
                            checked = selected == filter,
                            onCheckedChange = { checked -> if (checked) onSelected(filter) },
                            modifier = with(groupScope) {
                                Modifier.heightIn(min = 56.dp).weight(1f).animateWidth(interactions)
                            },
                            interactionSource = interactions,
                        ) { Text(labels.getValue(filter), maxLines = 1) }
                    },
                    menuContent = { menuState ->
                        SelectableDropdownMenuItem(
                            selected = selected == filter,
                            shapes = MenuDefaults.itemShape(filter.ordinal, AppFilter.entries.size),
                            modifier = Modifier.heightIn(min = 56.dp),
                            onClick = { onSelected(filter); menuState.dismiss() },
                            text = { Text(labels.getValue(filter)) },
                        )
                    },
                )
            } else {
                toggleableItem(
                    checked = selected == filter,
                    label = labels.getValue(filter),
                    onCheckedChange = { checked -> if (checked) onSelected(filter) },
                    weight = 1f,
                )
            }
        }
    }
}

@Composable
internal fun AppRow(
    app: InstalledApp,
    index: Int,
    count: Int,
    selected: Boolean,
    selectionMode: Boolean,
    exporting: Boolean,
    exportProgress: Float,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    unselectedContainerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    onContextExtract: () -> Unit = onClick,
    onRangeSelection: () -> Unit = onLongClick,
) {
    val selectDescription = stringResource(
        if (selected) R.string.deselect_app else R.string.select_app, app.label,
    )
    val colorScheme = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val hovered by interactionSource.collectIsHoveredAsState()
    val containerColor by animateColorAsState(
        targetValue = when {
            selected -> colorScheme.secondaryContainer
            hovered && enabled -> colorScheme.surfaceContainerHighest
            else -> unselectedContainerColor
        },
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "list item container",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) colorScheme.onSecondaryContainer else colorScheme.onSurface,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "list item content",
    )
    val supportingContentColor by animateColorAsState(
        targetValue = if (selected) colorScheme.onSecondaryContainer else colorScheme.onSurfaceVariant,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "list item supporting content",
    )
    val defaultShapes = ListItemDefaults.segmentedShapes(index = index, count = count)
    val shape = if (selected) defaultShapes.selectedShape else defaultShapes.shape

    DesktopRowActions(
        modifier = modifier,
        appLabel = app.label,
        selected = selected,
        enabled = enabled,
        onExtract = onContextExtract,
        onSelect = onLongClick,
        onRangeSelect = onRangeSelection,
    ) { desktopModifier ->
        SegmentedListItem(
            shapes = defaultShapes.copy(shape = shape),
            colors = ListItemDefaults.colors(
                containerColor = containerColor,
                contentColor = contentColor,
                leadingContentColor = supportingContentColor,
                supportingContentColor = supportingContentColor,
                disabledContainerColor = unselectedContainerColor,
            ),
            trailingContent = if (LocalXrUi.current.isXr) {
                {
                    androidx.compose.material3.Checkbox(
                        checked = selected,
                        onCheckedChange = { if (selectionMode) onClick() else onLongClick() },
                        enabled = enabled,
                        modifier = Modifier.size(56.dp).semantics {
                            contentDescription = selectDescription
                        },
                    )
                }
            } else null,
            leadingContent = {
                Box(
                    modifier = Modifier.size(56.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (app.icon != null) {
                        Image(
                            bitmap = app.icon,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.ic_download),
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
            },
            modifier = desktopModifier
                .fillMaxWidth()
                // Preserve Material's three-line minimum with the single content slot.
                .heightIn(min = 88.dp)
                .clip(shape)
                .then(if (focused && enabled) Modifier.border(2.dp, colorScheme.primary, shape) else Modifier)
                .semantics {
                    if (selectionMode) this.selected = selected
                }
                .combinedClickable(
                    enabled = enabled,
                    interactionSource = interactionSource,
                    indication = androidx.compose.material3.ripple(),
                    onClickLabel = stringResource(
                        when {
                            !selectionMode -> R.string.extract_app
                            selected -> R.string.deselect_app
                            else -> R.string.select_app
                        },
                        app.label,
                    ),
                    onLongClickLabel = stringResource(R.string.select_app, app.label),
                    role = if (selectionMode) Role.Checkbox else Role.Button,
                    onLongClick = onLongClick,
                    onClick = onClick,
                ),
        ) {
            // Keep all row text in the main slot: Material's supporting slot queries
            // descendant baselines during measurement, triggering RectManager crashes.
            Column {
                Text(
                    text = app.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodyMedium.copy(textDirection = androidx.compose.ui.text.style.TextDirection.Ltr),
                    color = supportingContentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    style = MaterialTheme.typography.bodyMedium,
                    color = supportingContentColor,
                    text = buildString {
                        append(androidx.core.text.BidiFormatter.getInstance().unicodeWrap(app.versionName))
                        append(" · ")
                        append(androidx.core.text.BidiFormatter.getInstance().unicodeWrap(android.text.format.Formatter.formatShortFileSize(LocalContext.current, app.totalBytes)))
                        append(" · ")
                        append(stringResource(if (app.isSplit) R.string.split_apk else R.string.single_apk))
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                AnimatedVisibility(
                    visible = exporting,
                    enter = fadeIn(
                        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                    ) + expandVertically(
                        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                    ),
                    exit = fadeOut(
                        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                    ) + shrinkVertically(
                        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                    ),
                ) {
                    Column {
                        Spacer(Modifier.height(8.dp))
                        LinearWavyProgressIndicator(
                            progress = { exportProgress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LoadingIndicator()
            Text(
                text = stringResource(R.string.loading_apps),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun ErrorState(message: String, onRefresh: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = message, style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onRefresh) {
            Icon(
                painter = painterResource(R.drawable.ic_refresh),
                contentDescription = null,
            )
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.retry))
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.no_apps),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = stringResource(R.string.no_apps_detail),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun share(context: Context, files: List<ExportedFile>) {
    createShareChooserIntent(context, files)?.let(context::startActivity)
}
