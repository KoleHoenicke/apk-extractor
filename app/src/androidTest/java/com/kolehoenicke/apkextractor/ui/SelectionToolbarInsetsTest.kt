@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.ui.test.ExperimentalTestApi::class,
)

package com.kolehoenicke.apkextractor.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.kolehoenicke.apkextractor.ui.theme.ApkExtractorTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SelectionToolbarInsetsTest {
    @get:Rule val rule = createComposeRule()

    private data class Case(val name: String, val fontScale: Float, val rtl: Boolean, val xr: Boolean, val count: Int)

    @Test fun renderedButtonHasEqualTopBottomAndEndInsets() {
        val cases = listOf(
            Case("normal", 1f, false, false, 2),
            Case("large-text", 2f, false, false, 123456),
            Case("rtl-large-text", 2f, true, false, 123456),
            Case("xr", 1f, false, true, 2),
            Case("xr-large-text", 2f, false, true, 123456),
        )
        val current = mutableStateOf(cases.first())
        var toolbarColor = 0
        var buttonColor = 0
        rule.setContent {
            val case = current.value
            val density = LocalDensity.current.density
            CompositionLocalProvider(
                LocalDensity provides Density(density, case.fontScale),
                LocalLayoutDirection provides if (case.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                LocalXrUi provides XrUi(case.xr, false),
                LocalMinimumInteractiveComponentSize provides if (case.xr) 56.dp else 48.dp,
            ) {
                ApkExtractorTheme(darkTheme = false) {
                    toolbarColor = FloatingToolbarDefaults.vibrantFloatingToolbarColors().toolbarContainerColor.toArgb()
                    buttonColor = ButtonDefaults.buttonColors().containerColor.toArgb()
                    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                        SelectionToolbar(case.count, true, {}, {}, Modifier.testTag("toolbar"))
                    }
                }
            }
        }
        cases.forEach { case ->
            rule.runOnIdle { current.value = case }
            rule.waitForIdle()
            val bitmap = rule.onNodeWithTag("toolbar").captureToImage().asAndroidBitmap()
            val directory = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)!!
            File(directory, "toolbar-insets-${case.name}.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            // Measure the actual filled pixels. Touch-target bounds hid the original 40dp
            // button / 48dp target mismatch, so semantic bounds are insufficient here.
            val outer = colorBounds(bitmap, toolbarColor)
            val button = colorBounds(bitmap, buttonColor)
            val top = button.top - outer.top
            val bottom = outer.bottom - button.bottom
            val end = if (case.rtl) button.left - outer.left else outer.right - button.right
            assertTrue("${case.name}: button must be inset", minOf(top, bottom, end) > 0)
            assertEquals("${case.name}: top/end gap", top.toFloat(), end.toFloat(), 2f)
            assertEquals("${case.name}: bottom/end gap", bottom.toFloat(), end.toFloat(), 2f)
        }
    }

    private fun colorBounds(bitmap: Bitmap, color: Int): android.graphics.Rect {
        var left = bitmap.width
        var top = bitmap.height
        var right = -1
        var bottom = -1
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            if (bitmap.getPixel(x, y) == color) {
                left = minOf(left, x)
                right = maxOf(right, x)
                top = minOf(top, y)
                bottom = maxOf(bottom, y)
            }
        }
        assertTrue("Filled color must be visible", right >= left && bottom >= top)
        return android.graphics.Rect(left, top, right, bottom)
    }
}
