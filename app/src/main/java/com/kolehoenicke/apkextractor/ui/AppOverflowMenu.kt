package com.kolehoenicke.apkextractor.ui

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.xr.compose.spatial.SpatialPopup
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.roundToInt
import com.kolehoenicke.apkextractor.R
import com.kolehoenicke.apkextractor.data.AppSort

@Composable
internal fun AppOverflowMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    sort: AppSort,
    onSort: (AppSort) -> Unit,
    hasResult: Boolean,
    onResult: () -> Unit,
    onAbout: () -> Unit,
    onNewWindow: (() -> Unit)? = null,
) {
    var sorting by remember { mutableStateOf(false) }
    var sortRow by remember { mutableStateOf<LayoutCoordinates?>(null) }
    // Keep room for both native menus. Larger labels wrap instead of covering
    // the parent menu when accessibility text is enabled on a compact window.
    val menuWidth = if (LocalXrUi.current.isSpatial) Modifier else Modifier.widthIn(
        max = ((LocalWindowInfo.current.containerDpSize.width - 32.dp) / 2).coerceAtLeast(0.dp),
    )
    val hostView = LocalView.current
    val materialPosition = MenuDefaults.rememberDropdownMenuPopupPositionProvider(MenuAnchorPosition.End)
    val submenuPosition = remember(materialPosition, hostView) {
        object : DropdownMenuPopupPositionProvider {
            override val transformOrigin get() = materialPosition.transformOrigin
            override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize,
                layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
                val row = sortRow?.takeIf { it.isAttached }
                val screen = IntArray(2)
                val window = IntArray(2)
                hostView.getLocationOnScreen(screen)
                hostView.getLocationInWindow(window)
                val position = row?.positionOnScreen()
                val rowBounds = if (row != null && position != null) IntRect(
                    IntOffset((position.x - screen[0] + window[0]).roundToInt(),
                        (position.y - screen[1] + window[1]).roundToInt()), row.size,
                ) else anchorBounds
                return materialPosition.calculatePosition(rowBounds, windowSize, layoutDirection, popupContentSize)
            }
        }
    }
    LaunchedEffect(expanded) { if (!expanded) sorting = false }
    val itemCount = 2 + (if (hasResult) 1 else 0) + (if (onNewWindow != null) 1 else 0)
    val rootMenu: @Composable () -> Unit = {
        DropdownMenuGroup(MenuDefaults.groupShape(0, 1), menuWidth) {
            if (onNewWindow != null) DropdownMenuItem(
                modifier = if (LocalXrUi.current.isXr) Modifier.heightIn(min = 56.dp) else Modifier,
                text = { Text(stringResource(R.string.new_window)) },
                onClick = { onDismiss(); onNewWindow() },
                shape = MenuDefaults.itemShape(0, itemCount).shape,
            )
            Box(Modifier.onGloballyPositioned { sortRow = it }) {
                DropdownMenuItem(
                    modifier = if (LocalXrUi.current.isXr) Modifier.heightIn(min = 56.dp) else Modifier,
                    text = { Text(stringResource(R.string.sort_by)) },
                    onClick = { sorting = true },
                    trailingContent = { Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null) },
                    shape = MenuDefaults.itemShape(if (onNewWindow != null) 1 else 0, itemCount).shape,
                )
            }
            if (hasResult) DropdownMenuItem(
                modifier = if (LocalXrUi.current.isXr) Modifier.heightIn(min = 56.dp) else Modifier,
                text = { Text(stringResource(R.string.last_export)) },
                onClick = { onDismiss(); onResult() },
                shape = MenuDefaults.itemShape(itemCount - 2, itemCount).shape,
            )
            DropdownMenuItem(
                modifier = if (LocalXrUi.current.isXr) Modifier.heightIn(min = 56.dp) else Modifier,
                text = { Text(stringResource(R.string.privacy_and_licenses)) },
                onClick = { onDismiss(); onAbout() },
                shape = MenuDefaults.itemShape(itemCount - 1, itemCount).shape,
            )
        }
    }
    val sortMenu: @Composable () -> Unit = {
        DropdownMenuGroup(MenuDefaults.groupShape(0, 1), menuWidth.selectableGroup()) {
            AppSort.entries.forEachIndexed { index, option ->
                SelectableDropdownMenuItem(
                    modifier = if (LocalXrUi.current.isXr) Modifier.heightIn(min = 56.dp) else Modifier,
                    selected = sort == option,
                    onClick = { onSort(option); sorting = false; onDismiss() },
                    text = { Text(stringResource(if (option == AppSort.Name) R.string.sort_name else R.string.sort_recent)) },
                    shapes = MenuDefaults.itemShape(index, AppSort.entries.size),
                )
            }
        }
    }
    if (LocalXrUi.current.isSpatial) {
        // An Android Popup in an orbiter is constrained to the toolbar's short window.
        // A native spatial popup measures the full Material menu independently. Keep sort
        // choices in the same anchored popup so a cascading menu cannot overlap the toolbar.
        val menuOffset = with(LocalDensity.current) { 64.dp.roundToPx() }
        if (expanded) SpatialPopup(
            alignment = Alignment.TopEnd,
            offset = IntOffset(0, menuOffset),
            onDismissRequest = { if (sorting) sorting = false else onDismiss() },
            properties = MenuDefaults.DefaultMenuProperties,
        ) {
            if (sorting) sortMenu() else rootMenu()
        }
    } else {
        DropdownMenuPopup(expanded, { if (!sorting) onDismiss() }) { rootMenu() }
        // Keep both 2D popups attached to the Activity window. Nested Compose popups mix
        // screen coordinates with floating-window dimensions, causing submenu overlap.
        DropdownMenuPopup(
            expanded = expanded && sorting,
            onDismissRequest = { sorting = false; onDismiss() },
            popupPositionProvider = submenuPosition,
        ) { sortMenu() }
    }

}
