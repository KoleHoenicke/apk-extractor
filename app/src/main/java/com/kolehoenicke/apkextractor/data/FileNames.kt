package com.kolehoenicke.apkextractor.data

private val unsafeFileNameCharacters = Regex("[^A-Za-z0-9._ -]+")
private val repeatedWhitespace = Regex("\\s+")

fun safeFileStem(value: String): String {
    val cleaned = value
        .replace(unsafeFileNameCharacters, "")
        .replace(repeatedWhitespace, " ")
        .trim(' ', '.')
    return cleaned.ifBlank { "app" }.take(80)
}

fun exportFileName(app: InstalledApp): String {
    val stem = safeFileStem(app.label)
    val version = safeFileStem(app.versionName)
    return if (app.isSplit) {
        "$stem-$version-split-apks.zip"
    } else {
        "$stem-$version.apk"
    }
}

