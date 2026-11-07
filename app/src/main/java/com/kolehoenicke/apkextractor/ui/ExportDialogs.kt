package com.kolehoenicke.apkextractor.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
    XrAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (result.failures.isEmpty()) R.string.export_success_title else R.string.last_export)) },
        text = {
            val scrollState = rememberScrollState()
            val scrollbar = scrollState.scrollIndicatorState?.let {
                Modifier.appScrollbar(it)
            } ?: Modifier
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(scrollState).then(scrollbar), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (result.failures.isNotEmpty()) ExportResultSummary(result)
                if (result.files.any(ExportedFile::isSplitArchive)) SplitExportNote()
                result.failures.forEach { ExportFailureDetail(it) }
                if (result.files.size > 1) ExportFileDragTile(result.files)
                result.files.forEach { file ->
                    ExportFileDragTile(listOf(file))
                }

            }
        },
        confirmButton = {
            ExportResultActions(result, canRetry, onRetry, onOpenFolder, onShare, onDismiss)
        },
    )
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
            if (result.files.size > 1) item("all-files") { ExportFileDragTile(result.files) }
            itemsIndexed(result.files, key = { index, file -> "file:$index:${file.uri}" }) { _, file ->
                ExportFileDragTile(listOf(file))
            }
        }
        ExportResultActions(result, canRetry, onRetry, onOpenFolder, onShare, onDismiss,
            modifier = Modifier.padding(top = 16.dp))
    }
}

@Composable
private fun ExportResultSummary(result: UiEvent.ExportFinished) {
    Text(if (result.failures.isEmpty()) pluralStringResource(R.plurals.result_saved, result.files.size, result.files.size)
        else stringResource(R.string.export_result_summary,
            pluralStringResource(R.plurals.result_saved, result.files.size, result.files.size),
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
) {
    DialogActions(modifier) {
        if (canRetry) TextButton(modifier = Modifier.xrActionSize(), onClick = onRetry) { Text(stringResource(R.string.retry_failed)) }
        if (result.outputFolder != null) TextButton(modifier = Modifier.xrActionSize(), onClick = onOpenFolder) { Text(stringResource(R.string.open_folder_action)) }
        if (result.files.isNotEmpty()) TextButton(modifier = Modifier.xrActionSize(), onClick = onShare) { Text(stringResource(R.string.share)) }
        TextButton(modifier = Modifier.xrActionSize(), onClick = onDismiss) { Text(stringResource(R.string.done)) }
    }
}

@Composable
internal fun ExportFolderDialog(folderName: String?, onOpen: () -> Unit, onChange: () -> Unit, onDismiss: () -> Unit) {
    XrAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.export_folder)) },
        text = { Text(folderName ?: stringResource(R.string.no_export_folder)) },
        confirmButton = {
            DialogActions {
                if (folderName != null) TextButton(modifier = Modifier.xrActionSize(), onClick = onOpen) { Text(stringResource(R.string.open_folder_action)) }
                TextButton(modifier = Modifier.xrActionSize(), onClick = onChange) { Text(stringResource(if (folderName == null) R.string.choose_folder else R.string.change_folder_action)) }
                TextButton(modifier = Modifier.xrActionSize(), onClick = onDismiss) { Text(stringResource(R.string.done)) }
            }
        },
    )
}

@Composable
private fun DialogActions(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.End), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}
