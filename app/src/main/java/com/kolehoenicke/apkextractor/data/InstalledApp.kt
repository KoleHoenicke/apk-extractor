package com.kolehoenicke.apkextractor.data

import androidx.compose.ui.graphics.ImageBitmap

data class InstalledApp(
    val label: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val icon: ImageBitmap?,
    val isSystemApp: Boolean,
    val apkFiles: List<InstalledApk>,
) {
    val isSplit: Boolean get() = apkFiles.size > 1
    val totalBytes: Long get() = apkFiles.sumOf(InstalledApk::bytes)
}

data class InstalledApk(
    val path: String,
    val splitName: String?,
    val bytes: Long,
)

enum class AppFilter {
    User,
    System,
    All,
}

fun filterApps(
    apps: List<InstalledApp>,
    filter: AppFilter,
    query: String,
): List<InstalledApp> {
    val normalizedQuery = query.trim()
    return apps.filter { app ->
        val matchesFilter = when (filter) {
            AppFilter.User -> !app.isSystemApp
            AppFilter.System -> app.isSystemApp
            AppFilter.All -> true
        }
        val matchesQuery = normalizedQuery.isEmpty() ||
            app.label.contains(normalizedQuery, ignoreCase = true) ||
            app.packageName.contains(normalizedQuery, ignoreCase = true)
        matchesFilter && matchesQuery
    }
}

