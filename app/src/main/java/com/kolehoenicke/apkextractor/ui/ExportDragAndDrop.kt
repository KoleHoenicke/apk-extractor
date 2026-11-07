package com.kolehoenicke.apkextractor.ui

import android.content.Context
import android.view.View
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kolehoenicke.apkextractor.R
import com.kolehoenicke.apkextractor.createExportClipData
import com.kolehoenicke.apkextractor.data.ExportedFile
import com.kolehoenicke.apkextractor.data.LauncherIconRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal fun exportedFileDragData(files: List<ExportedFile>): DragAndDropTransferData? =
    createExportClipData(files)?.let {
        DragAndDropTransferData(clipData = it,
            flags = View.DRAG_FLAG_GLOBAL or View.DRAG_FLAG_GLOBAL_URI_READ)
    }

@Composable
internal fun Modifier.exportDragSource(files: List<ExportedFile>): Modifier {
    val context = LocalContext.current
    val cursor = remember(context) {
        PointerIcon(android.view.PointerIcon.getSystemIcon(context, android.view.PointerIcon.TYPE_GRAB))
    }
    // Compose's native shadow reproduces the whole file row, including its name/count.
    return dragAndDropSource { _ -> exportedFileDragData(files) }.pointerHoverIcon(cursor)
}

/** Resolve by package identity, never by a filename or translated app label. */
internal suspend fun loadExportAppIcon(context: Context, packageName: String?): ImageBitmap? {
    if (packageName.isNullOrBlank()) return null
    return withContext(Dispatchers.IO) {
        runCatching {
            LauncherIconRenderer.render(
                source = context.packageManager.getApplicationIcon(packageName),
                resources = context.resources,
                size = 128,
            ).asImageBitmap()
        }.getOrNull()
    }
}

/** A content drag source. Share stays a separate action so clicks never start a transfer. */
@Composable
internal fun ExportFileDragTile(files: List<ExportedFile>, modifier: Modifier = Modifier) {
    if (files.isEmpty()) return
    val singleFile = files.singleOrNull()
    val context = LocalContext.current
    val icon by key(context, singleFile?.packageName) {
        produceState<ImageBitmap?>(null) {
            value = loadExportAppIcon(context, singleFile?.packageName)
        }
    }
    val title = singleFile?.displayName ?: pluralStringResource(
        R.plurals.exported_files, files.size, files.size)
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val containerColor by animateColorAsState(
        if (hovered) MaterialTheme.colorScheme.surfaceContainerHighest
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "export file hover",
    )
    Surface(
        modifier = modifier.fillMaxWidth().exportDragSource(files)
            .hoverable(interactionSource).semantics(mergeDescendants = true) {},
        shape = MaterialTheme.shapes.medium,
        color = containerColor,
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
            leadingContent = {
                val appIcon = icon
                if (appIcon != null) {
                    Image(bitmap = appIcon, contentDescription = null,
                        contentScale = ContentScale.Fit, modifier = Modifier.size(40.dp))
                } else {
                    Surface(shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.secondaryContainer) {
                        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                            Icon(painterResource(R.drawable.ic_description), contentDescription = null,
                                modifier = Modifier.size(24.dp))
                        }
                    }
                }
            },
            supportingContent = singleFile?.takeIf { it.appLabel != it.displayName }?.let { file ->
                { Text(file.appLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            },
        ) {
            Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
