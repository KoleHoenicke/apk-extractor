package com.kolehoenicke.apkextractor

import android.app.LocaleConfig
import android.app.LocaleManager
import android.os.LocaleList
import android.view.View
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@SdkSuppress(minSdkVersion = 33)
class LocalizationTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun nativeLanguageSelectionRecreatesAppAndFallsBackToEnglish() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = context.getSystemService(LocaleManager::class.java)
        val original = manager.applicationLocales
        try {
            val supported = requireNotNull(LocaleConfig(context).supportedLocales)
            assertEquals(setOf("en", "es", "pt", "fr", "ar", "fa"), (0 until supported.size()).map { supported[it].language }.toSet())
            for ((tag, search) in listOf(
                "es" to "Buscar apps", "pt-BR" to "Pesquisar apps", "fr" to "Rechercher des applis",
                "ar" to "البحث عن تطبيقات", "fa" to "جستجوی برنامه‌ها", "de" to "Search apps",
            )) {
                manager.applicationLocales = LocaleList.forLanguageTags(tag)
                rule.waitUntil(10_000) { rule.activity.getString(R.string.search_apps) == search }
                rule.onNodeWithText(search).assertIsDisplayed()
                val rtl = tag == "ar" || tag == "fa"
                assertEquals(if (rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR,
                    rule.activity.resources.configuration.layoutDirection)
                val pluralIds = listOf(R.plurals.app_count, R.plurals.selected_count,
                    R.plurals.export_complete_count, R.plurals.export_failed_count,
                    R.plurals.export_partial, R.plurals.notification_extracting_apps,
                    R.plurals.result_saved, R.plurals.result_failed)
                for (id in pluralIds) for (count in listOf(0, 1, 2, 3, 11, 100, 1_000_000)) {
                    assertFalse(rule.activity.resources.getQuantityString(id, count, count, count).contains("%"))
                }
                saveLocalizationScreenshot("locale-$tag-main")
                rule.onAllNodesWithContentDescription(rule.activity.getString(R.string.more_options)).onLast().performClick()
                rule.onNodeWithText(rule.activity.getString(R.string.sort_by)).performClick()
                rule.onNodeWithText(rule.activity.getString(R.string.sort_recent)).assertIsDisplayed()
                saveLocalizationScreenshot("locale-$tag-sort")
                rule.onNodeWithText(rule.activity.getString(R.string.sort_recent)).performClick()
            }
        } finally { manager.applicationLocales = original }
    }
}

internal fun saveLocalizationScreenshot(name: String) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    instrumentation.uiAutomation.waitForIdle(500, 5_000)
    val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
    val file = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "$name.png")
    file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    bitmap.recycle()
    instrumentation.uiAutomation.executeShellCommand("cp ${file.absolutePath} /data/local/tmp/$name.png").use {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(it).use { stream -> stream.readBytes() }
    }
}
