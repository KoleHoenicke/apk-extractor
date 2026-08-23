package com.kolehoenicke.apkextractor.ui

import androidx.window.core.layout.WindowSizeClass
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveLayoutTest {
    @Test
    fun `compact windows use full screen search`() {
        assertFalse(shouldUseDockedSearch(WindowSizeClass(minWidthDp = 599, minHeightDp = 800)))
    }

    @Test
    fun `medium windows switch to docked search`() {
        assertTrue(shouldUseDockedSearch(WindowSizeClass(minWidthDp = 600, minHeightDp = 800)))
    }

    @Test
    fun `expanded windows keep docked search`() {
        assertTrue(shouldUseDockedSearch(WindowSizeClass(minWidthDp = 1280, minHeightDp = 800)))
    }

    @Test
    fun `search fills compact content width`() {
        assertEquals(380.dp, adaptiveSearchWidth(412.dp))
    }

    @Test
    fun `search fills medium content width`() {
        assertEquals(568.dp, adaptiveSearchWidth(600.dp))
    }

    @Test
    fun `search stops growing at Material maximum`() {
        assertEquals(720.dp, adaptiveSearchWidth(1280.dp))
    }
}
