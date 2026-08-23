package com.kolehoenicke.apkextractor.ui

import java.util.Locale

fun formatBytes(bytes: Long): String = when {
    bytes < 1_000 -> "$bytes B"
    bytes < 1_000_000 -> formatUnit(bytes / 1_000.0, "KB")
    bytes < 1_000_000_000 -> formatUnit(bytes / 1_000_000.0, "MB")
    else -> formatUnit(bytes / 1_000_000_000.0, "GB")
}

private fun formatUnit(value: Double, unit: String): String {
    val decimals = if (value >= 100) 0 else 1
    return String.format(Locale.getDefault(), "%.${decimals}f %s", value, unit)
}

