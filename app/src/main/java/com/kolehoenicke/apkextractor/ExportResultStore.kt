package com.kolehoenicke.apkextractor

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.kolehoenicke.apkextractor.data.ExportedFile
import com.kolehoenicke.apkextractor.data.InstalledApp
import org.json.JSONArray
import org.json.JSONObject

/** One local journal, retained so a dismissed notification does not lose the export result. */
class ExportResultStore(private val context: Context) {
    private val preferences = context.getSharedPreferences("last_export", Context.MODE_PRIVATE)

    fun begin(apps: List<InstalledApp>, folder: Uri) = synchronized(Lock) {
        val items = JSONArray()
        apps.forEach { items.put(JSONObject().put("package", it.packageName).put("label", it.label)) }
        write(JSONObject().put("active", true).put("folder", folder.toString()).put("items", items))
    }

    fun created(packageName: String, uri: Uri) = update(packageName) { it.put("partial", uri.toString()) }

    fun saved(packageName: String, file: ExportedFile) = update(packageName) {
        it.remove("partial")
        it.put("uri", file.uri.toString()).put("name", file.displayName).put("split", file.isSplitArchive)
    }

    fun failed(packageName: String, reason: String) = update(packageName) {
        it.put("error", reason)
    }

    fun finish() = synchronized(Lock) {
        load()?.let { write(it.put("active", false)) }
    }

    /** Only called off the main thread when no extraction is running in this process. */
    fun recoverInterrupted() = synchronized(Lock) {
        val json = load() ?: return@synchronized
        if (!json.optBoolean("active")) return@synchronized
        val items = json.getJSONArray("items")
        for (index in 0 until items.length()) {
            val item = items.getJSONObject(index)
            if (!item.has("uri")) {
                val removed = if (item.has("partial")) runCatching {
                    val doc = DocumentFile.fromSingleUri(context, Uri.parse(item.getString("partial")))
                    doc == null || !doc.exists() || doc.delete()
                }.getOrDefault(false) else true
                item.put("error", context.getString(if (removed) R.string.export_interrupted else R.string.export_interrupted_partial))
                if (removed) item.remove("partial")
            }
        }
        write(json.put("active", false))
    }

    fun read(): UiEvent.ExportFinished? = synchronized(Lock) {
        val json = load() ?: return@synchronized null
        if (json.optBoolean("active")) return@synchronized null
        val files = mutableListOf<ExportedFile>()
        val failures = mutableListOf<ExportFailure>()
        val items = json.getJSONArray("items")
        for (index in 0 until items.length()) {
            val item = items.getJSONObject(index)
            if (item.has("uri")) files += ExportedFile(Uri.parse(item.getString("uri")), item.getString("name"), item.optBoolean("split"), item.optString("label", item.getString("name")))
            else failures += ExportFailure(item.getString("label"), item.optString("error", context.getString(R.string.export_interrupted)), item.getString("package"))
        }
        UiEvent.ExportFinished(files, items.length(), failures, Uri.parse(json.getString("folder")))
    }

    private fun update(packageName: String, action: (JSONObject) -> Unit) = synchronized(Lock) {
        val json = load() ?: return@synchronized
        val items = json.getJSONArray("items")
        for (index in 0 until items.length()) {
            val item = items.getJSONObject(index)
            if (item.getString("package") == packageName) action(item)
        }
        write(json)
    }

    private fun load(): JSONObject? = preferences.getString("result", null)?.let {
        runCatching { JSONObject(it) }.getOrNull()
    }

    // Commit before a file is treated as safely finished, so process death has a recovery record.
    @android.annotation.SuppressLint("UseKtx") // The Boolean commit result is required for journal durability.
    private fun write(json: JSONObject) {
        check(preferences.edit().putString("result", json.toString()).commit()) { "Could not save export status" }
    }

    private companion object { val Lock = Any() }
}
