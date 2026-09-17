package com.kolehoenicke.apkextractor

import android.content.ActivityNotFoundException

// Launch directly: a resolver check can race with package changes and package visibility.
internal inline fun tryLaunchExternalActivity(launch: () -> Unit): Boolean = try {
    launch()
    true
} catch (_: ActivityNotFoundException) {
    false
} catch (_: SecurityException) {
    false
}
