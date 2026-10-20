package com.romanianguy.klover.ui.reader.book

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.min

internal sealed interface Piece {
    val x: Float
    val y: Float
    val w: Float
    val h: Float
}

internal class TextPiece(
    val block: TextBlock,
    val layout: TextLayoutResult,
    override val x: Float,
    override val y: Float,
    override val w: Float,
    override val h: Float,
    val top: Float,
    val charFrom: Int,
    val charTo: Int,
) : Piece {
    val speechFrom get() = block.speechStart + charFrom
    val speechTo get() = block.speechStart + charTo
}

internal class ImagePiece(
    val image: ImageBlock,
    override val x: Float,
    override val y: Float,
    override val w: Float,
    override val h: Float,
) : Piece

internal class RulePiece(override val x: Float, override val y: Float, override val w: Float) : Piece {
    override val h: Float get() = 1f
}

internal class BookLayout(
    val screens: List<List<Piece>>,
) {
    private val speechFrom: IntArray
    private val speechScreen: IntArray

    init {
        val from = ArrayList<Int>()
        val on = ArrayList<Int>()
        screens.forEachIndexed { index, pieces ->
            for (p in pieces) if (p is TextPiece) {
                from += p.speechFrom
                on += index
            }
        }
        speechFrom = from.toIntArray()
        speechScreen = on.toIntArray()
    }

    val count: Int get() = screens.size.coerceAtLeast(1)

    val hasText: Boolean get() = speechFrom.isNotEmpty()

    fun screenOfSpeech(offset: Int): Int {
        if (speechFrom.isEmpty()) return 0
        var lo = 0
        var hi = speechFrom.lastIndex
        var found = 0
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (speechFrom[mid] <= offset) {
                found = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return speechScreen[found]
    }

    fun firstSpeechOn(screen: Int): Int {
        if (speechFrom.isEmpty()) return 0
        val i = speechScreen.indexOfFirst { it >= screen }
        return if (i < 0) speechFrom.last() else if (speechScreen[i] == screen) speechFrom[i] else speechFrom[(i - 1).coerceAtLeast(0)]
    }
}

private class Laid(
    val block: BookBlock,
    val layout: TextLayoutResult?,
    val indentPx: Float,
    val imageW: Float,
    val imageH: Float,
    val before: Float,
    val after: Float,
    val keepWithNext: Boolean,
    val rightPx: Float = 0f,
    val table: TableLaid? = null,
)

private class CellLaid(val block: TextBlock, val layout: TextLayoutResult, val x: Float, val w: Float)

private class RowLaid(val cells: List<CellLaid>, val height: Float)

private class TableLaid(val rows: List<RowLaid>, val width: Float, val padX: Float, val padY: Float)

// the book's colour is used only when it can be read on the page
private const val MIN_CONTRAST = 3f

private fun contrast(a: Color, b: Color): Float {
    val la = a.luminance() + 0.05f
    val lb = b.luminance() + 0.05f
    return if (la > lb) la / lb else lb / la
}

private fun TextBlock.annotated(style: BookStyle, baseSp: Float): AnnotatedString = buildAnnotatedString {
    append(text)
    for (s in spans) {
        val f = s.fmt
        val decoration = when {
            (f.underline || f.link != null) && f.strike ->
                TextDecoration.combine(listOf(TextDecoration.Underline, TextDecoration.LineThrough))
            f.underline || f.link != null -> TextDecoration.Underline
            f.strike -> TextDecoration.LineThrough
            else -> null
        }
        val own = f.color?.takeIf { style.bookStyles && contrast(it, style.background) >= MIN_CONTRAST }
        val span = SpanStyle(
            color = if (f.link != null) style.link else own ?: Color.Unspecified,
            fontWeight = if (f.bold) FontWeight.Bold else null,
            fontStyle = if (f.italic) FontStyle.Italic else null,
            fontFamily = if (f.mono) FontFamily.Monospace else null,
            fontSize = if (f.sizeRel != 1f) (baseSp * scale * f.sizeRel).sp else TextUnit.Unspecified,
            letterSpacing = if (style.bookStyles && f.spacing != 0f) (f.spacing * baseSp * scale).sp else TextUnit.Unspecified,
            baselineShift = when (f.baseline) {
                1 -> BaselineShift.Superscript
                -1 -> BaselineShift.Subscript
                else -> null
            },
            textDecoration = decoration,
        )
        if (span != EMPTY_SPAN) addStyle(span, s.start, s.end)
    }
}

private val EMPTY_SPAN = SpanStyle()

private fun TextBlock.textStyle(style: BookStyle, baseSp: Float): TextStyle {
    val size = baseSp * scale
    val short = text.length <= 200
    val align = when {
        align == Align.Center && (short || kind == Kind.Heading) -> TextAlign.Center
        align == Align.End && (short || kind == Kind.Heading) -> TextAlign.End
        align == Align.Start && kind == Kind.Heading -> TextAlign.Start
        kind == Kind.Heading || kind == Kind.Pre -> TextAlign.Start
        style.justify -> TextAlign.Justify
        else -> TextAlign.Start
    }
    val first = box.firstEm
    val indent = when {
        hang > 0f -> TextIndent(0.sp, (hang * size).sp)
        style.bookStyles && first != null && kind == Kind.Para -> TextIndent((first.coerceAtLeast(0f) * baseSp).sp, 0.sp)
        style.indent && kind == Kind.Para -> TextIndent((1.5f * size).sp, 0.sp)
        else -> TextIndent.None
    }
    return TextStyle(
        color = style.text,
        fontSize = size.sp,
        fontFamily = style.fontFamily,
        lineHeight = (size * style.lineHeight).sp,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
        textAlign = align,
        textDirection = TextDirection.Content,
        hyphens = if (style.hyphenate && kind != Kind.Pre) Hyphens.Auto else Hyphens.None,
        lineBreak = LineBreak.Paragraph,
        textIndent = indent,
    )
}

// columns the size of the style inside the view; one screen is exactly one view width and the gap is twice the
// padding.
// paragraphs split line by line (two lines minimum per side), headings stay with their text, pictures and table rows
// aren't split
internal fun layoutBook(
    model: BookModel,
    style: BookStyle,
    widthPx: Int,
    heightPx: Int,
    density: Density,
    direction: LayoutDirection,
    resolver: FontFamily.Resolver,
    imageSizes: Map<String, IntSize>,
    checkActive: () -> Unit,
): BookLayout {
    val dp = density.density
    val side = style.sidePadDp * dp
    val top = style.topPadDp * dp
    val bottom = style.bottomPadDp * dp
    val n = style.columns.coerceAtLeast(1)
    val gap = side * 2
    val colW = ((widthPx - 2 * side - (n - 1) * gap) / n).coerceAtLeast(64f)
    val colH = (heightPx - top - bottom).coerceAtLeast(64f)
    val baseSp = style.fontSizeDp.toFloat()
    val em = with(density) { baseSp.sp.toPx() }
    val paraGap = (style.paragraphGap ?: DEFAULT_PARAGRAPH_GAP) * em
    // with the spacing set to "Book", the book's own margins are followed
    val bookSpacing = style.bookStyles && style.paragraphGap == null

    val measurer = TextMeasurer(resolver, density, direction, 0)

    fun measure(block: TextBlock, text: TextStyle, width: Float, style: BookStyle) = measurer.measure(
        text = block.annotated(style, baseSp),
        style = text,
        // the whole width, so centred and right aligned lines have room to move in
        constraints = width.toInt().coerceAtLeast(1).let { Constraints(minWidth = it, maxWidth = it) },
        layoutDirection = direction,
        density = density,
        fontFamilyResolver = resolver,
        skipCache = true,
    )

    // a table: the columns share the width by how wide their text wants to be, every cell is one block of text
    fun table(t: TableBlock): TableLaid {
        val padX = 0.6f * em
        val padY = 0.35f * em
        val plain = style.copy(justify = false, indent = false, bookStyles = false)
        val columns = t.rows.maxOf { row -> row.sumOf { it.span } }.coerceAtLeast(1)
        val wanted = FloatArray(columns) { 2 * padX + em }
        // the least a column can be without breaking a word
        val least = FloatArray(columns) { 2 * padX + em }
        for (row in t.rows) {
            var c = 0
            for (cell in row) {
                val b = cell.block
                if (b != null && cell.span == 1) {
                    val natural = measurer.measure(
                        text = b.annotated(plain, baseSp),
                        style = b.textStyle(plain, baseSp),
                        constraints = Constraints(),
                        layoutDirection = direction,
                        density = density,
                        fontFamilyResolver = resolver,
                        skipCache = true,
                    ).size.width
                    wanted[c] = max(wanted[c], natural + 2 * padX)
                    val word = b.text.split(Regex("\\s+")).maxByOrNull { it.length }.orEmpty()
                    if (word.isNotEmpty()) {
                        val w = measurer.measure(AnnotatedString(word), b.textStyle(plain, baseSp), constraints = Constraints()).size.width
                        least[c] = max(least[c], w * 1.1f + 2 * padX)
                    }
                }
                c += cell.span
            }
        }
        for (i in wanted.indices) wanted[i] = max(wanted[i], least[i])
        val total = wanted.sum()
        val leastSum = least.sum()
        val widths = FloatArray(columns) {
            when {
                total <= colW && total >= colW * 0.5f -> colW * wanted[it] / total
                total <= colW -> wanted[it]
                // too wide: every column keeps its longest word, the rest of the width goes to the columns that want more
                leastSum >= colW -> colW * least[it] / leastSum
                else -> least[it] + (colW - leastSum) * (wanted[it] - least[it]) / (total - leastSum)
            }
        }
        val rows = t.rows.map { row ->
            var c = 0
            var x = 0f
            val cells = ArrayList<CellLaid>()
            for (cell in row) {
                val w = (c until min(columns, c + cell.span)).sumOf { widths[it].toDouble() }.toFloat()
                cell.block?.let { b ->
                    checkActive()
                    val layout = measure(b, b.textStyle(plain, baseSp), w - 2 * padX, plain)
                    cells += CellLaid(b, layout, x, w)
                }
                x += w
                c += cell.span
            }
            RowLaid(cells, (cells.maxOfOrNull { it.layout.size.height } ?: 0).toFloat() + 2 * padY)
        }
        return TableLaid(rows, widths.sum(), padX, padY)
    }

    // 1. Measure every block at the width it will have.
    val laid = ArrayList<Laid>(model.blocks.size)
    for (block in model.blocks) {
        checkActive()
        laid += when (block) {
            is TextBlock -> {
                val size = em * block.scale
                val book = style.bookStyles
                val indent = ((if (book) block.box.indent else block.indent) * 1.4f + (if (book) block.box.leftEm else 0f)) * em
                val right = if (book) block.box.rightEm * em else 0f
                val width = (colW - indent - right).coerceAtLeast(48f)
                val layout = measure(block, block.textStyle(style, baseSp), width, style)
                var (before, after) = when (block.kind) {
                    Kind.Heading -> (1.0f * size) to (0.45f * size)
                    Kind.ListItem -> 0f to (0.25f * em)
                    else -> 0f to paraGap
                }
                if (bookSpacing && block.kind != Kind.Pre) {
                    block.box.beforeEm?.let { before = it * em }
                    block.box.afterEm?.let { after = it * em }
                }
                Laid(block, layout, indent, 0f, 0f, before, after, block.kind == Kind.Heading, right)
            }

            is ImageBlock -> {
                val natural = block.natural ?: imageSizes[block.src]
                var w: Float
                var h: Float
                if (natural != null && natural.width > 0 && natural.height > 0) {
                    w = natural.width * dp
                    h = natural.height * dp
                } else {
                    // Not measured (yet): a plausible frame, so the page is laid out and can be fixed up when the
                    // size is known.
                    w = colW
                    h = colW * 0.75f
                }
                val wantedW = block.widthShare?.let { colW * it } ?: block.widthDp?.let { it * dp }
                if (wantedW != null) {
                    h = if (block.heightDp != null && block.widthShare == null) block.heightDp * dp else h * wantedW / w
                    w = wantedW
                } else if (block.heightDp != null) {
                    w = w * block.heightDp * dp / h
                    h = block.heightDp * dp
                }
                val scale = min(1f, min(colW / w, colH / h))
                w *= scale
                h *= scale
                Laid(block, null, 0f, w, h, 0.3f * em, 0.6f * em, false)
            }

            is TableBlock -> {
                val laidTable = table(block)
                Laid(block, null, 0f, laidTable.width, 0f, 0.4f * em, paraGap, false, table = laidTable)
            }

            RuleBlock -> Laid(block, null, 0f, colW, 1f, 0.8f * em, 0.8f * em, false)

            BreakBlock -> Laid(block, null, 0f, 0f, 0f, 0f, 0f, false)
        }
    }

    // 2. Pour them into columns.
    val columns = ArrayList<List<Piece>>()
    var current = ArrayList<Piece>()
    var y = 0f
    var previousAfter = 0f
    fun nextColumn() {
        columns += current
        current = ArrayList()
        y = 0f
    }

    fun followHeight(index: Int): Float {
        val next = laid.getOrNull(index + 1) ?: return 0f
        val l = next.layout
        return next.before + when {
            l != null -> l.getLineBottom(min(2, l.lineCount) - 1)
            else -> next.imageH
        }
    }

    for ((index, item) in laid.withIndex()) {
        checkActive()
        if (item.block === BreakBlock) {
            // the next block starts a new screen
            if (current.isNotEmpty()) nextColumn()
            if (columns.isNotEmpty()) while (columns.size % n != 0) columns += emptyList<Piece>()
            previousAfter = 0f
            continue
        }
        val table = item.table
        if (table != null) {
            var first = true
            for (row in table.rows) {
                val spacing = if (current.isEmpty()) 0f else if (first) max(previousAfter, item.before) else 0f
                if (y + spacing + row.height > colH && current.isNotEmpty()) nextColumn()
                val at = if (current.isEmpty()) 0f else y + (if (first) max(previousAfter, item.before) else 0f)
                val x = if (table.width < colW) 0f else 0f
                if (first || current.isEmpty()) current += RulePiece(x, at, table.width)
                for (cell in row.cells) {
                    val lines = cell.layout.lineCount
                    current += TextPiece(
                        block = cell.block,
                        layout = cell.layout,
                        x = x + cell.x + table.padX,
                        y = at + table.padY,
                        w = cell.w - 2 * table.padX,
                        h = cell.layout.size.height.toFloat(),
                        top = 0f,
                        charFrom = 0,
                        charTo = cell.layout.getLineEnd(lines - 1),
                    )
                }
                current += RulePiece(x, at + row.height, table.width)
                y = at + row.height
                first = false
            }
            previousAfter = item.after
            continue
        }
        val layout = item.layout
        if (layout == null) {
            val spacing = if (current.isEmpty()) 0f else max(previousAfter, item.before)
            if (y + spacing + item.imageH > colH && current.isNotEmpty()) nextColumn()
            val at = if (current.isEmpty()) 0f else y + max(previousAfter, item.before)
            val x = (colW - item.imageW) / 2f
            current += when (val b = item.block) {
                is ImageBlock -> ImagePiece(b, x, at, item.imageW, min(item.imageH, colH))
                else -> RulePiece(0f, at, colW)
            }
            y = at + item.imageH
            previousAfter = item.after
            continue
        }

        val block = item.block as TextBlock
        val lines = layout.lineCount
        var line = 0
        while (line < lines) {
            val spacing = if (current.isEmpty() || line > 0) 0f else max(previousAfter, item.before)
            val room = colH - y - spacing
            val firstTop = layout.getLineTop(line)
            var end = line
            while (end < lines && layout.getLineBottom(end) - firstTop <= room + 0.5f) end++
            var fit = end - line
            val left = lines - end
            if (fit > 0 && left > 0) {
                // Orphans and widows: a lone line is not left at the foot of a column or carried to the top of the
                // next.
                if (line == 0 && fit < 2 && current.isNotEmpty()) fit = 0
                else if (left == 1 && fit >= 3) fit -= 1
            }
            if (fit == lines - line && item.keepWithNext && current.isNotEmpty()) {
                // A heading that would end a column goes to the next one, with its text.
                val need = layout.getLineBottom(lines - 1) + followHeight(index)
                if (need > room) fit = 0
            }
            if (fit == 0) {
                if (current.isEmpty()) fit = 1 else {
                    nextColumn()
                    continue
                }
            }
            val last = line + fit - 1
            val at = y + spacing
            val height = layout.getLineBottom(last) - firstTop
            current += TextPiece(
                block = block,
                layout = layout,
                x = item.indentPx,
                y = at,
                w = colW - item.indentPx - item.rightPx,
                h = height,
                top = firstTop,
                charFrom = layout.getLineStart(line),
                charTo = layout.getLineEnd(last),
            )
            y = at + height
            line += fit
            if (line < lines) nextColumn()
        }
        previousAfter = item.after
    }
    if (current.isNotEmpty() || columns.isEmpty()) columns += current

    // 3. Group columns into screens and give every piece its place on its screen.
    val screens = ArrayList<List<Piece>>()
    var c = 0
    while (c < columns.size) {
        val pieces = ArrayList<Piece>()
        for (i in 0 until n) {
            val col = columns.getOrNull(c + i) ?: break
            val dx = side + i * (colW + gap)
            for (p in col) pieces += p.moved(dx, top)
        }
        screens += pieces
        c += n
    }
    return BookLayout(screens)
}

private fun Piece.moved(dx: Float, dy: Float): Piece = when (this) {
    is TextPiece -> TextPiece(block, layout, x + dx, y + dy, w, h, top, charFrom, charTo)
    is ImagePiece -> ImagePiece(image, x + dx, y + dy, w, h)
    is RulePiece -> RulePiece(x + dx, y + dy, w)
}

private const val DEFAULT_PARAGRAPH_GAP = 0.6f
