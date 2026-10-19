package com.romanianguy.klover.data.chapters

import com.romanianguy.klover.data.session.SingleFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class InnerReadMarksTest {
    // A file of 12 pages: chapters of 4 pages each.
    private val chapters = listOf(
        InnerChapter(0, 4, "Chapter 1", "1"),
        InnerChapter(4, 8, "Chapter 2", "2"),
        InnerChapter(8, 12, "Chapter 3", "3"),
    )
    private val file = 7

    private fun marks() = InnerReadMarks(
        SingleFile(Files.createTempFile("marks", ".json").toFile()),
        CoroutineScope(Job() + Dispatchers.Unconfined),
    )

    @Test
    fun theFilesOwnProgressSaysHowMuchOfAChapterIsRead() {
        assertEquals(1f, innerReadFraction(chapters[0], filePagesRead = 6, marked = null), 0f)
        assertEquals(0.5f, innerReadFraction(chapters[1], filePagesRead = 6, marked = null), 0f)
        assertEquals(0f, innerReadFraction(chapters[2], filePagesRead = 6, marked = null), 0f)
    }

    @Test
    fun aMarkWinsOverTheFilesProgress() {
        assertEquals(1f, innerReadFraction(chapters[2], 0, marked = true), 0f)
        assertEquals(0f, innerReadFraction(chapters[0], 12, marked = false), 0f)
    }

    @Test
    fun markingAChapterReadIsKeptAndDoesNotTouchTheOthers() {
        val marks = marks()
        assertNull(marks.mark(file, chapters, chapters[1], read = true, filePagesRead = 0))
        assertEquals(true, marks.marks.value[markKey(file, 4)])
        assertEquals(1, marks.marks.value.size)
    }

    @Test
    fun markingWhatTheProgressAlreadySaysKeepsNothing() {
        val marks = marks()
        // chapter 1 is already read through the file's progress
        assertNull(marks.mark(file, chapters, chapters[0], read = true, filePagesRead = 5))
        assertTrue(marks.marks.value.isEmpty())
        // marking it unread, then back to what the progress says, drops the mark again
        marks.mark(file, chapters, chapters[0], read = false, filePagesRead = 5)
        assertEquals(false, marks.marks.value[markKey(file, 0)])
        marks.mark(file, chapters, chapters[0], read = true, filePagesRead = 5)
        assertTrue(marks.marks.value.isEmpty())
    }

    @Test
    fun readingEveryChapterMakesTheWholeFileReadAndDropsTheMarks() {
        val marks = marks()
        assertNull(marks.mark(file, chapters, chapters[0], true, 0))
        assertNull(marks.mark(file, chapters, chapters[1], true, 0))
        assertEquals(Whole.Read, marks.mark(file, chapters, chapters[2], true, 0))
        assertTrue(marks.marks.value.isEmpty())
    }

    @Test
    fun unreadingEveryChapterMakesTheWholeFileUnread() {
        val marks = marks()
        // the whole file is read; chapters 1 and 2 are marked unread, then 3
        assertNull(marks.mark(file, chapters, chapters[0], false, 12))
        assertNull(marks.mark(file, chapters, chapters[1], false, 12))
        assertEquals(Whole.Unread, marks.mark(file, chapters, chapters[2], false, 12))
        assertTrue(marks.marks.value.isEmpty())
    }

    @Test
    fun aPartReadChapterKeepsTheFileFromCountingAsUnreadUntilItIsMarkedToo() {
        val marks = marks()
        // progress is in the middle of chapter 2: marking chapter 1 unread leaves chapter 2 part read...
        assertNull(marks.mark(file, chapters, chapters[0], false, 6))
        // ...until that one is marked unread as well
        assertEquals(Whole.Unread, marks.mark(file, chapters, chapters[1], false, 6))
    }

    @Test
    fun marksOfOneFileAreNotMixedWithAnother() {
        val marks = marks()
        marks.mark(1, chapters, chapters[1], true, 0)
        marks.mark(2, chapters, chapters[1], false, 12)
        assertEquals(true, marks.marks.value[markKey(1, 4)])
        assertEquals(false, marks.marks.value[markKey(2, 4)])
    }

    @Test
    fun onlyTheNewestMarksAreKept() {
        val marks = marks()
        val many = (0 until 700).map { InnerChapter(it * 4, it * 4 + 4, "c$it", "$it") }
        // each file has one chapter marked, so nothing is ever "whole"
        for (i in 0 until 700) marks.mark(i, listOf(many[i], many[(i + 1) % 700]), many[i], true, 0)
        assertEquals(500, marks.marks.value.size)
        assertTrue(markKey(699, 2796) in marks.marks.value)
        assertTrue(markKey(0, 0) !in marks.marks.value)
    }
}
