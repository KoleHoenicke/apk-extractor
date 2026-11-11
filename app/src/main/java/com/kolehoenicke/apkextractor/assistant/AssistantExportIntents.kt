package com.kolehoenicke.apkextractor.assistant

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.kolehoenicke.apkextractor.MainActivity

internal fun createAssistantExportIntent(context: Context, token: String): Intent =
    Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        data = Uri.Builder().scheme("apk-extractor").authority("prepare-export")
            .appendPath(token).build()
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }

internal fun assistantExportToken(intent: Intent?): String? {
    val uri = intent?.data ?: return null
    return uri.pathSegments.singleOrNull()?.takeIf {
        intent.action == Intent.ACTION_VIEW && uri.scheme == "apk-extractor" &&
            uri.authority == "prepare-export" && it.length == 36
    }
}

internal fun assistantExportPendingIntent(context: Context, token: String): PendingIntent =
    PendingIntent.getActivity(context, 0, createAssistantExportIntent(context, token),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT)
