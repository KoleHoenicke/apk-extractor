package com.kolehoenicke.apkextractor.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.kolehoenicke.apkextractor.data.AppSort
import com.kolehoenicke.apkextractor.ui.theme.ApkExtractorTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

class DesktopMenuTest {
    @get:Rule val rule = createComposeRule()

    @Test fun sortMenuAtRightWindowEdgeSelectsAndDismisses() = checkMenu(LayoutDirection.Ltr)

    @Test fun sortMenuAtLeftWindowEdgeWorksInRtl() = checkMenu(LayoutDirection.Rtl)

    private fun checkMenu(direction: LayoutDirection) {
        var expanded by mutableStateOf(false)
        var sort by mutableStateOf(AppSort.Name)
        rule.setContent { CompositionLocalProvider(LocalLayoutDirection provides direction) { ApkExtractorTheme {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopEnd) {
                Box {
                    IconButton(onClick = { expanded = true }) { Text("Menu") }
                    AppOverflowMenu(expanded, { expanded = false }, sort, { sort = it },
                        true, {}, {}, onNewWindow = {})
                }
            }
        } } }
        rule.onNodeWithText("Menu").performClick()
        rule.onNodeWithText("Sort by").assertIsDisplayed()
        capture("desktop-menu-parent-${direction.name}.png")
        rule.onNodeWithText("Sort by").performClick()
        rule.onNodeWithText("Recently updated").assertIsDisplayed()
        capture("desktop-menu-sort-${direction.name}.png")
        val row = bounds("Sort by")
        val option = bounds("Name")
        assertTrue("Submenu must sit beside its row: $row / $option",
            option.right <= row.left || option.left >= row.right)
        assertTrue("Submenu must align with its row", kotlin.math.abs(option.top - row.top) < row.height)
        rule.onNodeWithText("Recently updated").performClick()
        rule.runOnIdle { assertEquals(AppSort.RecentlyUpdated, sort); assertEquals(false, expanded) }
    }

    private fun bounds(text: String): Rect {
        val node = rule.onNodeWithText(text).fetchSemanticsNode()
        return Rect(node.positionOnScreen, Size(node.size.width.toFloat(), node.size.height.toFloat()))
    }

    private fun capture(name: String) {
        rule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
            File(instrumentation.targetContext.cacheDir, name).outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
    }
}
