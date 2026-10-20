package com.romanianguy.klover.di

import com.romanianguy.klover.BuildConfig
import com.romanianguy.klover.data.backup.BackupManager
import com.romanianguy.klover.data.chapters.InnerReadMarks
import com.romanianguy.klover.data.downloads.DownloadFiles
import com.romanianguy.klover.data.downloads.DownloadManager
import com.romanianguy.klover.data.downloads.DownloadStore
import com.romanianguy.klover.data.files.PdfCache
import com.romanianguy.klover.data.garden.BadgeWatcher
import com.romanianguy.klover.data.history.Bookmarks
import com.romanianguy.klover.data.history.LastReadStore
import com.romanianguy.klover.data.history.ReadingHistory
import com.romanianguy.klover.data.lookup.ChapterLookup
import com.romanianguy.klover.data.marks.MarkKind
import com.romanianguy.klover.data.marks.MarkQueue
import com.romanianguy.klover.data.marks.PendingMark
import com.romanianguy.klover.data.network.KavitaApi
import com.romanianguy.klover.data.network.KavitaJson
import com.romanianguy.klover.data.network.PLACEHOLDER_BASE_URL
import com.romanianguy.klover.data.network.ServerUrls
import com.romanianguy.klover.data.network.buildKavitaClient
import com.romanianguy.klover.data.prefs.SeriesReaderStore
import com.romanianguy.klover.data.prefs.SettingsStore
import com.romanianguy.klover.data.profiles.ProfileStore
import com.romanianguy.klover.data.repo.AuthRepository
import com.romanianguy.klover.data.repo.LibraryRepository
import com.romanianguy.klover.data.repo.ReaderRepository
import com.romanianguy.klover.data.session.AccountFiles
import com.romanianguy.klover.data.session.AccountStorage
import com.romanianguy.klover.data.session.AccountStore
import com.romanianguy.klover.data.session.SecretBox
import com.romanianguy.klover.data.session.SessionManager
import com.romanianguy.klover.data.session.SessionState
import com.romanianguy.klover.data.stats.ReadingAccuracy
import com.romanianguy.klover.data.stats.ReadingPace
import com.romanianguy.klover.data.stats.ReadingStats
import com.romanianguy.klover.ui.about.AboutViewModel
import com.romanianguy.klover.ui.downloads.DownloadsViewModel
import com.romanianguy.klover.ui.home.HomeViewModel
import com.romanianguy.klover.ui.library.LibraryViewModel
import com.romanianguy.klover.ui.login.LoginViewModel
import com.romanianguy.klover.ui.reader.ReaderArgs
import com.romanianguy.klover.ui.reader.ReaderViewModel
import com.romanianguy.klover.ui.reader.book.sectionTextLength
import com.romanianguy.klover.ui.series.SeriesViewModel
import com.romanianguy.klover.ui.settings.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

val appModule = module {
    single { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    single { SessionManager(androidContext(), get()) }
    single { SettingsStore(androidContext(), get<AccountStorage>(), get()) }
    // The accounts used on this device, to switch between them from the profile picker.
    single {
        val sessions = get<SessionManager>()
        val box = SecretBox()
        ProfileStore(
            file = File(androidContext().filesDir, "profiles/profiles.json"),
            photoDir = File(androidContext().filesDir, "profiles/photos"),
            scope = get(),
            session = sessions.state.map { (it as? SessionState.SignedIn)?.session },
            signIn = { sessions.signIn(it) },
            seal = box::encrypt,
            unseal = box::decrypt,
        )
    }
    single { SeriesReaderStore(androidContext(), get<AccountStorage>(), get()) }

    single<OkHttpClient> { buildKavitaClient(get()) }
    single<KavitaApi> {
        Retrofit.Builder()
            .baseUrl(PLACEHOLDER_BASE_URL)
            .client(get<OkHttpClient>())
            .addConverterFactory(KavitaJson.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(KavitaApi::class.java)
    }
    single { ServerUrls { get<SessionManager>().current } }

    single { AuthRepository(get(), get()) }
    single { LibraryRepository(get()) }
    single { ReaderRepository(get()) }
    // Marks made while the server is away: kept per account and sent when it answers again.
    single {
        val storage = get<AccountStorage>()
        val repo = get<LibraryRepository>()
        MarkQueue(
            store = AccountStore(
                storage, "pending-marks.json",
                ListSerializer(PendingMark.serializer()),
                emptyList(), get(),
            ),
            scope = get(),
            enabled = { get<SettingsStore>().settings.first().queueMarks },
            accountKey = { storage.key.value },
            send = { m ->
                when (m.kind) {
                    MarkKind.Series -> repo.markSeries(m.seriesId, m.read)
                    MarkKind.Volume -> repo.markVolume(m.seriesId, m.id, m.read)
                    MarkKind.Chapter -> repo.markChapter(m.seriesId, m.id, m.read)
                }
            },
        )
    }
    // Public catalogues are asked through a client of their own: it never carries the Kavita sign-in.
    single(named("public")) {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .build()
    }
    single { ChapterLookup(get(named("public")), File(androidContext().cacheDir, "lookup")) }
    single { PdfCache(get(), File(androidContext().cacheDir, "pdf")) }
    // Reading statistics, pace and the accuracy of the estimates are kept per Kavita account.
    single<AccountStorage> { AccountFiles(File(androidContext().filesDir, "stats"), get(), get()) }
    single { ReadingStats(get(), get()) }
    single { LastReadStore(get(), get()) }
    single { ReadingHistory(get(), get()) }
    single { Bookmarks(get(), get()) }
    // Tells when a badge is earned for the first time (kept per account, so a badge is celebrated once).
    single {
        BadgeWatcher(
            store = AccountStore(
                get<AccountStorage>(), "badges.json",
                ListSerializer(kotlinx.serialization.serializer<String>()),
                emptyList(), get(),
            ),
            history = get<ReadingHistory>().entries,
            days = get<ReadingStats>().days,
            settings = get<SettingsStore>().settings,
            accountKey = get<AccountStorage>().key,
            scope = get(),
        )
    }
    single { ReadingPace(get(), get()) }
    single { ReadingAccuracy(get(), get()) }
    single { InnerReadMarks(get(), get()) }
    // Export and import of what belongs to the reader (stats, clovers, settings...), without the sign-in or downloads.
    single {
        BackupManager(
            accounts = get<AccountStorage>(),
            settings = get(),
            series = get(),
            appVersion = BuildConfig.VERSION_NAME,
            ensure = listOf(
                get<ReadingStats>(), get<LastReadStore>(), get<ReadingHistory>(), get<Bookmarks>(), get<BadgeWatcher>(),
                get<ReadingPace>(), get<ReadingAccuracy>(), get<InnerReadMarks>(),
            ),
        )
    }
    single { DownloadFiles(File(androidContext().filesDir, "downloads")) }
    single { DownloadStore(File(androidContext().filesDir, "downloads/index.json")) }
    single { DownloadManager(androidContext(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), ::sectionTextLength) }

    viewModel { LoginViewModel(get(), get()) }
    viewModel { HomeViewModel(get(), get()) }
    viewModel { params -> LibraryViewModel(get(), params.getOrNull<Boolean>() ?: false, get(), get()) }
    viewModel { params -> SeriesViewModel(params.get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { params ->
        ReaderViewModel(params.get<ReaderArgs>(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get())
    }
    viewModel { DownloadsViewModel(get(), get()) }
    viewModel { AboutViewModel(get(), get(), get()) }
    viewModel { SettingsViewModel(get(), get(), get(), androidContext(), get(), get()) }
}
