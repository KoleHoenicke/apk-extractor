package com.kolehoenicke.apkextractor

import android.content.ComponentName
import android.content.Context
import android.content.Intent

internal fun createNewWindowIntent(context: Context): Intent =
    Intent.makeMainActivity(ComponentName(context, MainActivity::class.java)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
            Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
    }
