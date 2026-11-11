package com.kolehoenicke.apkextractor.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.swipeDown
import com.kolehoenicke.apkextractor.AppUiState
import com.kolehoenicke.apkextractor.data.AppFilter
import com.kolehoenicke.apkextractor.data.InstalledApp
import com.kolehoenicke.apkextractor.ui.theme.ApkExtractorTheme
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MaterialSelectionTest {
    @get:Rule val rule = createComposeRule()
    private val apps = List(100) { InstalledApp("App $it", "example.$it", "1", 1, null, it % 2 == 0, emptyList()) }
    private var state by mutableStateOf(AppUiState(apps = apps, loading = false, filter = AppFilter.All))
    private var extracted = emptyList<InstalledApp>()

    private fun screen() {
        rule.setContent { ApkExtractorTheme {
            ApkExtractorScreen(state, SnackbarHostState(),
                onQueryChange = { state = state.copy(query = it) },
                onFilterChange = { state = state.copy(filter = it) },
                onRefresh = {}, onChooseFolder = {}, onExtract = { extracted = listOf(it) },
                onExtractSelected = { extracted = it },
                onStartSelection = { state = state.copy(selectedPackages = setOf(it)) },
                onToggleSelection = { state = state.copy(selectedPackages = state.selectedPackages.let { selected ->
                    if (it in selected) selected - it else selected + it
                }) },
                onClearSelection = { state = state.copy(selectedPackages = emptySet()) },
                focusSearchRequests = emptyFlow())
        } }
    }

    @Test fun selectionActionsRemainVisibleWithCollapsedAppBarAndAfterScrolling() {
        screen()
        rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex)).performScrollToIndex(40)
        rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex)).performTouchInput { swipeUp() }
        // Also exercises an assistant selection arriving while the catalog is scrolled.
        rule.runOnIdle { state = state.copy(selectedPackages = setOf("example.40", "example.41")) }
        rule.onNodeWithText("2 selected").assertIsDisplayed()
        rule.onNodeWithText("Extract", useUnmergedTree = true).assertIsDisplayed()
        rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex)).performTouchInput { swipeUp() }
        rule.onNodeWithText("2 selected").assertIsDisplayed()
        rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex)).performTouchInput { swipeDown() }
        rule.onAllNodesWithContentDescription("Extract selected apps").onLast().performClick()
        rule.runOnIdle { assertEquals(listOf(apps[40], apps[41]), extracted) }
        rule.onAllNodesWithContentDescription("Exit selection").onLast().performClick()
        rule.onNodeWithText("2 selected").assertDoesNotExist()
    }

    @Test fun selectionShowsCheckedRowsAndFiltersRemainSingleChoice() {
        screen()
        rule.onNodeWithText("User").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)).performClick()
        rule.onNodeWithText("User").performClick()
        rule.runOnIdle { assertEquals(AppFilter.User, state.filter) }
        rule.onNodeWithText("All").performClick()
        rule.runOnIdle { state = state.copy(selectedPackages = setOf("example.0")) }
        rule.onNode(hasText("App 0")).assertIsOn()
        rule.onNodeWithText("100 apps").assertDoesNotExist()
    }

    @Test fun expandedSearchHasVisibleBackAndExternalQueryChangesReachTheField() {
        screen()
        rule.onNodeWithText("Search apps").performClick()
        rule.onAllNodes(hasSetTextAction() and isFocused()).onLast().performTextInput("App")
        rule.onAllNodesWithContentDescription("Close search").onLast().assertIsDisplayed().performClick()
        rule.runOnIdle { assertEquals("App", state.query); state = state.copy(query = "") }
        rule.onNodeWithText("Search apps").assertIsDisplayed()
    }
}
