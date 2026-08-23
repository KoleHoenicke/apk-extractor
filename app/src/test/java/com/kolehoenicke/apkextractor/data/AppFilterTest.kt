package com.kolehoenicke.apkextractor.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AppFilterTest {
    private val userApp = app("Camera Tool", "com.example.camera", system = false)
    private val systemApp = app("Package Installer", "com.android.packageinstaller", system = true)
    private val apps = listOf(userApp, systemApp)

    @Test
    fun `user filter hides system packages`() {
        assertEquals(listOf(userApp), filterApps(apps, AppFilter.User, ""))
    }

    @Test
    fun `system filter hides user packages`() {
        assertEquals(listOf(systemApp), filterApps(apps, AppFilter.System, ""))
    }

    @Test
    fun `search matches labels and package names without case sensitivity`() {
        assertEquals(listOf(userApp), filterApps(apps, AppFilter.All, "CAMERA"))
        assertEquals(listOf(systemApp), filterApps(apps, AppFilter.All, "android.package"))
    }

    private fun app(label: String, packageName: String, system: Boolean) = InstalledApp(
        label = label,
        packageName = packageName,
        versionName = "1.0",
        versionCode = 1,
        icon = null,
        isSystemApp = system,
        apkFiles = listOf(InstalledApk("/tmp/base.apk", null, 1)),
    )
}

