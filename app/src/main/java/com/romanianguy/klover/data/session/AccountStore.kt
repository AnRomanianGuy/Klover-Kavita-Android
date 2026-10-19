package com.romanianguy.klover.data.session

import com.romanianguy.klover.data.network.KavitaJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import java.io.File
import java.security.MessageDigest

// data per account (a user on a server) in its own folder, so accounts never share stats, pace or streaks
interface AccountStorage {
    val key: StateFlow<String?>

    fun file(name: String, key: String): File

    fun legacy(name: String): File?
}

fun accountKey(session: Session): String {
    val who = if (session.userId > 0) "id${session.userId}" else "name:" + session.username.trim().lowercase()
    val text = session.serverUrl.trim().trimEnd('/').lowercase() + "|" + who
    return MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).take(8).joinToString("") { "%02x".format(it) }
}

class AccountFiles(private val root: File, sessions: SessionManager, scope: CoroutineScope) : AccountStorage {
    override val key: StateFlow<String?> = sessions.state
        .map { (it as? SessionState.SignedIn)?.session?.let(::accountKey) }
        .distinctUntilChanged()
        .stateIn(
            scope,
            SharingStarted.Eagerly,
            (sessions.state.value as? SessionState.SignedIn)?.session?.let(::accountKey),
        )

    override fun file(name: String, key: String) = File(File(root, key), name)

    override fun legacy(name: String): File? = File(root, name).takeIf { it.isFile }
}

// one file for a pretend account (tests)
class SingleFile(private val file: File) : AccountStorage {
    override val key: StateFlow<String?> = MutableStateFlow("single")
    override fun file(name: String, key: String) = file
    override fun legacy(name: String): File? = null
}

// JSON per account, held in memory and saved shortly after changes; switching accounts saves the old one at once and
// loads the new.
// data from before accounts goes to the first account signed in
@OptIn(FlowPreview::class)
class AccountStore<T>(
    private val storage: AccountStorage,
    private val name: String,
    private val serializer: KSerializer<T>,
    private val empty: T,
    scope: CoroutineScope,
) {
    private class Holder<T>(val key: String?, val value: T)

    private val lock = Any()
    private val holder = MutableStateFlow(Holder<T>(null, empty))
    private val _data = MutableStateFlow(empty)

    val data: StateFlow<T> = _data

    init {
        switchTo(storage.key.value)
        scope.launch { storage.key.drop(1).collect { switchTo(it) } }
        scope.launch { holder.drop(1).debounce(1_500).collect { runCatching { save(it) } } }
    }

    fun update(block: (T) -> T) {
        synchronized(lock) {
            val h = holder.value
            val next = block(h.value)
            holder.value = Holder(h.key, next)
            _data.value = next
        }
    }

    fun set(value: T) = update { value }

    fun flush() {
        runCatching { save(holder.value) }
    }

    private fun switchTo(key: String?) {
        synchronized(lock) {
            val old = holder.value
            if (old.key == key && holder.value.key != null) return
            if (old.key != null) runCatching { save(old) }
            val loaded = if (key == null) empty else load(key)
            holder.value = Holder(key, loaded)
            _data.value = loaded
        }
    }

    private fun load(key: String): T = runCatching {
        val file = storage.file(name, key)
        if (!file.isFile) adoptLegacy(file)
        if (file.isFile) KavitaJson.decodeFromString(serializer, file.readText()) else empty
    }.getOrDefault(empty)

    private fun adoptLegacy(target: File) {
        val legacy = storage.legacy(name) ?: return
        if (legacy.path == target.path) return
        target.parentFile?.mkdirs()
        if (!legacy.renameTo(target)) {
            legacy.copyTo(target, overwrite = true)
            legacy.delete()
        }
    }

    private fun save(h: Holder<T>) {
        val key = h.key ?: return // nobody signed in: nothing to keep, and nowhere to keep it
        val file = storage.file(name, key)
        file.parentFile?.mkdirs()
        val temp = File(file.path + ".tmp")
        temp.writeText(KavitaJson.encodeToString(serializer, h.value))
        if (!temp.renameTo(file)) {
            temp.copyTo(file, overwrite = true)
            temp.delete()
        }
    }
}
