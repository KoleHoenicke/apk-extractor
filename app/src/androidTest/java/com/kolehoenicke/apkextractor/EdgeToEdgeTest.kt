package com.kolehoenicke.apkextractor

import android.graphics.Bitmap
import android.content.pm.ActivityInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowCompat
import android.app.UiModeManager
import android.content.res.Configuration
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.filters.SdkSuppress
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@SdkSuppress(minSdkVersion = 30)
class EdgeToEdgeTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun fullScreenSearchResultsExtendBehindGestureBar() {
        rule.onNodeWithText("Search apps").performClick()
        waitForKeyboard()
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        rule.waitUntil(10_000) {
            var hidden = false
            rule.runOnUiThread {
                hidden = ViewCompat.getRootWindowInsets(rule.activity.window.decorView)
                    ?.isVisible(WindowInsetsCompat.Type.ime()) == false
            }
            hidden && rule.onAllNodes(hasScrollAction()).fetchSemanticsNodes().size >= 2
        }
        rule.waitForIdle()
        val windowHeight = rule.activity.windowManager.currentWindowMetrics.bounds.height()
        rule.waitUntil(10_000) {
            rule.onAllNodes(hasScrollAction()).fetchSemanticsNodes().any {
                kotlin.math.abs(it.boundsInWindow.bottom - windowHeight.toFloat()) <= 1f
            }
        }
        val results = rule.onAllNodes(hasScrollAction()).fetchSemanticsNodes().maxBy { it.boundsInWindow.bottom }.boundsInWindow
        assertEquals("Results viewport must reach the bottom of the window", windowHeight.toFloat(), results.bottom, 1f)
        screenshot("search-edge-to-edge")
    }

    @Test
    @SdkSuppress(minSdkVersion = 31)
    fun systemBarIconsFollowLightAndDarkTheme() {
        val manager = rule.activity.getSystemService(UiModeManager::class.java)
        try {
            for (dark in listOf(true, false)) {
                manager.setApplicationNightMode(if (dark) UiModeManager.MODE_NIGHT_YES else UiModeManager.MODE_NIGHT_NO)
                rule.waitUntil(10_000) {
                    (rule.activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                        Configuration.UI_MODE_NIGHT_YES) == dark
                }
                rule.waitForIdle()
                rule.runOnUiThread {
                    val window = rule.activity.window
                    val controller = WindowCompat.getInsetsController(window, window.decorView)
                    assertEquals(!dark, controller.isAppearanceLightStatusBars)
                    assertEquals(!dark, controller.isAppearanceLightNavigationBars)
                }
                screenshot(if (dark) "dark-theme" else "light-theme")
            }
        } finally {
            manager.setApplicationNightMode(UiModeManager.MODE_NIGHT_AUTO)
        }
    }

    @Test fun searchRemainsAboveKeyboardInPortraitAndLandscape() {
        rule.onNodeWithText("Search apps").performClick()
        waitForKeyboard()
        rule.waitUntil(10_000) { rule.onAllNodes(hasSetTextAction() and isFocused()).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodes(hasSetTextAction() and isFocused()).onLast().performTextInput("apkextractor")
        waitForKeyboard()
        screenshot("search-portrait")
        assertSearchAboveKeyboard()
        rule.runOnUiThread {
            rule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        rule.waitUntil(10_000) {
            rule.activity.resources.configuration.orientation ==
                android.content.res.Configuration.ORIENTATION_LANDSCAPE
        }
        rule.waitForIdle()
        rule.onAllNodes(hasSetTextAction() and isFocused()).onLast().performClick()
        waitForKeyboard()
        screenshot("search-landscape")
        assertSearchAboveKeyboard()
    }

    private fun waitForKeyboard() {
        rule.waitUntil(10_000) {
            var visible = false
            rule.runOnUiThread {
                visible = ViewCompat.getRootWindowInsets(rule.activity.window.decorView)
                    ?.isVisible(WindowInsetsCompat.Type.ime()) == true
            }
            visible
        }
    }

    private fun assertSearchAboveKeyboard() {
        val bounds = rule.onAllNodes(hasSetTextAction() and isFocused()).onLast().fetchSemanticsNode().boundsInWindow
        rule.runOnUiThread {
            val view = rule.activity.window.decorView
            val insets = requireNotNull(ViewCompat.getRootWindowInsets(view))
            val system = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            assertTrue("Search clears status bar", bounds.top >= system.top)
            val windowHeight = rule.activity.windowManager.currentWindowMetrics.bounds.height()
            assertTrue("Search bottom ${bounds.bottom}, window $windowHeight, IME ${ime.bottom}",
                bounds.bottom <= windowHeight - ime.bottom)
        }
    }

    private fun screenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.waitForIdle(1_000, 8_000)
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val directory = File(rule.activity.getExternalFilesDir(null), "verification").apply { mkdirs() }
        val output = File(directory, "$name.png")
        output.outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        // Gradle uninstalls the tested app after the suite, so retain the image outside its data.
        instrumentation.uiAutomation.executeShellCommand(
            "cp ${output.absolutePath} /data/local/tmp/apk-extractor-$name.png",
        ).use { descriptor ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
        }
    }
}
