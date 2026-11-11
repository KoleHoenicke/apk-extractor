@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.kolehoenicke.apkextractor.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.SheetValue
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.window.core.layout.WindowSizeClass
import androidx.xr.compose.spatial.SpatialDialog
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import com.kolehoenicke.apkextractor.R
import com.kolehoenicke.apkextractor.ExportFailure
import com.kolehoenicke.apkextractor.UiEvent
import com.kolehoenicke.apkextractor.data.ExportedFile
import com.kolehoenicke.apkextractor.tryLaunchExternalActivity

internal fun openExportFolder(context: Context, tree: Uri): Boolean = tryLaunchExternalActivity {
    val document = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
    context.startActivity(Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(document, DocumentsContract.Document.MIME_TYPE_DIR)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    })
}

@Composable
internal fun ExportResultDialog(
    result: UiEvent.ExportFinished,
    canRetry: Boolean,
    onRetry: () -> Unit,
    onOpenFolder: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
) {
    val compactHeight = LocalWindowInfo.current.containerDpSize.height <
        WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND.dp
    ExportDetailsSheet(onDismiss, allowPartialExpansion = false) { dismiss ->
        if (compactHeight && !LocalXrUi.current.isSpatial) {
            CompactExportResultContent(result, canRetry, onRetry, onOpenFolder, onShare, dismiss)
        } else {
            ExportResultPane(result, canRetry, onRetry, onOpenFolder, onShare, dismiss)
        }
    }
}


/** Detailed reports stay on a stable panel. Only a simple one-file success uses XR elevation. */
internal fun needsDetailedExportReport(result: UiEvent.ExportFinished): Boolean =
    result.requestedCount > 1 || result.files.size > 1 || result.failures.isNotEmpty() ||
        result.files.any(ExportedFile::isSplitArchive)

@Composable
internal fun ExportResultPane(
    result: UiEvent.ExportFinished,
    canRetry: Boolean,
    onRetry: () -> Unit,
    onOpenFolder: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberLazyListState()
    val scrollbar = scrollState.scrollIndicatorState?.let {
        Modifier.appScrollbar(it)
    } ?: Modifier
    Column(modifier.fillMaxWidth()) {
        Text(stringResource(if (result.failures.isEmpty()) R.string.export_success_title else R.string.last_export),
            style = MaterialTheme.typography.headlineSmall)
        ExportResultSummary(result)
        // The list wraps short reports and is bounded for large batches. Virtualizing rows
        // avoids loading every app icon at once, while the actions remain outside the scroll.
        LazyColumn(
            state = scrollState,
            modifier = Modifier.weight(1f, fill = false).padding(top = 16.dp).then(scrollbar),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (result.files.any(ExportedFile::isSplitArchive)) item("split-note") { SplitExportNote() }
            itemsIndexed(result.failures, key = { index, failure -> "failure:$index:${failure.packageName}" }) { _, failure ->
                ExportFailureDetail(failure)
            }
            itemsIndexed(result.files, key = { index, file -> "file:$index:${file.uri}" }) { _, file ->
                ExportFileDragTile(listOf(file))
            }
        }
        ExportResultActions(result, canRetry, onRetry, onOpenFolder, onShare, onDismiss,
            modifier = Modifier.padding(top = 16.dp))
    }
}

