package com.kolehoenicke.apkextractor.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMultiModalInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.click
import com.kolehoenicke.apkextractor.AppUiState
import com.kolehoenicke.apkextractor.data.InstalledApp
import com.kolehoenicke.apkextractor.ui.theme.ApkExtractorTheme
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class DesktopScreenTest {
    @get:Rule val rule = createComposeRule()
    private lateinit var inputModeManager: InputModeManager
    private val apps = (1..4).map { InstalledApp("Example $it", "example.$it", "1.0", 1, null, false, emptyList()) }
    private var state by mutableStateOf(AppUiState(apps = apps, loading = false))
    private var extracted = emptyList<InstalledApp>()

    private fun screen() {
        rule.setContent { ApkExtractorTheme {
            inputModeManager = LocalInputModeManager.current
            ApkExtractorScreen(state, SnackbarHostState(),
                onQueryChange = { state = state.copy(query = it) },
                onFilterChange = { state = state.copy(filter = it) },
                onRefresh = {}, onChooseFolder = {}, onExtract = { extracted = listOf(it) },
                onExtractSelected = { extracted = it },
                onStartSelection = { state = state.copy(selectedPackages = setOf(it)) },
                onToggleSelection = { state = state.copy(selectedPackages = if (it in state.selectedPackages) state.selectedPackages - it else state.selectedPackages + it) },
                onClearSelection = { state = state.copy(selectedPackages = emptySet()) },
                focusSearchRequests = emptyFlow(),
                onSelectionChange = { state = state.copy(selectedPackages = it) })
        } }
    }

    @Test fun selectAllAndExtractSelectedWorkWithAKeyboard() {
        screen()
        rule.runOnIdle { inputModeManager.requestInputMode(InputMode.Keyboard) }
        // Switching input mode can initially focus and expand Material search.
        rule.onAllNodes(hasSetTextAction()).onLast().performImeAction()
        rule.onNodeWithText("Example 1").performSemanticsAction(SemanticsActions.RequestFocus) { it() }.assertIsFocused()
        rule.onNodeWithText("Example 1").performKeyInput {
            keyDown(Key.CtrlLeft); pressKey(Key.A); keyUp(Key.CtrlLeft)
        }
        rule.onNodeWithText("4 selected").assertIsDisplayed()
        rule.onNodeWithText("Example 1").performKeyInput {
            keyDown(Key.CtrlLeft); pressKey(Key.Enter); keyUp(Key.CtrlLeft)
        }
        rule.runOnIdle { assertEquals(apps, extracted) }
    }

    @Test fun shiftClickExtendsTheSelectionThroughVisibleRows() {
        screen()
        rule.onNodeWithText("Example 1").performMultiModalInput {
            key { keyDown(Key.CtrlLeft) }; mouse { click() }; key { keyUp(Key.CtrlLeft) }
        }
        rule.onNodeWithText("Example 3").performMultiModalInput {
            key { keyDown(Key.ShiftLeft) }; mouse { click() }; key { keyUp(Key.ShiftLeft) }
        }
        rule.runOnIdle { assertEquals(setOf("example.1", "example.2", "example.3"), state.selectedPackages); assertTrue(extracted.isEmpty()) }
    }

    @Test fun controlAInSearchSelectsTextInsteadOfApps() {
        screen()
        rule.onNodeWithText("Search apps").performClick()
        rule.onAllNodes(hasSetTextAction() and isFocused()).onLast().performTextInput("Example")
        rule.onAllNodes(hasSetTextAction() and isFocused()).onLast().performKeyInput {
            keyDown(Key.CtrlLeft); pressKey(Key.A); keyUp(Key.CtrlLeft); pressKey(Key.Backspace)
        }
        rule.runOnIdle { assertEquals("", state.query); assertTrue(state.selectedPackages.isEmpty()) }
    }
}
