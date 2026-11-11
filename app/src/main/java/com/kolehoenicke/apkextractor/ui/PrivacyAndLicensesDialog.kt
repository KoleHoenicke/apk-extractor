@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.kolehoenicke.apkextractor.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kolehoenicke.apkextractor.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun PrivacyAndLicensesDialog(onDismissRequest: () -> Unit) {
    var licensesVisible by rememberSaveable { mutableStateOf(false) }
    var selectedNotice by rememberSaveable { mutableStateOf<String?>(null) }
    val documents = linkedMapOf(
        "NOTICE.txt" to stringResource(R.string.license_notices),
        "APACHE-2.0.txt" to stringResource(R.string.license_apache),
        "OFL-1.1.txt" to stringResource(R.string.license_ofl),
    )
    val back = {
        if (selectedNotice != null) selectedNotice = null else licensesVisible = false
    }
    XrAlertDialog(
        onDismissRequest = if (licensesVisible) back else onDismissRequest,
        title = {
            Text(if (licensesVisible) selectedNotice?.let { documents.getValue(it) }
                ?: stringResource(R.string.licenses) else stringResource(R.string.privacy_and_licenses))
        },
        text = {
            if (!licensesVisible) {
                Text(stringResource(R.string.privacy_summary),
                    modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()))
            } else if (selectedNotice == null) {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    documents.forEach { (asset, title) ->
                        ListItem(
                            onClick = { selectedNotice = asset },
                            modifier = Modifier.xrActionSize(),
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        ) { Text(title) }
                    }
                }
            } else {
                key(selectedNotice) { LicenseText(requireNotNull(selectedNotice)) }
            }
        },
        dismissButton = {
            TextButton(modifier = Modifier.xrActionSize(), onClick = {
                if (licensesVisible) back() else licensesVisible = true
            }) { Text(stringResource(if (licensesVisible) R.string.back else R.string.licenses)) }
        },
        confirmButton = {
            TextButton(modifier = Modifier.xrActionSize(), onClick = onDismissRequest) {
                Text(stringResource(R.string.done))
            }
        },
    )
}

@Composable
private fun LicenseText(asset: String) {
    val context = LocalContext.current.applicationContext
    val text by produceState("", asset) {
        value = withContext(Dispatchers.IO) {
            context.assets.open(asset).bufferedReader().use { it.readText() }
        }
    }
    SelectionContainer {
        Text(text, modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()))
    }
}
