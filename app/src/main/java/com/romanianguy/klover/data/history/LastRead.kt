package com.romanianguy.klover.data.history

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.romanianguy.klover.MainActivity
import com.romanianguy.klover.R
import com.romanianguy.klover.data.session.AccountStorage
import com.romanianguy.klover.data.session.AccountStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

// the chapter that was opened last, so "Continue" can open it from anywhere
@Serializable
data class LastRead(
    val chapterId: Int = 0,
    val seriesId: Int = 0,
    val seriesName: String = "",
    val title: String = "",
    val at: Long = 0,
) {
    val exists: Boolean get() = chapterId > 0

    // "Series · Chapter 4", or just what there is of it - and not the same words twice (a book often has its own name
    // as title)
    val label: String
        get() {
            val repeats = title.isBlank() || seriesName.contains(title, ignoreCase = true) || title.contains(seriesName, ignoreCase = true)
            return if (repeats) seriesName.ifBlank { title } else listOf(seriesName, title).filter { it.isNotBlank() }.joinToString(" · ")
        }

    val shortLabel: String get() = seriesName.ifBlank { title }
}

class LastReadStore(storage: AccountStorage, scope: CoroutineScope) {
    private val store = AccountStore(storage, "last-read.json", LastRead.serializer(), LastRead(), scope)
    val data: StateFlow<LastRead> = store.data

    fun set(value: LastRead) = store.set(value)
}

object ContinueShortcut {
    const val ACTION = "com.romanianguy.klover.CONTINUE"
    const val EXTRA_CHAPTER = "chapter"
    const val EXTRA_ACCOUNT = "account"
    private const val ID = "continue"

    fun update(context: Context, last: LastRead?, account: String?) {
        if (last == null || !last.exists || account == null) {
            runCatching { ShortcutManagerCompat.removeDynamicShortcuts(context, listOf(ID)) }
            return
        }
        val intent = Intent(context, MainActivity::class.java)
            .setAction(ACTION)
            .putExtra(EXTRA_CHAPTER, last.chapterId)
            .putExtra(EXTRA_ACCOUNT, account)
        val shortcut = ShortcutInfoCompat.Builder(context, ID)
            .setShortLabel(context.getString(R.string.shortcut_continue))
            .setLongLabel(context.getString(R.string.shortcut_continue_long, last.label.ifBlank { context.getString(R.string.app_name) }))
            .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
            .setIntent(intent)
            .build()
        runCatching { ShortcutManagerCompat.pushDynamicShortcut(context, shortcut) }
    }
}
