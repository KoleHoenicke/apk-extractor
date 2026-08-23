package com.kolehoenicke.apkextractor.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.kolehoenicke.apkextractor.R
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class ExportedFile(
    val uri: Uri,
    val displayName: String,
    val isSplitArchive: Boolean,
)

class ApkExporter(private val context: Context) {
    private val destinationMutex = Mutex()

    suspend fun export(
        app: InstalledApp,
        outputTree: Uri,
        onProgress: (Float) -> Unit,
    ): ExportedFile = withContext(Dispatchers.IO) {
        val directory = DocumentFile.fromTreeUri(context, outputTree)
            ?.takeIf(DocumentFile::canWrite)
            ?: error(context.getString(R.string.error_folder_unavailable))

        val requestedName = exportFileName(app)
        val mimeType = if (app.isSplit) ZIP_MIME else APK_MIME
        val (displayName, document) = destinationMutex.withLock {
            val displayName = uniqueName(directory, requestedName)
            val document = directory.createFile(mimeType, displayName)
                ?: error(context.getString(R.string.error_create_export))
            displayName to document
        }

        try {
            val output = context.contentResolver.openOutputStream(document.uri, "w")
                ?: error(context.getString(R.string.error_open_export))
            output.use { rawOutput ->
                if (app.isSplit) {
                    writeSplitArchive(
                        app = app,
                        output = ZipOutputStream(BufferedOutputStream(rawOutput)),
                        onProgress = onProgress,
                    )
                } else {
                    BufferedOutputStream(rawOutput).use { bufferedOutput ->
                        copyFile(
                            source = File(app.apkFiles.single().path),
                            output = bufferedOutput,
                            totalBytes = app.totalBytes,
                            bytesAlreadyCopied = 0,
                            onProgress = onProgress,
                        )
                    }
                }
            }
            onProgress(1f)
            ExportedFile(document.uri, document.name ?: displayName, app.isSplit)
        } catch (failure: Throwable) {
            document.delete()
            throw failure
        }
    }

    private fun writeSplitArchive(
        app: InstalledApp,
        output: ZipOutputStream,
        onProgress: (Float) -> Unit,
    ) {
        output.use { zip ->
            var copied = 0L
            val apkManifest = JSONArray()
            app.apkFiles.forEachIndexed { index, apk ->
                val entryName = if (index == 0) "base.apk" else uniqueSplitName(apk, index)
                val digest = MessageDigest.getInstance("SHA-256")
                zip.putNextEntry(ZipEntry(entryName))
                copied = copyFile(
                    source = File(apk.path),
                    output = zip,
                    totalBytes = app.totalBytes,
                    bytesAlreadyCopied = copied,
                    digest = digest,
                    onProgress = onProgress,
                )
                zip.closeEntry()
                apkManifest.put(
                    JSONObject()
                        .put("file", entryName)
                        .put("splitName", apk.splitName ?: JSONObject.NULL)
                        .put("bytes", apk.bytes)
                        .put("sha256", digest.digest().toHex()),
                )
            }

            val manifest = JSONObject()
                .put("formatVersion", 1)
                .put("appName", app.label)
                .put("packageName", app.packageName)
                .put("versionName", app.versionName)
                .put("versionCode", app.versionCode)
                .put("exportedAt", Instant.now().toString())
                .put("apks", apkManifest)

            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifest.toString(2).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    }

    private fun copyFile(
        source: File,
        output: java.io.OutputStream,
        totalBytes: Long,
        bytesAlreadyCopied: Long,
        digest: MessageDigest? = null,
        onProgress: (Float) -> Unit,
    ): Long {
        var copied = bytesAlreadyCopied
        var lastReported = copied
        BufferedInputStream(source.inputStream()).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                output.write(buffer, 0, count)
                digest?.update(buffer, 0, count)
                copied += count
                if (copied - lastReported >= PROGRESS_STEP_BYTES) {
                    onProgress(progress(copied, totalBytes))
                    lastReported = copied
                }
            }
        }
        onProgress(progress(copied, totalBytes))
        return copied
    }

    private fun uniqueName(directory: DocumentFile, requestedName: String): String {
        if (directory.findFile(requestedName) == null) return requestedName
        val dotIndex = requestedName.lastIndexOf('.')
        val stem = requestedName.take(dotIndex.coerceAtLeast(0))
        val extension = requestedName.substring(dotIndex.coerceAtLeast(0))
        var suffix = 2
        while (directory.findFile("$stem ($suffix)$extension") != null) suffix += 1
        return "$stem ($suffix)$extension"
    }

    private fun uniqueSplitName(apk: InstalledApk, index: Int): String {
        val sourceName = File(apk.path).name
        return sourceName.takeIf { it.endsWith(".apk", ignoreCase = true) }
            ?: "split-$index.apk"
    }

    private fun progress(copied: Long, total: Long): Float =
        if (total <= 0) 0f else (copied.toDouble() / total.toDouble()).toFloat().coerceIn(0f, 1f)

    private fun ByteArray.toHex(): String =
        joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private companion object {
        const val APK_MIME = "application/vnd.android.package-archive"
        const val ZIP_MIME = "application/zip"
        const val PROGRESS_STEP_BYTES = 512 * 1024
    }
}
