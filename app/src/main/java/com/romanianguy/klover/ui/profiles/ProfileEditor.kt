package com.romanianguy.klover.ui.profiles

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.romanianguy.klover.R
import com.romanianguy.klover.data.profiles.PROFILE_SHAPES
import com.romanianguy.klover.data.profiles.PROFILE_TONES
import com.romanianguy.klover.data.profiles.ProfileStore
import com.romanianguy.klover.data.profiles.SavedProfile
import com.romanianguy.klover.data.profiles.importProfilePhoto
import com.romanianguy.klover.data.repo.AuthRepository
import com.romanianguy.klover.ui.common.PageSheet
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
internal fun rememberRemoveProfile(): (SavedProfile) -> Unit {
    val store = koinInject<ProfileStore>()
    val auth = koinInject<AuthRepository>()
    val scope = rememberCoroutineScope()
    return { profile ->
        val inUse = store.activeKey.value == profile.key
        store.remove(profile.key)
        if (inUse) scope.launch { auth.signOut() }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileToneRow(profile: SavedProfile, modifier: Modifier = Modifier) {
    val store = koinInject<ProfileStore>()
    val scheme = MaterialTheme.colorScheme
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        for (tone in 0 until PROFILE_TONES) {
            val chosen = tone == profile.tone
            val color = toneColor(tone, scheme)
            val grow by animateFloatAsState(
                targetValue = if (chosen) 1f else 0.86f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
                label = "tone-grow",
            )
            val name = toneName(tone)
            Box(
                Modifier
                    .size(44.dp)
                    .graphicsLayer { scaleX = grow; scaleY = grow }
                    .then(if (chosen) Modifier.border(3.dp, scheme.onSurface.copy(alpha = 0.85f), CircleShape).padding(4.dp) else Modifier)
                    .clip(CircleShape)
                    .background(color)
                    .clickable(role = Role.RadioButton) { store.setTone(profile.key, tone) }
                    .semantics {
                        contentDescription = name
                        selected = chosen
                        role = Role.RadioButton
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (chosen) Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileShapeRow(profile: SavedProfile, modifier: Modifier = Modifier) {
    val store = koinInject<ProfileStore>()
    val scheme = MaterialTheme.colorScheme
    val tone = toneColor(profile.tone, scheme)
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        for (index in 0 until PROFILE_SHAPES) {
            val chosen = index == profile.shape
            val shape = profileShape(index)
            val grow by animateFloatAsState(
                targetValue = if (chosen) 1f else 0.86f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
                label = "shape-grow",
            )
            Box(
                Modifier
                    .size(52.dp)
                    .graphicsLayer { scaleX = grow; scaleY = grow }
                    .border(3.dp, tone, shape)
                    .padding(6.dp)
                    .clip(shape)
                    .background(if (chosen) tone else tone.copy(alpha = 0.14f))
                    .clickable(role = Role.RadioButton) { store.setShape(profile.key, index) }
                    .semantics { selected = chosen; role = Role.RadioButton },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditorSheet(profile: SavedProfile, onDismiss: () -> Unit) {
    val store = koinInject<ProfileStore>()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val removeProfile = rememberRemoveProfile()
    val all by store.profiles.collectAsState()
    val activeKey by store.activeKey.collectAsState()
    val live = all.firstOrNull { it.key == profile.key } ?: profile
    var name by remember(profile.key) { mutableStateOf(live.name.ifBlank { live.username }) }
    var confirmRemove by remember { mutableStateOf(false) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { importProfilePhoto(context, store, profile.key, uri) }
    }

    PageSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                ProfileAvatar(live, size = 96.dp)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it.take(24)
                            store.rename(profile.key, name)
                        },
                        singleLine = true,
                        label = { Text(stringResource(R.string.profile_name)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        stringResource(
                            R.string.profile_account_line,
                            live.username,
                            live.serverUrl.removePrefix("https://").removePrefix("http://"),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.profile_ring), style = MaterialTheme.typography.titleSmall)
                ProfileToneRow(live)
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.profile_shape), style = MaterialTheme.typography.titleSmall)
                ProfileShapeRow(live)
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.profile_picture), style = MaterialTheme.typography.titleSmall)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledTonalButton(
                        onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    ) {
                        Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.profile_choose_photo), modifier = Modifier.padding(start = 8.dp))
                    }
                    if (live.photo != null) {
                        TextButton(onClick = { store.setPhoto(profile.key, null) }) { Text(stringResource(R.string.profile_use_initial)) }
                    }
                }
            }

            TextButton(onClick = { confirmRemove = true }) {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    stringResource(if (activeKey == profile.key) R.string.profile_sign_out_remove else R.string.profile_remove),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringResource(R.string.profile_remove_title, live.displayName)) },
            text = { Text(stringResource(R.string.profile_remove_body)) },
            confirmButton = {
                TextButton(onClick = { confirmRemove = false; removeProfile(live); onDismiss() }) {
                    Text(stringResource(R.string.profile_remove_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
fun ProfileSettingsSection() {
    val profile = rememberCurrentProfile() ?: return
    var editing by remember { mutableStateOf(false) }
    ListItem(
        modifier = Modifier.clickable { editing = true },
        leadingContent = { ProfileAvatar(profile, size = 56.dp) },
        overlineContent = { Text(stringResource(R.string.profile_title)) },
        headlineContent = { Text(profile.displayName) },
        supportingContent = { Text(stringResource(R.string.profile_settings_hint, toneName(profile.tone))) },
        trailingContent = { Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.profile_edit)) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
    ProfileToneRow(profile, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    ProfileShapeRow(profile, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    if (editing) ProfileEditorSheet(profile) { editing = false }
}
