package com.kolehoenicke.apkextractor.ui

import android.content.res.Configuration
import android.net.Uri
import android.os.LocaleList
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.test.platform.app.InstrumentationRegistry
import com.kolehoenicke.apkextractor.*
import com.kolehoenicke.apkextractor.data.ExportedFile
import com.kolehoenicke.apkextractor.ui.theme.ApkExtractorTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class LocalizedDialogsTest {
    @get:Rule val rule = createComposeRule()

    @Test fun translatedActionsRemainVisibleWithLargeTextAndRtl() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val locale = mutableStateOf("es")
        val folder = mutableStateOf(false)
        val failed = mutableStateOf(false)
        var compactHeight = false
        var shared = 0
        rule.setContent {
            compactHeight = LocalWindowInfo.current.containerDpSize.height < 480.dp
            val configuration = Configuration(context.resources.configuration).apply {
                setLocales(LocaleList.forLanguageTags(locale.value))
                fontScale = 2f
            }
            val localized = context.createConfigurationContext(configuration)
            CompositionLocalProvider(
                LocalContext provides localized,
                LocalResources provides localized.resources,
                LocalDensity provides Density(LocalDensity.current.density, 2f),
                LocalConfiguration provides configuration,
                LocalLayoutDirection provides if (locale.value in listOf("ar", "fa")) LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                ApkExtractorTheme {
                    if (folder.value) ExportFolderDialog("APK Extractor", {}, {}, {})
                    else ExportResultDialog(
                        UiEvent.ExportFinished(listOf(
                            ExportedFile(Uri.parse("content://test/1"), "1Password-8.zip", true, "1Password"),
                            ExportedFile(Uri.parse("content://test/2"), "日本語.apk", false, "日本語"),
                            ExportedFile(Uri.parse("content://test/3"), "تطبيق.apk", false, "تطبيق"),
                        ), 4, if (failed.value) listOf(ExportFailure("Example", localized.getString(R.string.export_failed_partial))) else emptyList(), Uri.parse("content://test/tree/root")),
                        failed.value, {}, {}, { shared++ }, {},
                    )
                }
            }
        }
        fun reveal(text: String) {
            if (compactHeight) {
                rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex))
                    .performScrollToNode(hasText(text))
            }
        }
        fun assertWholeLabelVisible(text: String) {
            val node = rule.onNodeWithText(text, useUnmergedTree = true)
            node.assertIsDisplayed()
            val layouts = mutableListOf<TextLayoutResult>()
            node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue("Expected text layout for $text", layouts.isNotEmpty())
            val visibleHeight = node.fetchSemanticsNode().boundsInRoot.height
            assertTrue("Partially clipped label: $text", visibleHeight >= layouts.maxOf { it.size.height } - 1f)
        }
        for (tag in listOf("es", "pt", "fr", "ar", "fa", "ru", "de", "ko", "zh-Hans")) {
            rule.runOnIdle { locale.value = tag; folder.value = false; failed.value = false }
            val resources = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
                setLocales(LocaleList.forLanguageTags(tag))
            }).resources
            reveal(resources.getString(R.string.share))
            rule.onNodeWithText(resources.getString(R.string.share)).assertIsDisplayed().performClick()
            rule.onNodeWithText(resources.getString(R.string.done)).assertIsDisplayed()
            saveLocalizationScreenshot("locale-$tag-result")
            rule.runOnIdle { failed.value = true }
            reveal(resources.getString(R.string.retry_failed))
            rule.onNodeWithText(resources.getString(R.string.retry_failed)).assertIsDisplayed()
            if (compactHeight) {
                // A short sheet must retain a real viewport for the error and file list.
                reveal(resources.getString(R.string.export_failed_partial))
                rule.onNodeWithText(resources.getString(R.string.export_failed_partial)).assertIsDisplayed()
                reveal("تطبيق.apk")
                rule.onNodeWithText("تطبيق.apk").assertIsDisplayed()
            }
            rule.onNodeWithText(resources.getString(R.string.done)).assertIsDisplayed()
            saveLocalizationScreenshot("locale-$tag-failure")
            rule.runOnIdle { folder.value = true }
            assertWholeLabelVisible(resources.getString(R.string.open_folder_action))
            assertWholeLabelVisible(resources.getString(R.string.change_folder_action))
            assertWholeLabelVisible(resources.getString(R.string.done))
            saveLocalizationScreenshot("locale-$tag-folder")
        }
        assertEquals(9, shared)
    }
}