/** In short windows, only dismissal is fixed; report text and other actions can scroll. */
@Composable
private fun CompactExportResultContent(
    result: UiEvent.ExportFinished,
    canRetry: Boolean,
    onRetry: () -> Unit,
    onOpenFolder: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
) {
    val list = rememberLazyListState()
    val scrollbar = list.scrollIndicatorState?.let { Modifier.appScrollbar(it) } ?: Modifier
    Column(Modifier.fillMaxWidth()) {
        LazyColumn(
            state = list,
            modifier = Modifier.weight(1f, fill = false).then(scrollbar),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item("title", "title") {
                Text(stringResource(if (result.failures.isEmpty()) R.string.export_success_title else R.string.last_export),
                    style = MaterialTheme.typography.headlineSmall)
            }
            item("summary", "summary") { ExportResultSummary(result) }
            item("actions", "actions") {
                ExportResultActions(result, canRetry, onRetry, onOpenFolder, onShare, onDismiss,
                    includeDismiss = false)
            }
            if (result.files.any(ExportedFile::isSplitArchive)) item("split-note", "split-note") { SplitExportNote() }
            itemsIndexed(result.failures, key = { index, failure -> "failure:$index:${failure.packageName}" },
                contentType = { _, _ -> "failure" }) { _, failure -> ExportFailureDetail(failure) }
            itemsIndexed(result.files, key = { index, file -> "file:$index:${file.uri}" },
                contentType = { _, _ -> "files" }) { _, file -> ExportFileDragTile(listOf(file)) }
        }
        DialogActions(Modifier.padding(top = 8.dp)) {
            Button(onClick = onDismiss, shapes = ButtonDefaults.shapes(), modifier = Modifier.xrActionSize()) {
                Text(stringResource(R.string.done))
            }
        }
    }
}

@Composable
private fun ExportResultSummary(result: UiEvent.ExportFinished) {
    Text(if (result.failures.isEmpty()) pluralStringResource(R.plurals.result_extracted, result.files.size, result.files.size)
        else stringResource(R.string.export_result_summary,
            pluralStringResource(R.plurals.result_extracted, result.files.size, result.files.size),
            pluralStringResource(R.plurals.result_failed, result.failures.size, result.failures.size)))
}

