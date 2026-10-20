package com.romanianguy.klover.data.update

import com.romanianguy.klover.data.network.KavitaJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

// a newer version of the app, as found on its releases page
@Serializable
data class UpdateInfo(val version: String, val name: String, val notes: String, val url: String)

sealed interface CheckResult {
    data class Found(val info: UpdateInfo) : CheckResult

    data object UpToDate : CheckResult

    data object Failed : CheckResult
}

@Serializable
private class GitHubRelease(
    @SerialName("tag_name") val tag: String,
    val name: String? = null,
    val body: String? = null,
    @SerialName("html_url") val url: String,
    val draft: Boolean = false,
)

@Serializable
private class UpdateFile(val checkedAt: Long = 0, val found: UpdateInfo? = null, val skipped: String? = null)

// Asks the releases page of the app whether there is a newer version than [current], at most once in [CHECK_EVERY_MS],
// and remembers the answer, so the announcement is there at the next start even without a connection. It only reads; the
// person is sent to the page to download. Nothing about them is sent: the address is public and the request is plain
class UpdateChecker(
    private val client: OkHttpClient,
    private val url: String,
    private val current: String,
    private val file: File,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private var saved = load()
    private val _announcement = MutableStateFlow(visible(saved))

    // the newer version to tell about, or null: none, or the person skipped it
    val announcement: StateFlow<UpdateInfo?> = _announcement

    suspend fun checkIfDue(): CheckResult? {
        if (now() - saved.checkedAt < CHECK_EVERY_MS) return null
        return check()
    }

    suspend fun check(): CheckResult {
        val releases = withContext(Dispatchers.IO) { fetch() } ?: return CheckResult.Failed
        val newest = releases.filter { !it.draft }
            .mapNotNull { r -> parseVersion(r.tag)?.let { it to r } }
            .maxByOrNull { it.first }?.second
        val found = newest?.takeIf { isNewerVersion(it.tag, current) }
            ?.let { UpdateInfo(it.tag.trim().removePrefix("v"), it.name.orEmpty().ifBlank { "Klover " + it.tag }, it.body.orEmpty().trim(), it.url) }
        update(UpdateFile(now(), found, saved.skipped))
        return if (found != null) CheckResult.Found(found) else CheckResult.UpToDate
    }

    // the announcement of this version goes away, and comes back for a later one
    fun skip(version: String) = update(UpdateFile(saved.checkedAt, saved.found, version))

    // asking by hand shows a version that was skipped again
    fun clearSkip() = update(UpdateFile(saved.checkedAt, saved.found, null))

    private fun fetch(): List<GitHubRelease>? = try {
        val request = Request.Builder().url(url)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Klover/$current")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) null else KavitaJson.decodeFromString(ListSerializer(GitHubRelease.serializer()), response.body.string())
        }
    } catch (e: IOException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }

    private fun visible(state: UpdateFile): UpdateInfo? =
        state.found?.takeIf { isNewerVersion(it.version, current) && it.version != state.skipped }

    private fun update(next: UpdateFile) {
        saved = next
        _announcement.value = visible(next)
        runCatching {
            file.parentFile?.mkdirs()
            val temp = File(file.path + ".tmp")
            temp.writeText(KavitaJson.encodeToString(UpdateFile.serializer(), next))
            if (!temp.renameTo(file)) {
                temp.copyTo(file, overwrite = true)
                temp.delete()
            }
        }
    }

    private fun load(): UpdateFile = runCatching {
        if (file.isFile) KavitaJson.decodeFromString(UpdateFile.serializer(), file.readText()) else UpdateFile()
    }.getOrDefault(UpdateFile())

    companion object {
        const val CHECK_EVERY_MS = 12L * 60 * 60 * 1000
    }
}
