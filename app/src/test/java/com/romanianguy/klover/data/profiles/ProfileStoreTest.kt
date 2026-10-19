package com.romanianguy.klover.data.profiles

import com.romanianguy.klover.data.session.Session
import com.romanianguy.klover.data.session.accountKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ProfileStoreTest {
    private val server = "https://kavita.example"
    private fun session(user: String, id: Int = 0, token: String = "t-$user", server: String = this.server) =
        Session(server, token, "r-$user", "key-$user", user, id)

    private class Rig(dir: File) {
        val sessions = MutableStateFlow<Session?>(null)
        val signedInWith = ArrayList<Session>()
        var clock = 1_000L
        val store = ProfileStore(
            file = File(dir, "profiles.json"),
            photoDir = File(dir, "photos"),
            scope = CoroutineScope(Job() + Dispatchers.Unconfined),
            session = sessions,
            signIn = { signedInWith += it; sessions.value = it },
            // not real encryption, but unreadable as it is, and something a test can break
            seal = { "sealed:" + it.reversed() },
            unseal = { if (it.startsWith("sealed:")) it.removePrefix("sealed:").reversed() else null },
            now = { clock },
        )
    }

    private fun rig(): Rig = Rig(Files.createTempDirectory("profiles").toFile())

    @Test
    fun signingInAddsAProfileWithTheUserNameAndAColour() {
        val rig = rig()
        rig.sessions.value = session("sam", 2)
        val profile = rig.store.profiles.value.single()
        assertEquals("sam", profile.displayName)
        assertEquals("S", profile.initial)
        assertEquals(accountKey(session("sam", 2)), profile.key)
        assertEquals(profile.key, rig.store.activeKey.value)
    }

    @Test
    fun theTokensAreNotKeptInTheClear() {
        val rig = rig()
        rig.sessions.value = session("sam", 2, token = "very-secret-token")
        val saved = File(File(rig.store.photoDir.parentFile, "profiles.json").path).readText()
        assertFalse("very-secret-token" in saved)
        assertFalse("key-sam" in saved)
    }

    @Test
    fun eachNewProfileTakesTheNextColour() {
        val rig = rig()
        rig.sessions.value = session("a", 1)
        rig.sessions.value = session("b", 2)
        rig.sessions.value = session("c", 3)
        assertEquals(setOf(0, 1, 2), rig.store.profiles.value.map { it.tone }.toSet())
    }

    @Test
    fun aRefreshedTokenKeepsTheNameColourAndPicture() {
        val rig = rig()
        rig.sessions.value = session("sam", 2)
        val key = rig.store.profiles.value.single().key
        rig.store.rename(key, "Sam the reader")
        rig.store.setTone(key, 7)
        rig.store.setPhoto(key, "sam.jpg")
        rig.sessions.value = session("sam", 2, token = "newer-token")
        val profile = rig.store.profiles.value.single()
        assertEquals("Sam the reader", profile.displayName)
        assertEquals(7, profile.tone)
        assertEquals("sam.jpg", profile.photo)
        // and the newer token is what a switch signs in with
        runBlocking { rig.store.switchTo(key) }
        assertEquals("newer-token", rig.signedInWith.last().token)
    }

    @Test
    fun switchingSignsInWithWhatWasSavedForThatProfile() = runBlocking {
        val rig = rig()
        rig.sessions.value = session("alice", 1)
        rig.sessions.value = session("bob", 2)
        val alice = rig.store.profiles.value.first { it.username == "alice" }
        assertTrue(rig.store.switchTo(alice.key))
        val used = rig.signedInWith.last()
        assertEquals("alice", used.username)
        assertEquals("t-alice", used.token)
        assertEquals("r-alice", used.refreshToken)
        assertEquals("key-alice", used.apiKey)
        assertEquals(alice.key, rig.store.activeKey.value)
    }

    @Test
    fun profilesKeepTheOrderTheyWereAddedInWhenOneIsUsed() {
        val rig = rig()
        rig.clock = 1
        rig.sessions.value = session("alice", 1)
        rig.clock = 2
        rig.sessions.value = session("bob", 2)
        assertEquals(listOf("alice", "bob"), rig.store.profiles.value.map { it.username })
        rig.clock = 3
        runBlocking { rig.store.switchTo(rig.store.profiles.value.first().key) }
        // the picker must not shuffle under the finger: using alice does not move her
        assertEquals(listOf("alice", "bob"), rig.store.profiles.value.map { it.username })
        assertEquals(3L, rig.store.profiles.value.first().usedAt)
    }

    @Test
    fun profilesAreListedPerServer() {
        val rig = rig()
        rig.sessions.value = session("alice", 1)
        rig.sessions.value = session("alice", 1, server = "https://other.example")
        assertEquals(2, rig.store.profiles.value.size)
        assertEquals(1, rig.store.forServer("$server/").size)
        assertEquals(2, rig.store.forServer(null).size)
    }

    @Test
    fun removingAProfileForgetsItsLoginAndPicture() {
        val rig = rig()
        rig.sessions.value = session("sam", 2)
        val key = rig.store.profiles.value.single().key
        rig.store.photoDir.mkdirs()
        val picture = File(rig.store.photoDir, "sam.jpg").apply { writeText("x") }
        rig.store.setPhoto(key, "sam.jpg")
        rig.store.remove(key)
        assertTrue(rig.store.profiles.value.isEmpty())
        assertFalse(picture.exists())
        assertFalse(runBlocking { rig.store.switchTo(key) })
    }

    @Test
    fun aProfileWhoseSecretsCannotBeReadCannotBeSwitchedTo() = runBlocking {
        val dir = Files.createTempDirectory("profiles").toFile()
        val first = Rig(dir)
        first.sessions.value = session("sam", 2)
        val key = first.store.profiles.value.single().key
        // the file is read back by another store whose key does not open it (a backup restored on another phone)
        val other = ProfileStore(
            File(dir, "profiles.json"), File(dir, "photos"), CoroutineScope(Job() + Dispatchers.Unconfined),
            MutableStateFlow<Session?>(null), {}, { it }, { null },
        )
        assertEquals(1, other.profiles.value.size)
        assertFalse(other.switchTo(key))
    }

    @Test
    fun theListSurvivesARestart() {
        val dir = Files.createTempDirectory("profiles").toFile()
        val first = Rig(dir)
        first.sessions.value = session("sam", 2)
        first.store.rename(first.store.profiles.value.single().key, "Sam")
        val second = Rig(dir)
        assertEquals("Sam", second.store.profiles.value.single().displayName)
        assertNull(second.store.activeKey.value)
    }

    @Test
    fun theInitialSkipsSymbolsAndHasAFallback() {
        fun profile(name: String) = SavedProfile("k", "s", "user", name = name, sealed = "")
        assertEquals("É", profile("émile").initial)
        assertEquals("R", profile("  ~ riley").initial)
        assertEquals("U", profile("").initial)
        assertEquals("?", SavedProfile("k", "s", "", name = "", sealed = "").initial)
    }

    @Test
    fun twoSpellingsOfOneServerAreTheSameServer() {
        assertTrue(sameServer("https://Kavita.example/", "https://kavita.example"))
        assertFalse(sameServer("https://kavita.example", "https://kavita.example/sub"))
    }
}
