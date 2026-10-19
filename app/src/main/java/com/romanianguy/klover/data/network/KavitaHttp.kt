package com.romanianguy.klover.data.network

import com.romanianguy.klover.data.model.TokenRequest
import com.romanianguy.klover.data.model.UserDto
import com.romanianguy.klover.data.session.Session
import com.romanianguy.klover.data.session.SessionManager
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import java.io.IOException
import java.util.concurrent.TimeUnit

val KavitaJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
    encodeDefaults = true
    isLenient = true
}

class ServerInterceptor(private val sessions: SessionManager) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        val session = sessions.current

        if (request.url.host == PLACEHOLDER_HOST) {
            val base = session?.serverUrl?.toHttpUrlOrNull()
                ?: throw IOException("Not signed in to a Kavita server")
            val rewritten = request.url.newBuilder()
                .scheme(base.scheme)
                .host(base.host)
                .port(base.port)
                .encodedPath(base.encodedPath.trimEnd('/') + request.url.encodedPath)
                .build()
            request = request.newBuilder().url(rewritten).build()
        }

        if (session != null && request.header("Authorization") == null && request.url.isSameServerAs(session)) {
            request = request.newBuilder().header("Authorization", "Bearer ${session.token}").build()
        }
        return chain.proceed(request)
    }
}

// on a 401 swaps the refresh token for a new JWT (or re-authenticates with the API key) and retries once; failing for
// good signs the user out
class TokenAuthenticator(
    private val sessions: SessionManager,
    private val plainClient: OkHttpClient,
) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.priorResponseCount() >= 2) return null
        val sent = response.request
        val sentToken = sent.header("Authorization")?.removePrefix("Bearer ")

        synchronized(this) {
            val session = sessions.current ?: return null
            if (!sent.url.isSameServerAs(session)) return null

            // Another request already refreshed while we waited for the lock.
            if (sentToken != null && sentToken != session.token) return sent.withToken(session.token)

            when (val outcome = resolveRefresh(attempts(session))) {
                is RefreshOutcome.Renewed -> {
                    runBlocking { sessions.updateTokens(outcome.user.token, outcome.user.refreshToken) }
                    return sent.withToken(outcome.user.token)
                }
                // The server said no to every way of getting a new token: the session is over.
                RefreshOutcome.Rejected -> {
                    runBlocking { sessions.signOut() }
                    return null
                }
                // the server couldn't be asked (offline, timeout, 5xx): fail this request but keep the session
                RefreshOutcome.Unavailable -> return null
            }
        }
    }

    private fun attempts(session: Session): Sequence<RefreshAttempt> = sequence {
        session.refreshToken?.let { refreshToken ->
            val body = KavitaJson.encodeToString(TokenRequest.serializer(), TokenRequest(session.token, refreshToken))
            val request = Request.Builder()
                .url("${session.serverUrl}/api/Account/refresh-token")
                .post(body.toRequestBody(JSON))
                .build()
            yield(
                attempt(request) { text ->
                    val tokens = KavitaJson.decodeFromString(TokenRequest.serializer(), text)
                    UserDto(token = tokens.token, refreshToken = tokens.refreshToken)
                },
            )
        }
        // API-key sessions can always mint a fresh token from the key.
        if (session.apiKey.isNotBlank()) {
            val request = Request.Builder()
                .url("${session.serverUrl}/api/Plugin/authenticate".toHttpUrlWithQuery(session.apiKey))
                .post(ByteArray(0).toRequestBody(null))
                .build()
            yield(attempt(request) { text -> KavitaJson.decodeFromString(UserDto.serializer(), text) })
        }
    }

    private fun attempt(request: Request, parse: (String) -> UserDto): RefreshAttempt =
        try {
            plainClient.newCall(request).execute().use { r ->
                when {
                    r.isSuccessful -> {
                        val user = parse(r.body.string())
                        if (user.token.isBlank()) RefreshAttempt.Failed else RefreshAttempt.Renewed(user)
                    }
                    // 4xx: the server understood and refused. Anything else (5xx, a proxy's error page) says nothing
                    // about the login.
                    r.code in 400..499 -> RefreshAttempt.Refused
                    else -> RefreshAttempt.Failed
                }
            }
        } catch (e: IOException) {
            RefreshAttempt.Failed
        } catch (e: SerializationException) {
            RefreshAttempt.Failed
        } catch (e: IllegalArgumentException) {
            RefreshAttempt.Failed
        }

    private fun String.toHttpUrlWithQuery(apiKey: String): HttpUrl =
        toHttpUrl().newBuilder()
            .addQueryParameter("apiKey", apiKey)
            .addQueryParameter("pluginName", PLUGIN_NAME)
            .build()

    private fun Request.withToken(token: String): Request =
        newBuilder().header("Authorization", "Bearer $token").build()

    private fun Response.priorResponseCount(): Int {
        var count = 1
        var prior = priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}

const val PLUGIN_NAME = "Klover"

fun buildPlainClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .build()

fun buildKavitaClient(sessions: SessionManager): OkHttpClient {
    val plain = buildPlainClient()
    return plain.newBuilder()
        .readTimeout(60, TimeUnit.SECONDS) // the server extracts archives on first page request
        .addInterceptor(ServerInterceptor(sessions))
        .authenticator(TokenAuthenticator(sessions, plain))
        .build()
}

internal sealed interface RefreshAttempt {
    data class Renewed(val user: UserDto) : RefreshAttempt

    data object Refused : RefreshAttempt

    data object Failed : RefreshAttempt
}

internal sealed interface RefreshOutcome {
    data class Renewed(val user: UserDto) : RefreshOutcome
    data object Rejected : RefreshOutcome
    data object Unavailable : RefreshOutcome
}

internal fun resolveRefresh(attempts: Sequence<RefreshAttempt>): RefreshOutcome {
    var failed = false
    for (attempt in attempts) {
        when (attempt) {
            is RefreshAttempt.Renewed -> return RefreshOutcome.Renewed(attempt.user)
            RefreshAttempt.Refused -> Unit
            RefreshAttempt.Failed -> failed = true
        }
    }
    // No try at all (no refresh token and no key) is as rejected as it gets: there is nothing to renew with.
    return if (failed) RefreshOutcome.Unavailable else RefreshOutcome.Rejected
}
