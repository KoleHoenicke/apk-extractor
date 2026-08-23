package com.kolehoenicke.apkextractor.data

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LauncherIconRendererTest {
    private val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources

    @Test
    fun legacyArtworkIsInsetInsideAWhiteAdaptiveWrapper() {
        val bitmap = LauncherIconRenderer.render(
            source = SolidDrawable(Color.BLACK),
            resources = resources,
            size = IconSize,
        )

        val opaqueBounds = visibleBounds(bitmap) { color ->
            Color.alpha(color) > 0
        }
        val artworkBounds = visibleBounds(bitmap) { color ->
            Color.alpha(color) > 239 && Color.red(color) < 16 &&
                Color.green(color) < 16 && Color.blue(color) < 16
        }
        val whitePixels = countPixels(bitmap) { color ->
            Color.alpha(color) > 0 && Color.red(color) > 239 &&
                Color.green(color) > 239 && Color.blue(color) > 239
        }

        assertTrue("The legacy wrapper should expose a white background", whitePixels > 100)
        assertTrue(artworkBounds.left > opaqueBounds.left)
        assertTrue(artworkBounds.top > opaqueBounds.top)
        assertTrue(artworkBounds.right < opaqueBounds.right)
        assertTrue(artworkBounds.bottom < opaqueBounds.bottom)
        assertTrue("Pixel legacy artwork scale should match Launcher", artworkBounds.pixelWidth in 70..74)
        assertTrue("Pixel legacy artwork scale should match Launcher", artworkBounds.pixelHeight in 70..74)
    }

    @Test
    fun adaptiveIconLayersUsePixelLauncherFullBleedGeometry() {
        val bitmap = LauncherIconRenderer.render(
            source = AdaptiveIconDrawable(
                ColorDrawable(Color.GREEN),
                ColorDrawable(Color.TRANSPARENT),
            ),
            resources = resources,
            size = IconSize,
        )

        val greenPixels = countPixels(bitmap) { color ->
            Color.alpha(color) > 0 && Color.green(color) > 239 &&
                Color.red(color) < 16 && Color.blue(color) < 16
        }
        val whitePixels = countPixels(bitmap) { color ->
            Color.alpha(color) > 0 && Color.red(color) > 239 &&
                Color.green(color) > 239 && Color.blue(color) > 239
        }
        val greenBounds = visibleBounds(bitmap) { color ->
            Color.alpha(color) > 239 && Color.green(color) > 239 &&
                Color.red(color) < 16 && Color.blue(color) < 16
        }
        val shadowPixels = countPixels(bitmap) { color ->
            Color.alpha(color) in 1..64 && Color.red(color) < 16 &&
                Color.green(color) < 16 && Color.blue(color) < 16
        }

        assertTrue("Adaptive background should remain visible", greenPixels > 100)
        assertEquals("Adaptive icons must not receive a legacy wrapper", 0, whitePixels)
        assertEquals("Pixel mask should touch the left output edge", 0, greenBounds.left)
        assertEquals("Pixel mask should touch the top output edge", 0, greenBounds.top)
        assertEquals("Pixel mask should touch the right output edge", IconSize - 1, greenBounds.right)
        assertEquals("Pixel mask should touch the bottom output edge", IconSize - 1, greenBounds.bottom)
        assertEquals("Pixel's full-bleed branch omits launcher shadow", 0, shadowPixels)
    }

    private fun visibleBounds(
        width: Int,
        height: Int,
        predicate: (Int) -> Boolean,
    ): Rect {
        var left = width
        var top = height
        var right = -1
        var bottom = -1
        for (y in 0 until height) {
            for (x in 0 until width) {
                val color = currentBitmapPixel(x, y)
                if (predicate(color)) {
                    left = minOf(left, x)
                    top = minOf(top, y)
                    right = maxOf(right, x)
                    bottom = maxOf(bottom, y)
                }
            }
        }
        return Rect(left, top, right, bottom)
    }

    private lateinit var bitmapPixels: IntArray
    private var bitmapWidth = 0

    private fun countPixels(width: Int, height: Int, predicate: (Int) -> Boolean): Int {
        var count = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (predicate(currentBitmapPixel(x, y))) count++
            }
        }
        return count
    }

    private fun currentBitmapPixel(x: Int, y: Int): Int = bitmapPixels[y * bitmapWidth + x]

    private fun preparePixels(bitmap: android.graphics.Bitmap) {
        bitmapWidth = bitmap.width
        bitmapPixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(
            bitmapPixels,
            0,
            bitmap.width,
            0,
            0,
            bitmap.width,
            bitmap.height,
        )
    }

    private fun visibleBounds(
        bitmap: android.graphics.Bitmap,
        predicate: (Int) -> Boolean,
    ): Rect {
        preparePixels(bitmap)
        return visibleBounds(bitmap.width, bitmap.height, predicate)
    }

    private fun countPixels(
        bitmap: android.graphics.Bitmap,
        predicate: (Int) -> Boolean,
    ): Int {
        preparePixels(bitmap)
        return countPixels(bitmap.width, bitmap.height, predicate)
    }

    private class SolidDrawable(color: Int) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

        override fun draw(canvas: Canvas) {
            canvas.drawRect(bounds, paint)
        }

        override fun setAlpha(alpha: Int) {
            paint.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            paint.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Android")
        override fun getOpacity(): Int = PixelFormat.OPAQUE

        override fun getIntrinsicWidth(): Int = IconSize

        override fun getIntrinsicHeight(): Int = IconSize
    }

    private companion object {
        const val IconSize = 128
    }
}

private val Rect.pixelWidth: Int
    get() = right - left + 1

private val Rect.pixelHeight: Int
    get() = bottom - top + 1
