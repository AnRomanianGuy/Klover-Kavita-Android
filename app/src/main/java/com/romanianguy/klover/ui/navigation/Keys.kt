package com.romanianguy.klover.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.NavKey
import com.romanianguy.klover.R
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

@Serializable data object HomeKey : NavKey
@Serializable data object LibraryKey : NavKey
@Serializable data object WantToReadKey : NavKey
@Serializable data object DownloadsKey : NavKey
@Serializable data object SettingsKey : NavKey
@Serializable data object StatsKey : NavKey
@Serializable data object HistoryKey : NavKey
@Serializable data object GardenKey : NavKey
@Serializable data class SeriesKey(val seriesId: Int) : NavKey
@Serializable data class ReaderKey(val chapterId: Int, val startPage: Int = -1) : NavKey

enum class TopLevelDestination(
    val key: NavKey,
    @StringRes val label: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    Home(HomeKey, R.string.nav_home, Icons.Outlined.Home, Icons.Filled.Home),
    Library(
        LibraryKey,
        R.string.nav_library,
        Icons.AutoMirrored.Outlined.LibraryBooks,
        Icons.AutoMirrored.Filled.LibraryBooks,
    ),
    WantToRead(WantToReadKey, R.string.nav_want_to_read, Icons.Outlined.BookmarkBorder, Icons.Filled.Bookmark),
    Downloads(DownloadsKey, R.string.nav_downloads, Icons.Outlined.Download, Icons.Filled.Download),
    Settings(SettingsKey, R.string.nav_settings, Icons.Outlined.Settings, Icons.Filled.Settings),
}

// polymorphic registration so the back stack can be saved and restored (needed for process death)
val NavSerializersModule = SerializersModule {
    polymorphic(NavKey::class) {
        subclass(HomeKey::class)
        subclass(LibraryKey::class)
        subclass(WantToReadKey::class)
        subclass(DownloadsKey::class)
        subclass(SettingsKey::class)
        subclass(StatsKey::class)
        subclass(HistoryKey::class)
        subclass(GardenKey::class)
        subclass(SeriesKey::class)
        subclass(ReaderKey::class)
    }
}
