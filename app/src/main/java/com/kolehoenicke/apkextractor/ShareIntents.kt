package com.kolehoenicke.apkextractor

import android.content.ClipData
import android.content.Context
import android.content.Intent
import com.kolehoenicke.apkextractor.data.ExportedFile

internal fun createShareChooserIntent(
    context: Context,
    files: List<ExportedFile>,
): Intent? {
    val exportedClip = createExportClipData(files) ?: return null
    val mimeType = exportedClip.description.getMimeType(0)
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
        clipData = exportedClip
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    return Intent.createChooser(sendIntent, context.getString(R.string.share_apk)).apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

internal fun createExportClipData(files: List<ExportedFile>): ClipData? {
    if (files.isEmpty()) return null
    val mimeType = when {
        files.all(ExportedFile::isSplitArchive) -> "application/zip"
        files.none(ExportedFile::isSplitArchive) -> "application/vnd.android.package-archive"
        else -> "application/octet-stream"
    }
    // Some document providers reject stream-type queries. Export MIME types are already known.
    return ClipData(files.first().displayName, arrayOf(mimeType), ClipData.Item(files.first().uri)).apply {
        files.drop(1).forEach { addItem(ClipData.Item(it.uri)) }
    }
}
