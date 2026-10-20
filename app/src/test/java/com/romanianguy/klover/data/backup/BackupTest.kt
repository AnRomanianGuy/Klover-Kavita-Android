package com.romanianguy.klover.data.backup

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.romanianguy.klover.data.network.KavitaJson
import com.romanianguy.klover.data.session.AccountStorage
import com.romanianguy.klover.data.session.AccountStore
import com.romanianguy.klover.data.session.BackupSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.nio.file.Files

class BackupTest {
    private class Folders(val root: File) : AccountStorage {
        val account = MutableStateFlow<String?>(null)
        val sources = mutableListOf<BackupSource>()
        override val key: StateFlow<String?> = account
        override val backupSources: List<BackupSource> get() = sources
        override fun register(source: BackupSource) {
            sources += source
        }

        override fun file(name: String, key: String) = File(File(root, key), name)
        override fun legacy(name: String): File? = null
    }

    private fun problemOf(text: String): BackupProblem {
        try {
            decodeBackup(text)
        } catch (e: BackupException) {
            return e.problem
        }
        fail("expected the file to be refused")
        error("unreachable")
    }

    @Test
    fun aBackupFileSurvivesBeingWrittenAndRead() {
        val original = BackupFile(
            exportedAt = 1234,
            appVersion = "1.0",
            files = mapOf("stats.json" to KavitaJson.parseToJsonElement("""{"days":[{"day":"2026-10-19","seconds":600}]}""")),
            settings = mapOf("theme_mode" to PrefEntry("string", JsonPrimitive("dark"))),
            series = mapOf("42" to "RTL,Dual,-,-"),
        )
        assertEquals(original, decodeBackup(original.encode()))
    }

    @Test
    fun somethingThatIsNotABackupIsRefused() {
        assertEquals(BackupProblem.NotABackup, problemOf("not json at all"))
        assertEquals(BackupProblem.NotABackup, problemOf("""{"hello":"world"}"""))
        assertEquals(BackupProblem.NotABackup, problemOf("""{"app":"another app","format":1}"""))
        assertEquals(BackupProblem.NotABackup, problemOf("[1,2,3]"))
    }

    @Test
    fun aBackupFromANewerAppIsRefusedInsteadOfHalfRead() {
        assertEquals(BackupProblem.TooNew, problemOf("""{"app":"klover","format":${BACKUP_FORMAT + 1}}"""))
    }

    @Test
    fun aKloverFileWithTheWrongShapeIsUnreadable() {
        assertEquals(BackupProblem.Unreadable, problemOf("""{"app":"klover","format":1,"files":[1,2]}"""))
    }

    @Test
    fun settingsAreCarriedExceptTheDeviceLevelOnes() {
        val server = stringPreferencesKey("last_server")
        val prefs = mutablePreferencesOf(
            stringPreferencesKey("theme_mode") to "dark",
            booleanPreferencesKey("amoled") to true,
            intPreferencesKey("download_parallel") to 3,
            floatPreferencesKey("accent_hue") to 120.5f,
            stringSetPreferencesKey("hidden") to setOf("a", "b"),
            server to "http://kavita.local",
        )
        val entries = prefs.asMap().toEntries(skip = setOf(server.name))
        assertFalse("the server stays on the device", server.name in entries)

        val restored = mutablePreferencesOf()
        assertEquals(5, restored.putEntries(entries, skip = emptySet()))
        assertEquals("dark", restored[stringPreferencesKey("theme_mode")])
        assertEquals(true, restored[booleanPreferencesKey("amoled")])
        assertEquals(3, restored[intPreferencesKey("download_parallel")])
        assertEquals(120.5f, restored[floatPreferencesKey("accent_hue")])
        assertEquals(setOf("a", "b"), restored[stringSetPreferencesKey("hidden")])
    }

    @Test
    fun aSettingOfTheWrongTypeIsSkippedNotApplied() {
        val restored = mutablePreferencesOf()
        val taken = restored.putEntries(
            mapOf(
                "download_parallel" to PrefEntry("int", JsonPrimitive("many")),
                "amoled" to PrefEntry("boolean", JsonPrimitive(true)),
                "mystery" to PrefEntry("matrix", JsonPrimitive(1)),
                "last_server" to PrefEntry("string", JsonPrimitive("http://elsewhere")),
            ),
            skip = setOf("last_server"),
        )
        assertEquals(1, taken)
        assertEquals(true, restored[booleanPreferencesKey("amoled")])
        assertEquals(null, restored[stringPreferencesKey("last_server")])
    }

    private fun scope() = CoroutineScope(Job() + Dispatchers.Unconfined)
    private val serializer = MapSerializer(String.serializer(), Int.serializer())

    @Test
    fun aStoreRegistersItselfSoABackupCanFindIt() {
        val folders = Folders(Files.createTempDirectory("backup").toFile())
        folders.account.value = "alice"
        val store = AccountStore(folders, "stats.json", serializer, emptyMap(), scope())
        assertEquals(listOf<BackupSource>(store), folders.backupSources)
        assertEquals("stats.json", folders.backupSources.single().name)
    }

    @Test
    fun whatOneAccountExportsAnotherCanImport() {
        val folders = Folders(Files.createTempDirectory("backup").toFile())
        folders.account.value = "alice"
        val store = AccountStore(folders, "stats.json", serializer, emptyMap(), scope())
        store.update { it + ("minutes" to 40) + ("streak" to 9) }
        val text = store.exportJson()

        folders.account.value = "bob"
        assertTrue(store.data.value.isEmpty())
        assertTrue(store.accepts(text))
        store.importJson(text)
        assertEquals(mapOf("minutes" to 40, "streak" to 9), store.data.value)

        folders.account.value = "alice"
        folders.account.value = "bob"
        assertEquals("it was written to disk as well", mapOf("minutes" to 40, "streak" to 9), store.data.value)
    }

    @Test
    fun aStoreRefusesTextItCannotRead() {
        val folders = Folders(Files.createTempDirectory("backup").toFile())
        folders.account.value = "alice"
        val store = AccountStore(folders, "stats.json", serializer, emptyMap(), scope())
        store.update { it + ("minutes" to 40) }
        assertFalse(store.accepts("""{"minutes":"lots"}"""))
        assertFalse(store.accepts("garbage"))
        assertEquals(mapOf("minutes" to 40), store.data.value)
    }
}
