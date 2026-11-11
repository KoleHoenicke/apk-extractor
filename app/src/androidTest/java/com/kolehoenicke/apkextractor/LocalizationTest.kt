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
        val preferences = context.getSharedPreferences("preferences", android.content.Context.MODE_PRIVATE)
        val originalSort = preferences.getString("sort", null)
        try {
            val supported = requireNotNull(LocaleConfig(context).supportedLocales)
            assertEquals(setOf("en", "es", "pt", "fr", "ar", "fa", "ru", "de", "ko", "zh"), (0 until supported.size()).map { supported[it].language }.toSet())
            assertTrue((0 until supported.size()).any { supported[it].language == "zh" && supported[it].script == "Hans" })
            for ((tag, search) in listOf(
                "es" to "Buscar apps", "pt-BR" to "Pesquisar apps", "fr" to "Rechercher des applis",
                "ar" to "البحث عن تطبيقات", "fa" to "جستجوی برنامه‌ها",
                "ru" to "Поиск приложений", "ru-RU" to "Поиск приложений", "ru-KZ" to "Поиск приложений",
                "de" to "Apps suchen", "de-DE" to "Apps suchen", "de-CH" to "Apps suchen",
                "ko" to "앱 검색", "ko-KR" to "앱 검색",
                "zh-Hans" to "搜索应用", "zh-CN" to "搜索应用", "zh-SG" to "搜索应用",
                "it" to "Search apps",
            )) {
                manager.applicationLocales = LocaleList.forLanguageTags(tag)
                rule.waitUntil(10_000) { rule.activity.getString(R.string.search_apps) == search }
                rule.onNodeWithText(search).assertIsDisplayed()
                val rtl = tag == "ar" || tag == "fa"
                assertEquals(if (rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR,
                    rule.activity.resources.configuration.layoutDirection)
                val pluralIds = listOf(R.plurals.selected_count,
                    R.plurals.export_complete_count, R.plurals.export_failed_count,
                    R.plurals.export_partial, R.plurals.notification_extracting_apps,
                    R.plurals.result_extracted, R.plurals.result_failed, R.plurals.exported_files)
                for (id in pluralIds) for (count in listOf(0, 1, 2, 3, 5, 11, 21, 22, 25, 100, 101, 111, 1_000_000)) {
                    assertFalse(rule.activity.resources.getQuantityString(id, count, count, count).contains("%"))
                }
                saveLocalizationScreenshot("locale-$tag-main")
                rule.onAllNodesWithContentDescription(rule.activity.getString(R.string.more_options)).onLast().performClick()
                rule.onNodeWithText(rule.activity.getString(R.string.sort_by)).performClick()
                rule.onNodeWithText(rule.activity.getString(R.string.sort_recent)).assertIsDisplayed()
                saveLocalizationScreenshot("locale-$tag-sort")
                rule.onNodeWithText(rule.activity.getString(R.string.sort_recent)).performClick()
            }
        } finally {
            manager.applicationLocales = original
            preferences.edit().apply {
                if (originalSort == null) remove("sort") else putString("sort", originalSort)
            }.commit()
        }
    }

    @Test fun newLanguageCountsUseNativePluralRulesAndArgumentOrder() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        fun resourcesFor(tag: String) = context.createConfigurationContext(android.content.res.Configuration(context.resources.configuration).apply {
            setLocales(LocaleList.forLanguageTags(tag))
        }).resources
        val german = resourcesFor("de")
        assertEquals("1 App gespeichert", german.getQuantityString(R.plurals.export_complete_count, 1, 1))
        assertEquals("2 Apps gespeichert", german.getQuantityString(R.plurals.export_complete_count, 2, 2))
        assertEquals("0 Apps gespeichert", german.getQuantityString(R.plurals.export_complete_count, 0, 0))
        assertEquals("2 von 3 Apps gespeichert", german.getQuantityString(R.plurals.export_partial, 3, 2, 3))
        for (count in listOf(0, 1, 2, 21)) {
            assertEquals("앱 ${count}개 저장됨", resourcesFor("ko").getQuantityString(R.plurals.export_complete_count, count, count))
            assertEquals("已保存 $count 个应用", resourcesFor("zh-Hans").getQuantityString(R.plurals.export_complete_count, count, count))
        }
        assertEquals("앱 3개 중 2개 저장됨", resourcesFor("ko").getQuantityString(R.plurals.export_partial, 3, 2, 3))
        assertEquals("已保存 2 个应用，共 3 个", resourcesFor("zh-Hans").getQuantityString(R.plurals.export_partial, 3, 2, 3))
        assertEquals("Search apps", resourcesFor("zh-Hant-TW").getString(R.string.search_apps))
    }

    @Test fun russianAppAndFileCountsUseNativePluralRules() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val configuration = android.content.res.Configuration(context.resources.configuration).apply {
            setLocales(LocaleList.forLanguageTags("ru"))
        }
        val resources = context.createConfigurationContext(configuration).resources
        for ((count, appNoun, filePhrase) in listOf(
            Triple(0, "приложений", "Экспортировано 0 файлов"),
            Triple(1, "приложение", "Экспортирован 1 файл"),
            Triple(2, "приложения", "Экспортировано 2 файла"),
            Triple(5, "приложений", "Экспортировано 5 файлов"),
            Triple(11, "приложений", "Экспортировано 11 файлов"),
            Triple(21, "приложение", "Экспортирован 21 файл"),
            Triple(22, "приложения", "Экспортировано 22 файла"),
            Triple(25, "приложений", "Экспортировано 25 файлов"),
            Triple(101, "приложение", "Экспортирован 101 файл"),
            Triple(111, "приложений", "Экспортировано 111 файлов"),
        )) {
            assertEquals("Сохранено $count $appNoun", resources.getQuantityString(R.plurals.export_complete_count, count, count))
            assertEquals("Не удалось извлечь $count $appNoun", resources.getQuantityString(R.plurals.export_failed_count, count, count))
            assertEquals(filePhrase, resources.getQuantityString(R.plurals.exported_files, count, count))
            assertEquals("Выбрано: $count", resources.getQuantityString(R.plurals.selected_count, count, count))
        }
        assertEquals("Сохранено 2 из 21 приложения", resources.getQuantityString(R.plurals.export_partial, 21, 2, 21))
        assertEquals("Сохранено 2 из 22 приложений", resources.getQuantityString(R.plurals.export_partial, 22, 2, 22))
        assertEquals("Извлечение 21 приложения", resources.getQuantityString(R.plurals.notification_extracting_apps, 21, 21))
        assertEquals("Извлечение 22 приложений", resources.getQuantityString(R.plurals.notification_extracting_apps, 22, 22))
    }
}

internal fun saveLocalizationScreenshot(name: String) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    instrumentation.uiAutomation.waitForIdle(500, 5_000)
    val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
    val file = java.io.File(instrumentation.targetContext.filesDir, "$name.png")
    file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    bitmap.recycle()
}
