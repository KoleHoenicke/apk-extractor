package com.kolehoenicke.apkextractor.ui

import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.platform.app.InstrumentationRegistry
import com.kolehoenicke.apkextractor.R
import com.kolehoenicke.apkextractor.ui.theme.ApkExtractorTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PrivacyDialogTest {
    @get:Rule val rule = createComposeRule()

    @Test fun shortPrivacyKeepsFullNoticesAndLicensesAvailableOffline() {
        val open = mutableStateOf(true)
        rule.setContent { ApkExtractorTheme { if (open.value) PrivacyAndLicensesDialog { open.value = false } } }
        rule.onNodeWithText("APK Extractor has no internet access", substring = true).assertIsDisplayed()
        rule.onNodeWithText("The Android robot is reproduced or modified", substring = true).assertDoesNotExist()
        rule.onNodeWithText("Licenses").performClick()
        rule.onNodeWithText("Credits & notices").performScrollTo().performClick()
        waitForDocument("The Android robot is reproduced or modified")
        rule.onNodeWithText("Copyright 2015 Google LLC", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Back").performClick()
        rule.onNodeWithText("Apache License 2.0").performScrollTo().performClick()
        waitForDocument("TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION")
        val body = rule.onNode(hasScrollAction())
        body.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 1000f) }
        assertTrue(body.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value() > 0f)
        rule.onNodeWithText("Done").assertIsDisplayed()
        rule.onNodeWithText("Back").performClick()
        rule.onNodeWithText("SIL Open Font License 1.1").performScrollTo().performClick()
        waitForDocument("SIL OPEN FONT LICENSE Version 1.1")
        rule.onNodeWithText("Back").performClick()
        rule.onNodeWithText("Back").performClick()
        rule.onNodeWithText("APK Extractor has no internet access", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Done").performClick()
        rule.onNodeWithText("Privacy & licenses").assertDoesNotExist()
    }

    @Test fun shortPrivacyAndActionsStayAvailableInAllLanguagesAtLargeText() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val tag = mutableStateOf("en")
        val open = mutableStateOf(true)
        rule.setContent {
            val configuration = Configuration(context.resources.configuration).apply {
                setLocales(LocaleList.forLanguageTags(tag.value)); fontScale = 2f
            }
            val localized = context.createConfigurationContext(configuration)
            CompositionLocalProvider(LocalContext provides localized, LocalResources provides localized.resources,
                LocalConfiguration provides configuration, LocalDensity provides Density(LocalDensity.current.density, 2f),
                LocalLayoutDirection provides if (tag.value in listOf("ar", "fa")) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                ApkExtractorTheme { if (open.value) PrivacyAndLicensesDialog { open.value = false } }
            }
        }
        for (locale in listOf("en", "es", "pt", "fr", "ar", "fa", "ru", "de", "ko", "zh-Hans")) {
            rule.runOnIdle { tag.value = locale; open.value = true }
            val resources = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
                setLocales(LocaleList.forLanguageTags(locale))
            }).resources
            rule.onNodeWithText(resources.getString(R.string.privacy_summary)).assertIsDisplayed()
            rule.onNodeWithText(resources.getString(R.string.licenses)).assertIsDisplayed()
            rule.onNodeWithText(resources.getString(R.string.done)).assertIsDisplayed().performClick()
        }
    }

    private fun waitForDocument(text: String) {
        rule.waitUntil(10_000) { rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText(text, substring = true).assertIsDisplayed()
    }
}
