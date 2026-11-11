package com.kolehoenicke.apkextractor.ui

import androidx.compose.foundation.layout.heightIn

import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.kolehoenicke.apkextractor.R

/** Selection follows the visible order, including the current search, filter, and sort. */
internal fun selectionRange(visible: List<String>, anchor: String?, target: String): Set<String> {
    val end = visible.indexOf(target)
    if (end < 0) return emptySet()
    val start = visible.indexOf(anchor).takeIf { it >= 0 } ?: end
    return visible.subList(minOf(start, end), maxOf(start, end) + 1).toSet()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppActionButton(
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
        tooltip = { PlainTooltip { Text(description) } },
        state = rememberTooltipState(),
    ) {
        IconButton(onClick = onClick, enabled = enabled,
            modifier = modifier.semantics { contentDescription = description }
                .then(if (LocalXrUi.current.isXr) Modifier.size(56.dp) else Modifier)) { content() }
    }
}

@Composable
internal fun DesktopRowActions(
    modifier: Modifier,
    appLabel: String,
    selected: Boolean,
    enabled: Boolean,
    onExtract: () -> Unit,
    onSelect: () -> Unit,
    onRangeSelect: () -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    var menuVisible by remember { mutableStateOf(false) }
    var menuOffset by remember { mutableStateOf(DpOffset.Zero) }
    var rowSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val select by rememberUpdatedState(onSelect)
    val rangeSelect by rememberUpdatedState(onRangeSelect)

    Box(modifier) {
        content(
            Modifier
                .onSizeChanged { rowSize = it }
                .onPreviewKeyEvent { event ->
                    when {
                        !enabled -> false
                        event.key == Key.Spacebar -> {
                            if (event.type == KeyEventType.KeyUp) {
                                if (event.isShiftPressed) rangeSelect() else select()
                            }
                            true
                        }
                        event.key == Key.Menu || (event.key == Key.F10 && event.isShiftPressed) -> {
                            if (event.type == KeyEventType.KeyUp) {
                                menuOffset = DpOffset.Zero
                                menuVisible = true
                            }
                            true
                        }
                        else -> false
                    }
                }
                .pointerInput(enabled, density, layoutDirection) {
                    if (!enabled) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        val event = currentEvent
                        val secondary = event.buttons.isSecondaryPressed
                        val range = event.keyboardModifiers.isShiftPressed
                        val toggle = event.keyboardModifiers.isCtrlPressed || event.keyboardModifiers.isMetaPressed
                        if (down.type == PointerType.Mouse && (secondary || range || toggle)) {
                            down.consume()
                            val up = waitForUpOrCancellation(pass = PointerEventPass.Initial)
                            if (up != null) {
                                up.consume()
                                when {
                                    secondary -> {
                                        // DropdownMenu offsets start at the row's bottom/start corner.
                                        val x = if (layoutDirection == LayoutDirection.Ltr) up.position.x
                                            else rowSize.width - up.position.x
                                        menuOffset = with(density) {
                                            DpOffset(x.toDp(), (up.position.y - rowSize.height).toDp())
                                        }
                                        menuVisible = true
                                    }
                                    range -> rangeSelect()
                                    else -> select()
                                }
                            }
                        }
                    }
                },
        )
        DropdownMenu(
            expanded = menuVisible && enabled,
            onDismissRequest = { menuVisible = false },
            offset = menuOffset,
        ) {
            DropdownMenuItem(
                modifier = if (LocalXrUi.current.isXr) Modifier.heightIn(min = 56.dp) else Modifier,
                text = { Text(stringResource(R.string.extract_app, appLabel)) },
                onClick = { menuVisible = false; onExtract() },
            )
            DropdownMenuItem(
                modifier = if (LocalXrUi.current.isXr) Modifier.heightIn(min = 56.dp) else Modifier,
                text = { Text(stringResource(if (selected) R.string.deselect_app else R.string.select_app, appLabel)) },
                onClick = { menuVisible = false; select() },
            )
        }
    }
}
