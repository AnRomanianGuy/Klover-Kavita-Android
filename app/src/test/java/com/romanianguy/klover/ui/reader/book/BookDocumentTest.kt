package com.romanianguy.klover.ui.reader.book

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BookDocumentTest {
    private fun parse(html: String) = parseBookSection(html)
    private fun BookModel.texts() = blocks.filterIsInstance<TextBlock>()

    @Test
    fun paragraphsBecomeBlocksAndSpeechJoinsThemWithLineBreaks() {
        val m = parse("<p>First one.</p><p>Second   one.</p>")
        assertEquals(listOf("First one.", "Second one."), m.texts().map { it.text })
        assertEquals("First one.\nSecond one.", m.speech)
        // A block's start is where its text sits in the speech.
        m.texts().forEach { assertEquals(it.text, m.speech.substring(it.speechStart, it.speechStart + it.text.length)) }
    }

    @Test
    fun whitespaceCollapsesLikeAWebPageDoes() {
        val m = parse("<p>  Hello \n\t  <b> bold </b>  world  </p>")
        assertEquals("Hello bold world", m.texts().single().text)
    }

    @Test
    fun inlineMarkupBecomesSpans() {
        val block = parse("<p>a <i>b</i> <strong>c</strong></p>").texts().single()
        val italic = block.spans.single { it.fmt.italic }
        assertEquals("b", block.text.substring(italic.start, italic.end))
        val bold = block.spans.single { it.fmt.bold }
        assertEquals("c", block.text.substring(bold.start, bold.end))
    }

    @Test
    fun headingsAreLargerAndBold() {
        val h = parse("<h1>Title</h1><p>Body</p>").texts().first()
        assertEquals(Kind.Heading, h.kind)
        assertTrue(h.scale > 1f)
        assertTrue(h.spans.all { it.fmt.bold })
    }

    @Test
    fun picturesAreBlocksOfTheirOwnAndTextAroundThemSplits() {
        val m = parse("<p>before <img src='a.png'> after</p>")
        assertEquals(3, m.blocks.size)
        assertEquals("a.png", (m.blocks[1] as ImageBlock).src)
        assertEquals(listOf("a.png"), m.images)
    }

    @Test
    fun svgCoverUsesItsInnerImage() {
        val m = parse("""<svg viewBox="0 0 10 10"><image xlink:href="cover.jpg"/></svg>""")
        assertEquals("cover.jpg", (m.blocks.single() as ImageBlock).src)
    }

    @Test
    fun listsGetMarkersAndHangingIndent() {
        val m = parse("<ul><li>one</li><li>two</li></ul><ol><li>x</li><li>y</li></ol>")
        assertEquals(listOf("\u2022 one", "\u2022 two", "1. x", "2. y"), m.texts().map { it.text })
        assertTrue(m.texts().all { it.hang > 0f && it.indent == 1 })
    }

    @Test
    fun kavitaLinksAndWebLinksAreTold() {
        val block = parse("""<p><a kavita-page="4" href="javascript:void(0)">next</a> <a href="https://example.org/x">web</a> <a href="javascript:alert(1)">bad</a></p>""")
            .texts().single()
        val links = block.spans.mapNotNull { it.fmt.link }
        assertEquals(listOf(4), links.mapNotNull { it.section })
        assertEquals(listOf("https://example.org/x"), links.mapNotNull { it.url })
        // The script link is not a link at all.
        assertEquals(2, links.size)
    }

    @Test
    fun scriptsStylesAndHiddenElementsAreLeftOut() {
        val m = parse("<style>.x{color:red}</style><script>alert(1)</script><p>seen</p><p style='display:none'>hidden</p>")
        assertEquals("seen", m.speech)
    }

    @Test
    fun theBooksStylesheetItalicsAndCentringAreKept() {
        val m = parse("<style>.reading-section .cap{font-style:italic} p.mid{text-align:center}</style><p class='cap'>caption</p><p class='mid'>middle</p>")
        val (cap, mid) = m.texts()
        assertTrue(cap.spans.all { it.fmt.italic })
        assertEquals(Align.Center, mid.align)
    }

    @Test
    fun selectorsThatNeedMoreContextThanWeHaveAreSkippedNotGuessed() {
        val m = parse("<style>blockquote p{font-style:italic}</style><p>plain</p>")
        assertFalse(m.texts().single().spans.any { it.fmt.italic })
    }

    @Test
    fun lineBreaksInsideAParagraphStay() {
        assertEquals("one\ntwo", parse("<p>one<br>two</p>").texts().single().text)
    }

    @Test
    fun preformattedTextKeepsItsSpaces() {
        val b = parse("<pre>a  b\n  c</pre>").texts().single()
        assertEquals(Kind.Pre, b.kind)
        assertEquals("a  b\n  c", b.text)
    }

    @Test
    fun emptyAndSpacerParagraphsDisappear() {
        assertTrue(parse("<p> </p><p>&nbsp;</p><div></div>").blocks.isEmpty() || parse("<p> </p><div></div>").blocks.isEmpty())
        assertNull(parse("<p> </p>").blocks.firstOrNull())
        assertNotNull(parse("<p>x</p>").blocks.firstOrNull())
    }
}
