package com.kolehoenicke.apkextractor.assistant

import androidx.appfunctions.AppFunctionSerializable
import com.kolehoenicke.apkextractor.data.InstalledApp

/** Filters combined with AND. An omitted filter imposes no restriction, except appType defaults to user. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class AssistantAppQuery(
    /** Case-insensitive substring of an app label or package name. Not a natural-language command. */
    val query: String? = null,
    /** Exact package identifiers from searchInstalledApps. Empty means no package restriction. */
    val packageNames: List<String> = emptyList(),
    /** user, system, or all. Omitted means user apps. Set all when looking up a known system package. */
    val appType: String? = null,
    /** Inclusive earliest last-update time, in Unix epoch milliseconds. Resolve relative dates using the user's timezone. */
    val updatedSinceMillis: Long? = null,
    /** Inclusive minimum combined size of all installed APK files, in bytes. */
    val minimumBytes: Long? = null,
    /** Inclusive maximum combined size of all installed APK files, in bytes. */
    val maximumBytes: Long? = null,
    /** true selects split APK installations, false selects single APK installations, omitted selects either. */
    val splitApks: Boolean? = null,
) {
    fun validate() {
        require(appType == null || appType in setOf("user", "system", "all")) {
            "appType must be user, system, or all."
        }
        require(query == null || query.length <= 200) { "query must be at most 200 characters." }
        require(packageNames.size <= MaxPreparedApps && packageNames.all { it.isNotBlank() && it.length <= 255 }) {
            "Supply at most $MaxPreparedApps nonblank package identifiers, each at most 255 characters."
        }
        require(updatedSinceMillis == null || updatedSinceMillis >= 0) { "updatedSinceMillis must not be negative." }
        require(minimumBytes == null || minimumBytes >= 0) { "minimumBytes must not be negative." }
        require(maximumBytes == null || maximumBytes >= 0) { "maximumBytes must not be negative." }
        require(minimumBytes == null || maximumBytes == null || minimumBytes <= maximumBytes) {
            "minimumBytes must not exceed maximumBytes."
        }
    }

    fun matches(app: InstalledApp): Boolean =
        when (appType ?: "user") {
            "user" -> !app.isSystemApp
            "system" -> app.isSystemApp
            else -> true
        } &&
            (query.isNullOrBlank() || app.label.contains(query.trim(), ignoreCase = true) ||
                app.packageName.contains(query.trim(), ignoreCase = true)) &&
            (packageNames.isEmpty() || app.packageName in packageNames) &&
            (updatedSinceMillis == null || app.lastUpdateTime >= updatedSinceMillis) &&
            (minimumBytes == null || app.totalBytes >= minimumBytes) &&
            (maximumBytes == null || app.totalBytes <= maximumBytes) &&
            (splitApks == null || app.isSplit == splitApks)

    companion object {
        const val MaxPreparedApps = 500
    }
}
