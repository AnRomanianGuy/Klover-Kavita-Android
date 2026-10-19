package com.romanianguy.klover.ui.profiles

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.romanianguy.klover.R
import com.romanianguy.klover.data.profiles.MAX_PROFILES
import com.romanianguy.klover.data.profiles.ProfileStore
import com.romanianguy.klover.data.profiles.SavedProfile
import com.romanianguy.klover.data.profiles.sameServer
import com.romanianguy.klover.data.session.SessionManager
import com.romanianguy.klover.data.session.SessionState
import com.romanianguy.klover.ui.common.bouncyClickable
import com.romanianguy.klover.ui.login.LoginScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun ProfilePickerHost(content: @Composable () -> Unit) {
    var open by remember { mutableStateOf(false) }
    CompositionLocalProvider(LocalProfilePicker provides { open = true }) {
        Box(Modifier.fillMaxSize()) {
            content()
            AnimatedVisibility(
                visible = open,
                enter = fadeIn(tween(220)) + scaleIn(
                    spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
                    initialScale = 0.94f,
                ),
                exit = fadeOut(tween(180)) + scaleOut(tween(180), targetScale = 0.97f),
            ) {
                ProfilePicker(onClose = { open = false })
            }
        }
    }
}

// while signed out all profiles show and onClose is null: choosing or adding one is the way in
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ProfilePicker(onClose: (() -> Unit)?, modifier: Modifier = Modifier) {
    val store = koinInject<ProfileStore>()
    val sessions = koinInject<SessionManager>()
    val scope = rememberCoroutineScope()
    val all by store.profiles.collectAsState()
    val active by store.activeKey.collectAsState()
    val ended by store.ended.collectAsState()
    val state by sessions.state.collectAsState()
    val server = (state as? SessionState.SignedIn)?.session?.serverUrl
    val profiles = remember(all, server) { all.filter { server == null || sameServer(it.serverUrl, server) } }

    var manage by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<SavedProfile?>(null) }
    var adding by remember { mutableStateOf(false) }
    var switching by remember { mutableStateOf<String?>(null) }
    // The width of the window this is drawn in (not of the screen: a folded, split or resized window is narrower than
    // its screen).
    val density = LocalDensity.current
    val wide = with(density) { LocalWindowInfo.current.containerSize.width.toDp() } >= 600.dp
    val avatar: Dp = if (wide) 140.dp else 108.dp

    // Signing in as somebody else (from the add screen) is the end of the picker too.
    LaunchedEffect(adding) {
        if (adding) {
            sessions.state.drop(1).first { it is SessionState.SignedIn }
            adding = false
            onClose?.invoke()
        }
    }
    BackHandler(enabled = adding || manage || onClose != null) {
        when {
            adding -> adding = false
            manage -> manage = false
            else -> onClose?.invoke()
        }
    }

    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        if (adding) {
            Box(Modifier.fillMaxSize()) {
                LoginScreen()
                IconButton(
                    onClick = { adding = false },
                    modifier = Modifier
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(8.dp),
                ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_cancel)) }
            }
        } else {
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 72.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        stringResource(if (manage) R.string.profile_manage_title else R.string.profile_picker_title),
                        style = MaterialTheme.typography.headlineLargeEmphasized,
                        textAlign = TextAlign.Center,
                    )
                    server?.let {
                        Text(
                            stringResource(
                                R.string.profile_on_server,
                                it.removePrefix("https://").removePrefix("http://"),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    FlowRow(
                        modifier = Modifier.padding(top = 36.dp),
                        horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(32.dp),
                    ) {
                        profiles.forEachIndexed { index, profile ->
                            ProfileTile(
                                profile = profile,
                                index = index,
                                size = avatar,
                                current = profile.key == active,
                                manage = manage,
                                needsSignIn = profile.key == ended,
                                popping = switching == profile.key,
                                dimmed = switching != null && switching != profile.key,
                            ) {
                                when {
                                    switching != null -> Unit
                                    manage -> editing = profile
                                    profile.key == ended -> adding = true
                                    profile.key == active -> onClose?.invoke()
                                    else -> scope.launch {
                                        switching = profile.key
                                        // The picture pops and the others step back before the app turns to the other
                                        // account.
                                        delay(300)
                                        if (store.switchTo(profile.key)) onClose?.invoke() else adding = true
                                        switching = null
                                    }
                                }
                            }
                        }
                        if (all.size < MAX_PROFILES && !manage) AddTile(index = profiles.size, size = avatar) { adding = true }
                    }
                    if (profiles.isNotEmpty()) {
                        TextButton(onClick = { manage = !manage }, modifier = Modifier.padding(top = 36.dp)) {
                            Text(stringResource(if (manage) R.string.profile_done else R.string.profile_manage))
                        }
                    }
                }
                if (onClose != null) {
                    IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close))
                    }
                }
            }
        }
    }
    editing?.let { ProfileEditorSheet(it) { editing = null } }
}

@Composable
private fun ProfileTile(
    profile: SavedProfile,
    index: Int,
    size: Dp,
    current: Boolean,
    manage: Boolean,
    needsSignIn: Boolean,
    popping: Boolean,
    dimmed: Boolean,
    onClick: () -> Unit,
) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index * 70L)
        shown = true
    }
    val appear by animateFloatAsState(
        if (shown) 1f else 0f,
        spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow),
        label = "tile-in",
    )
    val swell by animateFloatAsState(
        if (popping) 1.16f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "tile-swell",
    )
    val fade by animateFloatAsState(if (dimmed) 0.3f else 1f, tween(200), label = "tile-fade")
    Column(
        Modifier
            .width(size)
            .graphicsLayer {
                val s = (0.6f + 0.4f * appear) * swell
                scaleX = s
                scaleY = s
                alpha = appear.coerceIn(0f, 1f) * fade
            }
            .bouncyClickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            ProfileAvatar(profile, size)
            if (manage) {
                // The pencil over the picture, the way the other apps with profiles show that it can be edited.
                Box(
                    Modifier
                        .matchParentSize()
                        .padding(size * 0.1f)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.Edit, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.3f)) }
            } else if (current) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(size * 0.3f)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .border(3.dp, MaterialTheme.colorScheme.surface, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(size * 0.17f),
                    )
                }
            }
        }
        Text(
            profile.displayName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp),
        )
        if (needsSignIn) {
            Text(
                stringResource(R.string.profile_sign_in_again),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun AddTile(index: Int, size: Dp, onClick: () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index * 70L)
        shown = true
    }
    val appear by animateFloatAsState(
        if (shown) 1f else 0f,
        spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow),
        label = "add-in",
    )
    Column(
        Modifier
            .width(size)
            .graphicsLayer {
                val s = 0.6f + 0.4f * appear
                scaleX = s
                scaleY = s
                alpha = appear.coerceIn(0f, 1f)
            }
            .bouncyClickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(size)
                .border(2.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                .padding(8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(size * 0.4f),
            )
        }
        Text(
            stringResource(R.string.profile_add),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}
