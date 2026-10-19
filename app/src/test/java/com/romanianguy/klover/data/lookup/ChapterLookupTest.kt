package com.romanianguy.klover.data.lookup

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class ChapterLookupTest {
    private val search = """
        {"result":"ok","data":[
          {"id":"a77742b1","attributes":{"title":{"en":"Chainsaw Man"},"altTitles":[{"ja":"CSM-ja"},{"en":"CSM"}]}},
          {"id":"other","attributes":{"title":{"en":"Chainsaw Man: Buddy Stories"},"altTitles":[]}}
        ]}
    """.trimIndent()

    private fun chapter(volume: String, number: String?, pages: Int, title: String? = null) =
        """{"id":"c${number}x$pages","attributes":{"volume":"$volume","chapter":${number?.let { "\"$it\"" } ?: "null"},"title":${title?.let { "\"$it\"" } ?: "null"},"pages":$pages,"translatedLanguage":"en"}}"""

    private val chapters = """{"result":"ok","data":[
        ${chapter("1", "1", 53, "A Dog and a Chainsaw")},
        ${chapter("1", "1", 0, "Dog and Chainsaw")},
        ${chapter("1", "1", 49)},
        ${chapter("1", "2", 26, "Pochitas Whereabouts")},
        ${chapter("1", "3", 24)},
        ${chapter("1", "4", 20)},
        ${chapter("1", null, 12)}
    ]}"""

    @Test
    fun aSearchGivesTheWorksWithAllTheirTitles() {
        val found = parseSearch(search)
        assertEquals(2, found.size)
        assertTrue("CSM" in found[0].titles && "Chainsaw Man" in found[0].titles)
    }

    @Test
    fun theWorkWithTheSameTitleIsChosenNotTheSpinOff() {
        val found = parseSearch(search)
        assertEquals("a77742b1", bestMatch(listOf("Chainsaw Man"), found)?.id)
        assertEquals("a77742b1", bestMatch(listOf("chainsaw-man!"), found)?.id)
        assertEquals("a77742b1", bestMatch(listOf("Something else", "CSM"), found)?.id)
    }

    @Test
    fun aTitleThatMatchesNothingIsNotGuessed() {
        assertNull(bestMatch(listOf("Blue Hour"), parseSearch(search)))
        assertNull(bestMatch(emptyList(), parseSearch(search)))
    }

    @Test
    fun aSlightlyDifferentSpellingStillMatchesWhenMostWordsAreShared() {
        val candidates = listOf(Candidate("x", listOf("The Promised Neverland")))
        assertEquals("x", bestMatch(listOf("Promised Neverland The"), candidates)?.id)
        assertNull(bestMatch(listOf("The Promised Land"), candidates))
    }

    @Test
    fun aVolumesChaptersAreOnePerNumberWithPagesToReadInOrder() {
        val list = parseVolumeChapters(chapters)
        assertEquals(listOf("1", "2", "3", "4"), list.map { it.number })
        // the fullest copy of chapter 1; external links (0 pages) and chapters without a number are left out
        assertEquals(53, list[0].pages)
        assertEquals("A Dog and a Chainsaw", list[0].title)
    }

    @Test
    fun badOrEmptyResponsesGiveNothing() {
        assertTrue(parseVolumeChapters("not json").isEmpty())
        assertTrue(parseVolumeChapters("""{"result":"ok","data":[]}""").isEmpty())
        assertTrue(parseSearch("{}").isEmpty())
    }

    @Test
    fun theFilesPagesAreSharedOutInProportion() {
        val list = parseVolumeChapters(chapters) // 53 + 26 + 24 + 20 = 123
        val found = estimateInnerChapters(list, filePages = 123)
        assertEquals(listOf(0, 53, 79, 103), found.map { it.start })
        assertEquals(123, found.last().endExclusive)
        assertTrue(found.all { it.estimated })
        assertEquals("Chapter 2 · Pochitas Whereabouts", found[1].title)
        // A file of different length scales them.
        val scaled = estimateInnerChapters(list, filePages = 246)
        assertEquals(listOf(0, 106, 158, 206), scaled.map { it.start })
    }

    @Test
    fun aFileOfJustOneChapterIsNotSplitByTheWholeVolumesChapters() {
        val list = parseVolumeChapters(chapters)
        // 25 pages is about one chapter of this volume, not the volume.
        assertTrue(estimateInnerChapters(list, filePages = 25).isEmpty())
        assertTrue(estimateInnerChapters(list, filePages = 400).isEmpty())
    }

    @Test
    fun tooFewChaptersOrPagesSayNothing() {
        assertTrue(estimateInnerChapters(listOf(LookupChapter("1", null, 30)), 30).isEmpty())
        assertTrue(estimateInnerChapters(listOf(LookupChapter("1", null, 1), LookupChapter("2", null, 1)), 4).isEmpty())
    }

    private class Net(private val responses: Map<String, String>) {
        val requests = ArrayList<String>()
        val client = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            val url = chain.request().url.toString()
            requests += url
            val body = responses.entries.firstOrNull { url.contains(it.key) }?.value
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(if (body != null) 200 else 404).message("x")
                .body((body ?: "").toResponseBody("application/json".toMediaType())).build()
        }).build()
    }

    @Test
    fun aVolumeIsFoundThroughTheCatalogueAndAskedAboutOnlyOnce() = runBlocking {
        val net = Net(mapOf("/manga?" to search, "/chapter?" to chapters))
        val lookup = ChapterLookup(net.client, Files.createTempDirectory("lookup").toFile())
        val first = lookup.volumeChapters(listOf("Chainsaw Man"), 1, network = true)
        assertNotNull(first)
        assertEquals(4, first!!.size)
        val before = net.requests.size
        // The second time it comes from what was kept.
        assertEquals(4, lookup.volumeChapters(listOf("Chainsaw Man"), 1, network = true)!!.size)
        assertEquals(before, net.requests.size)
        // Only the title and the volume were sent, to the public catalogue.
        assertTrue(net.requests.all { it.startsWith("https://api.mangadex.org/") })
        assertTrue(net.requests.none { it.contains("apiKey", ignoreCase = true) || it.contains("token", ignoreCase = true) })
    }

    @Test
    fun withTheNetworkOffOnlyWhatWasKeptIsUsed() = runBlocking {
        val net = Net(mapOf("/manga?" to search, "/chapter?" to chapters))
        val dir = Files.createTempDirectory("lookup").toFile()
        val lookup = ChapterLookup(net.client, dir)
        assertNull(lookup.volumeChapters(listOf("Chainsaw Man"), 1, network = false))
        assertTrue(net.requests.isEmpty())
        lookup.volumeChapters(listOf("Chainsaw Man"), 1, network = true)
        assertEquals(4, lookup.volumeChapters(listOf("Chainsaw Man"), 1, network = false)!!.size)
    }

    @Test
    fun aFailedRequestIsNotRememberedButAnEmptyAnswerIs() = runBlocking {
        val broken = Net(emptyMap())
        val dir = Files.createTempDirectory("lookup").toFile()
        val lookup = ChapterLookup(broken.client, dir)
        assertNull(lookup.volumeChapters(listOf("Chainsaw Man"), 1, network = true))
        // nothing kept, so a later try asks again
        assertEquals(0, dir.listFiles().orEmpty().size)

        val nothing = Net(mapOf("/manga?" to """{"result":"ok","data":[]}"""))
        val lookup2 = ChapterLookup(nothing.client, dir)
        assertNull(lookup2.volumeChapters(listOf("Unknown Title"), 3, network = true))
        val asked = nothing.requests.size
        assertNull(lookup2.volumeChapters(listOf("Unknown Title"), 3, network = true))
        assertEquals("not asked again for a while", asked, nothing.requests.size)
    }

    @Test
    fun keptAnswersGoStaleAfterAWhile() = runBlocking {
        val net = Net(mapOf("/manga?" to search, "/chapter?" to chapters))
        val dir = Files.createTempDirectory("lookup").toFile()
        var now = System.currentTimeMillis()
        val lookup = ChapterLookup(net.client, dir) { now }
        lookup.volumeChapters(listOf("Chainsaw Man"), 1, network = true)
        val asked = net.requests.size
        now += 70L * 24 * 60 * 60 * 1000
        lookup.volumeChapters(listOf("Chainsaw Man"), 1, network = true)
        assertTrue("asked again after two months", net.requests.size > asked)
    }
}
