package com.romanianguy.klover.data.update

import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

class UpdateTest {
    @Test
    fun versionsAreComparedByTheirNumbers() {
        assertTrue(isNewerVersion("v1.1", "1.0"))
        assertTrue(isNewerVersion("1.10", "1.9"))
        assertTrue(isNewerVersion("2.0", "1.99.9"))
        assertTrue(isNewerVersion("1.0.1", "1.0"))
        assertFalse(isNewerVersion("1.0", "1.0"))
        assertFalse(isNewerVersion("1.0.0", "1.0"))
        assertFalse(isNewerVersion("v0.9", "1.0"))
    }

    @Test
    fun aPreReleaseIsBeforeItsFinalVersion() {
        assertTrue(isNewerVersion("1.2", "1.2-beta1"))
        assertFalse(isNewerVersion("1.2-beta1", "1.2"))
        assertTrue(isNewerVersion("1.3-beta1", "1.2"))
    }

    @Test
    fun aVersionThatCannotBeReadIsNeverNewer() {
        assertFalse(isNewerVersion("latest", "1.0"))
        assertFalse(isNewerVersion("", "1.0"))
        assertFalse(isNewerVersion("v1.x", "1.0"))
        assertFalse(isNewerVersion("2.0", "dev"))
    }

    private class Rig(dir: File = Files.createTempDirectory("update").toFile()) {
        var body = "[]"
        var code = 200
        var broken = false
        var requests = 0
        var clock = 1_000_000_000L
        val file = File(dir, "update.json")
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests++
            if (broken) throw IOException("offline")
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("x")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        }.build()

        fun checker(current: String = "1.0") = UpdateChecker(client, "https://example.test/releases", current, file) { clock }
    }

    private fun release(tag: String, draft: Boolean = false) =
        """{"tag_name":"$tag","name":"Klover $tag","body":"notes of $tag","html_url":"https://example.test/$tag","draft":$draft,"prerelease":false,"id":1}"""

    @Test
    fun aNewerReleaseIsFoundAndAnnounced() = runBlocking {
        val rig = Rig().apply { body = "[" + release("v1.1") + "," + release("v1.0") + "]" }
        val checker = rig.checker()
        assertTrue(checker.check() is CheckResult.Found)
        val info = checker.announcement.value
        assertNotNull(info)
        assertEquals("1.1", info!!.version)
        assertEquals("Klover v1.1", info.name)
        assertEquals("notes of v1.1", info.notes)
        assertEquals("https://example.test/v1.1", info.url)
    }

    @Test
    fun theNewestOfSeveralIsTheOneAnnouncedWhateverTheOrder() = runBlocking {
        val rig = Rig().apply { body = "[" + release("v1.0") + "," + release("v1.3") + "," + release("v1.2") + "]" }
        val checker = rig.checker()
        checker.check()
        assertEquals("1.3", checker.announcement.value?.version)
    }

    @Test
    fun theSameVersionOrAnOlderOneAnnouncesNothing() = runBlocking {
        val rig = Rig().apply { body = "[" + release("v1.0") + "," + release("v0.9") + "]" }
        val checker = rig.checker()
        assertTrue(checker.check() is CheckResult.UpToDate)
        assertNull(checker.announcement.value)
    }

    @Test
    fun aDraftIsNotAnAnnouncement() = runBlocking {
        val rig = Rig().apply { body = "[" + release("v2.0", draft = true) + "," + release("v1.0") + "]" }
        val checker = rig.checker()
        assertTrue(checker.check() is CheckResult.UpToDate)
        assertNull(checker.announcement.value)
    }

    @Test
    fun noConnectionAndOddAnswersAreFailuresThatChangeNothing() = runBlocking {
        val rig = Rig().apply { body = "[" + release("v1.5") + "]" }
        val checker = rig.checker()
        checker.check()
        assertEquals("1.5", checker.announcement.value?.version)

        rig.broken = true
        assertEquals(CheckResult.Failed, checker.check())
        rig.broken = false
        rig.code = 403
        assertEquals(CheckResult.Failed, checker.check())
        rig.code = 200
        rig.body = "this is not json"
        assertEquals(CheckResult.Failed, checker.check())
        assertEquals("1.5", checker.announcement.value?.version)
    }

    @Test
    fun itAsksOnlyOnceInTwelveHours() = runBlocking {
        val rig = Rig().apply { body = "[" + release("v1.1") + "]" }
        val checker = rig.checker()
        assertNotNull(checker.checkIfDue())
        assertEquals(1, rig.requests)
        rig.clock += 11 * 60 * 60 * 1000L
        assertNull(checker.checkIfDue())
        assertEquals(1, rig.requests)
        rig.clock += 2 * 60 * 60 * 1000L
        assertNotNull(checker.checkIfDue())
        assertEquals(2, rig.requests)
    }

    @Test
    fun aFailedTryIsNotCountedSoItIsMadeAgain() = runBlocking {
        val rig = Rig().apply { broken = true }
        val checker = rig.checker()
        assertEquals(CheckResult.Failed, checker.checkIfDue())
        rig.clock += 60_000
        assertEquals(CheckResult.Failed, checker.checkIfDue())
        assertEquals(2, rig.requests)
    }

    @Test
    fun skippingAVersionHidesItUntilALaterOneComes() = runBlocking {
        val rig = Rig().apply { body = "[" + release("v1.1") + "]" }
        val checker = rig.checker()
        checker.check()
        checker.skip("1.1")
        assertNull(checker.announcement.value)
        checker.check()
        assertNull(checker.announcement.value)
        rig.body = "[" + release("v1.2") + "]"
        checker.check()
        assertEquals("1.2", checker.announcement.value?.version)
    }

    @Test
    fun clearingTheSkipShowsTheVersionAgain() = runBlocking {
        val rig = Rig().apply { body = "[" + release("v1.1") + "]" }
        val checker = rig.checker()
        checker.check()
        checker.skip("1.1")
        checker.clearSkip()
        assertEquals("1.1", checker.announcement.value?.version)
    }

    @Test
    fun theAnnouncementSurvivesARestartAndGoesOnceTheAppIsUpdated() = runBlocking {
        val rig = Rig().apply { body = "[" + release("v1.1") + "]" }
        rig.checker().check()
        assertEquals("1.1", rig.checker().announcement.value?.version)
        assertNull(rig.checker(current = "1.1").announcement.value)
        assertNull(rig.checker(current = "1.2").announcement.value)
    }

    @Test
    fun aSkippedVersionStaysSkippedAfterARestart() = runBlocking {
        val rig = Rig().apply { body = "[" + release("v1.1") + "]" }
        val first = rig.checker()
        first.check()
        first.skip("1.1")
        assertNull(rig.checker().announcement.value)
    }

    @Test
    fun aDamagedFileIsStartedOverNotACrash() {
        val rig = Rig()
        rig.file.writeText("{ not json")
        assertNull(rig.checker().announcement.value)
    }
}
