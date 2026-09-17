package com.kolehoenicke.apkextractor.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import com.kolehoenicke.apkextractor.data.InstalledApp
import com.kolehoenicke.apkextractor.saveLocalizationScreenshot
import com.kolehoenicke.apkextractor.ui.theme.ApkExtractorTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AppRowLayoutTest {
    @get:Rule val rule = createComposeRule()

    @Test fun mainSlotDoesNotPlaceTextWhileRowIsBeingMeasured() {
        var placements = 0
        rule.setContent {
            ApkExtractorTheme {
                Layout(content = {
                    SegmentedListItem(shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1)) {
                        Column {
                            Text("Example", Modifier.onPlaced { placements++ })
                            Text("com.example", Modifier.onPlaced { placements++ }, style = MaterialTheme.typography.bodyMedium)
                            Text("1.0 · 24 MB · APK", Modifier.onPlaced { placements++ }, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }) { measurables, constraints ->
                    val before = placements
                    val row = measurables.single().measure(constraints)
                    assertEquals("Text must not be placed until the row's parent is placed", before, placements)
                    layout(row.width, row.height) { row.placeRelative(0, 0) }
                }
            }
        }
        rule.waitForIdle()
        assertTrue(placements >= 3)
    }

    @Test fun rowPreservesThreeLineHeightAndExpandsForProgress() {
        val exporting = mutableStateOf(false)
        var clicked = false
        val app = InstalledApp("Example", "com.example.app", "1.0", 1, null, false, emptyList())
        rule.setContent { ApkExtractorTheme {
            AppRow(app, 0, 1, false, false, exporting.value, 0.5f, true,
                { clicked = true }, {}, Modifier.testTag("row"))
        } }
        rule.onNodeWithTag("row").assertHeightIsAtLeast(88.dp).performClick()
        assertTrue(clicked)
        val original = rule.onNodeWithTag("row").fetchSemanticsNode().boundsInRoot.height
        saveLocalizationScreenshot("crash-fix-row")
        rule.runOnIdle { exporting.value = true }
        rule.waitForIdle()
        assertTrue(rule.onNodeWithTag("row").fetchSemanticsNode().boundsInRoot.height > original)
        rule.onNodeWithText("com.example.app", useUnmergedTree = true).assertIsDisplayed()
        saveLocalizationScreenshot("crash-fix-row-progress")
        rule.runOnIdle { exporting.value = false }
        rule.waitForIdle()
        assertEquals(original, rule.onNodeWithTag("row").fetchSemanticsNode().boundsInRoot.height, 1f)
    }
}
