package com.romanianguy.klover.ui.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.SingletonImageLoader
import com.romanianguy.klover.data.prefs.AppSettings
import com.romanianguy.klover.data.prefs.SettingsStore
import com.romanianguy.klover.data.profiles.ProfileStore
import com.romanianguy.klover.data.repo.AuthRepository
import com.romanianguy.klover.data.session.Session
import com.romanianguy.klover.data.session.SessionManager
import com.romanianguy.klover.data.session.SessionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val session: Session? = null,
)

class SettingsViewModel(
    private val store: SettingsStore,
    sessions: SessionManager,
    private val auth: AuthRepository,
    private val context: Context,
    private val profiles: ProfileStore,
) : ViewModel() {
    private val _cacheBytes = MutableStateFlow(0L)

    val cacheBytes: StateFlow<Long> = _cacheBytes

    init {
        refreshCache()
    }

    fun refreshCache() {
        viewModelScope.launch(Dispatchers.IO) {
            val images = SingletonImageLoader.get(context).diskCache?.size ?: 0L
            _cacheBytes.value = images + File(context.cacheDir, "pdf").walkTopDown().filter { it.isFile }.sumOf { it.length() }
        }
    }

    fun clearCache() {
        viewModelScope.launch(Dispatchers.IO) {
            val loader = SingletonImageLoader.get(context)
            loader.memoryCache?.clear()
            loader.diskCache?.clear()
            File(context.cacheDir, "pdf").deleteRecursively()
            refreshCache()
        }
    }

    val state: StateFlow<SettingsUiState> = combine(store.settings, sessions.state) { settings, session ->
        SettingsUiState(settings, (session as? SessionState.SignedIn)?.session)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun <T> set(key: Preferences.Key<T>, value: T) {
        viewModelScope.launch { store.set(key, value) }
    }

    fun <E : Enum<E>> setEnum(key: Preferences.Key<String>, value: E) {
        viewModelScope.launch { store.setEnum(key, value) }
    }

    fun resetAll() {
        viewModelScope.launch { store.resetAll() }
    }

    fun signOut() {
        // Signing out is final for this profile: its saved login goes with it (the other profiles stay to pick from).
        profiles.activeKey.value?.let(profiles::remove)
        viewModelScope.launch { auth.signOut() }
    }
}
