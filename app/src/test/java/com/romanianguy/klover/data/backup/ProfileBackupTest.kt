package com.romanianguy.klover.data.backup

import com.romanianguy.klover.data.profiles.PROFILE_SHAPES
import com.romanianguy.klover.data.profiles.PROFILE_TONES
import com.romanianguy.klover.data.profiles.ProfileStore
import com.romanianguy.klover.data.session.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ProfileBackupTest {
    private class Rig(val dir: File) {
        val sessions = MutableStateFlow<Session?>(null)
        val store = ProfileStore(
            file = File(dir, "profiles.json"),
            photoDir = File(dir, "photos"),
            scope = CoroutineScope(Job() + Dispatchers.Unconfined),
            session = sessions,
            signIn = { sessions.value = it },
            seal = { it.reversed() },
            unseal = { it.reversed() },
        )

        init {
            sessions.value = Session("https://kavita.example", "t", "r", "k", "sam", 2)
        }

        val key: String get() = store.profiles.value.single().key
    }

    private fun rig() = Rig(Files.createTempDirectory("profile-backup").toFile())

    private fun Rig.withLook(photo: ByteArray? = byteArrayOf(1, 2, 3, 4, 5)) {
        store.rename(key, "Sam the reader")
        store.setTone(key, 7)
        store.setShape(key, 11)
        if (photo != null) {
            val name = "sam.jpg"
            store.photoDir.mkdirs()
            File(store.photoDir, name).writeBytes(photo)
            store.setPhoto(key, name)
        }
    }

    @Test
    fun theLookOfAProfileComesBackOnAnotherDevice() {
        val before = rig()
        before.withLook()
        val backup = before.store.backupOf(before.key)!!
        assertEquals("Sam the reader", backup.name)
        assertEquals(7, backup.tone)
        assertEquals(11, backup.shape)
        assertNotNull(backup.photo)

        val after = rig()
        after.store.restore(after.key, backup, now = 99)
        val profile = after.store.profiles.value.single()
        assertEquals("Sam the reader", profile.name)
        assertEquals(7, profile.tone)
        assertEquals(11, profile.shape)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5), File(after.store.photoDir, profile.photo!!).readBytes())
    }

    @Test
    fun aBackupWithoutAPictureTakesTheOldPictureOff() {
        val rig = rig()
        rig.withLook()
        val old = rig.store.profiles.value.single().photo!!
        rig.store.restore(rig.key, ProfileBackup(name = "Sam", tone = 1, shape = 2, photo = null))
        assertNull(rig.store.profiles.value.single().photo)
        assertTrue("the file goes too", !File(rig.store.photoDir, old).exists())
    }

    @Test
    fun aNewPictureReplacesTheOldFile() {
        val rig = rig()
        rig.withLook(byteArrayOf(9, 9))
        val old = rig.store.profiles.value.single().photo!!
        val backup = ProfileBackup("Sam", 0, 0, java.util.Base64.getEncoder().encodeToString(byteArrayOf(5, 6, 7)))
        rig.store.restore(rig.key, backup, now = 1234)
        val now = rig.store.profiles.value.single().photo!!
        assertTrue(now != old)
        assertTrue(!File(rig.store.photoDir, old).exists())
        assertArrayEquals(byteArrayOf(5, 6, 7), File(rig.store.photoDir, now).readBytes())
    }

    @Test
    fun valuesOutOfRangeAreClampedAndABrokenPictureIsIgnored() {
        val rig = rig()
        rig.store.restore(rig.key, ProfileBackup("Sam", tone = 999, shape = -5, photo = "not base64 !!"))
        val profile = rig.store.profiles.value.single()
        assertEquals(PROFILE_TONES - 1, profile.tone)
        assertEquals(0, profile.shape.coerceAtLeast(0))
        assertTrue(profile.shape in 0 until PROFILE_SHAPES)
        assertNull(profile.photo)
    }

    @Test
    fun nothingHappensForAProfileThatIsNotThere() {
        val rig = rig()
        rig.store.restore("someone-else", ProfileBackup(name = "Ghost"))
        assertEquals("sam", rig.store.profiles.value.single().displayName)
    }

    @Test
    fun anOldBackupWithoutAProfileStillReads() {
        val backup = decodeBackup("""{"app":"klover","format":1,"files":{}}""")
        assertNull(backup.profile)
    }

    @Test
    fun theProfileTravelsInsideTheBackupFile() {
        val rig = rig()
        rig.withLook()
        val text = BackupFile(profile = rig.store.backupOf(rig.key)).encode()
        val read = decodeBackup(text).profile!!
        assertEquals("Sam the reader", read.name)
        assertEquals(11, read.shape)
    }
}
