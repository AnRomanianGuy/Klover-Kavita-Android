package com.romanianguy.klover.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.romanianguy.klover.data.prefs.SettingsStore
import com.romanianguy.klover.data.repo.AuthRepository
import com.romanianguy.klover.data.repo.SignInError
import com.romanianguy.klover.data.repo.SignInException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SignInMethod { Password, ApiKey }

data class LoginUiState(
    val method: SignInMethod = SignInMethod.Password,
    val server: String = "",
    val username: String = "",
    val password: String = "",
    val apiKey: String = "",
    val loading: Boolean = false,
    val error: SignInError? = null,
) {
    val canSubmit: Boolean
        get() = !loading && server.isNotBlank() && when (method) {
            SignInMethod.Password -> username.isNotBlank() && password.isNotEmpty()
            SignInMethod.ApiKey -> apiKey.isNotBlank()
        }
}

class LoginViewModel(
    private val auth: AuthRepository,
    private val settings: SettingsStore,
) : ViewModel() {
    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val last = settings.lastServer.first()
            // Don't clobber anything the user already started typing.
            _state.update { if (it.server.isEmpty()) it.copy(server = last) else it }
        }
    }

    fun onMethodChange(method: SignInMethod) = _state.update { it.copy(method = method, error = null) }
    fun onServerChange(value: String) = _state.update { it.copy(server = value, error = null) }
    fun onUsernameChange(value: String) = _state.update { it.copy(username = value, error = null) }
    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, error = null) }
    fun onApiKeyChange(value: String) = _state.update { it.copy(apiKey = value, error = null) }

    fun submit() {
        val current = _state.value
        if (!current.canSubmit) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val session = when (current.method) {
                    SignInMethod.Password -> auth.signInWithPassword(current.server, current.username, current.password)
                    SignInMethod.ApiKey -> auth.signInWithApiKey(current.server, current.apiKey)
                }
                settings.setLastServer(session.serverUrl)
                // The root composable reacts to the new session; nothing else to do here.
                _state.update { it.copy(loading = false, password = "", apiKey = "") }
            } catch (e: SignInException) {
                _state.update { it.copy(loading = false, error = e.error) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = SignInError.Unknown) }
            }
        }
    }
}
