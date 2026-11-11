package com.kolehoenicke.apkextractor.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.text.Collator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Installed metadata is independent of the icons needed by visible rows. */
class AppCatalog(context: Context) {
    private val packageManager = context.packageManager

    suspend fun load(): List<InstalledApp> = withContext(Dispatchers.IO) {
        catalogTrace("AppCatalog.load") {
            val packages = catalogTrace("AppCatalog.packages") {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    packageManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    packageManager.getInstalledPackages(0)
                }
            }
            val collator = Collator.getInstance()
            packages.mapNotNull(::toInstalledApp).sortedWith { first, second ->
                collator.compare(first.label, second.label)
                    .takeUnless { it == 0 }
                    ?: first.packageName.compareTo(second.packageName)
            }
        }
    }

    suspend fun loadPackages(packageNames: Collection<String>): List<InstalledApp> =
        withContext(Dispatchers.IO) {
            packageNames.mapNotNull { packageName ->
                val packageInfo = runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
                    } else {
                        @Suppress("DEPRECATION")
                        packageManager.getPackageInfo(packageName, 0)
                    }
                }.getOrNull() ?: return@mapNotNull null
                toInstalledApp(packageInfo)
            }
        }

    private fun toInstalledApp(packageInfo: PackageInfo): InstalledApp? = runCatching {
        val applicationInfo = packageInfo.applicationInfo ?: return null
        val basePath = applicationInfo.publicSourceDir ?: return null
        val splitPaths = applicationInfo.splitPublicSourceDirs.orEmpty()
        val splitNames = applicationInfo.splitNames.orEmpty()
        val apkFiles = buildList {
            add(InstalledApk(basePath, null, File(basePath).length()))
            splitPaths.forEachIndexed { index, path ->
                add(InstalledApk(path, splitNames.getOrNull(index), File(path).length()))
            }
        }
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
        InstalledApp(
            label = applicationInfo.loadLabel(packageManager).toString(),
            packageName = packageInfo.packageName,
            versionName = packageInfo.versionName ?: versionCode.toString(),
            versionCode = versionCode,
            icon = null,
            isSystemApp = applicationInfo.flags and
                (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0,
            apkFiles = apkFiles,
            lastUpdateTime = packageInfo.lastUpdateTime,
        )
    }.getOrNull()
}

/** Native trace sections measure catalog work independently from activity launch time. */
internal inline fun <T> catalogTrace(name: String, block: () -> T): T {
    android.os.Trace.beginSection(name)
    return try { block() } finally { android.os.Trace.endSection() }
}
