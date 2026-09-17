package com.kolehoenicke.apkextractor.data

import org.junit.Assert.assertEquals
import org.junit.Test

class FileNamesTest {
    @Test
    fun `single APK uses readable name and version`() {
        val app = sampleApp(label = "Example App", version = "2.1.0", apkCount = 1)

        assertEquals("Example App-2.1.0.apk", exportFileName(app))
    }

    @Test
    fun `split APK uses explicit archive suffix`() {
        val app = sampleApp(label = "Example App", version = "2.1.0", apkCount = 3)

        assertEquals("Example App-2.1.0-split-apks.zip", exportFileName(app))
    }

    @Test
    fun `unsafe file characters are removed`() {
        assertEquals("Odd name", safeFileStem("  Odd:/\\*? name.  "))
    }

    @Test
    fun `international names remain readable`() {
        for (name in listOf("日本語", "العربية", "हिन्दी", "Русский", "Café")) {
            assertEquals(name, safeFileStem(name))
        }
    }

    @Test
    fun `control and direction override characters cannot disguise filenames`() {
        assertEquals("ab", safeFileStem("a\u202Eb\u0000"))
    }

    @Test
    fun `long names do not split supplementary unicode characters`() {
        val letter = "𐐀"
        assertEquals(letter.repeat(80), safeFileStem(letter.repeat(100)))
    }

    @Test
    fun `export filenames fit common byte limits with long international names`() {
        val name = exportFileName(sampleApp("日本語".repeat(80), "𐐀".repeat(80), 3))
        org.junit.Assert.assertTrue(name.toByteArray(Charsets.UTF_8).size <= 240)
        org.junit.Assert.assertTrue(name.endsWith("-split-apks.zip"))
    }

    private fun sampleApp(label: String, version: String, apkCount: Int) = InstalledApp(
        label = label,
        packageName = "com.example.app",
        versionName = version,
        versionCode = 1,
        icon = null,
        isSystemApp = false,
        apkFiles = List(apkCount) { index ->
            InstalledApk(path = "/tmp/$index.apk", splitName = index.takeIf { it > 0 }?.toString(), bytes = 1)
        },
    )
}

