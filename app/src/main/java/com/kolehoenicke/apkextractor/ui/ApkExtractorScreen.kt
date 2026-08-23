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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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
import com.kolehoenicke.apkextractor.AppUiState
import com.kolehoenicke.apkextractor.MainViewModel
import com.kolehoenicke.apkextractor.R
import com.kolehoenicke.apkextractor.UiEvent
import com.kolehoenicke.apkextractor.createShareChooserIntent
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
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingExtraction by remember { mutableStateOf<PendingExtraction?>(null) }

    val startExtraction: (PendingExtraction) -> Unit = { pending ->
        viewModel.extract(pending.apps)
        if (pending.clearSelectionOnStart) viewModel.clearSelection()
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
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
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

    val extract: (List<InstalledApp>, Boolean) -> Unit = { apps, clearSelectionOnStart ->
        if (state.outputFolder == null) {
            pendingExtraction = PendingExtraction(apps, clearSelectionOnStart)
            folderLauncher.launch(null)
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
                    val result = snackbarHostState.showSnackbar(
                        message = message,
                        actionLabel = if (event.files.isNotEmpty()) {
                            resources.getString(R.string.share)
                        } else {
                            null
                        },
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) share(context, event.files)
                }
            }
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
            folderLauncher.launch(state.outputFolder)
        },
        onExtract = { app -> extract(listOf(app), false) },
        onExtractSelected = { apps -> extract(apps, true) },
        onStartSelection = viewModel::startSelection,
        onToggleSelection = viewModel::toggleSelection,
        onClearSelection = viewModel::clearSelection,
        focusSearchRequests = focusSearchRequests,
    )
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
) {
    val searchBarState = rememberSearchBarState()
    val textFieldState = rememberTextFieldState(state.query)
    val searchFocusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val topBarScrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val useDockedSearch = shouldUseDockedSearch(currentWindowAdaptiveInfoV2().windowSizeClass)
    val searchWidth = adaptiveSearchWidth(LocalWindowInfo.current.containerDpSize.width)
    val selectionMode = state.selectedPackages.isNotEmpty()
    val selectedApps = remember(state.apps, state.selectedPackages) {
        state.apps.filter { it.packageName in state.selectedPackages }
    }
    var overflowMenuExpanded by remember { mutableStateOf(false) }
    var aboutDialogVisible by remember { mutableStateOf(false) }

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
            .nestedScroll(topBarScrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets.safeDrawing.union(WindowInsets.ime),
        topBar = {
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
                        IconButton(onClick = onClearSelection) {
                            Icon(
                                painter = painterResource(R.drawable.ic_close),
                                contentDescription = stringResource(R.string.exit_selection),
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { onExtractSelected(selectedApps) },
                            enabled = selectedApps.isNotEmpty(),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_download),
                                contentDescription = stringResource(R.string.extract_selected),
                            )
                        }
                    },
                    scrollBehavior = topBarScrollBehavior,
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.app_name)) },
                    actions = {
                        IconButton(
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
                            IconButton(onClick = { overflowMenuExpanded = true }) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_more_vert),
                                    contentDescription = stringResource(R.string.more_options),
                                )
                            }
                            DropdownMenu(
                                expanded = overflowMenuExpanded,
                                onDismissRequest = { overflowMenuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.privacy_and_licenses)) },
                                    onClick = {
                                        overflowMenuExpanded = false
                                        aboutDialogVisible = true
                                    },
                                )
                            }
                        }
                    },
                    scrollBehavior = topBarScrollBehavior,
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
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
                onStartSelection = onStartSelection,
                onToggleSelection = onToggleSelection,
                modifier = Modifier
                    .widthIn(max = AdaptiveContentMaxWidth)
                    .fillMaxSize()
                    .consumeWindowInsets(innerPadding),
            )
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
                    onStartSelection(packageName)
                }
            },
            onToggleSelection = { packageName ->
                scope.launch {
                    searchBarState.animateToCollapsed()
                    onToggleSelection(packageName)
                }
            },
            onExtract = { app ->
                scope.launch {
                    searchBarState.animateToCollapsed()
                    onExtract(app)
                }
            },
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
            content = searchResults,
        )
    }

    if (aboutDialogVisible) {
        PrivacyAndLicensesDialog(onDismissRequest = { aboutDialogVisible = false })
    }

}

@Composable
private fun PrivacyAndLicensesDialog(onDismissRequest: () -> Unit) {
    AlertDialog(
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
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.done))
            }
        },
    )
}

internal fun shouldUseDockedSearch(windowSizeClass: WindowSizeClass): Boolean =
    windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND)

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

    LazyColumn(
        modifier = modifier.fillMaxSize(),
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
) {
    if (apps.isEmpty()) {
        EmptyState()
        return
    }
    val resultContainerColor = MaterialTheme.colorScheme.surface
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        contentPadding = PaddingValues(vertical = 8.dp),
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
    ButtonGroup(
        overflowIndicator = { menuState ->
            ButtonGroupDefaults.OverflowIndicator(menuState = menuState)
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        AppFilter.entries.forEach { filter ->
            toggleableItem(
                checked = selected == filter,
                label = labels.getValue(filter),
                onCheckedChange = { checked -> if (checked) onSelected(filter) },
                weight = 1f,
            )
        }
    }
}

@Composable
private fun AppRow(
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
) {
    val colorScheme = MaterialTheme.colorScheme
    val containerColor by animateColorAsState(
        targetValue = if (selected) colorScheme.secondaryContainer else unselectedContainerColor,
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

    SegmentedListItem(
        shapes = defaultShapes.copy(shape = shape),
        colors = ListItemDefaults.colors(
            containerColor = containerColor,
            contentColor = contentColor,
            leadingContentColor = supportingContentColor,
            supportingContentColor = supportingContentColor,
            disabledContainerColor = unselectedContainerColor,
        ),
        supportingContent = {
            Column {
                Text(
                    text = app.packageName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append(app.versionName)
                        append(" · ")
                        append(formatBytes(app.totalBytes))
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
        },
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
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .semantics {
                if (selectionMode) this.selected = selected
            }
            .combinedClickable(
                enabled = enabled,
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
        Text(
            text = app.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
