package com.romanianguy.klover.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.romanianguy.klover.data.prefs.AppSettings
import com.romanianguy.klover.data.prefs.SettingsStore
import com.romanianguy.klover.data.prefs.StartTab
import com.romanianguy.klover.data.profiles.ProfileStore
import com.romanianguy.klover.data.session.SessionManager
import com.romanianguy.klover.data.session.SessionState
import com.romanianguy.klover.data.session.accountKey
import com.romanianguy.klover.ui.common.LoadingState
import com.romanianguy.klover.ui.common.OpenTransitionHost
import com.romanianguy.klover.ui.common.PeekHost
import com.romanianguy.klover.ui.garden.BadgeCelebrationHost
import com.romanianguy.klover.ui.login.LoginScreen
import com.romanianguy.klover.ui.navigation.TopLevelDestination
import com.romanianguy.klover.ui.profiles.ProfilePicker
import com.romanianguy.klover.ui.profiles.ProfilePickerHost
import com.romanianguy.klover.ui.theme.KloverTheme
import org.koin.compose.koinInject

private enum class Kind { Loading, SignedOut, SignedIn }

private data class Stage(val kind: Kind, val account: String? = null)

@Composable
fun KloverRoot(onReady: () -> Unit = {}) {
    // null until DataStore has answered (a few milliseconds): the theme and start tab depend on it.
    val loadedSettings by koinInject<SettingsStore>().settings.collectAsStateWithLifecycle<AppSettings?>(null)
    val settings = loadedSettings ?: return
    val sessionState by koinInject<SessionManager>().state.collectAsStateWithLifecycle()
    // What to draw first is known: the window may show it (see MainActivity.holdFirstFrame).
    if (sessionState !is SessionState.Loading) SideEffect { onReady() }

    val profiles = koinInject<ProfileStore>()
    val saved by profiles.profiles.collectAsStateWithLifecycle()
    // Keyed on the stage and the account, not the session itself, so silent token refreshes never re-run the
    // transition -
    // but another profile is a new start: its own screens, as if the app had just been opened for it.
    val stage = when (val s = sessionState) {
        SessionState.Loading -> Stage(Kind.Loading)
        SessionState.SignedOut -> Stage(Kind.SignedOut)
        is SessionState.SignedIn -> Stage(Kind.SignedIn, accountKey(s.session))
    }

    KloverTheme(settings) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            AnimatedContent(
                targetState = stage,
                transitionSpec = {
                    (fadeIn() + scaleIn(initialScale = 0.96f)) togetherWith fadeOut()
                },
                label = "root-stage",
            ) { target ->
                when (target.kind) {
                    Kind.Loading -> Box(Modifier.fillMaxSize()) { LoadingState() }
                    // Accounts used before are there to pick from; the login form is for the first one (or another).
                    Kind.SignedOut -> if (saved.isEmpty()) LoginScreen() else ProfilePicker(onClose = null)
                    Kind.SignedIn -> Box(Modifier.fillMaxSize()) {
                        ProfilePickerHost {
                            OpenTransitionHost { PeekHost { MainShell(startTab = settings.startTab.toDestination()) } }
                        }
                        // Above everything, the reader included: a badge is celebrated where it is earned.
                        BadgeCelebrationHost(Modifier.align(Alignment.TopCenter))
                    }
                }
            }
        }
    }
}

private fun StartTab.toDestination(): TopLevelDestination = when (this) {
    StartTab.Home -> TopLevelDestination.Home
    StartTab.Library -> TopLevelDestination.Library
    StartTab.WantToRead -> TopLevelDestination.WantToRead
}
