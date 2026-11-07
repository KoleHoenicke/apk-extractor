package com.kolehoenicke.apkextractor.data

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

class OutputFolderStore(context: Context) {
    private val preferences = context.getSharedPreferences("output", Context.MODE_PRIVATE)

    fun get(): Uri? = preferences.getString(KEY_URI, null)?.let(Uri::parse)

    fun observe() = callbackFlow {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_URI || key == null) trySend(get())
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        trySend(get())
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    fun set(uri: Uri) {
        preferences.edit { putString(KEY_URI, uri.toString()) }
    }

    fun clear() {
        preferences.edit { remove(KEY_URI) }
    }

    private companion object {
        const val KEY_URI = "folder_uri"
    }
}
