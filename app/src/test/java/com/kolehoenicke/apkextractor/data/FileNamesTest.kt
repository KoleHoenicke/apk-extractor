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

