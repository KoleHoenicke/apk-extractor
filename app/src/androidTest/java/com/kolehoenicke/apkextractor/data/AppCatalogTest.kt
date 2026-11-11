package com.kolehoenicke.apkextractor.data

import android.content.res.Configuration
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AppCatalogTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun bulkCatalogMatchesInstalledPackagesWithoutRenderingIcons() = runBlocking {
        @Suppress("DEPRECATION")
        val native = context.packageManager.getInstalledPackages(0)
            .filter { it.applicationInfo?.publicSourceDir != null }.associateBy { it.packageName }
        val apps = AppCatalog(context).load()
        assertEquals(native.keys, apps.map { it.packageName }.toSet())
        assertTrue(apps.all { it.icon == null })
        apps.forEach { app ->
            val original = native.getValue(app.packageName)
            assertEquals(original.lastUpdateTime, app.lastUpdateTime)
            assertEquals(original.applicationInfo!!.publicSourceDir, app.apkFiles.first().path)
            assertEquals(original.applicationInfo!!.splitPublicSourceDirs.orEmpty().size + 1, app.apkFiles.size)
        }
    }

    @Test fun targetedExportMetadataMatchesTheBulkCatalog() = runBlocking {
        val catalog = AppCatalog(context)
        val app = catalog.load().first { it.packageName == context.packageName }
        assertEquals(app, catalog.loadPackages(listOf(context.packageName)).single())
        assertTrue(catalog.loadPackages(listOf("com.example.not.installed.apkextractor")).isEmpty())
    }

    @Test fun visibleIconsAreCachedAndPackageOrResourceChangesInvalidateTheirKey() = runBlocking {
        val app = AppCatalog(context).loadPackages(listOf(context.packageName)).single()
        val configuration = Configuration(context.resources.configuration)
        val key = AppIconKey(app.packageName, app.apkFiles.first().path, app.versionCode,
            app.lastUpdateTime, configuration)
        val first = requireNotNull(AppIconLoader.load(context, key))
        assertEquals(128, first.width)
        assertSame(first, AppIconLoader.load(context, key))
        val updated = key.copy(lastUpdateTime = key.lastUpdateTime + 1)
        assertNull(AppIconLoader.cached(updated))
        assertNotSame(first, AppIconLoader.load(context, updated))
        val night = Configuration(configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES)
                    Configuration.UI_MODE_NIGHT_NO else Configuration.UI_MODE_NIGHT_YES
        }
        assertNull(AppIconLoader.cached(key.copy(configuration = night)))
    }
}
