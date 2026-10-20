package com.romanianguy.klover.ui.reader.book

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BookStyleTest {
    private fun parse(html: String) = parseBookSection(html)
    private fun BookModel.texts() = blocks.filterIsInstance<TextBlock>()
    private fun TextBlock.italic() = spans.all { it.fmt.italic }
    private fun TextBlock.bold() = spans.all { it.fmt.bold }

    @Test
    fun descendantAndChildSelectorsMatchWhereJsoupSaysTheyDo() {
        val m = parse(
            "<style>.reading-section div > p{font-style:italic}</style>" +
                "<div><p>inside</p><section><p>deeper</p></section></div><p>outside</p>",
        )
        val t = m.texts()
        assertTrue(t[0].italic())
        assertFalse("a child selector does not reach a grandchild", t[1].italic())
        assertFalse(t[2].italic())
    }

    @Test
    fun theMoreSpecificRuleWinsWhateverTheOrder() {
        val m = parse(
            "<style>p.a{font-weight:normal} p{font-weight:bold} #only{font-style:italic} .b{font-style:normal}</style>" +
                "<p class='a'>a</p><p class='b' id='only'>b</p><p>c</p>",
        )
        val t = m.texts()
        assertFalse(t[0].bold())
        assertTrue(t[1].italic())
        assertTrue(t[2].bold())
    }

    @Test
    fun importantBeatsTheInlineStyleAndTheInlineStyleBeatsTheRules() {
        val m = parse(
            "<style>.x{font-style:italic !important} .y{font-style:italic}</style>" +
                "<p class='x' style='font-style:normal'>a</p><p class='y' style='font-style:normal'>b</p>",
        )
        assertTrue(m.texts()[0].italic())
        assertFalse(m.texts()[1].italic())
    }

    @Test
    fun pseudoClassesAndAttributeSelectorsWork() {
        val m = parse(
            "<style>p:first-child{font-weight:bold} p[lang='fr']{font-style:italic}</style>" +
                "<div><p>one</p><p lang='fr'>two</p></div>",
        )
        val t = m.texts()
        assertTrue(t[0].bold())
        assertFalse(t[1].bold())
        assertTrue(t[1].italic())
    }

    @Test
    fun rulesThatCannotApplyAreLeftOutWithoutBreakingTheRest() {
        val m = parse(
            "<style>p::before{content:'x'} a:hover{color:red} p:nth-child(){font-style:italic} p{font-weight:bold}</style><p>text</p>",
        )
        assertTrue(m.texts().single().bold())
    }

    @Test
    fun onlyMediaForAScreenApplies() {
        val m = parse(
            "<style>@media print{p{font-weight:bold}} @media screen{.s{font-style:italic}} @media (max-width:300px){.s{font-weight:bold}}</style>" +
                "<p class='s'>text</p>",
        )
        val t = m.texts().single()
        assertTrue(t.italic())
        assertFalse(t.bold())
    }

    @Test
    fun atRulesAndCommentsDoNotDisturbTheRulesAfterThem() {
        val m = parse(
            "<style>@charset \"utf-8\"; @import url(x.css); /* c */ @font-face{font-family:F;src:url(a.ttf)} p{font-weight:bold}</style><p>t</p>",
        )
        assertTrue(m.texts().single().bold())
    }

    @Test
    fun theBodyRuleReachesEverythingInside() {
        val m = parse("<style>.reading-section{text-align:center} body{font-style:italic}</style><p>t</p>")
        val t = m.texts().single()
        assertEquals(Align.Center, t.align)
        assertTrue(t.italic())
    }

    @Test
    fun marginsAndIndentsAreKeptInEmOfTheTextSize() {
        val m = parse("<style>p{margin:0 0 1em 2em;text-indent:1.5em}</style><p>a</p>")
        val box = m.texts().single().box
        assertEquals(0f, box.beforeEm!!, 0.001f)
        assertEquals(1f, box.afterEm!!, 0.001f)
        assertEquals(2f, box.leftEm, 0.001f)
        assertEquals(1.5f, box.firstEm!!, 0.001f)
    }

    @Test
    fun pixelsAndPointsBecomeEm() {
        assertEquals(1f, lengthEm("16px")!!, 0.001f)
        assertEquals(1f, lengthEm("12pt")!!, 0.001f)
        assertEquals(0.5f, lengthEm("0.5rem")!!, 0.001f)
        assertEquals(0f, lengthEm("0")!!, 0.001f)
        assertNull(lengthEm("50%"))
        assertNull(lengthEm("auto"))
    }

    @Test
    fun aBlockWithoutMarginsLeavesTheSpacingToTheReader() {
        val box = parse("<p>plain</p>").texts().single().box
        assertNull(box.beforeEm)
        assertNull(box.afterEm)
        assertNull(box.firstEm)
    }

    @Test
    fun theSpaceAContainerAsksForGoesToItsFirstAndLastBlock() {
        val m = parse("<style>.wrap{margin:2em 0}</style><div class='wrap'><p>first</p><p>last</p></div>")
        val t = m.texts()
        assertEquals(2f, t.first().box.beforeEm!!, 0.001f)
        assertEquals(2f, t.last().box.afterEm!!, 0.001f)
    }

    @Test
    fun theSidesOfNestedBlocksAddUp() {
        val m = parse("<style>.a{margin-left:1em} .b{margin-left:2em;margin-right:1em}</style><div class='a'><div class='b'><p>x</p></div></div>")
        val box = m.texts().single().box
        assertEquals(3f, box.leftEm, 0.001f)
        assertEquals(1f, box.rightEm, 0.001f)
    }

    @Test
    fun aBlockquoteWithItsOwnMarginsDoesNotAlsoGetTheDefaultIndent() {
        val plain = parse("<blockquote><p>q</p></blockquote>").texts().single()
        assertEquals(1, plain.indent)
        assertEquals(1, plain.box.indent)
        val styled = parse("<style>blockquote{margin:1em 2em}</style><blockquote><p>q</p></blockquote>").texts().single()
        assertEquals("the plain reader still indents it", 1, styled.indent)
        assertEquals(0, styled.box.indent)
        assertEquals(2f, styled.box.leftEm, 0.001f)
    }

    @Test
    fun coloursAreParsedAndKeptOnTheText() {
        assertEquals(Color(0xFFB00020), cssColor("#b00020"))
        assertEquals(Color(0xFF112233), cssColor("#123"))
        assertEquals(Color(255, 0, 0), cssColor("rgb(255, 0, 0)"))
        assertEquals(Color(0xFF008000), cssColor("green"))
        assertNull(cssColor("transparent"))
        assertNull(cssColor("inherit"))
        val block = parse("<p>a <span style='color:#b00020'>red</span> b</p>").texts().single()
        val red = block.spans.single { it.fmt.color != null }
        assertEquals("red", block.text.substring(red.start, red.end))
    }

    @Test
    fun textTransformKeepsEveryPlaceInTheText() {
        val m = parse("<style>.u{text-transform:uppercase} .c{text-transform:capitalize}</style><p class='u'>straße now</p><p class='c'>one two's three</p>")
        val t = m.texts()
        assertEquals("STRAßE NOW", t[0].text)
        assertEquals("straße now".length, t[0].text.length)
        assertEquals("One Two's Three", t[1].text)
    }

    @Test
    fun smallCapitalsAreCapitalsOfTwoSizes() {
        val block = parse("<p style='font-variant:small-caps'>Hello World</p>").texts().single()
        assertEquals("HELLO WORLD", block.text)
        val small = block.spans.filter { it.fmt.sizeRel < 1f }
        assertEquals(listOf("ELLO", "ORLD"), small.map { block.text.substring(it.start, it.end) })
    }

    @Test
    fun whiteSpaceKeepsLineBreaksWhenAskedTo() {
        val lines = parse("<p style='white-space:pre-line'>one  \n   two\nthree</p>").texts().single()
        assertEquals("one\ntwo\nthree", lines.text)
        val pre = parse("<p style='white-space:pre'>a   b\n c</p>").texts().single()
        assertEquals("a   b\n c", pre.text)
    }

    @Test
    fun aPageBreakInTheStylesheetOrTheMarkupStartsANewPage() {
        val m = parse("<style>h2{page-break-before:always}</style><p>one</p><h2>two</h2><p style='break-before:page'>three</p>")
        assertEquals(listOf("one", "two", "three"), m.texts().map { it.text })
        assertEquals(2, m.blocks.count { it === BreakBlock })
        assertTrue(m.blocks[1] === BreakBlock)
    }

    @Test
    fun noBreakIsMadeAtTheVeryStart() {
        val m = parse("<h2 style='page-break-before:always'>first</h2>")
        assertTrue(m.blocks.none { it === BreakBlock })
    }

    @Test
    fun listsFollowTheirStyle() {
        val m = parse(
            "<style>.r{list-style-type:upper-roman} .n{list-style-type:none}</style>" +
                "<ol type='a'><li>x</li><li>y</li></ol><ol class='r'><li>one</li><li>two</li><li>three</li><li>four</li></ol>" +
                "<ul class='n'><li>bare</li></ul><ul><li>dot<ul><li>ring</li></ul></li></ul>",
        )
        assertEquals(
            listOf("a. x", "b. y", "I. one", "II. two", "III. three", "IV. four", "bare", "• dot", "◦ ring"),
            m.texts().map { it.text },
        )
    }

    @Test
    fun inlineSvgIsKeptAsAPictureWithItsOwnSize() {
        val m = parse("""<p>a</p><svg width="200" height="100" viewBox="0 0 200 100"><rect width="200" height="100"/></svg><p>b</p>""")
        val svg = m.blocks[1] as ImageBlock
        assertNotNull(svg.svg)
        assertEquals(IntSize(200, 100), svg.natural)
        assertTrue("the parser lowercases attributes, the picture needs its own case", "viewBox=" in svg.svg!!)
        assertTrue("xmlns=" in svg.svg!!)
        assertTrue("a picture drawn from markup has no file to fetch", m.images.isEmpty())
    }

    @Test
    fun anSvgWithoutASizeTakesItsShapeFromTheViewBox() {
        val m = parse("""<svg viewBox="0 0 400 100"><circle r="5"/></svg>""")
        assertEquals(IntSize(400, 100), (m.blocks.single() as ImageBlock).natural)
        val onlyWidth = parse("""<svg width="100" viewBox="0 0 400 200"><circle r="5"/></svg>""")
        assertEquals(IntSize(100, 50), (onlyWidth.blocks.single() as ImageBlock).natural)
    }

    @Test
    fun anSvgFileSaysItsSizeInItsFirstTag() {
        assertEquals(IntSize(240, 140), svgFileSize("""<?xml version="1.0"?><svg xmlns="http://www.w3.org/2000/svg" width="240" height="140">"""))
        assertEquals(IntSize(600, 800), svgFileSize("""<svg viewBox="0 0 600 800">"""))
        assertEquals(IntSize(300, 150), svgFileSize("<svg>"))
        assertNull(svgFileSize("not a picture"))
    }

    @Test
    fun aPictureCanAskForItsSize() {
        val m = parse("<style>.s{width:30%}</style><img class='s' src='a.png'><img src='b.png' width='120'><img src='c.png' style='width:2em'>")
        val (a, b, c) = m.blocks.map { it as ImageBlock }
        assertEquals(0.3f, a.widthShare!!, 0.001f)
        assertEquals(120f, b.widthDp!!, 0.001f)
        assertEquals(32f, c.widthDp!!, 0.001f)
    }

    @Test
    fun aTableBecomesRowsOfCellsAndItsTextIsSpoken() {
        val m = parse(
            "<table><caption>Yields</caption><tr><th>Field</th><th>Tonnes</th></tr>" +
                "<tr><td>North</td><td><b>12</b></td></tr><tr><td colspan='2'>Total</td></tr></table><p>after</p>",
        )
        val table = m.blocks.filterIsInstance<TableBlock>().single()
        assertEquals(3, table.rows.size)
        assertEquals(listOf("Field", "Tonnes"), table.rows[0].map { it.block!!.text })
        assertTrue(table.rows[0].all { it.header })
        assertEquals(2, table.rows[2].single().span)
        assertTrue(table.rows[1][1].block!!.spans.all { it.fmt.bold })
        assertEquals("Yields\nField\nTonnes\nNorth\n12\nTotal\nafter", m.speech)
        // every cell knows where it sits in the speech
        table.rows.flatten().forEach { c ->
            val b = c.block!!
            assertEquals(b.text, m.speech.substring(b.speechStart, b.speechStart + b.text.length))
        }
    }

    @Test
    fun cellsKeepTheirAlignment() {
        val m = parse("<style>.n{text-align:right}</style><table><tr><td class='n'>1</td><th>H</th><td>x</td></tr></table>")
        val cells = m.blocks.filterIsInstance<TableBlock>().single().rows.single()
        assertEquals(Align.End, cells[0].block!!.align)
        assertEquals(Align.Center, cells[1].block!!.align)
        assertNull(cells[2].block!!.align)
    }

    @Test
    fun aTableInsideATableCellIsFlattenedIntoTheCell() {
        val m = parse("<table><tr><td>outer<table><tr><td>inner</td></tr></table></td></tr></table>")
        val outer = m.blocks.filterIsInstance<TableBlock>().single()
        assertEquals(1, outer.rows.size)
        assertEquals(1, outer.rows[0].size)
    }

    @Test
    fun rubyReadingsAreLeftOutAndTheWordStays() {
        val t = parse("<p>a <ruby>漢字<rp>(</rp><rt>かんじ</rt><rp>)</rp></ruby> b</p>").texts().single()
        assertEquals("a 漢字 b", t.text)
    }

    @Test
    fun displayChangesWhatIsABlock() {
        val m = parse("<style>.i{display:inline} .b{display:block}</style><div>one <div class='i'>two</div> three</div><span class='b'>four</span><span>five</span>")
        assertEquals(listOf("one two three", "four", "five"), m.texts().map { it.text })
    }

    @Test
    fun sizesInPixelsAndPointsScaleTheText() {
        val m = parse("<p style='font-size:24px'>big</p><p style='font-size:8pt'>small</p>")
        assertEquals(1.5f, m.texts()[0].scale, 0.001f)
        assertEquals(0.667f, m.texts()[1].scale, 0.01f)
    }

    @Test
    fun letterSpacingIsKeptInEm() {
        val t = parse("<p>a <span style='letter-spacing:.25em'>wide</span></p>").texts().single()
        assertEquals(0.25f, t.spans.single { it.fmt.spacing != 0f }.fmt.spacing, 0.001f)
    }

    @Test
    fun aHugeStylesheetOfNothingUsefulStillParsesQuickly() {
        val css = (1..3000).joinToString(" ") { ".c$it > .d$it:first-child{color:red} " }
        val start = System.nanoTime()
        val m = parse("<style>$css</style>" + (1..200).joinToString("") { "<p class='c$it'>text $it</p>" })
        val ms = (System.nanoTime() - start) / 1_000_000
        assertEquals(200, m.texts().size)
        assertTrue("took $ms ms", ms < 5_000)
    }
}
