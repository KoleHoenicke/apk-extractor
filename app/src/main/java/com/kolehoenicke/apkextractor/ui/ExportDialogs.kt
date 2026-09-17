package com.kolehoenicke.apkextractor.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import com.kolehoenicke.apkextractor.R
import com.kolehoenicke.apkextractor.UiEvent
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (result.failures.isEmpty()) R.string.export_success_title else R.string.last_export)) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (result.failures.isNotEmpty()) Text(stringResource(R.string.export_result_summary,
                    pluralStringResource(R.plurals.result_saved, result.files.size, result.files.size),
                    pluralStringResource(R.plurals.result_failed, result.failures.size, result.failures.size),
                ))
                result.failures.forEach {
                    Column {
                        Text(it.appLabel, style = MaterialTheme.typography.titleSmall)
                        Text(it.reason, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                result.files.forEach { Text(it.appLabel, style = MaterialTheme.typography.bodyMedium) }

            }
        },
        confirmButton = {
            DialogActions {
                if (canRetry) TextButton(onClick = onRetry) { Text(stringResource(R.string.retry_failed)) }
                if (result.outputFolder != null) TextButton(onClick = onOpenFolder) { Text(stringResource(R.string.open_folder_action)) }
                if (result.files.isNotEmpty()) TextButton(onClick = onShare) { Text(stringResource(R.string.share)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) }
            }
        },
    )
}

@Composable
internal fun ExportFolderDialog(folderName: String?, onOpen: () -> Unit, onChange: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.export_folder)) },
        text = { Text(folderName ?: stringResource(R.string.no_export_folder)) },
        confirmButton = {
            DialogActions {
                if (folderName != null) TextButton(onClick = onOpen) { Text(stringResource(R.string.open_folder_action)) }
                TextButton(onClick = onChange) { Text(stringResource(if (folderName == null) R.string.choose_folder else R.string.change_folder_action)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) }
            }
        },
    )
}

@Composable
private fun DialogActions(content: @Composable () -> Unit) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.End), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}
