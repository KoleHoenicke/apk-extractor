package com.kolehoenicke.apkextractor.ui

import android.net.Uri
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.kolehoenicke.apkextractor.AppUiState
import com.kolehoenicke.apkextractor.ExportFailure
import com.kolehoenicke.apkextractor.UiEvent
import com.kolehoenicke.apkextractor.data.AppSort
import com.kolehoenicke.apkextractor.data.ExportedFile
import com.kolehoenicke.apkextractor.ui.theme.ApkExtractorTheme
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ExportImprovementsTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun resultShowsFailureAndProvidesRetryFolderAndShare() {
        var retried = false
        var opened = false
        var shared = false
        val result = UiEvent.ExportFinished(
            listOf(ExportedFile(Uri.parse("content://test/file"), "日本語-1.apk", false)),
            2,
            listOf(ExportFailure("Example", "The selected folder is no longer available.", "test.app")),
            Uri.parse("content://test/tree/root"),
        )
        composeRule.setContent {
            ApkExtractorTheme {
                ExportResultDialog(result, true, { retried = true }, { opened = true }, { shared = true }, {})
            }
        }
        composeRule.onNodeWithText("1 saved · 1 failed").assertIsDisplayed()
        composeRule.onNodeWithText("The selected folder is no longer available.").assertIsDisplayed()
        composeRule.onNodeWithText("Retry failed apps").performClick()
        composeRule.onNodeWithText("Open folder").performClick()
        composeRule.onNodeWithText("Share").performClick()
        assertTrue(retried && opened && shared)
        screenshot("export-result.png")
    }

    @Test fun recentlyUpdatedSortIsAvailableInOverflow() {
        var selected: AppSort? = null
        var folderOpened = false
        composeRule.setContent {
            ApkExtractorTheme {
                ApkExtractorScreen(
                    state = AppUiState(loading = false, outputFolderName = "APK Extractor"),
                    snackbarHostState = SnackbarHostState(),
                    onQueryChange = {}, onFilterChange = {}, onRefresh = {}, onChooseFolder = { folderOpened = true },
                    onExtract = {}, onExtractSelected = {}, onStartSelection = {}, onToggleSelection = {},
                    onClearSelection = {}, focusSearchRequests = emptyFlow(), onSortChange = { selected = it },
                )
            }
        }
        composeRule.onNodeWithText("Saving to APK Extractor").assertDoesNotExist()
        composeRule.onAllNodesWithContentDescription("More options")[1].performClick()
        composeRule.onNodeWithText("Sort by").performClick()
        composeRule.onNodeWithText("Name").assertIsSelected()
        composeRule.onNodeWithText("Recently updated").assertIsDisplayed()
        screenshot("sort-menu.png")
        composeRule.onNodeWithText("Recently updated").performClick()
        assertEquals(AppSort.RecentlyUpdated, selected)
    }

    @Test fun successfulExportUsesAppNamesWithoutFailureSummary() {
        val result = UiEvent.ExportFinished(
            listOf(ExportedFile(Uri.parse("content://test/file"), "日本語-1.apk", false, "日本語")),
            1, emptyList(), Uri.parse("content://test/tree/root"),
        )
        composeRule.setContent { ApkExtractorTheme { ExportResultDialog(result, false, {}, {}, {}, {}) } }
        composeRule.onNodeWithText("Export complete").assertIsDisplayed()
        composeRule.onNodeWithText("日本語").assertIsDisplayed()
        composeRule.onNodeWithText("日本語-1.apk").assertDoesNotExist()
        composeRule.onNodeWithText("1 saved · 0 failed").assertDoesNotExist()
        screenshot("export-success.png")
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
        assertTrue(opened && changed && dismissed)
        screenshot("export-folder.png")
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
