package com.kolehoenicke.apkextractor.ui

import android.net.Uri
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.kolehoenicke.apkextractor.AppUiState
import com.kolehoenicke.apkextractor.ExportFailure
import com.kolehoenicke.apkextractor.UiEvent
import com.kolehoenicke.apkextractor.data.AppSort
import com.kolehoenicke.apkextractor.data.ExportedFile
import com.kolehoenicke.apkextractor.ui.theme.ApkExtractorTheme
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ExportImprovementsTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun resultShowsFailureAndProvidesRetryFolderAndShare() {
        var retried = false
        var opened = false
        var shared = false
        var dismissed = false
        val result = UiEvent.ExportFinished(
            listOf(ExportedFile(Uri.parse("content://test/file"), "日本語-1.apk", false)),
            2,
            listOf(ExportFailure("Example", "The selected folder is no longer available.", "test.app")),
            Uri.parse("content://test/tree/root"),
        )
        composeRule.setContent {
            ApkExtractorTheme {
                ExportResultDialog(result, true, { retried = true }, { opened = true }, { shared = true }, { dismissed = true })
            }
        }
        composeRule.onNodeWithText("1 extracted · 1 failed").assertIsDisplayed()
        composeRule.onNodeWithText("The selected folder is no longer available.").assertIsDisplayed()
        composeRule.onNodeWithText("Retry failed apps").performClick()
        composeRule.onNodeWithText("Open folder").performClick()
        composeRule.onNodeWithText("Share").performClick()
        composeRule.onNodeWithText("Done").performClick()
        composeRule.runOnIdle { assertTrue(retried && opened && shared && dismissed) }
        screenshot("export-result.png")
    }

    @Test fun recentlyUpdatedSortIsAvailableInOverflow() {
        var selected: AppSort? = null
        var folderOpened = false
        val state = mutableStateOf(AppUiState(loading = false, outputFolderName = "APK Extractor"))
        composeRule.setContent {
            ApkExtractorTheme {
                ApkExtractorScreen(
                    state = state.value,
                    snackbarHostState = SnackbarHostState(),
                    onQueryChange = {}, onFilterChange = {}, onRefresh = {}, onChooseFolder = { folderOpened = true },
                    onExtract = {}, onExtractSelected = {}, onStartSelection = {}, onToggleSelection = {},
                    onClearSelection = {}, focusSearchRequests = emptyFlow(), onSortChange = {
                        selected = it
                        state.value = state.value.copy(sort = it)
                    },
                )
            }
        }
        composeRule.onNodeWithText("Saving to APK Extractor").assertDoesNotExist()
        composeRule.onAllNodesWithContentDescription("More options")[1].performClick()
        composeRule.onNodeWithText("Sort by").performClick()
        // A submenu must leave its parent menu visible on compact windows, too.
        composeRule.onNodeWithText("Sort by").assertIsDisplayed()
        composeRule.onNodeWithText("Privacy & licenses").assertIsDisplayed()
        composeRule.onNodeWithText("Name").assertIsSelected()
        composeRule.onNodeWithText("Recently updated").assertIsDisplayed()
        assertSubmenuDoesNotCoverParent()
        screenshot("sort-menu.png")
        composeRule.onNodeWithText("Recently updated").performClick()
        assertEquals(AppSort.RecentlyUpdated, selected)
        composeRule.onNodeWithText("Sort by").assertDoesNotExist()
        composeRule.onNodeWithText("Recently updated").assertDoesNotExist()
        composeRule.onAllNodesWithContentDescription("More options")[1].performClick()
        composeRule.onNodeWithText("Sort by").performClick()
        composeRule.onNodeWithText("Privacy & licenses").assertIsDisplayed()
        composeRule.onNodeWithText("Recently updated").assertIsSelected()
    }

    @Test fun successfulExportUsesAppNamesWithoutFailureSummary() {
        val result = UiEvent.ExportFinished(
            listOf(
                ExportedFile(Uri.parse("content://test/file"), "日本語-1.apk", false, "日本語"),
                ExportedFile(Uri.parse("content://test/second"), "Example-2.apk", false, "Example"),
            ),
            2, emptyList(), Uri.parse("content://test/tree/root"),
        )
        composeRule.setContent { ApkExtractorTheme { ExportResultDialog(result, false, {}, {}, {}, {}) } }
        composeRule.onNodeWithText("Export complete").assertIsDisplayed()
        composeRule.onNodeWithText("日本語").assertIsDisplayed()
        // File names accompany app names so desktop users can identify each drag source.
        composeRule.onNodeWithText("日本語-1.apk").assertIsDisplayed()
        composeRule.onNodeWithText("2 extracted").assertIsDisplayed()
        composeRule.onNodeWithText("2 exported files").assertDoesNotExist()
        composeRule.onNodeWithText("Example-2.apk").assertIsDisplayed()
        composeRule.onNodeWithText("2 extracted · 0 failed").assertDoesNotExist()
        screenshot("export-success.png")
    }

    @Test fun doneWaitsForTheNativeExitBeforeRemovingResultAndFolderSheets() {
        val visible = mutableStateOf(false)
        val folder = mutableStateOf(false)
        var dismissals = 0
        val result = UiEvent.ExportFinished(
            listOf(ExportedFile(Uri.parse("content://test/file"), "Example.apk", false)),
            1, emptyList(), null,
        )
        composeRule.setContent {
            ApkExtractorTheme {
                if (visible.value) {
                    val dismiss = { dismissals++; visible.value = false }
                    if (folder.value) ExportFolderDialog("Extracted APKs", {}, {}, dismiss)
                    else ExportResultDialog(result, false, {}, {}, {}, dismiss)
                }
            }
        }
        for ((index, isFolder) in listOf(false, true).withIndex()) {
            composeRule.runOnIdle { folder.value = isFolder; visible.value = true }
            composeRule.onNodeWithText("Done").assertIsDisplayed()
            composeRule.waitForIdle()
            composeRule.mainClock.autoAdvance = false
            try {
                composeRule.onNodeWithText("Done").performClick()
                composeRule.runOnIdle {
                    assertTrue("Keep sheet present while native hide runs", visible.value)
                    assertEquals(index, dismissals)
                }
                composeRule.mainClock.advanceTimeBy(2_000)
                composeRule.waitForIdle()
                composeRule.runOnIdle {
                    assertFalse(visible.value)
                    assertEquals(index + 1, dismissals)
                }
            } finally {
                composeRule.mainClock.autoAdvance = true
            }
        }
    }

    @Test fun pressingAnActionKeepsAllGroupLabelsComplete() {
        val result = UiEvent.ExportFinished(
            listOf(ExportedFile(Uri.parse("content://test/file"), "Example.apk", false)),
            1, emptyList(), Uri.parse("content://test/tree/root"),
        )
        composeRule.setContent {
            ApkExtractorTheme {
                Box(androidx.compose.ui.Modifier.width(360.dp).height(600.dp)) {
                    ExportResultPane(result, false, {}, {}, {}, {})
                }
            }
        }
        val labels = listOf("Open folder", "Share", "Done")
        for (pressed in labels) {
            composeRule.onNodeWithText(pressed).performTouchInput { down(center) }
            try {
                composeRule.mainClock.advanceTimeBy(500)
                for (label in labels) {
                    val text = composeRule.onNodeWithText(label, useUnmergedTree = true)
                    val layouts = mutableListOf<TextLayoutResult>()
                    text.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                    val layout = layouts.single()
                    assertEquals("Full label while $pressed is held", label.length, layout.getLineEnd(0, visibleEnd = true))
                    assertFalse(layout.isLineEllipsized(0))
                    val button = composeRule.onNode(hasText(label) and hasClickAction()).fetchSemanticsNode().boundsInRoot
                    val origin = text.fetchSemanticsNode().positionInRoot.x
                    val diagnostic = "$label while $pressed held: button=$button, textOrigin=$origin, " +
                        "size=${layout.size}, line=${layout.getLineLeft(0)}..${layout.getLineRight(0)}"
                    assertTrue("Label starts inside button: $diagnostic", origin + layout.getLineLeft(0) >= button.left - 1f)
                    assertTrue("Label ends inside button: $diagnostic", origin + layout.getLineRight(0) <= button.right + 1f)
                }
            } finally {
                composeRule.onNodeWithText(pressed).performTouchInput { cancel() }
            }
        }
    }

    @Test fun splitExportExplainsThatTheZipNeedsASplitInstaller() {
        val result = UiEvent.ExportFinished(
            listOf(ExportedFile(Uri.parse("content://test/file"), "Example-1-split-apks.zip", true, "Example")),
            1, emptyList(), Uri.parse("content://test/tree/root"),
        )
        composeRule.setContent { ApkExtractorTheme { ExportResultDialog(result, false, {}, {}, {}, {}) } }
        composeRule.onNodeWithText("Split APK export").assertIsDisplayed()
        composeRule.onNodeWithText("cannot be installed by tapping the ZIP", substring = true).assertIsDisplayed()
    }

    @Test fun folderDialogGroupsWorkingActions() {
        var opened = false
        var changed = false
        var dismissed = false
        composeRule.setContent { ApkExtractorTheme {
            ExportFolderDialog("Extracted APKs", { opened = true }, { changed = true }, { dismissed = true })
        } }
        composeRule.onNodeWithText("Open folder").performClick()
        composeRule.onNodeWithText("Change").performClick()
        composeRule.onNodeWithText("Done").performClick()
        composeRule.runOnIdle { assertTrue(opened && changed && dismissed) }
        screenshot("export-folder.png")
    }

    private fun assertSubmenuDoesNotCoverParent() {
        val parents = listOf("Sort by", "Privacy & licenses").map {
            composeRule.onNodeWithText(it).fetchSemanticsNode()
        }
        val children = listOf("Name", "Recently updated").map {
            composeRule.onNodeWithText(it).fetchSemanticsNode()
        }
        // Separate Popup roots have separate window origins. Compare screen coordinates.
        composeRule.runOnIdle {
            fun bounds(node: androidx.compose.ui.semantics.SemanticsNode): Rect {
                val coordinates = node.layoutInfo.coordinates
                val origin = coordinates.positionOnScreen()
                return Rect(origin.x, origin.y,
                    origin.x + coordinates.size.width, origin.y + coordinates.size.height)
            }
            parents.forEach { parent -> children.forEach { child ->
                assertFalse("The sort submenu must not cover its parent menu", bounds(parent).overlaps(bounds(child)))
            } }
        }
    }

    private fun screenshot(name: String) {
        composeRule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        java.io.File(instrumentation.targetContext.getExternalFilesDir(null), name).outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
        val path = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), name).absolutePath
        instrumentation.uiAutomation.executeShellCommand("cp $path /data/local/tmp/$name").use {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(it).use { stream -> stream.readBytes() }
        }
    }
}
