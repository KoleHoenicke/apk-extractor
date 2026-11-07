package com.kolehoenicke.apkextractor.ui

import androidx.compose.foundation.ScrollIndicatorState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.nonInteractiveScrollbar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.kolehoenicke.apkextractor.ui.theme.ApkExtractorTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Exercises real drawing at the thumb's minimum/maximum crossover, rather than only the arithmetic. */
class ScrollbarCrashTest {
    @get:Rule val rule = createComposeRule()

    @Test fun regularScrollbarSurvivesShortTracksResizingAndRtl() = verifyScrollbar(lazy = false)

    @Test fun lazyScrollbarSurvivesShortTracksResizingAndRtl() = verifyScrollbar(lazy = true)

    private fun verifyScrollbar(lazy: Boolean) {
        val trackLength = mutableStateOf(360f)
        val inset = mutableStateOf(2f)
        val direction = mutableStateOf(LayoutDirection.Ltr)
        var scrolled = false
        rule.setContent {
            ApkExtractorTheme {
                CompositionLocalProvider(LocalLayoutDirection provides direction.value) {
                    val height = (trackLength.value + 2 * inset.value).dp
                    if (lazy) {
                        val state = rememberLazyListState()
                        LaunchedEffect(Unit) {
                            state.scrollToItem(4, 1)
                            scrolled = true
                        }
                        LazyColumn(
                            state = state,
                            modifier = Modifier.requiredSize(200.dp, height)
                                .testScrollbar(state.scrollIndicatorState!!, inset.value)
                                .testTag("scrollbar"),
                        ) {
                            items(150) { Text("Installed app $it", Modifier.requiredSize(200.dp, 48.dp)) }
                        }
                    } else {
                        val state = rememberScrollState()
                        LaunchedEffect(Unit) {
                            state.scrollTo(300)
                            scrolled = true
                        }
                        Column(
                            Modifier.requiredSize(200.dp, height)
                                .testScrollbar(state.scrollIndicatorState!!, inset.value)
                                .verticalScroll(state)
                                .testTag("scrollbar"),
                        ) {
                            repeat(150) { Text("Exported app $it", Modifier.requiredSize(200.dp, 48.dp)) }
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        rule.runOnIdle { assertTrue("Scrollbar must draw after a nonzero scroll offset", scrolled) }
        // Include the exact 24dp lower bound, the crashing 24..26.67dp interval,
        // and both sides. 48dp also models the app list's larger toolbar inset.
        for (layoutDirection in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
            for (trackInset in listOf(2f, 48f)) {
                for (length in listOf(360f, 27f, 26.5f, 25f, 24f, 23f, 0f, 25f, 360f)) {
                    rule.runOnIdle {
                        direction.value = layoutDirection
                        inset.value = trackInset
                        trackLength.value = length
                    }
                    rule.onNodeWithTag("scrollbar")
                        .assertHeightIsEqualTo((length + 2 * trackInset).dp)
                        .captureToImage()
                }
            }
        }
    }

    @Composable
    private fun Modifier.testScrollbar(state: ScrollIndicatorState, inset: Float): Modifier =
        if (InstrumentationRegistry.getArguments().getString("scrollbarBaseline") == "true") {
            // Optional reproduction mode deliberately uses the affected library defaults.
            nonInteractiveScrollbar(state, Orientation.Vertical, isFadeEnabled = false,
                mainAxisTrackInset = inset.dp)
        } else {
            appScrollbar(state, isFadeEnabled = false, mainAxisTrackInset = inset.dp)
        }
}
