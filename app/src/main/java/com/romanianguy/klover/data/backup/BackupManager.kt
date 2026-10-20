package com.romanianguy.klover.data.backup

import com.romanianguy.klover.data.network.KavitaJson
import com.romanianguy.klover.data.prefs.SeriesReaderStore
import com.romanianguy.klover.data.prefs.SettingsStore
import com.romanianguy.klover.data.session.AccountStorage
import kotlinx.serialization.json.JsonElement

data class RestoreSummary(val files: Int, val settings: Int, val series: Int)

// [ensure] are the stores that register themselves with the account storage when they are made: asking for them here
// makes sure they all exist before the backup looks
class BackupManager(
    private val accounts: AccountStorage,
    private val settings: SettingsStore,
    private val series: SeriesReaderStore,
    private val appVersion: String,
    @Suppress("unused") private val ensure: List<Any> = emptyList(),
    private val now: () -> Long = System::currentTimeMillis,
) {
    private companion object {
        // waiting for the server: tied to this moment and this session, not part of what a reader owns
        val SKIPPED = setOf("pending-marks.json")
    }

    private fun sources() = accounts.backupSources.filter { it.name !in SKIPPED }

    suspend fun export(): String {
        if (accounts.key.value == null) throw BackupException(BackupProblem.SignedOut)
        val files = sources().associate { it.name to KavitaJson.parseToJsonElement(it.exportJson()) }
        return BackupFile(
            exportedAt = now(),
            appVersion = appVersion,
            files = files,
            settings = settings.exportPrefs(),
            series = series.exportAll(),
        ).encode()
    }

    // checks everything first, so a damaged file changes nothing
    suspend fun import(text: String): RestoreSummary {
        if (accounts.key.value == null) throw BackupException(BackupProblem.SignedOut)
        val backup = decodeBackup(text)
        val known = sources().associateBy { it.name }
        val texts = backup.files.filterKeys { it in known }
            .mapValues { KavitaJson.encodeToString(JsonElement.serializer(), it.value) }
        if (texts.any { (name, json) -> !known.getValue(name).accepts(json) }) throw BackupException(BackupProblem.Unreadable)
        texts.forEach { (name, json) -> known.getValue(name).importJson(json) }
        return RestoreSummary(
            files = texts.size,
            settings = settings.importPrefs(backup.settings),
            series = series.importAll(backup.series),
        )
    }
}
