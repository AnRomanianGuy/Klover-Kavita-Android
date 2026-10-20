package com.romanianguy.klover.ui.settings

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.SingletonImageLoader
import com.romanianguy.klover.R
import com.romanianguy.klover.data.backup.BackupException
import com.romanianguy.klover.data.backup.BackupManager
import com.romanianguy.klover.data.backup.BackupProblem
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

private const val MAX_BACKUP_BYTES = 32 * 1024 * 1024

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
    private val backup: BackupManager,
) : ViewModel() {
    private val _cacheBytes = MutableStateFlow(0L)
    private val _backupResult = MutableStateFlow<Int?>(null)

    // the message to show after an export or import (a string resource)
    val backupResult: StateFlow<Int?> = _backupResult

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

    fun dismissBackupResult() {
        _backupResult.value = null
    }

    fun exportTo(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _backupResult.value = try {
                val bytes = backup.export().toByteArray()
                context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } ?: error("no output")
                R.string.backup_exported
            } catch (e: BackupException) {
                e.problem.message()
            } catch (e: Exception) {
                R.string.backup_write_failed
            }
        }
    }

    fun importFrom(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _backupResult.value = try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readNBytes(MAX_BACKUP_BYTES + 1) } ?: error("no input")
                if (bytes.size > MAX_BACKUP_BYTES) throw BackupException(BackupProblem.NotABackup)
                backup.import(bytes.decodeToString())
                R.string.backup_imported
            } catch (e: BackupException) {
                e.problem.message()
            } catch (e: Exception) {
                R.string.backup_read_failed
            }
        }
    }

    private fun BackupProblem.message() = when (this) {
        BackupProblem.NotABackup -> R.string.backup_not_a_backup
        BackupProblem.TooNew -> R.string.backup_too_new
        BackupProblem.Unreadable -> R.string.backup_read_failed
        BackupProblem.SignedOut -> R.string.backup_signed_out
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
