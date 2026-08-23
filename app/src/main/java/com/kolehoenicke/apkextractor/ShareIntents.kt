package com.kolehoenicke.apkextractor

import android.content.ClipData
import android.content.Context
import android.content.Intent
import com.kolehoenicke.apkextractor.data.ExportedFile

internal fun createShareChooserIntent(
    context: Context,
    files: List<ExportedFile>,
): Intent? {
    if (files.isEmpty()) return null

    val mimeType = when {
        files.all(ExportedFile::isSplitArchive) -> "application/zip"
        files.none(ExportedFile::isSplitArchive) -> "application/vnd.android.package-archive"
        else -> "application/octet-stream"
    }
    val sendIntent = Intent(
        if (files.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE,
    ).apply {
        type = mimeType
        if (files.size == 1) {
            putExtra(Intent.EXTRA_STREAM, files.single().uri)
        } else {
            putParcelableArrayListExtra(
                Intent.EXTRA_STREAM,
                ArrayList(files.map(ExportedFile::uri)),
            )
        }
        clipData = ClipData.newUri(
            context.contentResolver,
            files.first().displayName,
            files.first().uri,
        ).apply {
            files.drop(1).forEach { file -> addItem(ClipData.Item(file.uri)) }
        }
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    return Intent.createChooser(sendIntent, context.getString(R.string.share_apk)).apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

