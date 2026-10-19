package com.romanianguy.klover.data.prefs

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.romanianguy.klover.data.session.AccountStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class SeriesReaderStoreAccountTest {
    private class Accounts : AccountStorage {
        val account = MutableStateFlow<String?>(null)
        override val key: StateFlow<String?> = account
        override fun file(name: String, key: String) = File(name)
        override fun legacy(name: String): File? = null
    }

    private fun dataStore(scope: CoroutineScope) =
        PreferenceDataStoreFactory.create(scope = scope) { Files.createTempDirectory("series").toFile().resolve("s.preferences_pb") }

    private fun scope() = CoroutineScope(Job() + Dispatchers.IO)

    @Test
    fun eachAccountRemembersItsOwnChoicesForTheSameSeries() = runBlocking {
        val s = scope()
        val accounts = Accounts().apply { account.value = "alice" }
        val store = SeriesReaderStore(dataStore(s), accounts, s)
        store.update(7) { it.copy(direction = ReadingDirection.RightToLeft, zoom = DoubleTapZoom.entries.first()) }
        assertEquals(ReadingDirection.RightToLeft, store.prefs(7).first().direction)

        accounts.account.value = "bob"
        assertTrue("bob has made no choices for this series", store.prefs(7).first().isEmpty)
        store.update(7) { it.copy(direction = ReadingDirection.LeftToRight) }
        assertEquals(ReadingDirection.LeftToRight, store.prefs(7).first().direction)

        accounts.account.value = "alice"
        assertEquals(ReadingDirection.RightToLeft, store.prefs(7).first().direction)
    }

    @Test
    fun forgettingOnlyForgetsForTheAccountThatIsSignedIn() = runBlocking {
        val s = scope()
        val accounts = Accounts().apply { account.value = "alice" }
        val store = SeriesReaderStore(dataStore(s), accounts, s)
        store.update(3) { it.copy(direction = ReadingDirection.RightToLeft) }
        accounts.account.value = "bob"
        store.update(3) { it.copy(direction = ReadingDirection.LeftToRight) }
        store.clear(3)
        assertTrue(store.prefs(3).first().isEmpty)
        accounts.account.value = "alice"
        assertEquals(ReadingDirection.RightToLeft, store.prefs(3).first().direction)
    }

    @Test
    fun signedOutThereAreNoChoicesAndNothingIsRemembered() = runBlocking {
        val s = scope()
        val accounts = Accounts()
        val store = SeriesReaderStore(dataStore(s), accounts, s)
        store.update(1) { it.copy(direction = ReadingDirection.RightToLeft) }
        assertTrue(store.prefs(1).first().isEmpty)
        accounts.account.value = "alice"
        assertTrue(store.prefs(1).first().isEmpty)
    }

    @Test
    fun choicesFromBeforeAccountsGoToTheFirstAccountOnly() = runBlocking {
        val s = scope()
        val data = dataStore(s)
        data.edit { it[stringPreferencesKey("series_7")] = "RightToLeft,-,-,-" }
        val accounts = Accounts().apply { account.value = "alice" }
        val store = SeriesReaderStore(data, accounts, s)
        // The move happens just after the store is made.
        var found: ReadingDirection? = null
        repeat(40) {
            found = store.prefs(7).first().direction
            if (found != null) return@repeat
            delay(50)
        }
        assertEquals(ReadingDirection.RightToLeft, found)
        accounts.account.value = "bob"
        assertNull(store.prefs(7).first().direction)
        // And it is not still lying about under the old name.
        assertTrue(data.data.first().asMap().keys.none { it.name == "series_7" })
    }
}
