package com.kolehoenicke.apkextractor.ui

import androidx.compose.foundation.ScrollIndicatorState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.material3.NonInteractiveScrollbarDefaults
import androidx.compose.material3.nonInteractiveScrollbar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

@Composable
internal fun Modifier.appScrollbar(
    state: ScrollIndicatorState,
    isFadeEnabled: Boolean = true,
    mainAxisTrackInset: Dp = NonInteractiveScrollbarDefaults.MainAxisTrackInset,
): Modifier = nonInteractiveScrollbar(
    state = state,
    orientation = Orientation.Vertical,
    // Material 3 alpha28 skips tracks shorter than the 24dp minimum thumb, but
    // its default 0.9 maximum still creates an empty range on 24..26.67dp tracks.
    // A full-track maximum keeps that range valid, including while resizing.
    thumbMaxLengthFraction = 1f,
    isFadeEnabled = isFadeEnabled,
    mainAxisTrackInset = mainAxisTrackInset,
)
