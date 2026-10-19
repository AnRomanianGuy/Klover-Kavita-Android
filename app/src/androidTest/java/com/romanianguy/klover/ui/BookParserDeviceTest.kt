package com.romanianguy.klover.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.romanianguy.klover.ui.reader.book.TextBlock
import com.romanianguy.klover.ui.reader.book.parseBookSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

// the book parser on a real Android runtime: its regexes run on ICU here, which is stricter than the JVM's
@RunWith(AndroidJUnit4::class)
class BookParserDeviceTest {
    @Test
    fun aSectionWithAStylesheetParses() {
        val model = parseBookSection(
            "<style>.reading-section .cap{font-style:italic} h1{font-size:1.5em} /* c */ @media x { .a{color:red} }</style>" +
                "<h1>T</h1><p class='cap'>caption</p><p>plain</p>",
        )
        val blocks = model.blocks.filterIsInstance<TextBlock>()
        assertEquals(listOf("T", "caption", "plain"), blocks.map { it.text })
        assertTrue(blocks[1].spans.all { it.fmt.italic })
        assertEquals("T\ncaption\nplain", model.speech)
    }
}
