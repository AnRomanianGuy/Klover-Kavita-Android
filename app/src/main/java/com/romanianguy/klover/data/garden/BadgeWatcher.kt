package com.romanianguy.klover.data.garden

import com.romanianguy.klover.data.history.HistoryEntry
import com.romanianguy.klover.data.prefs.AppSettings
import com.romanianguy.klover.data.session.AccountStore
import com.romanianguy.klover.data.stats.DayStat
import com.romanianguy.klover.data.stats.summarize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import java.time.LocalDate

internal const val INITIALIZED = "_init"

class BadgeUpdate(val seen: List<String>, val celebrate: List<Badge>)

// the first look keeps everything silently so a reader with history isn't showered with badges; each is celebrated
// once, and only when [celebrate] allows
fun updateBadges(seen: List<String>, earnedNow: Set<Badge>, celebrate: Boolean): BadgeUpdate? {
    val firstLook = INITIALIZED !in seen
    val fresh = earnedNow.filter { it.name !in seen }
    val kept = (seen + INITIALIZED + fresh.map { it.name }).distinct()
    if (kept.size == seen.size) return null
    return BadgeUpdate(kept, if (firstLook || !celebrate) emptyList() else fresh)
}

fun goalMet(weekSeconds: Long, goalMinutes: Int): Boolean = goalMinutes > 0 && weekSeconds >= goalMinutes * 60L

// waits a moment after changes, so switching accounts (whose data arrives piece by piece) isn't taken for earning
// something
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class BadgeWatcher(
    private val store: AccountStore<List<String>>,
    history: Flow<List<HistoryEntry>>,
    days: Flow<Map<String, DayStat>>,
    settings: Flow<AppSettings>,
    private val accountKey: StateFlow<String?>,
    scope: CoroutineScope,
    private val today: () -> LocalDate = { LocalDate.now() },
    settleMillis: Long = 500,
) {
    val earned: Flow<Set<Badge>> = kotlinx.coroutines.flow.flow {
        store.data.collect { names -> emit(Badge.entries.filter { it.name in names }.toSet()) }
    }

    private val _celebrate = MutableSharedFlow<Badge>(extraBufferCapacity = 16)

    val justEarned: SharedFlow<Badge> = _celebrate.asSharedFlow()

    init {
        scope.launch {
            combine(history, days, settings, accountKey) { entries, stats, prefs, key -> Inputs(entries, stats, prefs, key) }
                .debounce(settleMillis)
                .collect { inputs ->
                    if (inputs.key == null || accountKey.value != inputs.key) return@collect
                    val summary = summarize(inputs.days, today())
                    val facts = badgeFacts(
                        history = inputs.history,
                        summary = summary,
                        bestDaySeconds = inputs.days.values.maxOfOrNull { it.seconds } ?: 0L,
                        goalMetWeeks = if (goalMet(summary.weekSeconds, inputs.prefs.weeklyGoalMinutes)) 1 else 0,
                    )
                    val now = Badge.entries.filter { it.earned(facts) }.toSet()
                    val update = updateBadges(store.data.value, now, inputs.prefs.garden) ?: return@collect
                    store.set(update.seen)
                    update.celebrate.forEach { _celebrate.tryEmit(it) }
                }
        }
    }

    private class Inputs(
        val history: List<HistoryEntry>,
        val days: Map<String, DayStat>,
        val prefs: AppSettings,
        val key: String?,
    )
}
