package com.kolehoenicke.apkextractor.ui

import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.platform.app.InstrumentationRegistry
import com.kolehoenicke.apkextractor.data.AppFilter
import com.kolehoenicke.apkextractor.R
import com.kolehoenicke.apkextractor.saveLocalizationScreenshot
import com.kolehoenicke.apkextractor.ui.theme.ApkExtractorTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

class LocalizedFiltersTest {
    @get:Rule val rule = createComposeRule()

    @Test fun translatedLabelsRemainReadableAndSelectableWithNativeOverflow() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        fun resourcesFor(tag: String) = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            setLocales(LocaleList.forLanguageTags(tag))
        }).resources
        val locale = mutableStateOf("ru")
        val large = mutableStateOf(false)
        val selected = mutableStateOf(AppFilter.User)
        val measurements = File(context.filesDir, "localized-filter-layouts.txt").apply { writeText("") }
        rule.setContent {
            CompositionLocalProvider(
                LocalResources provides resourcesFor(locale.value),
                LocalLayoutDirection provides if (locale.value in listOf("ar", "fa")) LayoutDirection.Rtl else LayoutDirection.Ltr,
                LocalDensity provides Density(LocalDensity.current.density, if (large.value) 2f else 1f),
            ) {
                ApkExtractorTheme {
                    Box(Modifier.width(if (large.value) 240.dp else 360.dp)) {
                        AppFilterButtons(selected.value, { selected.value = it })
                    }
                }
            }
        }
        fun visibleLabel(label: String, unmerged: Boolean = false): androidx.compose.ui.test.SemanticsNodeInteraction? {
            val nodes = rule.onAllNodesWithText(label, useUnmergedTree = unmerged)
            return (0 until nodes.fetchSemanticsNodes().size).map { nodes[it] }.firstOrNull { it.isDisplayed() }
        }
        for (tag in listOf("en", "es", "pt", "fr", "ar", "fa", "ru", "de", "ko", "zh-Hans")) {
            rule.runOnIdle { locale.value = tag }
            val resources = resourcesFor(tag)
            for (largeText in listOf(false, true)) {
                rule.runOnIdle { large.value = largeText; selected.value = AppFilter.User }
                for ((filter, id) in listOf(
                    AppFilter.User to R.string.user_apps,
                    AppFilter.System to R.string.system_apps,
                    AppFilter.All to R.string.all_apps,
                )) {
                    val label = resources.getString(id)
                    if (visibleLabel(label) == null) {
                        rule.onNode(hasClickAction() and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
                            .performClick()
                    }
                    val visible = requireNotNull(visibleLabel(label)) { "Filter not reachable: $label" }
                    visible.assertIsDisplayed()
                    saveLocalizationScreenshot("filters-$tag-${if (largeText) "large" else "normal"}-$filter")
                    val layouts = mutableListOf<TextLayoutResult>()
                    requireNotNull(visibleLabel(label, unmerged = true))
                        .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                    assertTrue("Expected a layout for $label", layouts.isNotEmpty())
                    layouts.forEach {
                        val glyphRight = (0 until it.lineCount).maxOf(it::getLineRight)
                        val glyphLeft = (0 until it.lineCount).minOf(it::getLineLeft)
                        // RTL paragraphs report line edges from the wider paragraph origin.
                        // Compare their painted span with the text box, rather than that origin.
                        val widthOverflow = if (tag in listOf("ar", "fa")) glyphRight - glyphLeft - it.size.width
                            else maxOf(glyphRight - it.size.width, -glyphLeft)
                        val lineEnd = it.getLineEnd(it.lineCount - 1, visibleEnd = true)
                        val ellipsized = (0 until it.lineCount).any(it::isLineEllipsized)
                        val detail = "$tag: $label, large=$largeText, size=${it.size}, paragraphWidth=${it.multiParagraph.width}, glyphLeft=$glyphLeft, glyphRight=$glyphRight, widthOverflow=$widthOverflow, heightOverflow=${it.didOverflowHeight}, lineEnd=$lineEnd/${label.length}, ellipsized=$ellipsized"
                        measurements.appendText("$detail\n")
                        // Paragraph constraints can exceed the text's actual advance in a popup.
                        // Check every character and the rendered line edges, allowing pixel rounding.
                        assertTrue("Clipped filter: $detail", widthOverflow <= 1f)
                        assertFalse("Clipped filter: $detail", it.didOverflowHeight || ellipsized)
                        assertEquals("Missing label characters: $detail", label.length, lineEnd)
                    }
                    visible.performClick()
                    rule.runOnIdle { assertEquals(filter, selected.value) }
                }
            }
        }
    }

    @Test fun englishButtonsFillTheAvailableWidthEquallyInBothDirections() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resources = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            setLocales(LocaleList.forLanguageTags("en"))
        }).resources
        val direction = mutableStateOf(LayoutDirection.Ltr)
        rule.setContent {
            CompositionLocalProvider(LocalResources provides resources,
                LocalLayoutDirection provides direction.value,
                LocalDensity provides Density(LocalDensity.current.density, 1f)) {
                ApkExtractorTheme {
                    Box(Modifier.width(360.dp).testTag("viewport")) {
                        AppFilterButtons(AppFilter.User, {}, Modifier.testTag("filters"))
                    }
                }
            }
        }
        for (rtl in listOf(false, true)) {
            rule.runOnIdle { direction.value = if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr }
            val viewport = rule.onNodeWithTag("viewport").fetchSemanticsNode().boundsInRoot
            val buttons = rule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
                .fetchSemanticsNodes().sortedBy { it.boundsInRoot.left }
            assertEquals(3, buttons.size)
            assertEquals(viewport.left, buttons.first().boundsInRoot.left, 1f)
            assertEquals(viewport.right, buttons.last().boundsInRoot.right, 1f)
            buttons.forEach { assertEquals(buttons.first().boundsInRoot.width, it.boundsInRoot.width, 1f) }
        }
    }
}
