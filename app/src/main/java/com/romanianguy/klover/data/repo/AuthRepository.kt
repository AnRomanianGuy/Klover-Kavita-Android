package com.romanianguy.klover.data.repo

import com.romanianguy.klover.data.model.LoginRequest
import com.romanianguy.klover.data.model.UserDto
import com.romanianguy.klover.data.network.KavitaApi
import com.romanianguy.klover.data.network.PLUGIN_NAME
import com.romanianguy.klover.data.network.candidateServerUrls
import com.romanianguy.klover.data.session.Session
import com.romanianguy.klover.data.session.SessionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException
import java.net.URLEncoder

enum class SignInError {
    InvalidUrl,
    BadCredentials,
    Unreachable,
    NotKavita,
    Unknown,
}

class SignInException(val error: SignInError, cause: Throwable? = null) : Exception(error.name, cause)

class AuthRepository(
    private val api: KavitaApi,
    private val sessions: SessionManager,
) {
    suspend fun signInWithPassword(server: String, username: String, password: String): Session =
        signIn(server) { base -> api.login("$base/api/Account/login", LoginRequest(username.trim(), password)) }

    suspend fun signInWithApiKey(server: String, apiKey: String): Session {
        val key = apiKey.trim()
        return signIn(server) { base ->
            api.pluginAuthenticate(
                "$base/api/Plugin/authenticate?apiKey=${key.urlEncoded()}&pluginName=$PLUGIN_NAME",
            )
        }
    }

    suspend fun signOut() = sessions.signOut()

    private suspend fun signIn(server: String, call: suspend (base: String) -> UserDto): Session {
        val candidates = candidateServerUrls(server)
        if (candidates.isEmpty()) throw SignInException(SignInError.InvalidUrl)

        var failure = SignInError.Unreachable
        var cause: Throwable? = null
        for (base in candidates) {
            try {
                // A plain-http server never answers a TLS handshake, so without a cap the https attempt
                // would sit on the read timeout before we get to try http.
                val user = withTimeout(PROBE_TIMEOUT_MS) { call(base) }
                if (user.token.isBlank()) throw SignInException(SignInError.NotKavita)
                val session = Session(
                    serverUrl = base,
                    token = user.token,
                    refreshToken = user.refreshToken,
                    apiKey = user.apiKey.orEmpty(),
                    username = user.username,
                    userId = user.id,
                )
                sessions.signIn(session)
                return session
            } catch (e: TimeoutCancellationException) {
                failure = SignInError.Unreachable
                cause = e
            } catch (e: CancellationException) {
                throw e
            } catch (e: SignInException) {
                throw e
            } catch (e: HttpException) {
                when (e.code()) {
                    400, 401, 403 -> throw SignInException(SignInError.BadCredentials, e)
                    404, 405 -> { failure = SignInError.NotKavita; cause = e }
                    else -> { failure = SignInError.Unknown; cause = e }
                }
            } catch (e: IOException) {
                failure = SignInError.Unreachable
                cause = e
            } catch (e: SerializationException) {
                failure = SignInError.NotKavita
                cause = e
            }
        }
        throw SignInException(failure, cause)
    }

    private companion object {
        const val PROBE_TIMEOUT_MS = 8_000L
    }

    private fun String.urlEncoded(): String = URLEncoder.encode(this, "UTF-8")
}
