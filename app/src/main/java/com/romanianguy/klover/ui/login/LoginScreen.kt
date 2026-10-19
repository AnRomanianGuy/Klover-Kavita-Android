package com.romanianguy.klover.ui.login

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass
import com.romanianguy.klover.R
import com.romanianguy.klover.data.network.isLikelyLocalHost
import com.romanianguy.klover.data.prefs.MotionStyle
import com.romanianguy.klover.data.repo.SignInError
import com.romanianguy.klover.ui.common.ConnectedChoiceGroup
import com.romanianguy.klover.ui.common.LocalAppSettings
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun LoginScreen(viewModel: LoginViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Local servers need the local-network permission first; sign in once the user has answered.
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.submit()
    }
    val onSubmit = {
        val server = viewModel.state.value.server
        if (isLikelyLocalHost(server) && !hasLocalNetworkPermission(context)) {
            permissionLauncher.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
        } else {
            viewModel.submit()
        }
    }
    val wide = currentWindowAdaptiveInfoV2().windowSizeClass
        .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        if (wide) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Branding(
                    loading = state.loading,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .imePadding()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    LoginForm(state, viewModel, onSubmit, Modifier.widthIn(max = 440.dp))
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Branding(loading = state.loading, compact = true)
                Spacer(Modifier.height(24.dp))
                LoginForm(state, viewModel, onSubmit, Modifier.widthIn(max = 440.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Branding(
    loading: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    // The logo turns slowly (faster while signing in); with Reduced motion it keeps still. The angle is only read
    // while drawing, so
    // the turning does not recompose anything, and no animation runs at all when it is not wanted.
    val moving = LocalAppSettings.current.motionStyle != MotionStyle.Reduced
    val angle = if (moving) {
        rememberInfiniteTransition(label = "logo-spin").animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                tween(durationMillis = 14_000, easing = LinearEasing),
                RepeatMode.Restart,
            ),
            label = "logo-angle",
        )
    } else {
        null
    }
    fun turned(): Float = (angle?.value ?: 0f).let { if (loading) it else it / 4f }
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(if (compact) 112.dp else 168.dp)
                .graphicsLayer { rotationZ = turned() }
                .clip(MaterialShapes.Cookie9Sided.toShape())
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                contentDescription = null,
                // Counter-rotate so the glyph stays upright while the cookie turns.
                modifier = Modifier
                    .size(if (compact) 48.dp else 72.dp)
                    .graphicsLayer { rotationZ = -turned() },
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.displayMediumEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.login_tagline),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LoginForm(
    state: LoginUiState,
    viewModel: LoginViewModel,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focus = LocalFocusManager.current
    val context = LocalContext.current
    var showPassword by remember { mutableStateOf(false) }
    // Android gates home-network servers behind the "nearby devices" permission; say why before the system asks.
    val needsLocalNetwork = isLikelyLocalHost(state.server) && !hasLocalNetworkPermission(context)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ConnectedChoiceGroup(
            SignInMethod.entries,
            state.method,
            viewModel::onMethodChange,
            enabled = !state.loading,
        ) { method ->
            Text(
                stringResource(
                    if (method == SignInMethod.Password) R.string.login_method_password else R.string.login_method_api_key,
                ),
                maxLines = 1,
            )
        }

        FormField(
            value = state.server,
            onValueChange = viewModel::onServerChange,
            label = stringResource(R.string.login_server),
            supporting = stringResource(if (needsLocalNetwork) R.string.login_server_local_hint else R.string.login_server_hint),
            icon = Icons.Filled.Dns,
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Next,
            enabled = !state.loading,
            isError = state.error == SignInError.InvalidUrl || state.error == SignInError.Unreachable,
            onNext = { focus.moveFocus(FocusDirection.Down) },
        )

        AnimatedContent(
            targetState = state.method,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "credentials",
        ) { method ->
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (method) {
                    SignInMethod.Password -> {
                        FormField(
                            value = state.username,
                            onValueChange = viewModel::onUsernameChange,
                            label = stringResource(R.string.login_username),
                            icon = Icons.Filled.Person,
                            imeAction = ImeAction.Next,
                            enabled = !state.loading,
                            isError = state.error == SignInError.BadCredentials,
                            onNext = { focus.moveFocus(FocusDirection.Down) },
                        )
                        FormField(
                            value = state.password,
                            onValueChange = viewModel::onPasswordChange,
                            label = stringResource(R.string.login_password),
                            icon = Icons.Filled.Lock,
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                            enabled = !state.loading,
                            isError = state.error == SignInError.BadCredentials,
                            visualTransformation =
                                if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            trailing = {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(
                                        imageVector = if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        contentDescription = stringResource(
                                            if (showPassword) R.string.login_hide_password else R.string.login_show_password,
                                        ),
                                    )
                                }
                            },
                            onDone = { focus.clearFocus(); onSubmit() },
                        )
                    }

                    SignInMethod.ApiKey -> FormField(
                        value = state.apiKey,
                        onValueChange = viewModel::onApiKeyChange,
                        label = stringResource(R.string.login_api_key),
                        supporting = stringResource(R.string.login_api_key_hint),
                        icon = Icons.Filled.Key,
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                        enabled = !state.loading,
                        isError = state.error == SignInError.BadCredentials,
                        onDone = { focus.clearFocus(); onSubmit() },
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = state.error != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            // Keep showing the last message while the exit animation plays.
            val message = remember(state.error) { state.error }
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = message?.let { stringResource(it.messageRes()) }.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }

        Button(
            onClick = onSubmit,
            enabled = state.canSubmit,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ButtonDefaults.MediumContainerHeight),
            contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
        ) {
            if (state.loading) {
                LoadingIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.size(ButtonDefaults.iconSpacingFor(ButtonDefaults.MediumContainerHeight)))
                Text(stringResource(R.string.login_connecting), style = MaterialTheme.typography.titleMedium)
            } else {
                Text(stringResource(R.string.login_sign_in), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    enabled: Boolean = true,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null,
    onNext: () -> Unit = {},
    onDone: () -> Unit = {},
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        trailingIcon = trailing,
        supportingText = supporting?.let { { Text(it) } },
        singleLine = true,
        enabled = enabled,
        isError = isError,
        shape = MaterialTheme.shapes.large,
        visualTransformation = visualTransformation,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = imeAction,
            autoCorrectEnabled = false,
        ),
        keyboardActions = KeyboardActions(onNext = { onNext() }, onDone = { onDone() }),
    )
}

private fun SignInError.messageRes(): Int = when (this) {
    SignInError.InvalidUrl -> R.string.login_error_invalid_url
    SignInError.BadCredentials -> R.string.login_error_credentials
    SignInError.Unreachable -> R.string.login_error_unreachable
    SignInError.NotKavita -> R.string.login_error_not_kavita
    SignInError.Unknown -> R.string.login_error_unknown
}
