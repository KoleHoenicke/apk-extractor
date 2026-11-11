package com.kolehoenicke.apkextractor.assistant

import com.kolehoenicke.apkextractor.data.InstalledApk
import com.kolehoenicke.apkextractor.data.InstalledApp
import org.junit.Assert.*
import org.junit.Test

class AssistantAppQueryTest {
    private val single = InstalledApp("Example", "com.example.app", "1.0", 1, null, false,
        listOf(InstalledApk("base.apk", null, 100)), lastUpdateTime = 1_000)
    private val split = single.copy(packageName = "com.example.split", apkFiles =
        single.apkFiles + InstalledApk("split.apk", "config.en", 200), lastUpdateTime = 2_000)

    @Test fun omittedTypeSelectsUserAppsAndAllIncludesSystemApps() {
        val system = single.copy(isSystemApp = true)
        assertTrue(AssistantAppQuery().matches(single))
        assertFalse(AssistantAppQuery().matches(system))
        assertTrue(AssistantAppQuery(appType = "all").matches(system))
        assertTrue(AssistantAppQuery(appType = "system").matches(system))
        assertFalse(AssistantAppQuery(appType = "system").matches(single))
    }

    @Test fun datesSizeAndSplitFiltersAreCombinedAndIncludeBoundaryValues() {
        val criteria = AssistantAppQuery(updatedSinceMillis = 2_000, minimumBytes = 300,
            maximumBytes = 300, splitApks = true)
        assertTrue(criteria.matches(split))
        assertFalse(criteria.matches(single))
        assertFalse(criteria.copy(minimumBytes = 301).matches(split))
        assertFalse(criteria.copy(maximumBytes = 299).matches(split))
        assertFalse(criteria.copy(updatedSinceMillis = 2_001).matches(split))
    }

    @Test fun singleApkRestrictionDoesNotIncludeSplitInstallations() {
        val criteria = AssistantAppQuery(splitApks = false)
        assertTrue(criteria.matches(single))
        assertFalse(criteria.matches(split))
    }

    @Test fun namesAreCaseInsensitiveButExactPackageIdsAreNotFuzzy() {
        assertTrue(AssistantAppQuery(query = "  EXAMPLE  ").matches(single))
        assertTrue(AssistantAppQuery(query = "example.app").matches(single))
        assertFalse(AssistantAppQuery(query = "missing").matches(single))
        assertTrue(AssistantAppQuery(packageNames = listOf("com.example.app")).matches(single))
        assertFalse(AssistantAppQuery(packageNames = listOf("com.example")).matches(single))
        assertFalse(AssistantAppQuery(packageNames = listOf("COM.EXAMPLE.APP")).matches(single))
        assertFalse(AssistantAppQuery(packageNames = listOf("com.example.app"), splitApks = true).matches(single))
    }

    @Test fun packageNameMustStillMatchOtherFilters() {
        assertFalse(AssistantAppQuery(packageNames = listOf(single.packageName), updatedSinceMillis = 2_000)
            .matches(single))
        assertTrue(AssistantAppQuery(query = "   ").matches(single))
    }

    @Test fun invalidCriteriaAreRejectedInsteadOfBroadeningSelection() {
        listOf(
            AssistantAppQuery(appType = "non-system"),
            AssistantAppQuery(query = "x".repeat(201)),
            AssistantAppQuery(packageNames = listOf("")),
            AssistantAppQuery(packageNames = List(501) { "com.example.$it" }),
            AssistantAppQuery(packageNames = listOf("x".repeat(256))),
            AssistantAppQuery(updatedSinceMillis = -1),
            AssistantAppQuery(minimumBytes = -1),
            AssistantAppQuery(maximumBytes = -1),
            AssistantAppQuery(minimumBytes = 301, maximumBytes = 300),
        ).forEach { criteria ->
            assertThrows(IllegalArgumentException::class.java, criteria::validate)
        }
    }
}
