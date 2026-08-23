package com.kolehoenicke.apkextractor.data

import android.content.Context
import android.net.Uri
import androidx.core.content.edit

class OutputFolderStore(context: Context) {
    private val preferences = context.getSharedPreferences("output", Context.MODE_PRIVATE)

    fun get(): Uri? = preferences.getString(KEY_URI, null)?.let(Uri::parse)

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
