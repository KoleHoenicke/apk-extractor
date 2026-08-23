package com.kolehoenicke.apkextractor.data

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PaintFlagsDrawFilter
import android.graphics.Rect
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.InsetDrawable
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
import kotlin.math.sqrt

/**
 * Renders installed-app icons with the current Pixel Launcher compatibility geometry.
 *
 * Genuine adaptive icons already contain maskable background and foreground layers, so Android
 * draws them directly with the device-configured mask. Pixel Launcher currently draws those icons
 * full bleed. Legacy icons are placed on an opaque white adaptive background and use Launcher's
 * fixed legacy-artwork scale before the same device mask is applied.
 *
 * The wrapper geometry is adapted from Android's Launcher3 BaseIconFactory, licensed under
 * Apache 2.0. The full-bleed branch and constants were also checked against BaseIconFactory in the
 * Pixel 11's installed NexusLauncherRelease build.
 */
internal object LauncherIconRenderer {
    private const val LegacyArtworkScale = 0.7f
    private const val MaxSquareAreaFactor = 375f / 576f

    fun render(source: Drawable, resources: Resources, size: Int): Bitmap {
        require(size > 0) { "Icon size must be positive" }

        val icon = source.constantState?.newDrawable(resources)?.mutate() ?: source.mutate()
        val displayIcon = if (icon is AdaptiveIconDrawable) {
            icon
        } else {
            wrapLegacyIcon(icon)
        }

        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap).apply {
            drawFilter = PaintFlagsDrawFilter(Paint.DITHER_FLAG, Paint.FILTER_BITMAP_FLAG)
        }
        val oldBounds = Rect(displayIcon.bounds)
        try {
            // Pixel Launcher's current full-bleed path uses the complete output bounds and omits
            // its launcher shadow. AdaptiveIconDrawable itself applies the configured mask.
            displayIcon.setBounds(0, 0, size, size)
            displayIcon.draw(canvas)
        } finally {
            displayIcon.bounds = oldBounds
        }
        return bitmap
    }

    private fun wrapLegacyIcon(icon: Drawable): AdaptiveIconDrawable {
        val legacyScale =
            sqrt(MaxSquareAreaFactor) * LegacyArtworkScale /
                (1f + 2f * AdaptiveIconDrawable.getExtraInsetFraction())

        return AdaptiveIconDrawable(
            Color.WHITE.toDrawable(),
            createScaledDrawable(icon, legacyScale),
        ).apply {
            // Establish the device-configured mask exactly as Launcher does for its wrapper.
            setBounds(0, 0, 1, 1)
        }
    }

    private fun createScaledDrawable(icon: Drawable, scale: Float): Drawable {
        val intrinsicWidth = icon.intrinsicWidth
        val intrinsicHeight = icon.intrinsicHeight
        var scaleX = scale
        var scaleY = scale

        if (intrinsicHeight > intrinsicWidth && intrinsicWidth > 0) {
            scaleX *= intrinsicWidth.toFloat() / intrinsicHeight
        } else if (intrinsicWidth > intrinsicHeight && intrinsicHeight > 0) {
            scaleY *= intrinsicHeight.toFloat() / intrinsicWidth
        }

        val horizontalInset = (1f - scaleX) / 2f
        val verticalInset = (1f - scaleY) / 2f
        return InsetDrawable(
            icon,
            horizontalInset,
            verticalInset,
            horizontalInset,
            verticalInset,
        )
    }
}
