package com.kolehoenicke.apkextractor.data

private val unsafeFileNameCharacters = Regex("[^\\p{L}\\p{M}\\p{N}._ -]+")
private val repeatedWhitespace = Regex("\\s+")

fun safeFileStem(value: String): String {
    val cleaned = value
        .replace(unsafeFileNameCharacters, "")
        .replace(repeatedWhitespace, " ")
        .trim(' ', '.')
    val stem = cleaned.ifBlank { "app" }
    return stem.substring(0, stem.offsetByCodePoints(0, minOf(80, stem.codePointCount(0, stem.length))))
}

fun exportFileName(app: InstalledApp): String {
    val version = truncateUtf8(safeFileStem(app.versionName), 60)
    val suffix = if (app.isSplit) "-split-apks.zip" else ".apk"
    val stem = truncateUtf8(safeFileStem(app.label), 240 - version.toByteArray(Charsets.UTF_8).size - suffix.length - 1)
    return if (app.isSplit) {
        "$stem-$version-split-apks.zip"
    } else {
        "$stem-$version.apk"
    }
}


private fun truncateUtf8(value: String, maxBytes: Int): String {
    var end = 0
    var bytes = 0
    while (end < value.length) {
        val next = value.offsetByCodePoints(end, 1)
        val count = value.substring(end, next).toByteArray(Charsets.UTF_8).size
        if (bytes + count > maxBytes) break
        bytes += count
        end = next
    }
    return value.substring(0, end)
}
