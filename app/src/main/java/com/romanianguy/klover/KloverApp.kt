package com.romanianguy.klover

import android.app.Application
import android.os.Process
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import com.romanianguy.klover.data.downloads.DownloadManager
import com.romanianguy.klover.data.garden.BadgeWatcher
import com.romanianguy.klover.data.history.ContinueShortcut
import com.romanianguy.klover.data.history.LastReadStore
import com.romanianguy.klover.data.marks.MarkQueue
import com.romanianguy.klover.data.prefs.SeriesReaderStore
import com.romanianguy.klover.data.prefs.SettingsStore
import com.romanianguy.klover.data.profiles.ProfileStore
import com.romanianguy.klover.data.session.AccountStorage
import com.romanianguy.klover.data.session.SessionManager
import com.romanianguy.klover.data.stats.ReadingAccuracy
import com.romanianguy.klover.data.stats.ReadingPace
import com.romanianguy.klover.data.stats.ReadingStats
import com.romanianguy.klover.di.appModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class KloverApp : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@KloverApp)
            modules(appModule)
        }
        // Only the container is built here, on the thread the first window waits for. Everything the app wants
        // running from the start
        // (stores, watchers, the download manager, the mark queue's network layer) takes a good part of a second to
        // construct, so it is
        // created off the main thread at background priority, after the settings and session the first screen needs.
        Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            startServices()
        }, "klover-startup").start()
    }

    private fun startServices() {
        get<SettingsStore>()
        get<SessionManager>()
        // Created early (not on first use) so interrupted downloads pick up again as soon as the app starts.
        get<DownloadManager>().sweepRead()
        // Reading data is created at once (not when a book is first opened), so data from before accounts goes to
        // whichever account
        // signs in first.
        get<ProfileStore>() // follows the signed-in account from the start
        // The launcher's long-press "Continue" follows the last chapter opened by the signed-in account.
        val lastRead = get<LastReadStore>()
        val accounts = get<AccountStorage>()
        val scope = get<CoroutineScope>()
        scope.launch {
            combine(lastRead.data, accounts.key) { last, key -> last to key }
                .collect { (last, key) -> ContinueShortcut.update(this@KloverApp, last, key) }
        }
        get<BadgeWatcher>() // watches for badges from the start
        get<ReadingStats>()
        // Marks kept for later start going out at once, and again whenever the network comes back.
        val marks = get<MarkQueue>()
        scope.launch {
            get<DownloadManager>().network.map { it.online }.distinctUntilChanged().filter { it }.collect { marks.nudge() }
        }
        get<ReadingPace>()
        get<ReadingAccuracy>()
        get<SeriesReaderStore>() // moves the choices from before accounts to the first account
    }

    // covers load through the same OkHttp client as the API so they share the session and its auth
    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val client = get<OkHttpClient>()
        return ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { client }))
                add(SvgDecoder.Factory())
            }
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.25).build() }
            .crossfade(true)
            .build()
    }
}
