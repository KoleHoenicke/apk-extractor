package com.kolehoenicke.apkextractor.assistant

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/** Short-lived, single-use selections. Tokens and package lists stay in app-private, non-backed-up storage. */
internal class AssistantExportDraftStore(
    context: Context,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "assistant-export-drafts.json"))

    fun create(packageNames: List<String>): String = synchronized(Lock) {
        require(packageNames.isNotEmpty() && packageNames.size <= AssistantAppQuery.MaxPreparedApps)
        val token = UUID.randomUUID().toString()
        val drafts = read()
        val kept = JSONArray()
        // Bound storage even if an assistant repeatedly prepares selections without opening them.
        for (index in (drafts.length() - MaxDrafts + 1).coerceAtLeast(0) until drafts.length()) {
            kept.put(drafts.getJSONObject(index))
        }
        kept.put(JSONObject().put("token", token).put("created", nowMillis())
            .put("packages", JSONArray(packageNames.distinct())))
        write(kept)
        token
    }

    fun consume(token: String): List<String>? = synchronized(Lock) {
        val drafts = read()
        val kept = JSONArray()
        var packages: List<String>? = null
        for (index in 0 until drafts.length()) {
            val draft = drafts.getJSONObject(index)
            if (draft.getString("token") == token) {
                val names = draft.getJSONArray("packages")
                packages = (0 until names.length()).map(names::getString)
            } else {
                kept.put(draft)
            }
        }
        write(kept)
        packages
    }

    private fun read(): JSONArray = runCatching {
        val all = JSONArray(file.openRead().bufferedReader().use { it.readText() })
        val kept = JSONArray()
        for (index in 0 until all.length()) {
            val draft = all.getJSONObject(index)
            val age = nowMillis() - draft.getLong("created")
            if (age in 0 until DraftLifetimeMillis) kept.put(draft)
        }
        kept
    }.getOrElse { JSONArray() }

    private fun write(drafts: JSONArray) {
        val stream = file.startWrite()
        try {
            stream.write(drafts.toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (failure: Throwable) {
            file.failWrite(stream)
            throw failure
        }
    }

    private companion object {
        val Lock = Any()
        const val DraftLifetimeMillis = 15 * 60 * 1000L
        const val MaxDrafts = 8
    }
}
