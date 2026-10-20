package com.romanianguy.klover.data.profiles

import com.romanianguy.klover.data.network.KavitaJson
import com.romanianguy.klover.data.session.Session
import com.romanianguy.klover.data.session.accountKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File

const val PROFILE_TONES = 16

const val PROFILE_SHAPES = 20

// more than this is refused
const val MAX_PROFILES = 12

// Kavita won't list other users, so the accounts used here are the profiles. Tokens are stored encrypted
@Serializable
data class SavedProfile(
    val key: String,
    val serverUrl: String,
    val username: String,
    val userId: Int = 0,
    val name: String = "",
    val tone: Int = 0,
    val shape: Int = 0,
    val photo: String? = null,
    // the event whose border the profile wears while that event is on, like "halloween-2026"
    val seasonal: String? = null,
    // token, refresh token and API key, encrypted
    val sealed: String,
    val addedAt: Long = 0,
    val usedAt: Long = 0,
) {
    val displayName: String get() = name.trim().ifBlank { username }

    val initial: String get() = displayName.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?"
}

@Serializable
private class Secrets(val token: String, val refreshToken: String? = null, val apiKey: String = "")

// follows the signed-in session so the active account is always saved; kept in its own small file
class ProfileStore(
    private val file: File,
    val photoDir: File,
    scope: CoroutineScope,
    session: Flow<Session?>,
    private val signIn: suspend (Session) -> Unit,
    private val seal: (String) -> String,
    private val unseal: (String) -> String?,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    private val _profiles = MutableStateFlow(load())

    // in the order added, so the picker holds still
    val profiles: StateFlow<List<SavedProfile>> = _profiles

    private var switchedTo: String? = null
    private var switchedAt = 0L

    private val _ended = MutableStateFlow<String?>(null)

    // switched to, but the server turned the login down
    val ended: StateFlow<String?> = _ended

    private val _activeKey = MutableStateFlow<String?>(null)

    val activeKey: StateFlow<String?> = _activeKey

    init {
        scope.launch {
            session.collect { s ->
                _activeKey.value = s?.let(::accountKey)
                if (s != null) {
                    _ended.value = null
                    remember(s)
                } else if (switchedTo != null && now() - switchedAt < ENDED_WITHIN_MS) {
                    _ended.value = switchedTo
                }
            }
        }
    }

    internal fun remember(session: Session) {
        val key = accountKey(session)
        val sealed = seal(KavitaJson.encodeToString(Secrets.serializer(), Secrets(session.token, session.refreshToken, session.apiKey)))
        change { list ->
            val existing = list.firstOrNull { it.key == key }
            if (existing != null) {
                list.map {
                    if (it.key == key) it.copy(
                        serverUrl = session.serverUrl,
                        username = session.username,
                        userId = session.userId,
                        sealed = sealed,
                        usedAt = now(),
                    ) else it
                }
            } else {
                // next colour in turn so a family doesn't share one ring
                val profile = SavedProfile(
                    key = key,
                    serverUrl = session.serverUrl,
                    username = session.username,
                    userId = session.userId,
                    tone = list.size % PROFILE_TONES,
                    sealed = sealed,
                    addedAt = now(),
                    usedAt = now(),
                )
                (list + profile).takeLast(MAX_PROFILES)
            }
        }
    }

    fun forServer(serverUrl: String?): List<SavedProfile> =
        _profiles.value.filter { serverUrl == null || sameServer(it.serverUrl, serverUrl) }

    suspend fun switchTo(key: String): Boolean {
        val profile = _profiles.value.firstOrNull { it.key == key } ?: return false
        val secrets = unseal(profile.sealed)
            ?.let { runCatching { KavitaJson.decodeFromString(Secrets.serializer(), it) }.getOrNull() }
            ?: return false
        switchedTo = key
        switchedAt = now()
        _ended.value = null
        signIn(
            Session(
                serverUrl = profile.serverUrl,
                token = secrets.token,
                refreshToken = secrets.refreshToken,
                apiKey = secrets.apiKey,
                username = profile.username,
                userId = profile.userId,
            ),
        )
        return true
    }

    fun rename(key: String, name: String) = edit(key) { it.copy(name = name.take(24)) }

    fun setShape(key: String, shape: Int) = edit(key) { it.copy(shape = shape.coerceIn(0, PROFILE_SHAPES - 1)) }

    fun setTone(key: String, tone: Int) = edit(key) { it.copy(tone = tone.coerceIn(0, PROFILE_TONES - 1)) }

    fun setSeasonal(key: String, event: String?) = edit(key) { it.copy(seasonal = event) }

    fun setPhoto(key: String, photo: String?) {
        val old = _profiles.value.firstOrNull { it.key == key }?.photo
        edit(key) { it.copy(photo = photo) }
        if (old != null && old != photo) File(photoDir, old).delete()
    }

    // reading statistics stay with the account
    fun remove(key: String) {
        _profiles.value.firstOrNull { it.key == key }?.photo?.let { File(photoDir, it).delete() }
        change { list -> list.filterNot { it.key == key } }
    }

    private fun edit(key: String, block: (SavedProfile) -> SavedProfile) =
        change { list -> list.map { if (it.key == key) block(it) else it } }

    private fun change(block: (List<SavedProfile>) -> List<SavedProfile>) {
        synchronized(this) {
            val next = block(_profiles.value).sortedBy { it.addedAt }
            _profiles.update { next }
            save(next)
        }
    }

    private fun load(): List<SavedProfile> = runCatching {
        if (file.isFile) KavitaJson.decodeFromString(LIST, file.readText()).sortedBy { it.addedAt } else emptyList()
    }.getOrDefault(emptyList())

    private fun save(list: List<SavedProfile>) {
        runCatching {
            file.parentFile?.mkdirs()
            val temp = File(file.path + ".tmp")
            temp.writeText(KavitaJson.encodeToString(LIST, list))
            if (!temp.renameTo(file)) {
                temp.copyTo(file, overwrite = true)
                temp.delete()
            }
        }
    }

    private companion object {
        // ending this soon after a switch means the server turned it down
        const val ENDED_WITHIN_MS = 6_000L
        val LIST = ListSerializer(SavedProfile.serializer())
    }
}

// same server if only case or a trailing slash differs
fun sameServer(a: String, b: String): Boolean = a.trim().trimEnd('/').equals(b.trim().trimEnd('/'), ignoreCase = true)
