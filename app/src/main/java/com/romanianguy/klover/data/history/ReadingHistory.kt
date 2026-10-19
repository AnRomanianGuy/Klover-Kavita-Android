package com.romanianguy.klover.data.history

import com.romanianguy.klover.data.garden.rollFor
import com.romanianguy.klover.data.session.AccountStorage
import com.romanianguy.klover.data.session.AccountStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

@Serializable
data class HistoryEntry(
    val chapterId: Int,
    val seriesId: Int,
    val seriesName: String,
    val title: String,
    val at: Long,
    val pages: Int = 0,
    val leaves: Int = 0,
    // flicked-through chapters don't count for clovers, badges or the recap; old entries do
    val read: Boolean = true,
)

private const val MAX_ENTRIES = 600

internal const val SAME_SITTING_MS = 12L * 60 * 60 * 1000

fun withEntry(entries: List<HistoryEntry>, entry: HistoryEntry): List<HistoryEntry> {
    val rest = entries.filterNot { it.chapterId == entry.chapterId && abs(entry.at - it.at) < SAME_SITTING_MS }
    return (rest + entry).sortedBy { it.at }.takeLast(MAX_ENTRIES)
}

fun groupByDay(entries: List<HistoryEntry>, zone: ZoneId = ZoneId.systemDefault()): List<Pair<LocalDate, List<HistoryEntry>>> =
    entries.sortedByDescending { it.at }
        .groupBy { Instant.ofEpochMilli(it.at).atZone(zone).toLocalDate() }
        .toList()
        .sortedByDescending { it.first }

// a skim is listed but grows nothing and keeps a clover from a moment ago; reading it properly later replaces it
fun withFinishedChapter(entries: List<HistoryEntry>, entry: HistoryEntry): List<HistoryEntry> {
    val replaced = entries.firstOrNull { it.chapterId == entry.chapterId && abs(entry.at - it.at) < SAME_SITTING_MS }
    if (!entry.read && replaced?.read == true) return entries
    val leaves = when {
        !entry.read -> 0
        else -> replaced?.takeIf { it.read }?.leaves?.takeIf { it > 0 } ?: rollFor(entries, entry)
    }
    return withEntry(entries, entry.copy(leaves = leaves))
}

class ReadingHistory(storage: AccountStorage, scope: CoroutineScope) {
    private val store = AccountStore(
        storage,
        "history.json",
        ListSerializer(HistoryEntry.serializer()),
        emptyList(),
        scope,
    )

    val entries: StateFlow<List<HistoryEntry>> = store.data

    // the clover is decided once and kept with the entry; finishing the same chapter again in a sitting replaces the
    // entry and keeps it
    fun add(entry: HistoryEntry) = store.update { entries -> withFinishedChapter(entries, entry) }

    fun remove(entry: HistoryEntry) = store.update { list -> list - entry }

    fun clear() = store.set(emptyList())

    fun flush() = store.flush()
}
