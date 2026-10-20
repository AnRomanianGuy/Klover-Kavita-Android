package com.romanianguy.klover.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.romanianguy.klover.data.model.LibraryKind
import com.romanianguy.klover.data.session.AccountStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val Context.seriesReaderStore by preferencesDataStore(name = "series_reader",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() })

data class SeriesReaderPrefs(
    val direction: ReadingDirection? = null,
    val pageMode: PageMode? = null,
    val style: ReadingStyle? = null,
    val zoom: DoubleTapZoom? = null,
) {
    val isEmpty: Boolean get() = direction == null && pageMode == null && style == null && zoom == null

    fun encode(): String = listOf(direction, pageMode, style, zoom).joinToString(",") { it?.name ?: "-" }

    companion object {
        fun decode(raw: String?): SeriesReaderPrefs {
            val parts = raw?.split(",").orEmpty()
            fun <E : Enum<E>> pick(index: Int, values: Array<E>): E? =
                parts.getOrNull(index)?.let { name -> values.firstOrNull { it.name == name } }
            return SeriesReaderPrefs(
                direction = pick(0, ReadingDirection.entries.toTypedArray()),
                pageMode = pick(1, PageMode.entries.toTypedArray()),
                style = pick(2, ReadingStyle.entries.toTypedArray()),
                zoom = pick(3, DoubleTapZoom.entries.toTypedArray()),
            )
        }
    }
}

fun defaultDirectionFor(libraryType: Int?): ReadingDirection? = when (libraryType?.let(LibraryKind::from)) {
    LibraryKind.Manga -> ReadingDirection.RightToLeft
    LibraryKind.Comic, LibraryKind.ComicVine -> ReadingDirection.LeftToRight
    else -> null
}

// a choice for the series wins, then the library's default (manga right to left, comics left to right) when
// by-library is on, then the general setting
fun effectiveReaderSettings(
    global: AppSettings,
    series: SeriesReaderPrefs,
    libraryType: Int?,
): AppSettings = global.copy(
    readingDirection = series.direction
        ?: (if (global.autoDirection) defaultDirectionFor(libraryType) else null)
        ?: global.readingDirection,
    pageMode = series.pageMode ?: global.pageMode,
    readingStyle = series.style ?: global.readingStyle,
    doubleTapZoom = series.zoom ?: global.doubleTapZoom,
)

internal fun seriesKey(account: String, seriesId: Int) = stringPreferencesKey("series_${account}_$seriesId")

private val LEGACY_KEY = Regex("series_(\\d+)")

// per series and per account, on the device only; data from before accounts goes to the first account signed in
class SeriesReaderStore(
    private val store: DataStore<Preferences>,
    private val accounts: AccountStorage,
    scope: CoroutineScope,
) {
    init {
        scope.launch {
            val first = accounts.key.filterNotNull().first()
            adoptLegacy(first)
        }
    }

    private suspend fun adoptLegacy(account: String) {
        store.edit { stored ->
            for (entry in stored.asMap().entries.toList()) {
                val id = LEGACY_KEY.matchEntire(entry.key.name)?.groupValues?.get(1)?.toIntOrNull() ?: continue
                val target = seriesKey(account, id)
                if (stored[target] == null) stored[target] = entry.value as String
                stored.remove(stringPreferencesKey(entry.key.name))
            }
        }
    }

    fun prefs(seriesId: Int): Flow<SeriesReaderPrefs> =
        combine(store.data, accounts.key) { stored, account ->
            if (account == null) SeriesReaderPrefs() else SeriesReaderPrefs.decode(stored[seriesKey(account, seriesId)])
        }

    suspend fun update(seriesId: Int, change: (SeriesReaderPrefs) -> SeriesReaderPrefs) {
        val account = accounts.key.value ?: return // signed out: nobody to remember it for
        val key = seriesKey(account, seriesId)
        store.edit { stored ->
            val next = change(SeriesReaderPrefs.decode(stored[key]))
            if (next.isEmpty) stored.remove(key) else stored[key] = next.encode()
        }
    }

    suspend fun clear(seriesId: Int) = update(seriesId) { SeriesReaderPrefs() }

    // series id -> encoded choices, for the signed-in account
    suspend fun exportAll(): Map<String, String> {
        val account = accounts.key.value ?: return emptyMap()
        val prefix = "series_${account}_"
        return store.data.first().asMap().entries
            .filter { it.key.name.startsWith(prefix) && it.value is String }
            .associate { it.key.name.removePrefix(prefix) to it.value as String }
    }

    // replaces the choices of the signed-in account with the ones from a backup
    suspend fun importAll(entries: Map<String, String>): Int {
        val account = accounts.key.value ?: return 0
        val prefix = "series_${account}_"
        var taken = 0
        store.edit { stored ->
            stored.asMap().keys.filter { it.name.startsWith(prefix) }.forEach { stored.remove(it) }
            for ((id, value) in entries) {
                if (id.toIntOrNull() == null || value.isBlank()) continue
                stored[seriesKey(account, id.toInt())] = value
                taken++
            }
        }
        return taken
    }
}

fun SeriesReaderStore(context: Context, accounts: AccountStorage, scope: CoroutineScope) =
    SeriesReaderStore(context.seriesReaderStore, accounts, scope)
