package com.romanianguy.klover.data.session

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val Context.sessionStore by preferencesDataStore(name = "session",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() })

data class Session(
    // normalised base URL of the Kavita server, without a trailing slash (may include a sub-path)
    val serverUrl: String,
    val token: String,
    val refreshToken: String?,
    val apiKey: String,
    val username: String,
    val userId: Int,
)

sealed interface SessionState {
    data object Loading : SessionState
    data object SignedOut : SessionState
    data class SignedIn(val session: Session) : SessionState
}

// in memory so interceptors can read it synchronously, mirrored to DataStore to survive process death
class SessionManager(
    private val context: Context,
    scope: CoroutineScope,
    private val secrets: SecretBox = SecretBox(),
) {
    private val _state = MutableStateFlow<SessionState>(SessionState.Loading)
    val state: StateFlow<SessionState> = _state

    val current: Session? get() = (_state.value as? SessionState.SignedIn)?.session

    init {
        scope.launch {
            val prefs = context.sessionStore.data.first()
            val server = prefs[SERVER]
            val token = prefs[TOKEN]?.let(secrets::decrypt)
            val apiKey = prefs[API_KEY]?.let(secrets::decrypt)
            val refresh = prefs[REFRESH]?.let(secrets::decrypt)
            _state.value = if (server != null && token != null && apiKey != null) {
                SessionState.SignedIn(
                    Session(
                        serverUrl = server,
                        token = token,
                        refreshToken = refresh,
                        apiKey = apiKey,
                        username = prefs[USERNAME].orEmpty(),
                        userId = prefs[USER_ID] ?: 0,
                    ),
                )
            } else {
                // Nothing stored, or the secrets cannot be decrypted (restored backup, wiped keystore).
                if (server != null) context.sessionStore.edit { it.clear() }
                SessionState.SignedOut
            }
        }
    }

    suspend fun signIn(session: Session) {
        persist(session)
        _state.value = SessionState.SignedIn(session)
    }

    suspend fun updateTokens(token: String, refreshToken: String?) {
        val old = current ?: return
        val updated = old.copy(token = token, refreshToken = refreshToken ?: old.refreshToken)
        persist(updated)
        _state.value = SessionState.SignedIn(updated)
    }

    suspend fun signOut() {
        context.sessionStore.edit { it.clear() }
        _state.value = SessionState.SignedOut
    }

    private suspend fun persist(session: Session) {
        context.sessionStore.edit {
            it[SERVER] = session.serverUrl
            it[TOKEN] = secrets.encrypt(session.token)
            it[API_KEY] = secrets.encrypt(session.apiKey)
            it[USERNAME] = session.username
            it[USER_ID] = session.userId
            if (session.refreshToken != null) it[REFRESH] = secrets.encrypt(session.refreshToken) else it.remove(REFRESH)
        }
    }

    private companion object {
        val SERVER = stringPreferencesKey("server")
        val TOKEN = stringPreferencesKey("token")
        val REFRESH = stringPreferencesKey("refresh")
        val API_KEY = stringPreferencesKey("api_key")
        val USERNAME = stringPreferencesKey("username")
        val USER_ID = intPreferencesKey("user_id")
    }
}
