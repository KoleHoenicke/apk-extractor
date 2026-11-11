package com.kolehoenicke.apkextractor.ui

import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.SnackbarHostState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertTrue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.kolehoenicke.apkextractor.AppUiState
import com.kolehoenicke.apkextractor.data.InstalledApk
import com.kolehoenicke.apkextractor.data.InstalledApp
import com.kolehoenicke.apkextractor.ui.theme.ApkExtractorTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class XrWorkspaceTest {
    @get:Rule val compose = createComposeRule()
    private val example = InstalledApp("Example", "com.example", "1.0", 1, null, false,
        listOf(InstalledApk("base.apk", null, 1024)))

    @Test fun xrCheckboxSelectsAndDeselectsWithoutExtracting() {
        val selected = mutableStateOf(false)
        var extracts = 0
        compose.setContent {
            ApkExtractorTheme {
                CompositionLocalProvider(LocalXrUi provides XrUi(true, false),
                    LocalMinimumInteractiveComponentSize provides 56.dp) {
                    AppRow(example, 0, 1, selected.value, selected.value, false, 0f, true,
                        onClick = { if (selected.value) selected.value = false else extracts++ },
                        onLongClick = { selected.value = true })
                }
            }
        }
        compose.onNodeWithContentDescription("Select Example", useUnmergedTree = true)
            .assertWidthIsAtLeast(56.dp).assertHeightIsAtLeast(56.dp).performClick()
        compose.onNodeWithContentDescription("Deselect Example", useUnmergedTree = true)
            .assertIsOn().performClick()
        compose.runOnIdle { assertEquals(false, selected.value); assertEquals(0, extracts) }
    }

    @Test fun catalogToolbarExtractsTheEntireSelection() {
        var extracted = emptyList<InstalledApp>()
        val batch = (1..100).map { example.copy(packageName = "com.example.$it", label = "Example $it") }
        compose.setContent {
            ApkExtractorTheme {
                CompositionLocalProvider(LocalXrUi provides XrUi(true, false),
                    LocalMinimumInteractiveComponentSize provides 56.dp) {
                    Catalog(AppUiState(apps = batch, loading = false,
                        selectedPackages = batch.map { it.packageName }.toSet())) { extracted = it }
                }
            }
        }
        compose.onAllNodesWithContentDescription("Extract selected apps")[0]
            .assertHeightIsAtLeast(56.dp).performClick()
        compose.runOnIdle { assertEquals(batch, extracted) }
    }

    @Test fun runningExportCannotStartAnotherBatch() {
        compose.setContent {
            ApkExtractorTheme {
                CompositionLocalProvider(LocalXrUi provides XrUi(true, false),
                    LocalMinimumInteractiveComponentSize provides 56.dp) {
                    Catalog(AppUiState(apps = listOf(example), loading = false,
                        selectedPackages = setOf(example.packageName),
                        exportingPackages = setOf(example.packageName))) {
                        error("Must remain disabled during export")
                    }
                }
            }
        }
        compose.onAllNodesWithContentDescription("Extract selected apps")[0].assertIsNotEnabled()
    }

    @Test fun compactWorkspaceKeepsFolderActionsTogetherAndResultFullyVisible() {
        val tree = android.net.Uri.parse("content://example/tree/export")
        val result = com.kolehoenicke.apkextractor.UiEvent.ExportFinished(
            listOf(com.kolehoenicke.apkextractor.data.ExportedFile(
                android.net.Uri.parse("content://example/export.apk"), "APK Extractor-1.0.5.apk", false,
                appLabel = "APK Extractor")), 1, emptyList(), tree)
        val height = mutableStateOf(720.dp)
        val scale = mutableStateOf(1f)
        val latestResult = mutableStateOf<com.kolehoenicke.apkextractor.UiEvent.ExportFinished?>(result)
        var pixelDensity = 1f
        compose.setContent {
            ApkExtractorTheme {
                pixelDensity = LocalDensity.current.density
                CompositionLocalProvider(LocalXrUi provides XrUi(true, false),
                    LocalMinimumInteractiveComponentSize provides 56.dp,
                    LocalDensity provides Density(pixelDensity, scale.value)) {
                    Box(Modifier.size(360.dp, height.value).testTag("workspace-viewport")) {
                        XrExportPane(AppUiState(loading = false, outputFolder = tree,
                            outputFolderName = "APK_Extractor_XR_QA_20261007", lastResult = latestResult.value),
                            onChooseFolder = {}, onOpenFolder = {}, onShowLastResult = {}, onShareLastResult = {},
                            modifier = Modifier.testTag("export-pane"))
                    }
                }
            }
        }
        compose.onNodeWithText("Select apps").assertDoesNotExist()
        val open = compose.onNodeWithText("Open folder").fetchSemanticsNode().boundsInRoot
        val change = compose.onNodeWithText("Change").fetchSemanticsNode().boundsInRoot
        assertEquals(open.top, change.top, 1f)
        val viewport = compose.onNodeWithTag("workspace-viewport").fetchSemanticsNode().boundsInRoot
        // The card's lower border follows its action row by the Material card padding.
        val share = compose.onNodeWithText("Share").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue("The recent export card must fit with room for its lower border",
            share.bottom + 16.dp.value * pixelDensity <= viewport.bottom - 24.dp.value * pixelDensity)
        val populatedPane = compose.onNodeWithTag("export-pane").fetchSemanticsNode().boundsInRoot
        assertEquals("Panel should end after card padding, without a blank tail",
            share.bottom + 40.dp.value * pixelDensity, populatedPane.bottom, 1f)
        assertTrue(populatedPane.height < viewport.height)
        compose.runOnIdle { latestResult.value = null }
        val folderOnlyPane = compose.onNodeWithTag("export-pane").fetchSemanticsNode().boundsInRoot
        assertTrue("Panel should shrink when there is no recent export", folderOnlyPane.height < populatedPane.height)
        compose.runOnIdle { latestResult.value = result; height.value = 480.dp; scale.value = 1.3f }
        compose.onNode(hasScrollAction()).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 10_000f) }
        compose.onNodeWithText("Share").assertIsDisplayed()
        val scrolledShare = compose.onNodeWithText("Share").fetchSemanticsNode().boundsInRoot
        assertTrue("Large text in a short panel must scroll far enough to reveal the lower border",
            scrolledShare.bottom + 16.dp.value * pixelDensity <= compose.onNodeWithTag("workspace-viewport").fetchSemanticsNode().boundsInRoot.bottom - 24.dp.value * pixelDensity)
    }

    @Test fun longReportScrollsFilesWithoutMovingActionsAndDoneRestoresWorkspace() {
        val tree = android.net.Uri.parse("content://example/tree/export")
        val files = (1..100).map {
            com.kolehoenicke.apkextractor.data.ExportedFile(
                android.net.Uri.parse("content://example/file/$it"), "Example$it.apk", false)
        }
        val result = com.kolehoenicke.apkextractor.UiEvent.ExportFinished(files, 101,
            listOf(com.kolehoenicke.apkextractor.ExportFailure("Failed app", "Folder unavailable", "com.failed")), tree)
        val visible = mutableStateOf(true)
        val height = mutableStateOf(720.dp)
        val scale = mutableStateOf(1f)
        var opened = 0
        var shared = 0
        var retried = 0
        compose.setContent {
            ApkExtractorTheme {
                val density = LocalDensity.current.density
                CompositionLocalProvider(LocalXrUi provides XrUi(true, true),
                    LocalMinimumInteractiveComponentSize provides 56.dp,
                    LocalDensity provides Density(density, scale.value)) {
                    Box(Modifier.size(400.dp, height.value).testTag("report-viewport")) {
                        XrExportPane(AppUiState(loading = false, outputFolder = tree,
                            outputFolderName = "Export folder", lastResult = result),
                            onChooseFolder = {}, onOpenFolder = {}, onShowLastResult = {},
                            onShareLastResult = { shared++ }, showResult = visible.value,
                            canRetry = true, onRetry = { retried++ },
                            onOpenResultFolder = { opened++ }, onCloseResult = { visible.value = false })
                    }
                }
            }
        }
        compose.onNodeWithText("100 extracted · 1 failed").assertIsDisplayed()
        compose.onNodeWithText("Folder unavailable").assertIsDisplayed()
        val initialDone = compose.onNodeWithText("Done").assertHeightIsAtLeast(56.dp)
            .fetchSemanticsNode().boundsInRoot
        compose.onNodeWithText("Example100.apk").assertDoesNotExist()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Example100.apk"))
        compose.onNodeWithText("Example100.apk").assertIsDisplayed()
        assertEquals(initialDone.top, compose.onNodeWithText("Done").fetchSemanticsNode().boundsInRoot.top, 1f)
        listOf("Open folder", "Share", "Retry failed apps").forEach {
            compose.onNodeWithText(it).assertIsDisplayed().assertHeightIsAtLeast(56.dp).performClick()
        }
        compose.runOnIdle { assertEquals(1, opened); assertEquals(1, shared); assertEquals(1, retried)
            height.value = 480.dp; scale.value = 1.3f }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Example100.apk"))
        compose.onNodeWithText("Example100.apk").assertIsDisplayed()
        listOf("Open folder", "Share", "Retry failed apps", "Done").forEach {
            compose.onNodeWithText(it).assertIsDisplayed().assertHeightIsAtLeast(56.dp)
        }
        val done = compose.onNodeWithText("Done").fetchSemanticsNode().boundsInRoot
        val viewport = compose.onNodeWithTag("report-viewport").fetchSemanticsNode().boundsInRoot
        assertTrue("Actions must fit within the shorter panel", done.bottom <= viewport.bottom)
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithText("Export workspace").assertIsDisplayed()
        compose.onNodeWithText("Example100.apk").assertDoesNotExist()
    }

    @Test fun shortBatchReportWrapsItsContentWithoutABlankTail() {
        val result = com.kolehoenicke.apkextractor.UiEvent.ExportFinished((1..2).map {
            com.kolehoenicke.apkextractor.data.ExportedFile(android.net.Uri.parse("content://example/$it"), "Example$it.apk", false)
        }, 2, emptyList(), null)
        var density = 1f
        compose.setContent {
            ApkExtractorTheme {
                density = LocalDensity.current.density
                CompositionLocalProvider(LocalXrUi provides XrUi(true, true),
                    LocalMinimumInteractiveComponentSize provides 56.dp) {
                    Box(Modifier.size(400.dp, 720.dp).testTag("report-viewport")) {
                        XrExportPane(AppUiState(loading = false, lastResult = result), {}, {}, {}, {},
                            modifier = Modifier.testTag("report-pane"), showResult = true)
                    }
                }
            }
        }
        compose.onNodeWithText("2 extracted").assertIsDisplayed()
        compose.onNodeWithText("Example2.apk").assertIsDisplayed()
        val pane = compose.onNodeWithTag("report-pane").fetchSemanticsNode().boundsInRoot
        val done = compose.onNodeWithText("Done").fetchSemanticsNode().boundsInRoot
        assertEquals(done.bottom + 24.dp.value * density, pane.bottom, 1f)
        assertTrue(pane.height < compose.onNodeWithTag("report-viewport").fetchSemanticsNode().boundsInRoot.height)
    }

    @Test fun singleFailuresAndSplitInstructionsUseTheDetailedPaneButSimpleSuccessDoesNot() {
        val file = com.kolehoenicke.apkextractor.data.ExportedFile(android.net.Uri.parse("content://example/1"), "Example.apk", false)
        val result = mutableStateOf(com.kolehoenicke.apkextractor.UiEvent.ExportFinished(
            emptyList(), 1, listOf(com.kolehoenicke.apkextractor.ExportFailure("Example", "Folder unavailable")), null))
        compose.setContent {
            ApkExtractorTheme {
                CompositionLocalProvider(LocalXrUi provides XrUi(true, true),
                    LocalMinimumInteractiveComponentSize provides 56.dp) {
                    Box(Modifier.size(400.dp, 720.dp)) {
                        XrExportPane(AppUiState(loading = false, lastResult = result.value), {}, {}, {}, {}, showResult = true)
                    }
                }
            }
        }
        compose.onNodeWithText("Folder unavailable").assertIsDisplayed()
        compose.onNodeWithText("Done").assertIsDisplayed()
        compose.runOnIdle { result.value = result.value.copy(files = listOf(file.copy(isSplitArchive = true)), failures = emptyList()) }
        compose.onNodeWithText("Split APK export").assertIsDisplayed()
        compose.onNodeWithText("cannot be installed by tapping the ZIP", substring = true).assertIsDisplayed()
        compose.runOnIdle { result.value = result.value.copy(files = listOf(file)) }
        compose.onNodeWithText("Export workspace").assertIsDisplayed()
        compose.onNodeWithText("Done").assertDoesNotExist()
    }

    @Composable
    private fun Catalog(state: AppUiState, onExtractSelected: (List<InstalledApp>) -> Unit) {
        ApkExtractorScreen(state, SnackbarHostState(), onQueryChange = {}, onFilterChange = {},
            onRefresh = {}, onChooseFolder = {}, onExtract = {}, onExtractSelected = onExtractSelected,
            onStartSelection = {}, onToggleSelection = {}, onClearSelection = {},
            focusSearchRequests = emptyFlow())
    }

    @Test fun spatialFolderDialogShowsReadableActionsAndDismisses() {
        val visible = mutableStateOf(true)
        compose.setContent {
            ApkExtractorTheme {
                CompositionLocalProvider(LocalXrUi provides XrUi(true, true),
                    LocalMinimumInteractiveComponentSize provides 56.dp) {
                    if (visible.value) ExportFolderDialog(
                        folderName = "APK Extractor XR export destination",
                        onOpen = {}, onChange = {}, onDismiss = { visible.value = false },
                    )
                }
            }
        }
        compose.onNodeWithText("APK Extractor XR export destination").assertIsDisplayed()
        compose.onNodeWithText("Done").assertHeightIsAtLeast(56.dp).performClick()
        compose.runOnIdle { assertEquals(false, visible.value) }
    }

    @Test fun spatialOverflowKeepsEveryActionAndSortChoiceReachable() {
        val expanded = mutableStateOf(true)
        val sort = mutableStateOf(com.kolehoenicke.apkextractor.data.AppSort.Name)
        var about = false
        compose.setContent {
            ApkExtractorTheme {
                CompositionLocalProvider(LocalXrUi provides XrUi(true, true),
                    LocalMinimumInteractiveComponentSize provides 56.dp) {
                    Box(Modifier.size(56.dp)) {
                        AppOverflowMenu(expanded.value, { expanded.value = false }, sort.value,
                            { sort.value = it }, true, {}, { about = true })
                    }
                }
            }
        }
        compose.onNodeWithText("Recent export").assertIsDisplayed().assertHeightIsAtLeast(56.dp)
        compose.onNodeWithText("Privacy & licenses").assertIsDisplayed().assertHeightIsAtLeast(56.dp)
        compose.onNodeWithText("Sort by").performClick()
        compose.onNodeWithText("Name").assertIsDisplayed()
        compose.onNodeWithText("Recently updated").assertIsDisplayed().assertHeightIsAtLeast(56.dp).performClick()
        compose.runOnIdle {
            assertEquals(com.kolehoenicke.apkextractor.data.AppSort.RecentlyUpdated, sort.value)
            assertEquals(false, expanded.value)
            expanded.value = true
        }
        compose.onNodeWithText("Privacy & licenses").performClick()
        compose.runOnIdle { assertTrue(about); assertEquals(false, expanded.value) }
    }

    @Test fun xrFilterAndSearchClearTargetsAre56Dp() {
        compose.setContent {
            ApkExtractorTheme {
                CompositionLocalProvider(LocalXrUi provides XrUi(true, false),
                    LocalMinimumInteractiveComponentSize provides 56.dp) {
                    Catalog(AppUiState(query = "Example", apps = listOf(example), loading = false)) {}
                }
            }
        }
        listOf("User", "System", "All").forEach {
            compose.onNodeWithText(it).assertHeightIsAtLeast(56.dp).assertWidthIsAtLeast(56.dp)
        }
        compose.onNodeWithContentDescription("Clear search")
            .assertHeightIsAtLeast(56.dp).assertWidthIsAtLeast(56.dp).performClick()
        compose.onNodeWithContentDescription("Clear search").assertDoesNotExist()
    }

    @Test fun ordinaryDevicesHaveNoSpaceToggleOrSupportingPanel() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        org.junit.Assume.assumeFalse(context.packageManager.hasSystemFeature("android.software.xr.api.spatial"))
        compose.setContent {
            ApkExtractorTheme {
                XrWorkspace(supportingPane = { error("Spatial pane must not render without XR") }) {
                    XrSpaceModeButton()
                    androidx.compose.material3.Text("Phone content")
                }
            }
        }
        compose.onNodeWithText("Phone content").assertIsDisplayed()
    }
}
