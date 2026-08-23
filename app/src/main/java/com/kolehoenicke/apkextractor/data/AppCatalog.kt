package com.kolehoenicke.apkextractor.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import java.text.Collator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppCatalog(private val context: Context) {
    private val packageManager: PackageManager = context.packageManager

    suspend fun load(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val applications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getInstalledApplications(0)
        }

        val collator = Collator.getInstance()
        applications.mapNotNull { toInstalledApp(it, includeIcon = true) }.sortedWith { first, second ->
            collator.compare(first.label, second.label)
                .takeUnless { it == 0 }
                ?: first.packageName.compareTo(second.packageName)
        }
    }

    suspend fun loadPackages(packageNames: Collection<String>): List<InstalledApp> =
        withContext(Dispatchers.IO) {
            packageNames.mapNotNull { packageName ->
                val applicationInfo = runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        packageManager.getApplicationInfo(
                            packageName,
                            PackageManager.ApplicationInfoFlags.of(0),
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        packageManager.getApplicationInfo(packageName, 0)
                    }
                }.getOrNull() ?: return@mapNotNull null
                toInstalledApp(applicationInfo, includeIcon = false)
            }
        }

    private fun toInstalledApp(
        applicationInfo: ApplicationInfo,
        includeIcon: Boolean,
    ): InstalledApp? = runCatching {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(
                applicationInfo.packageName,
                PackageManager.PackageInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(applicationInfo.packageName, 0)
        }

        val basePath = applicationInfo.publicSourceDir ?: return null
        val splitPaths = applicationInfo.splitPublicSourceDirs.orEmpty()
        val splitNames = applicationInfo.splitNames.orEmpty()
        val apkFiles = buildList {
            add(
                InstalledApk(
                    path = basePath,
                    splitName = null,
                    bytes = File(basePath).length(),
                ),
            )
            splitPaths.forEachIndexed { index, splitPath ->
                add(
                    InstalledApk(
                        path = splitPath,
                        splitName = splitNames.getOrNull(index),
                        bytes = File(splitPath).length(),
                    ),
                )
            }
        }

        val systemFlags = ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
        val icon = if (includeIcon) {
            runCatching {
                LauncherIconRenderer.render(
                    source = applicationInfo.loadIcon(packageManager),
                    resources = context.resources,
                    size = 128,
                )
                    .asImageBitmap()
            }.getOrNull()
        } else {
            null
        }

        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }

        InstalledApp(
            label = applicationInfo.loadLabel(packageManager).toString(),
            packageName = applicationInfo.packageName,
            versionName = packageInfo.versionName ?: versionCode.toString(),
            versionCode = versionCode,
            icon = icon,
            isSystemApp = applicationInfo.flags and systemFlags != 0,
            apkFiles = apkFiles,
        )
    }.getOrNull()

}
