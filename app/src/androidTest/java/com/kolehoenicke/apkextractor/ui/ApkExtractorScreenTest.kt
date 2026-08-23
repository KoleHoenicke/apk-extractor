@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.kolehoenicke.apkextractor.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.kolehoenicke.apkextractor.AppUiState
import com.kolehoenicke.apkextractor.data.InstalledApp
import com.kolehoenicke.apkextractor.ui.theme.ApkExtractorTheme
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Rule
import org.junit.Test

class ApkExtractorScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun selectedAppIsExposedAsASelectedCheckbox() {
        val app = InstalledApp(
            label = "Example",
            packageName = "com.example",
            versionName = "1.0",
            versionCode = 1,
            icon = null,
            isSystemApp = false,
            apkFiles = emptyList(),
        )

        composeRule.setContent {
            ApkExtractorTheme {
                ApkExtractorScreen(
                    state = AppUiState(
                        apps = listOf(app),
                        loading = false,
                        selectedPackages = setOf(app.packageName),
                    ),
                    snackbarHostState = SnackbarHostState(),
                    onQueryChange = {},
                    onFilterChange = {},
                    onRefresh = {},
                    onChooseFolder = {},
                    onExtract = {},
                    onExtractSelected = {},
                    onStartSelection = {},
                    onToggleSelection = {},
                    onClearSelection = {},
                    focusSearchRequests = emptyFlow(),
                )
            }
        }

        composeRule.onNode(hasText("Example"))
            .assertIsSelected()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.Role,
                    Role.Checkbox,
                ),
            )
    }

    @Test
    fun privacyAndLicensesAreAccessibleFromTheOverflowMenu() {
        composeRule.setContent {
            ApkExtractorTheme {
                ApkExtractorScreen(
                    state = AppUiState(loading = false),
                    snackbarHostState = SnackbarHostState(),
                    onQueryChange = {},
                    onFilterChange = {},
                    onRefresh = {},
                    onChooseFolder = {},
                    onExtract = {},
                    onExtractSelected = {},
                    onStartSelection = {},
                    onToggleSelection = {},
                    onClearSelection = {},
                    focusSearchRequests = emptyFlow(),
                )
            }
        }

        composeRule.onAllNodesWithContentDescription("More options")[1].performClick()
        composeRule.onNodeWithText("Privacy & licenses").performClick()

        composeRule.onNodeWithText("APK Extractor reads the installed-app inventory", substring = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText("The Android robot is reproduced or modified", substring = true)
            .assertIsDisplayed()
    }
}
