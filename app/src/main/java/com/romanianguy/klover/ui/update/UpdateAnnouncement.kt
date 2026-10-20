package com.romanianguy.klover.ui.update

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.romanianguy.klover.R
import com.romanianguy.klover.data.update.UpdateChecker
import com.romanianguy.klover.data.update.UpdateInfo
import com.romanianguy.klover.ui.common.LocalAppSettings
import com.romanianguy.klover.ui.common.bouncyClickable
import org.koin.compose.koinInject

// A card for the top of the home screen when a newer version is out. A tap shows what is new; "Later" hides it until the
// app is started again, "Skip this version" until a still newer one comes
@Composable
fun UpdateCard(modifier: Modifier = Modifier) {
    if (!LocalAppSettings.current.updateCheck) return
    val checker = koinInject<UpdateChecker>()
    val info by checker.announcement.collectAsStateWithLifecycle()
    var hidden by rememberSaveable { mutableStateOf<String?>(null) }
    var open by rememberSaveable { mutableStateOf(false) }
    val shown = info?.takeIf { it.version != hidden } ?: return
    Surface(
        modifier = modifier.fillMaxWidth().bouncyClickable { open = true },
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Outlined.SystemUpdate, contentDescription = null)
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.update_available, shown.version), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.update_tap), style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = { hidden = shown.version }) { Text(stringResource(R.string.update_later)) }
        }
    }
    if (open) {
        UpdateDialog(
            info = shown,
            onDismiss = { open = false },
            onSkip = {
                open = false
                checker.skip(shown.version)
            },
        )
    }
}

@Composable
fun UpdateDialog(info: UpdateInfo, onDismiss: () -> Unit, onSkip: (() -> Unit)?) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.SystemUpdate, contentDescription = null) },
        title = { Text(stringResource(R.string.update_available, info.version)) },
        text = {
            Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                Text(
                    info.notes.trim().ifBlank { stringResource(R.string.update_no_notes) },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, info.url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                onDismiss()
            }) { Text(stringResource(R.string.update_download)) }
        },
        dismissButton = {
            Row {
                if (onSkip != null) TextButton(onClick = onSkip) { Text(stringResource(R.string.update_skip)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.update_close)) }
            }
        },
    )
}
