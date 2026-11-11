package com.kolehoenicke.apkextractor.data

import android.content.Context
import android.content.res.Configuration
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A bounded process cache. Package updates and resource changes use a new key. */
internal data class AppIconKey(
    val packageName: String,
    val sourcePath: String?,
    val versionCode: Long,
    val lastUpdateTime: Long,
    val configuration: Configuration,
)

internal object AppIconLoader {
    private val icons = object : LruCache<AppIconKey, ImageBitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: AppIconKey, value: ImageBitmap) = value.width * value.height * 4
    }

    fun cached(key: AppIconKey): ImageBitmap? = icons.get(key)

    suspend fun load(context: Context, key: AppIconKey): ImageBitmap? = withContext(Dispatchers.IO) {
        icons.get(key) ?: runCatching {
            catalogTrace("AppCatalog.visibleIcon") {
                LauncherIconRenderer.render(
                    source = context.packageManager.getApplicationIcon(key.packageName),
                    resources = context.resources,
                    size = 128,
                ).asImageBitmap()
            }
        }.getOrNull()?.also { icons.put(key, it) }
    }
}