@Composable
private fun SplitExportNote() {
    Column {
        Text(stringResource(R.string.split_export_title), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.split_export_detail), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ExportFailureDetail(failure: ExportFailure) {
    Column {
        Text(failure.appLabel, style = MaterialTheme.typography.titleSmall)
        Text(failure.reason, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ExportResultActions(
    result: UiEvent.ExportFinished,
    canRetry: Boolean,
    onRetry: () -> Unit,
    onOpenFolder: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    includeDismiss: Boolean = true,
) {
    val actions = buildList {
        if (canRetry) add(ExportResultAction(stringResource(R.string.retry_failed), onRetry))
        if (result.outputFolder != null) add(ExportResultAction(stringResource(R.string.open_folder_action), onOpenFolder))
        if (result.files.isNotEmpty()) add(ExportResultAction(stringResource(R.string.share), onShare))
        if (includeDismiss) add(ExportResultAction(stringResource(R.string.done), onDismiss, primary = true))
    }
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val measurer = rememberTextMeasurer()
    val padding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MinHeight)
    val textStyle = ButtonDefaults.textStyleFor(ButtonDefaults.MinHeight)
    val gap = ButtonGroupDefaults.HorizontalArrangement.spacing
    val requiredWidth = actions.sumOf { action ->
        with(density) {
            (measurer.measure(action.label, style = textStyle, softWrap = false).size.width +
                padding.calculateStartPadding(direction).roundToPx() +
                padding.calculateEndPadding(direction).roundToPx() + 1)
                .coerceAtLeast(maxOf(ButtonDefaults.MinWidth, if (LocalXrUi.current.isXr) 56.dp else 48.dp).roundToPx())
        }
    } + with(density) { gap.roundToPx() } * (actions.size - 1).coerceAtLeast(0)
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        if (requiredWidth <= constraints.maxWidth) {
            // These are independent actions. Standard groups preserve each button's round shape.
            ButtonGroup(overflowIndicator = { state ->
                ButtonGroupDefaults.OverflowIndicator(state, Modifier.xrActionSize())
            }) {
                actions.forEach { action ->
                    customItem(
                        buttonGroupContent = {
                            val interaction = remember { MutableInteractionSource() }
                            ExportResultActionButton(action,
                                modifier = Modifier.animateWidth(interaction, padding.calculateEndPadding(direction)),
                                interaction = interaction,
                                singleLine = true)
                        },
                        menuContent = { state ->
                            DropdownMenuItem(text = { Text(action.label) }, onClick = {
                                state.dismiss()
                                action.onClick()
                            }, modifier = Modifier.xrActionSize())
                        },
                    )
                }
            }
        } else {
            // Keep every action visible when translations or large text need more room.
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(gap)) {
                actions.forEach { action -> ExportResultActionButton(action, Modifier.fillMaxWidth()) }
            }
        }
    }
}

private data class ExportResultAction(val label: String, val onClick: () -> Unit, val primary: Boolean = false)

@Composable
private fun ExportResultActionButton(
    action: ExportResultAction,
    modifier: Modifier = Modifier,
    interaction: MutableInteractionSource? = null,
    singleLine: Boolean = false,
) {
    Button(
        onClick = action.onClick,
        shapes = ButtonDefaults.shapes(),
        colors = if (action.primary) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors(),
        modifier = modifier.xrActionSize(),
        interactionSource = interaction,
    ) {
        // Keep the label's natural width and center it in the remaining native padding
        // while the group compresses a neighboring button.
        Text(action.label,
            modifier = if (singleLine) Modifier.wrapContentWidth(unbounded = true) else Modifier,
            maxLines = if (singleLine) 1 else Int.MAX_VALUE,
            softWrap = !singleLine,
            overflow = if (singleLine) TextOverflow.Visible else TextOverflow.Clip,
            textAlign = if (singleLine) androidx.compose.ui.text.style.TextAlign.Start
                else androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
internal fun ExportFolderDialog(folderName: String?, onOpen: () -> Unit, onChange: () -> Unit, onDismiss: () -> Unit) {
    val compactHeight = LocalWindowInfo.current.containerDpSize.height <
        WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND.dp
    ExportDetailsSheet(onDismiss, allowPartialExpansion = !compactHeight) { dismiss ->
        val scroll = rememberScrollState()
        Column(Modifier.verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.export_folder), style = MaterialTheme.typography.headlineSmall)
            Text(folderName ?: stringResource(R.string.no_export_folder))
            DialogActions {
                if (folderName != null) TextButton(modifier = Modifier.xrActionSize(), onClick = onOpen) { Text(stringResource(R.string.open_folder_action)) }
                TextButton(modifier = Modifier.xrActionSize(), onClick = onChange) { Text(stringResource(if (folderName == null) R.string.choose_folder else R.string.change_folder_action)) }
                TextButton(modifier = Modifier.xrActionSize(), onClick = dismiss) { Text(stringResource(R.string.done)) }
            }
        }
    }
}

@Composable
private fun DialogActions(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.End), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

/** Supplementary details and several actions belong in a sheet, rather than an alert. */
@Composable
private fun ExportDetailsSheet(
    onDismiss: () -> Unit,
    allowPartialExpansion: Boolean = true,
    content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
) {
    if (LocalXrUi.current.isSpatial) {
        SpatialDialog(onDismissRequest = onDismiss) {
            Surface(
                modifier = Modifier.widthIn(min = 280.dp, max = 560.dp),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Column(Modifier.heightIn(max = 720.dp).padding(24.dp)) { content(onDismiss) }
            }
        }
    } else {
        val sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            // Reports have a scrolling file list and fixed actions. Open fully so
            // those actions never start below a partially expanded viewport.
            enabledValues = if (allowPartialExpansion) SheetValue.entries.toSet()
                else setOf(SheetValue.Hidden, SheetValue.Expanded),
        )
        val scope = rememberCoroutineScope()
        val dismiss: () -> Unit = {
            scope.launch {
                // Keep the sheet composed until its native exit transition completes.
                sheetState.hide()
                if (!sheetState.isVisible) onDismiss()
            }
            Unit
        }
        ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
            Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp)) { content(dismiss) }
        }
    }
}
