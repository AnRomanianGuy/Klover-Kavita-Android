package com.romanianguy.klover.data.chapters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InnerChaptersTest {
    private fun pages(prefix: String, count: Int, from: Int = 1, ext: String = "png") =
        (from until from + count).map { "$prefix${it.toString().padStart(3, '0')}.$ext" }

    @Test
    fun foldersPerChapterAreFoundAsKavitaNamesThem() {
        // Kavita puts the folder's name in front of the page's, joined with an underscore.
        val names = pages("Chapter 1_", 4) + pages("Chapter 2_", 5) + pages("Chapter 3_", 3)
        val found = detectInnerChapters(names)
        assertEquals(listOf(0 to 4, 4 to 9, 9 to 12), found.map { it.start to it.endExclusive })
        assertEquals(listOf("Chapter 1", "Chapter 2", "Chapter 3"), found.map { it.title })
        assertEquals(listOf("1", "2", "3"), found.map { it.number })
    }

    @Test
    fun aChapterNumberWrittenInEveryPageNameIsEnough() {
        val names = pages("c01_p", 4) + pages("c02_p", 4) + pages("c03_p", 5)
        val found = detectInnerChapters(names)
        assertEquals(3, found.size)
        assertEquals(listOf("Chapter 1", "Chapter 2", "Chapter 3"), found.map { it.title })
        assertEquals(listOf(0, 4, 8), found.map { it.start })
    }

    @Test
    fun volumeAndChapterTogetherInTheNameAndDecimalChapters() {
        val names = pages("Vol01 Ch09 ", 4) + pages("Vol01 Ch10 ", 4) + pages("Vol01 Ch10.5 ", 4)
        val found = detectInnerChapters(names)
        assertEquals(listOf("9", "10", "10.5"), found.map { it.number })
    }

    @Test
    fun aCoverBeforeTheFirstNumberedPageJoinsTheFirstChapter() {
        val names = listOf("cover.jpg") + pages("ch001_", 4, ext = "jpg") + pages("ch002_", 4, ext = "jpg")
        val found = detectInnerChapters(names)
        assertEquals(2, found.size)
        assertEquals(0, found[0].start)
        assertEquals(5, found[1].start)
    }

    @Test
    fun foldersWithoutNumbersAreNamedByTheirOwnNames() {
        val names = pages("Prologue_", 3) + pages("The Fall_", 4) + pages("Aftermath_", 3)
        val found = detectInnerChapters(names)
        assertEquals(listOf("Prologue", "The Fall", "Aftermath"), found.map { it.title })
        assertNull(found[0].number)
    }

    @Test
    fun pathsAreJoinedTheSameWayAsTheUnderscore() {
        val names = pages("Chapter 1/", 4) + pages("Chapter 2/", 4)
        assertEquals(2, detectInnerChapters(names).size)
    }

    @Test
    fun plainNumberedPagesSayNothing() {
        assertTrue(detectInnerChapters(pages("", 40)).isEmpty())
        assertTrue(detectInnerChapters(pages("page_", 40)).isEmpty())
        assertTrue(detectInnerChapters(pages("scan-", 40, ext = "jpg")).isEmpty())
    }

    @Test
    fun oneNameForEveryPageSaysNothingEither() {
        assertTrue(detectInnerChapters(pages("Series - Volume 1 - ", 30)).isEmpty())
    }

    @Test
    fun aSplitIntoTinyPiecesIsNotBelieved() {
        // Every page its own "chapter": the names were read wrongly.
        val names = (1..12).map { "c$it.jpg" }
        assertTrue(detectInnerChapters(names).isEmpty())
    }

    @Test
    fun aNumberThatComesBackMeansTheNamesDoNotSeparateChapters() {
        val names = pages("c01_", 4) + pages("c02_", 4) + pages("c01_", 4)
        assertTrue(detectInnerChapters(names).isEmpty())
    }

    @Test
    fun aFewPagesCannotBeSplit() {
        assertTrue(detectInnerChapters(listOf("c1_1.jpg", "c1_2.jpg", "c2_1.jpg")).isEmpty())
    }

    @Test
    fun foldersNamedByTheirNumberAloneAreChaptersIncludingDecimals() {
        val names = pages("1_", 5) + pages("1.5_", 4) + pages("2_", 6) + pages("10.5_", 3) + pages(
            "10.99_",
            2,
        ) + pages("11_", 5)
        val found = detectInnerChapters(names)
        assertEquals(listOf("1", "1.5", "2", "10.5", "10.99", "11"), found.map { it.number })
        assertEquals(
            listOf("Chapter 1", "Chapter 1.5", "Chapter 2", "Chapter 10.5", "Chapter 10.99", "Chapter 11"),
            found.map { it.title },
        )
        assertEquals(listOf(0, 5, 9, 15, 18, 20), found.map { it.start })
    }

    @Test
    fun numberedFoldersInsideOneSharedFolderAreFoundToo() {
        val names = pages("Miman/21/", 4) + pages("Miman/21.5/", 4) + pages("Miman/22/", 5)
        val found = detectInnerChapters(names)
        assertEquals(listOf("Chapter 21", "Chapter 21.5", "Chapter 22"), found.map { it.title })
    }

    @Test
    fun aHalfChapterOfZeroPointFiveKeepsItsZero() {
        val found = detectInnerChapters(pages("0_", 4) + pages("0.5_", 4) + pages("1_", 4))
        assertEquals(listOf("0", "0.5", "1"), found.map { it.number })
    }

    @Test
    fun oneShortExtraAmongRealChaptersIsKept() {
        val found = detectInnerChapters(pages("1_", 8) + pages("2_", 8) + pages("2.5_", 2) + pages("3_", 8) + pages("4_", 8))
        assertEquals(5, found.size)
    }

    @Test
    fun aFileOfSeveralVolumesNamesTheVolumeInEachChapter() {
        val names = listOf("cover.jpg") + pages("Vol. 1 Ch. 10_", 4) + pages("Vol. 1 Ch. 10.99_", 3) + pages(
            "Vol. 2 Ch. 11_",
            5,
        ) +
            pages("Vol. 2 Ch. 20.9_", 3) + pages("Vol. 3 Ch. 28.1_", 4)
        val found = detectInnerChapters(names)
        assertEquals(listOf("10", "10.99", "11", "20.9", "28.1"), found.map { it.number })
        assertEquals("Vol. 1 · Chapter 10", found[0].title)
        assertEquals("Vol. 3 · Chapter 28.1", found.last().title)
        // the loose cover belongs to the first chapter
        assertEquals(0, found[0].start)
        assertEquals(5, found[0].pages)
    }

    @Test
    fun chapterNumbersThatStartAgainInEachVolumeStillSplit() {
        val names = pages("Vol. 1 Ch. 1_", 4) + pages("Vol. 1 Ch. 2_", 4) + pages(
            "Vol. 2 Ch. 1_",
            4,
        ) + pages("Vol. 2 Ch. 2_", 4)
        val found = detectInnerChapters(names)
        assertEquals(
            listOf("Vol. 1 · Chapter 1", "Vol. 1 · Chapter 2", "Vol. 2 · Chapter 1", "Vol. 2 · Chapter 2"),
            found.map { it.title },
        )
    }

    @Test
    fun foldersNamedCAndAZeroPaddedNumberAreChapters() {
        // c020 .. c026 with plain page numbers inside, the way a scanlation zip is laid out.
        val names = (20..26).flatMap { chapter -> pages("c0${chapter}_", 5 + chapter % 3, ext = "jpg") }
        val found = detectInnerChapters(names)
        assertEquals((20..26).map { it.toString() }, found.map { it.number })
        assertEquals("Chapter 20", found.first().title)
        assertEquals("Chapter 26", found.last().title)
    }

    @Test
    fun theVolumesOwnNameInThePathAndALooseCoverDoNotGetInTheWay() {
        val root = "Fufu Ijo, Koibito Miman Volume 4/"
        val names = listOf("!0000_Cover.jpg") + (20..23).flatMap { chapter -> pages("${root}c0${chapter}_", 6, ext = "jpg") }
        val found = detectInnerChapters(names)
        assertEquals(listOf("20", "21", "22", "23"), found.map { it.number })
        // one volume only, so its number is not repeated in every title
        assertEquals("Chapter 20", found.first().title)
        // the cover belongs to the first chapter
        assertEquals(0, found.first().start)
        assertEquals(7, found.first().pages)
    }

    @Test
    fun theChapterOfAPageIsFound() {
        val found = detectInnerChapters(pages("Chapter 1_", 4) + pages("Chapter 2_", 4))
        assertEquals("Chapter 1", innerChapterAt(found, 0)?.title)
        assertEquals("Chapter 1", innerChapterAt(found, 3)?.title)
        assertEquals("Chapter 2", innerChapterAt(found, 4)?.title)
        assertNull(innerChapterAt(found, 8))
        assertNull(innerChapterAt(emptyList(), 0))
    }
}
