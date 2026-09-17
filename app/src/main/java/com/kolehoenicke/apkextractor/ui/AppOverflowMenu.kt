package com.kolehoenicke.apkextractor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
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
) {
    var sorting by remember { mutableStateOf(false) }
    LaunchedEffect(expanded) { if (!expanded) sorting = false }
    DropdownMenuPopup(expanded, onDismiss) {
        DropdownMenuGroup(MenuDefaults.groupShape(0, 1)) {
            Box {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.sort_by)) },
                    onClick = { sorting = true },
                    trailingContent = { Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null) },
                    shape = MenuDefaults.itemShapes().shape,
                )
                DropdownMenuPopup(
                    expanded = sorting,
                    onDismissRequest = { sorting = false },
                    popupPositionProvider = MenuDefaults.rememberDropdownMenuPopupPositionProvider(MenuAnchorPosition.End),
                ) {
                    DropdownMenuGroup(MenuDefaults.groupShape(0, 1), Modifier.selectableGroup()) {
                        AppSort.entries.forEachIndexed { index, option ->
                            SelectableDropdownMenuItem(
                                selected = sort == option,
                                onClick = { onSort(option); sorting = false; onDismiss() },
                                text = { Text(stringResource(if (option == AppSort.Name) R.string.sort_name else R.string.sort_recent)) },
                                shapes = MenuDefaults.itemShape(index, AppSort.entries.size),
                            )
                        }
                    }
                }
            }
            if (hasResult) DropdownMenuItem(
                text = { Text(stringResource(R.string.last_export)) },
                onClick = { onDismiss(); onResult() },
                shape = MenuDefaults.itemShapes().shape,
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.privacy_and_licenses)) },
                onClick = { onDismiss(); onAbout() },
                shape = MenuDefaults.itemShapes().shape,
            )
        }
    }
}
