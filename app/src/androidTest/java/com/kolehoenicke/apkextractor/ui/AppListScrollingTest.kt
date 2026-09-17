package com.kolehoenicke.apkextractor.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.swipeDown
import com.kolehoenicke.apkextractor.data.InstalledApp
import com.kolehoenicke.apkextractor.ui.theme.ApkExtractorTheme
import org.junit.Rule
import org.junit.Test

class AppListScrollingTest {
    @get:Rule val rule = createComposeRule()

    @Test fun scrollAndReplaceRowsWithMultilineSupportingContent() {
        val allApps = List(150) { index ->
            InstalledApp("App $index", "com.example.app$index", "1.0", 1, null, false, emptyList())
        }
        val apps = mutableStateOf(allApps)
        val exporting = mutableStateOf(false)
        val selected = mutableStateOf(false)
        rule.setContent {
            ApkExtractorTheme {
                LazyColumn(
                    state = androidx.compose.foundation.lazy.rememberLazyListState(),
                    modifier = Modifier.fillMaxSize().testTag("apps"),
                ) {
                    itemsIndexed(apps.value, key = { _, app -> app.packageName }) { index, app ->
                        AppRow(app, index, apps.value.size, selected.value, selected.value,
                            exporting.value, 0.5f, true, {}, {})
                    }
                }
            }
        }
        repeat(15) {
            rule.onNodeWithTag("apps").performTouchInput { swipeUp() }
            rule.waitForIdle()
        }
        rule.runOnIdle { exporting.value = true; selected.value = true }
        rule.waitForIdle()
        rule.runOnIdle { apps.value = allApps.filterIndexed { index, _ -> index % 2 == 0 } }
        repeat(15) {
            rule.onNodeWithTag("apps").performTouchInput { swipeDown() }
            rule.waitForIdle()
        }
        rule.runOnIdle { exporting.value = false; apps.value = allApps.reversed(); selected.value = false }
        rule.waitForIdle()
    }
}
