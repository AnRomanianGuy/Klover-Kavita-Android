package com.romanianguy.klover.ui.reader.book

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadAloudTextTest {
    @Test
    fun htmlBecomesPlainTextWithParagraphBreaks() {
        val html = "<style>.x{color:red}</style><div><h1>Title</h1><p>One &amp; two.</p><p>Caf&#233; &nbsp;time</p><script>alert(1)</script></div>"
        assertEquals("Title\nOne & two.\nCafé time", htmlToSpeechText(html))
    }

    @Test
    fun anImageOnlyPageHasNothingToSay() {
        assertEquals("", htmlToSpeechText("<div><img src=\"a.png\"><p>  </p></div>"))
        assertTrue(chunkForSpeech("").isEmpty())
    }

    @Test
    fun chunksAreShortInOrderAndKnowWhereTheyAre() {
        val sentence = "The clover grows low and spreads wide, taking whatever light is left. "
        val text = (1..40).joinToString("") { sentence }.trim()
        val chunks = chunkForSpeech(text, max = 200)
        assertTrue(chunks.size > 5)
        assertTrue(chunks.all { it.text.length <= 200 })
        var last = -1
        for (c in chunks) {
            assertTrue(c.start > last)
            assertEquals(c.text, text.substring(c.start, c.start + c.text.length))
            last = c.start
        }
        // Nothing is lost: the pieces together are the text without its separating spaces.
        assertEquals(text.replace(" ", ""), chunks.joinToString("") { it.text }.replace(" ", ""))
    }

    @Test
    fun aVeryLongSentenceIsCutAtSpaces() {
        val text = (1..200).joinToString(" ") { "word$it" }
        val chunks = chunkForSpeech(text, max = 120)
        assertTrue(chunks.all { it.text.length <= 120 })
        assertFalse(chunks.any { it.text.startsWith(" ") || it.text.endsWith(" ") })
        assertEquals(text.replace(" ", ""), chunks.joinToString("") { it.text }.replace(" ", ""))
    }

    @Test
    fun shortPiecesAreMergedButNeverPastTheLimit() {
        // A heading and the paragraph after it fit in one chunk (the engine pauses at the line break itself)...
        assertEquals(
            listOf("Heading\nFirst paragraph here."),
            chunkForSpeech("Heading\nFirst paragraph here.", max = 200).map { it.text },
        )
        // ...but not when that would be too long.
        assertEquals(
            listOf("Heading", "First paragraph here."),
            chunkForSpeech("Heading\nFirst paragraph here.", max = 25).map { it.text },
        )
    }

    @Test
    fun startingInTheMiddleFindsTheRightChunk() {
        val text = "One one one. Two two two. Three three three."
        val chunks = chunkForSpeech(text, max = 20)
        assertEquals(0, chunkIndexAt(chunks, 0))
        val offset = text.indexOf("Three") + 2
        assertEquals("Three three three.", chunks[chunkIndexAt(chunks, offset)].text)
        assertEquals(chunks.lastIndex, chunkIndexAt(chunks, 9999))
    }

    private val text = "First one here. Second one is a little longer! Third?\nFourth line"

    @Test
    fun aSentenceIsFoundAroundAnyPositionInIt() {
        val second = text.indexOf("Second")
        val range = sentenceAt(text, second + 5)
        assertEquals("Second one is a little longer!", text.substring(range.first, range.last + 1))
        assertEquals("First one here.", text.substring(sentenceAt(text, 0).first, sentenceAt(text, 0).last + 1))
        // A line break ends a sentence too (the break itself is not part of it).
        val third = sentenceAt(text, text.indexOf("Third"))
        assertEquals("Third?", text.substring(third.first, third.last + 1))
        assertEquals("Fourth line", text.substring(sentenceAt(text, text.length - 1).first))
    }

    @Test
    fun nextSentenceSkipsTheSpacesAfterIt() {
        assertEquals(text.indexOf("Second"), nextSentenceStart(text, 3))
        assertEquals(text.indexOf("Fourth"), nextSentenceStart(text, text.indexOf("Third")))
        assertEquals(text.length, nextSentenceStart(text, text.length - 2))
    }

    @Test
    fun previousSentenceRestartsTheCurrentOneOnlyWhenWellIntoIt() {
        val second = text.indexOf("Second")
        // Right at the start of a sentence: go to the one before.
        assertEquals(0, previousSentenceStart(text, second + 3))
        // Well into a sentence: back to its own start.
        assertEquals(second, previousSentenceStart(text, second + 25, restartAfter = 20))
        // Not far enough in to count as "well into" with the default threshold: goes to the one before.
        assertEquals(0, previousSentenceStart(text, second + 25))
        assertEquals(0, previousSentenceStart(text, 0))
    }

    @Test
    fun aTapInTheMiddleOfAWordStartsAtThatWord() {
        val at = text.indexOf("longer") + 3
        assertEquals(text.indexOf("longer"), wordStartAt(text, at))
        assertEquals(0, wordStartAt(text, 2))
        assertEquals(text.indexOf("Second"), wordStartAt(text, text.indexOf("Second")))
    }

    @Test
    fun theHelpersSurviveEmptyAndOutOfRangeInput() {
        assertEquals(0 until 0, sentenceAt("", 5))
        assertEquals(0, nextSentenceStart("", 3))
        assertEquals(0, wordStartAt("", 9))
        assertEquals(0, wordStartAt("abc", 99))
    }
}
