package com.kolehoenicke.apkextractor.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.MouseButton
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performMultiModalInput
import androidx.compose.ui.test.pressKey
import com.kolehoenicke.apkextractor.data.InstalledApp
import com.kolehoenicke.apkextractor.ui.theme.ApkExtractorTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class DesktopInputTest {
    @get:Rule val rule = createComposeRule()
    private lateinit var inputModeManager: InputModeManager
    private var extracts = 0
    private var selections = 0
    private var ranges = 0
    private val app = InstalledApp("Example", "example.app", "1.0", 1, null, false, emptyList())

    private fun row(enabled: Boolean = true) {
        rule.setContent { ApkExtractorTheme {
            inputModeManager = LocalInputModeManager.current
            AppRow(app, 0, 1, false, false, false, 0f, enabled,
                onClick = { extracts++ }, onLongClick = { selections++ },
                modifier = Modifier.fillMaxWidth(), onContextExtract = { extracts++ },
                onRangeSelection = { ranges++ })
        } }
    }

    @Test fun secondaryClickOpensActionsWithoutExtracting() {
        row()
        rule.onNodeWithText("Example").performMouseInput { click(button = MouseButton.Secondary) }
        rule.onNodeWithText("Extract Example").assertIsDisplayed()
        rule.onNodeWithText("Select Example").performClick()
        rule.runOnIdle { assertEquals(0, extracts); assertEquals(1, selections) }
    }

    @Test fun contextMenuCanExtract() {
        row()
        rule.onNodeWithText("Example").performMouseInput { click(button = MouseButton.Secondary) }
        rule.onNodeWithText("Extract Example").performClick()
        rule.runOnIdle { assertEquals(1, extracts); assertEquals(0, selections) }
    }

    @Test fun controlClickSelectsWithoutExtracting() {
        row()
        rule.onNodeWithText("Example").performMultiModalInput {
            key { keyDown(Key.CtrlLeft) }
            mouse { click() }
            key { keyUp(Key.CtrlLeft) }
        }
        rule.runOnIdle { assertEquals(0, extracts); assertEquals(1, selections) }
    }

    @Test fun shiftClickSelectsARangeWithoutExtracting() {
        row()
        rule.onNodeWithText("Example").performMultiModalInput {
            key { keyDown(Key.ShiftLeft) }
            mouse { click() }
            key { keyUp(Key.ShiftLeft) }
        }
        rule.runOnIdle { assertEquals(0, extracts); assertEquals(1, ranges) }
    }

    @Test fun primaryClickKeepsTheExistingExtractionAction() {
        row()
        rule.onNodeWithText("Example").performMouseInput { click() }
        rule.runOnIdle { assertEquals(1, extracts); assertEquals(0, selections) }
    }

    @Test fun spaceSelectsTheFocusedRowWithoutExtracting() {
        row()
        rule.runOnIdle { inputModeManager.requestInputMode(InputMode.Keyboard) }
        rule.onNodeWithText("Example").performSemanticsAction(SemanticsActions.RequestFocus) { it() }.assertIsFocused()
        rule.onNodeWithText("Example").performKeyInput { pressKey(Key.Spacebar) }
        rule.runOnIdle { assertEquals(0, extracts); assertEquals(1, selections) }
    }

    @Test fun enterExtractsTheFocusedRow() {
        row()
        rule.runOnIdle { inputModeManager.requestInputMode(InputMode.Keyboard) }
        rule.onNodeWithText("Example").performSemanticsAction(SemanticsActions.RequestFocus) { it() }.assertIsFocused()
        rule.onNodeWithText("Example").performKeyInput { pressKey(Key.Enter) }
        rule.runOnIdle { assertEquals(1, extracts); assertEquals(0, selections) }
    }

    @Test fun keyboardCanOpenTheContextMenu() {
        row()
        rule.runOnIdle { inputModeManager.requestInputMode(InputMode.Keyboard) }
        rule.onNodeWithText("Example").performSemanticsAction(SemanticsActions.RequestFocus) { it() }.assertIsFocused()
        rule.onNodeWithText("Example").performKeyInput {
            keyDown(Key.ShiftLeft); pressKey(Key.F10); keyUp(Key.ShiftLeft)
        }
        rule.onNodeWithText("Select Example").assertIsDisplayed()
        rule.runOnIdle { assertEquals(0, extracts) }
    }

    @Test fun disabledRowIgnoresMouseActions() {
        row(enabled = false)
        rule.onNodeWithText("Example").performMouseInput { click(button = MouseButton.Secondary) }
        rule.onNodeWithText("Extract Example").assertDoesNotExist()
        rule.runOnIdle { assertEquals(0, extracts); assertEquals(0, selections) }
    }
}
