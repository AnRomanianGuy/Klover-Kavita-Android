package com.romanianguy.klover.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

// one back stack per tab, flattened (least recently used first) into backStack for a single NavDisplay
@Stable
class AppNavigator(
    initialStacks: Map<TopLevelDestination, List<NavKey>>,
    initialTab: TopLevelDestination,
) {
    // Ordered by recency of use: the last entry is the tab on screen.
    private val stacks = linkedMapOf<TopLevelDestination, MutableList<NavKey>>()

    var currentTab by mutableStateOf(initialTab)
        private set

    val backStack = mutableStateListOf<NavKey>()

    init {
        TopLevelDestination.entries.forEach { tab ->
            stacks[tab] = (initialStacks[tab]?.takeIf { it.isNotEmpty() } ?: listOf(tab.key)).toMutableList()
        }
        moveToEnd(initialTab)
        rebuild()
    }

    val top: NavKey get() = backStack.last()

    fun select(tab: TopLevelDestination) {
        if (tab == currentTab) {
            // Re-selecting the active tab pops back to its root, like the platform apps do.
            stacks.getValue(tab).let { while (it.size > 1) it.removeAt(it.lastIndex) }
        } else {
            currentTab = tab
            moveToEnd(tab)
        }
        rebuild()
    }

    fun push(key: NavKey) {
        stacks.getValue(currentTab).add(key)
        rebuild()
    }

    // swaps the top entry of the current tab instead of stacking on it (used for the list-detail detail pane)
    fun replaceTop(key: NavKey) {
        val stack = stacks.getValue(currentTab)
        stack[stack.lastIndex] = key
        rebuild()
    }

    fun pop(): Boolean {
        val stack = stacks.getValue(currentTab)
        return when {
            stack.size > 1 -> {
                stack.removeAt(stack.lastIndex)
                rebuild()
                true
            }

            currentTab != TopLevelDestination.Home -> {
                currentTab = TopLevelDestination.Home
                moveToEnd(TopLevelDestination.Home)
                rebuild()
                true
            }

            else -> false
        }
    }

    private fun moveToEnd(tab: TopLevelDestination) {
        val stack = stacks.remove(tab) ?: mutableListOf(tab.key)
        stacks[tab] = stack
    }

    private fun rebuild() {
        backStack.clear()
        stacks.values.forEach { backStack.addAll(it) }
    }

    internal fun snapshot(): Snapshot = Snapshot(
        tab = currentTab.name,
        stacks = stacks.mapKeys { it.key.name }.mapValues { it.value.toList() },
    )

    internal data class Snapshot(val tab: String, val stacks: Map<String, List<NavKey>>)

    companion object {
        private val json = Json { serializersModule = NavSerializersModule }
        private val stacksSerializer =
            MapSerializer(String.serializer(), ListSerializer(PolymorphicSerializer(NavKey::class)))

        val Saver: Saver<AppNavigator, Any> = Saver(
            save = { navigator ->
                val snap = navigator.snapshot()
                listOf(snap.tab, json.encodeToString(stacksSerializer, snap.stacks))
            },
            restore = { saved ->
                @Suppress("UNCHECKED_CAST")
                val parts = saved as List<String>
                val stacks = runCatching { json.decodeFromString(stacksSerializer, parts[1]) }.getOrDefault(emptyMap())
                    .mapNotNull { (name, keys) ->
                        TopLevelDestination.entries.firstOrNull { it.name == name }?.let { it to keys }
                    }.toMap()
                val tab = TopLevelDestination.entries.firstOrNull { it.name == parts[0] } ?: TopLevelDestination.Home
                AppNavigator(stacks, tab)
            },
        )
    }
}

@Composable
fun rememberAppNavigator(startTab: TopLevelDestination = TopLevelDestination.Home): AppNavigator =
    rememberSaveable(saver = AppNavigator.Saver) { AppNavigator(emptyMap(), startTab) }
