package com.romanianguy.klover.data.session

import com.romanianguy.klover.data.stats.DayStat
import com.romanianguy.klover.data.stats.ReadingStats
import com.romanianguy.klover.data.stats.summarize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.LocalDate

class AccountStoreTest {
    private class Folders(val root: File) : AccountStorage {
        val account = MutableStateFlow<String?>(null)
        override val key: StateFlow<String?> = account
        override fun file(name: String, key: String) = File(File(root, key), name)
        override fun legacy(name: String): File? = File(root, name).takeIf { it.isFile }
    }

    private fun root(): File = Files.createTempDirectory("accounts").toFile()
    private fun scope() = CoroutineScope(Job() + Dispatchers.Unconfined)
    private val serializer = MapSerializer(String.serializer(), Int.serializer())

    private fun store(folders: Folders) = AccountStore(folders, "data.json", serializer, emptyMap(), scope())

    @Test
    fun eachAccountKeepsItsOwnData() {
        val folders = Folders(root())
        folders.account.value = "alice"
        val store = store(folders)
        store.update { it + ("minutes" to 40) }

        folders.account.value = "bob"
        assertTrue("a new account starts empty", store.data.value.isEmpty())
        store.update { it + ("minutes" to 7) }

        folders.account.value = "alice"
        assertEquals(40, store.data.value["minutes"])
        folders.account.value = "bob"
        assertEquals(7, store.data.value["minutes"])
    }

    @Test
    fun theDataIsKeptOnDiskPerAccountNotJustInMemory() {
        val folders = Folders(root())
        folders.account.value = "alice"
        val store = store(folders)
        store.update { it + ("minutes" to 40) }
        folders.account.value = "bob" // switching writes the old account's data out at once

        val fresh = AccountStore(
            folders.also { it.account.value = "alice" },
            "data.json",
            serializer,
            emptyMap(),
            scope(),
        )
        assertEquals(40, fresh.data.value["minutes"])
    }

    @Test
    fun signingOutShowsNothingAndSigningBackInBringsTheDataBack() {
        val folders = Folders(root())
        folders.account.value = "alice"
        val store = store(folders)
        store.update { it + ("minutes" to 40) }

        folders.account.value = null
        assertTrue(store.data.value.isEmpty())
        // Nothing is written for nobody.
        store.update { it + ("ghost" to 1) }
        folders.account.value = "alice"
        assertEquals(40, store.data.value["minutes"])
        assertFalse(store.data.value.containsKey("ghost"))
    }

    @Test
    fun dataFromBeforeAccountsBelongsToTheFirstAccountOnly() {
        val root = root()
        File(root, "data.json").writeText("""{"minutes":99}""")
        val folders = Folders(root)
        folders.account.value = "alice"
        val store = store(folders)
        assertEquals(99, store.data.value["minutes"])
        assertFalse("moved, not copied", File(root, "data.json").exists())

        folders.account.value = "bob"
        assertTrue(store.data.value.isEmpty())
    }

    @Test
    fun streaksAndReadingTimeAreSeparatePerAccount() {
        val folders = Folders(root())
        val today = LocalDate.of(2026, 10, 18)
        folders.account.value = "alice"
        val stats = ReadingStats(folders, scope(), today = { today })
        stats.addSeconds(900)
        assertEquals(1, summarize(stats.days.value, today).currentStreak)
        assertEquals(900L, summarize(stats.days.value, today).todaySeconds)

        folders.account.value = "bob"
        assertEquals(0, summarize(stats.days.value, today).currentStreak)
        assertEquals(0L, summarize(stats.days.value, today).todaySeconds)
        stats.addSeconds(120)
        assertEquals(120L, summarize(stats.days.value, today).todaySeconds)

        folders.account.value = "alice"
        assertEquals(900L, summarize(stats.days.value, today).todaySeconds)
    }

    @Test
    fun resettingOnlyClearsTheAccountThatIsSignedIn() {
        val folders = Folders(root())
        folders.account.value = "alice"
        val stats = ReadingStats(folders, scope())
        stats.addSeconds(300)
        folders.account.value = "bob"
        stats.addSeconds(500)
        stats.reset()
        assertTrue(stats.days.value.isEmpty())
        folders.account.value = "alice"
        assertTrue(stats.days.value.values.sumOf { it.seconds } == 300L)
    }

    @Test
    fun anAccountIsTheServerAndTheUserOnIt() {
        fun session(url: String, id: Int, name: String) = Session(url, "t", null, "k", name, id)
        val a = accountKey(session("https://kavita.example.com", 5, "Alice"))
        // The same user whichever way the address is written.
        assertEquals(a, accountKey(session("https://KAVITA.example.com/", 5, "alice")))
        // Another user on the same server, or the same user number on another server, is another account.
        assertNotEquals(a, accountKey(session("https://kavita.example.com", 6, "Alice")))
        assertNotEquals(a, accountKey(session("https://other.example.com", 5, "Alice")))
        // When the user's id is not known, the name tells them apart (without regard to case).
        assertEquals(
            accountKey(session("https://k.example.com", 0, "Bob")),
            accountKey(session("https://k.example.com", 0, " bob ")),
        )
        assertNotEquals(
            accountKey(session("https://k.example.com", 0, "Bob")),
            accountKey(session("https://k.example.com", 0, "Carol")),
        )
        // Safe as a folder name.
        assertTrue(a.matches(Regex("[0-9a-f]{16}")))
    }

    @Test
    fun aDayStatSurvivesTheRoundTripThroughAnAccountFile() {
        val folders = Folders(root())
        folders.account.value = "alice"
        val stats = ReadingStats(folders, scope(), today = { LocalDate.of(2026, 1, 2) })
        stats.addSeconds(61)
        stats.addPages(3)
        stats.flush()
        val reread = ReadingStats(
            Folders(folders.root).also { it.account.value = "alice" },
            scope(),
            today = { LocalDate.of(2026, 1, 2) },
        )
        assertEquals(DayStat(seconds = 61, pages = 3), reread.days.value["2026-01-02"])
    }
}
