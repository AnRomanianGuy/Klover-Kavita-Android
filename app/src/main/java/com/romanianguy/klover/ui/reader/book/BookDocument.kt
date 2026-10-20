package com.romanianguy.klover.ui.reader.book

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntSize
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.util.IdentityHashMap

data class BookStyle(
    val columns: Int,
    // with two columns the gap between them is twice this
    val sidePadDp: Int,
    val topPadDp: Int,
    val bottomPadDp: Int,
    val fontSizeDp: Int,
    val background: Color,
    val text: Color,
    val link: Color,
    val selection: Color,
    val fontFamily: FontFamily = FontFamily.Serif,
    val lineHeight: Float = 1.6f,
    val justify: Boolean = true,
    val tapEdge: Float = 0.25f,
    val paragraphGap: Float? = null,
    val indent: Boolean = false,
    val hyphenate: Boolean = true,
    // follow what the book's own stylesheet says about margins, indents and colours
    val bookStyles: Boolean = true,
)

data class BookStart(val fraction: Float)

data class BookLink(val section: Int?, val url: String?)

internal data class Fmt(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strike: Boolean = false,
    val mono: Boolean = false,
    val baseline: Int = 0,
    val sizeRel: Float = 1f,
    val link: BookLink? = null,
    val color: Color? = null,
    // in em
    val spacing: Float = 0f,
)

internal class Span(val start: Int, val end: Int, val fmt: Fmt)

internal enum class Kind { Para, Heading, ListItem, Pre }

internal enum class Align { Start, Center, End, Justify }

internal sealed interface BookBlock

// what the book's own CSS asks of a block; the reader follows it when the book's styling is on. Lengths are in em of
// the reader's text size
internal data class BookBox(
    val indent: Int = 0,
    val leftEm: Float = 0f,
    val rightEm: Float = 0f,
    val firstEm: Float? = null,
    val beforeEm: Float? = null,
    val afterEm: Float? = null,
)

internal data class TextBlock(
    val text: String,
    val spans: List<Span>,
    val kind: Kind,
    val scale: Float,
    val indent: Int,
    val align: Align?,
    val hang: Float,
    val speechStart: Int,
    val box: BookBox = BookBox(indent = indent),
) : BookBlock

internal class ImageBlock(
    val src: String,
    val alt: String,
    // the markup of an SVG drawn in the text; such a picture has no file to fetch
    val svg: String? = null,
    val natural: IntSize? = null,
    // the size the book asks for: a share of the column, or dp
    val widthShare: Float? = null,
    val widthDp: Float? = null,
    val heightDp: Float? = null,
) : BookBlock

internal object RuleBlock : BookBlock

// the next block starts a new page (a new screen)
internal object BreakBlock : BookBlock

internal class TableCell(val block: TextBlock?, val span: Int, val header: Boolean)

internal class TableBlock(val rows: List<List<TableCell>>) : BookBlock

internal class BookModel(val blocks: List<BookBlock>, val speech: String) {
    val images: List<String> = blocks.filterIsInstance<ImageBlock>().filter { it.svg == null }.map { it.src }.distinct()
}

internal fun parseBookSection(html: String): BookModel {
    val doc = Jsoup.parseBodyFragment(html)
    val css = BookCss.parse(doc.select("style").joinToString("\n") { it.data() })
    return SectionParser(css.cascade(doc.body())).parse(doc.body())
}

// text length as the reader counts it, which is what the time-left estimate works with
fun sectionTextLength(html: String): Int = parseBookSection(html).speech.length

internal class Decl(val value: String, val important: Boolean)

// the stylesheet's rules, matched to the elements of one section with jsoup's selector engine
internal class BookCss private constructor(private val rules: List<Rule>) {
    private class Rule(val selector: String, val specificity: Int, val order: Int, val decl: Map<String, Decl>)

    // what applies to each element, by the cascade: later and more specific rules win, !important beats the rest and
    // the element's own style attribute beats the rules
    class Cascade(
        private val normal: Map<Element, Map<String, String>>,
        private val important: Map<Element, Map<String, String>>,
    ) {
        fun resolve(el: Element): Map<String, String> {
            val rules = normal[el]
            val strong = important[el]
            val inline = el.attr("style").takeIf { it.isNotBlank() }?.let(::parseDeclarations)
            if (rules == null && strong == null && inline == null) return emptyMap()
            val out = HashMap<String, String>()
            rules?.let(out::putAll)
            inline?.forEach { (k, d) -> if (!d.important) out[k] = d.value }
            strong?.let(out::putAll)
            inline?.forEach { (k, d) -> if (d.important) out[k] = d.value }
            return out
        }
    }

    fun cascade(root: Element): Cascade {
        if (rules.isEmpty()) return Cascade(emptyMap(), emptyMap())
        val hits = IdentityHashMap<Element, ArrayList<Rule>>()
        for (rule in rules) {
            val found = try {
                root.select(rule.selector)
            } catch (e: Exception) {
                continue // a selector jsoup does not know: the rule is left out
            }
            for (el in found) hits.getOrPut(el) { ArrayList() }.add(rule)
        }
        val normal = IdentityHashMap<Element, Map<String, String>>()
        val important = IdentityHashMap<Element, Map<String, String>>()
        for ((el, list) in hits) {
            list.sortWith(compareBy<Rule>({ it.specificity }, { it.order }))
            val n = HashMap<String, String>()
            val i = HashMap<String, String>()
            for (r in list) for ((k, d) in r.decl) (if (d.important) i else n)[k] = d.value
            normal[el] = n
            if (i.isNotEmpty()) important[el] = i
        }
        return Cascade(normal, important)
    }

    companion object {
        val Empty = BookCss(emptyList())

        private const val MAX_RULES = 4000
        private val COMMENT = Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL)
        private val AT_STATEMENT = Regex("@[a-zA-Z-]+[^;{}]*;")

        // the server puts these in front of every selector, standing for the container of the section
        private val SCOPE = Regex("^(?:\\.reading-section|\\.book-content|body|html)(?:\\.(?:reading-section|book-content))*(?![\\w-])\\s*(?:>\\s*)?")

        // pseudo-elements and states a page has none of
        private val UNMATCHABLE = Regex(
            "::|:(?:before|after|first-line|first-letter|selection|hover|active|focus|visited|link|any-link|target|" +
                "checked|disabled|enabled|focus-within|focus-visible|lang|dir)\\b",
        )

        fun parse(text: String): BookCss {
            if (text.isBlank()) return Empty
            val rules = ArrayList<Rule>()
            parseSheet(text.replace(COMMENT, ""), rules, intArrayOf(0))
            return if (rules.isEmpty()) Empty else BookCss(rules.take(MAX_RULES))
        }

        private fun parseSheet(css: String, out: MutableList<Rule>, order: IntArray) {
            var i = 0
            while (i < css.length) {
                val open = css.indexOf('{', i)
                if (open < 0) break
                val prelude = css.substring(i, open).replace(AT_STATEMENT, "").trim()
                val close = closingBrace(css, open)
                val body = css.substring(open + 1, close)
                i = close + 1
                if (prelude.startsWith("@")) {
                    // only media for a screen; fonts, pages, animations and the rest are not used
                    if (prelude.startsWith("@media") && appliesToScreen(prelude.removePrefix("@media").trim())) parseSheet(body, out, order)
                    continue
                }
                val decl = parseDeclarations(body)
                if (decl.isEmpty()) continue
                for (selector in splitSelectors(prelude)) {
                    val scoped = scoped(selector) ?: continue
                    out += Rule(scoped, specificity(scoped), order[0]++, decl)
                }
            }
        }

        private fun closingBrace(css: String, open: Int): Int {
            var depth = 0
            var quote = 0.toChar()
            for (k in open until css.length) {
                val c = css[k]
                if (quote != 0.toChar()) {
                    if (c == quote) quote = 0.toChar()
                    continue
                }
                when (c) {
                    '"', '\'' -> quote = c
                    '{' -> depth++
                    '}' -> if (--depth == 0) return k
                }
            }
            return css.length
        }

        private fun appliesToScreen(query: String): Boolean {
            val q = query.lowercase().trim()
            if (q.isEmpty() || q == "all" || q == "screen") return true
            // sizes and other features are not guessed at; anything for print, speech or an e-ink brand is skipped
            return false
        }

        // a comma separates selectors, except inside brackets and parentheses
        private fun splitSelectors(text: String): List<String> {
            val out = ArrayList<String>()
            var depth = 0
            var start = 0
            for ((k, c) in text.withIndex()) {
                when (c) {
                    '(', '[' -> depth++
                    ')', ']' -> depth--
                    ',' -> if (depth == 0) {
                        out += text.substring(start, k)
                        start = k + 1
                    }
                }
            }
            out += text.substring(start)
            return out.map { it.trim() }.filter { it.isNotEmpty() }
        }

        private fun scoped(selector: String): String? {
            if (UNMATCHABLE.containsMatchIn(selector)) return null
            var s = selector.trim()
            var stripped = false
            while (true) {
                val m = SCOPE.find(s) ?: break
                s = s.substring(m.value.length)
                stripped = true
            }
            if (s.isEmpty()) return if (stripped) "body" else null
            return s
        }

        // ids, then classes / attributes / pseudo-classes, then tags: weighed like CSS does
        private fun specificity(selector: String): Int {
            val noStrings = selector.replace(Regex("\"[^\"]*\"|'[^']*'"), "")
            val ids = Regex("#[\\w-]+").findAll(noStrings).count()
            val classes = Regex("\\.[\\w-]+|\\[[^\\]]*]|:(?!not\\b)[\\w-]+").findAll(noStrings).count()
            val bare = noStrings.replace(Regex("\\[[^\\]]*]|#[\\w-]+|\\.[\\w-]+|:[\\w-]+(?:\\([^)]*\\))?"), " ")
            val tags = Regex("(?<![\\w-])[a-zA-Z][\\w-]*").findAll(bare).count()
            return ids * 10_000 + classes * 100 + tags
        }

        fun parseDeclarations(text: String): Map<String, Decl> {
            val out = HashMap<String, Decl>()
            for (part in splitDeclarations(text)) {
                val i = part.indexOf(':')
                if (i <= 0) continue
                val name = part.substring(0, i).trim().lowercase()
                var value = part.substring(i + 1).trim()
                val important = value.contains("!important", ignoreCase = true)
                value = value.replace("!important", "", ignoreCase = true).trim().lowercase()
                if (value.isEmpty() || name.isEmpty()) continue
                expand(name, value, important, out)
            }
            return out
        }

        // a semicolon ends a declaration, except inside quotes and parentheses (url(data:image/png;base64,...))
        private fun splitDeclarations(text: String): List<String> {
            val out = ArrayList<String>()
            var depth = 0
            var quote = 0.toChar()
            var start = 0
            for ((k, c) in text.withIndex()) {
                if (quote != 0.toChar()) {
                    if (c == quote) quote = 0.toChar()
                    continue
                }
                when (c) {
                    '"', '\'' -> quote = c
                    '(' -> depth++
                    ')' -> depth--
                    ';' -> if (depth == 0) {
                        out += text.substring(start, k)
                        start = k + 1
                    }
                }
            }
            out += text.substring(start)
            return out
        }

        private val SIZE = Regex("^(?:\\d*\\.?\\d+(?:px|pt|em|rem|%|ex|ch)?|x-small|small|medium|large|x-large|xx-large|smaller|larger)(?:/.*)?$")

        private fun expand(name: String, value: String, important: Boolean, out: MutableMap<String, Decl>) {
            fun put(key: String, v: String) {
                out[key] = Decl(v, important)
            }
            when (name) {
                "margin", "padding" -> {
                    val v = value.split(Regex("\\s+"))
                    if (v.size > 4) return
                    val top = v[0]
                    val right = v.getOrElse(1) { top }
                    val bottom = v.getOrElse(2) { top }
                    val left = v.getOrElse(3) { right }
                    put("$name-top", top)
                    put("$name-right", right)
                    put("$name-bottom", bottom)
                    put("$name-left", left)
                }

                "list-style" -> value.split(Regex("\\s+")).firstOrNull { it in LIST_STYLES }?.let { put("list-style-type", it) }
                "font" -> {
                    val tokens = value.split(Regex("\\s+"))
                    for (t in tokens) {
                        when {
                            t == "italic" || t == "oblique" -> put("font-style", "italic")
                            t == "bold" || t == "bolder" || (t.toIntOrNull() ?: 0) >= 100 -> put("font-weight", t)
                            SIZE.matches(t) -> put("font-size", t.substringBefore('/'))
                        }
                    }
                    if (tokens.any { "mono" in it || "courier" in it }) put("font-family", "monospace")
                }

                else -> put(name, value)
            }
        }
    }
}

private val LIST_STYLES = setOf(
    "none", "disc", "circle", "square", "decimal", "decimal-leading-zero", "lower-alpha", "upper-alpha", "lower-latin",
    "upper-latin", "lower-roman", "upper-roman",
)

private val SKIPPED = setOf(
    "script",
    "style",
    "head",
    "noscript",
    "title",
    "meta",
    "link",
    "template",
    "audio",
    "video",
    "canvas",
    "object",
    "iframe",
    // ruby: the reading above a word is left out, the word itself stays
    "rt",
    "rp",
)

private val BLOCKS = setOf(
    "p", "div", "section", "article", "aside", "header", "footer", "nav", "main", "figure", "figcaption", "blockquote",
    "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol", "li", "dl", "dt", "dd", "caption", "pre", "address", "details", "summary",
    "form", "fieldset", "center", "tr", "td", "th", "thead", "tbody", "tfoot",
)

private val HEADING_SCALE = mapOf("h1" to 1.6f, "h2" to 1.4f, "h3" to 1.2f, "h4" to 1.1f, "h5" to 1.0f, "h6" to 0.9f)

private enum class ListKind { Decimal, LowerAlpha, UpperAlpha, LowerRoman, UpperRoman, Disc, Circle, Square, None }

private class ListState(var next: Int, val kind: ListKind, val depth: Int)

private enum class Transform { None, Upper, Lower, Capitalize }

// 0 = collapse white space, 1 = keep it as it is, 2 = keep the line breaks only
private const val WHITE_COLLAPSE = 0
private const val WHITE_PRE = 1
private const val WHITE_LINES = 2

private data class Ctx(
    val fmt: Fmt = Fmt(),
    val kind: Kind = Kind.Para,
    val scale: Float = 1f,
    val indent: Int = 0,
    val indentBook: Int = 0,
    val leftEm: Float = 0f,
    val rightEm: Float = 0f,
    val firstEm: Float? = null,
    val align: Align? = null,
    val white: Int = WHITE_COLLAPSE,
    val hang: Float = 0f,
    val transform: Transform = Transform.None,
    val smallCaps: Boolean = false,
)

private class SectionParser(private val styles: BookCss.Cascade) {
    private val blocks = ArrayList<BookBlock>()
    private val buf = StringBuilder()
    private val spans = ArrayList<Span>()
    private var open: Ctx? = null
    private var openBox = BookBox()

    private var marker: String? = null
    private val lists = ArrayList<ListState>()

    // the margins of the block being read, and the space a container asks for above its first block
    private var before: Float? = null
    private var after: Float? = null
    private var pendingBefore: Float? = null
    private var svgCount = 0

    fun parse(body: Element): BookModel {
        walkChildren(body, ctxOf(body, "body", Ctx(), isBlock = true))
        flush()
        return finish()
    }

    // the blocks of one piece of the page, such as a table cell
    fun fragment(el: Element, ctx: Ctx): List<BookBlock> {
        walkChildren(el, ctx)
        flush()
        return blocks
    }

    private fun finish(): BookModel {
        val out = ArrayList<BookBlock>(blocks.size)
        val speech = StringBuilder()

        fun place(b: TextBlock): TextBlock {
            if (speech.isNotEmpty()) speech.append('\n')
            val placed = b.copy(speechStart = speech.length)
            speech.append(b.text)
            return placed
        }
        for (b in blocks) {
            when (b) {
                is TextBlock -> out += place(b)
                is TableBlock -> out += TableBlock(b.rows.map { row -> row.map { c -> c.block?.let { TableCell(place(it), c.span, c.header) } ?: c } })
                else -> out += b
            }
        }
        return BookModel(out, speech.toString())
    }

    private fun add(block: BookBlock) {
        if (block !is TextBlock) pendingBefore = null
        blocks += block
    }

    private fun walkChildren(el: Node, ctx: Ctx) {
        for (child in el.childNodes()) walk(child, ctx)
    }

    private fun walk(node: Node, ctx: Ctx) {
        when (node) {
            is TextNode -> append(node.wholeText, ctx)
            is Element -> element(node, ctx)
            else -> Unit
        }
    }

    private fun element(el: Element, parent: Ctx) {
        val tag = el.normalName()
        if (tag in SKIPPED) return
        val decl = styles.resolve(el)
        if (decl["display"] == "none" || decl["visibility"] == "hidden" || el.hasAttr("hidden")) return

        when (tag) {
            "br" -> {
                if (open != null || buf.isNotEmpty()) appendRaw("\n", parent)
                return
            }

            "img" -> {
                flush()
                image(el, decl)
                return
            }

            "svg" -> {
                flush()
                svg(el)
                return
            }

            "hr" -> {
                flush()
                add(RuleBlock)
                return
            }

            "table" -> {
                table(el, parent)
                return
            }
        }

        val display = decl["display"]
        val isBlock = (tag in BLOCKS && display != "inline" && display != "inline-block") ||
            display == "block" || display == "list-item"
        if (isBlock && breaks(decl, "before")) pageBreak()
        var ctx = ctxOf(el, tag, parent, isBlock, decl)

        // margins belong to the block, not to what is inside it
        val savedBefore = before
        val savedAfter = after
        var container: Pair<Float?, Float?>? = null
        if (isBlock) {
            val (top, bottom) = verticalMargins(decl, ctx.scale)
            if (tag in CONTAINERS) {
                // a container's space above goes to its first block, the space below to its last
                container = top to bottom
                if (top != null) pendingBefore = maxOf(pendingBefore ?: 0f, top)
                before = null
                after = null
            } else {
                before = top
                after = bottom
            }
        }

        when {
            tag == "ul" || tag == "ol" -> {
                flush()
                val depth = lists.size
                val kind = listKind(tag, el, decl, depth)
                lists += ListState(if (tag == "ol") (el.attr("start").toIntOrNull() ?: 1) else 0, kind, depth)
                walkChildren(el, ctx.copy(indent = ctx.indent + 1, indentBook = ctx.indentBook + 1))
                lists.removeAt(lists.lastIndex)
                flush()
            }

            tag == "li" -> {
                flush()
                val state = lists.lastOrNull()
                val kind = decl["list-style-type"]?.let(::kindOf) ?: state?.kind ?: ListKind.Disc
                val number = state?.next ?: 0
                if (state != null && state.kind.numbered) state.next = number + 1
                val text = markerFor(kind, number)
                marker = text
                ctx = ctx.copy(
                    kind = Kind.ListItem,
                    hang = when {
                        text == null -> 0f
                        kind.numbered -> 0.65f * text.length
                        else -> 0.95f
                    },
                )
                walkChildren(el, ctx)
                flush()
                marker = null
            }

            tag == "a" -> {
                val link = linkOf(el)
                walkChildren(el, if (link != null) ctx.copy(fmt = ctx.fmt.copy(link = link)) else ctx)
            }

            tag == "blockquote" -> {
                flush()
                val sideways = decl.keys.any { it.startsWith("margin-left") || it.startsWith("padding-left") }
                walkChildren(el, ctx.copy(indent = ctx.indent + 1, indentBook = ctx.indentBook + if (sideways) 0 else 1))
                flush()
            }

            isBlock -> {
                flush()
                walkChildren(el, ctx)
                flush()
            }

            else -> walkChildren(el, ctx)
        }

        if (isBlock) {
            container?.second?.let { bottom -> bumpAfter(bottom) }
            before = savedBefore
            after = savedAfter
            if (breaks(decl, "after")) pageBreak()
        }
    }

    private fun breaks(decl: Map<String, String>, side: String): Boolean {
        val v = decl["page-break-$side"] ?: decl["break-$side"] ?: return false
        return v == "always" || v == "page" || v == "left" || v == "right" || v == "recto" || v == "verso"
    }

    private fun pageBreak() {
        flush()
        if (blocks.isNotEmpty() && blocks.last() !is BreakBlock) blocks += BreakBlock
    }

    // the space a container asks for below its last block
    private fun bumpAfter(em: Float) {
        val last = blocks.lastOrNull() as? TextBlock ?: return
        blocks[blocks.lastIndex] = last.copy(box = last.box.copy(afterEm = maxOf(last.box.afterEm ?: 0f, em)))
    }

    private fun verticalMargins(decl: Map<String, String>, scale: Float): Pair<Float?, Float?> {
        val top = decl["margin-top"]?.let(::lengthEm)?.let { it * scale }
        val bottom = decl["margin-bottom"]?.let(::lengthEm)?.let { it * scale }
        return top?.coerceAtLeast(0f) to bottom?.coerceAtLeast(0f)
    }

    private fun ctxOf(el: Element, tag: String, parent: Ctx, isBlock: Boolean, decl: Map<String, String> = styles.resolve(el)): Ctx {
        var f = parent.fmt
        var scale = parent.scale
        var kind = parent.kind
        var white = parent.white
        var align = parent.align
        var transform = parent.transform
        var smallCaps = parent.smallCaps
        var firstEm = parent.firstEm

        when (tag) {
            "b", "strong", "th" -> f = f.copy(bold = true)
            "i", "em", "cite", "dfn", "var" -> f = f.copy(italic = true)
            "u", "ins" -> f = f.copy(underline = true)
            "s", "strike", "del" -> f = f.copy(strike = true)
            "code", "kbd", "samp", "tt" -> f = f.copy(mono = true)
            "sup" -> f = f.copy(baseline = 1, sizeRel = f.sizeRel * 0.75f)
            "sub" -> f = f.copy(baseline = -1, sizeRel = f.sizeRel * 0.75f)
            "small" -> f = f.copy(sizeRel = f.sizeRel * 0.85f)
            "big" -> f = f.copy(sizeRel = f.sizeRel * 1.2f)
            "pre" -> {
                f = f.copy(mono = true)
                white = WHITE_PRE
                kind = Kind.Pre
            }

            "center" -> align = Align.Center
            "h1", "h2", "h3", "h4", "h5", "h6" -> {
                f = f.copy(bold = true)
                scale *= HEADING_SCALE.getValue(tag)
                kind = Kind.Heading
            }
        }
        if (el.attr("align").equals("center", ignoreCase = true)) align = Align.Center

        decl["font-weight"]?.let { w ->
            f = f.copy(bold = w == "bold" || w == "bolder" || (w.toIntOrNull() ?: 0) >= 600)
        }
        decl["font-style"]?.let { s -> f = f.copy(italic = s == "italic" || s == "oblique") }
        (decl["text-decoration-line"] ?: decl["text-decoration"])?.let { d ->
            f = when {
                "none" in d -> f.copy(underline = false, strike = false)
                else -> f.copy(underline = f.underline || "underline" in d, strike = f.strike || "line-through" in d)
            }
        }
        decl["font-family"]?.let { family -> if ("mono" in family || "courier" in family) f = f.copy(mono = true) }
        decl["vertical-align"]?.let { v ->
            when (v) {
                "super" -> f = f.copy(baseline = 1, sizeRel = f.sizeRel * 0.75f)
                "sub" -> f = f.copy(baseline = -1, sizeRel = f.sizeRel * 0.75f)
            }
        }
        decl["font-size"]?.let(::fontFactor)?.let { factor ->
            when {
                // A heading's own size is what the stylesheet means by its font-size, not a further change to the
                // default one.
                tag in HEADING_SCALE -> scale = parent.scale * factor
                isBlock -> scale *= factor
                else -> f = f.copy(sizeRel = f.sizeRel * factor)
            }
        }
        decl["color"]?.let(::cssColor)?.let { f = f.copy(color = it) }
        decl["letter-spacing"]?.let { s -> f = f.copy(spacing = if (s == "normal") 0f else lengthEm(s) ?: f.spacing) }
        decl["text-transform"]?.let { t ->
            transform = when (t) {
                "uppercase" -> Transform.Upper
                "lowercase" -> Transform.Lower
                "capitalize" -> Transform.Capitalize
                else -> Transform.None
            }
        }
        (decl["font-variant"] ?: decl["font-variant-caps"])?.let { v -> smallCaps = "small-caps" in v }
        decl["white-space"]?.let { w ->
            white = when (w) {
                "pre", "pre-wrap", "break-spaces" -> WHITE_PRE
                "pre-line" -> WHITE_LINES
                else -> WHITE_COLLAPSE
            }
        }
        decl["text-indent"]?.let { v -> lengthEm(v)?.let { firstEm = it * scale } }
        decl["text-align"]?.let { a ->
            align = when (a) {
                "center" -> Align.Center
                "right", "end" -> Align.End
                "left", "start" -> Align.Start
                "justify" -> Align.Justify
                else -> align
            }
        }

        var left = parent.leftEm
        var right = parent.rightEm
        if (isBlock) {
            // the sides of nested blocks add up
            left += ((decl["margin-left"]?.let(::lengthEm) ?: 0f) + (decl["padding-left"]?.let(::lengthEm) ?: 0f)) * scale
            right += ((decl["margin-right"]?.let(::lengthEm) ?: 0f) + (decl["padding-right"]?.let(::lengthEm) ?: 0f)) * scale
        }
        return parent.copy(
            fmt = f,
            scale = scale,
            kind = kind,
            white = white,
            align = align,
            transform = transform,
            smallCaps = smallCaps,
            firstEm = firstEm,
            leftEm = left.coerceAtLeast(0f),
            rightEm = right.coerceAtLeast(0f),
        )
    }

    private val ListKind.numbered: Boolean
        get() = this == ListKind.Decimal || this == ListKind.LowerAlpha || this == ListKind.UpperAlpha ||
            this == ListKind.LowerRoman || this == ListKind.UpperRoman

    private fun listKind(tag: String, el: Element, decl: Map<String, String>, depth: Int): ListKind {
        decl["list-style-type"]?.let { return kindOf(it) }
        if (tag == "ol") {
            return when (el.attr("type")) {
                "a" -> ListKind.LowerAlpha
                "A" -> ListKind.UpperAlpha
                "i" -> ListKind.LowerRoman
                "I" -> ListKind.UpperRoman
                else -> ListKind.Decimal
            }
        }
        return when (depth) {
            0 -> ListKind.Disc
            1 -> ListKind.Circle
            else -> ListKind.Square
        }
    }

    private fun kindOf(value: String) = when (value) {
        "none" -> ListKind.None
        "disc" -> ListKind.Disc
        "circle" -> ListKind.Circle
        "square" -> ListKind.Square
        "lower-alpha", "lower-latin" -> ListKind.LowerAlpha
        "upper-alpha", "upper-latin" -> ListKind.UpperAlpha
        "lower-roman" -> ListKind.LowerRoman
        "upper-roman" -> ListKind.UpperRoman
        else -> ListKind.Decimal
    }

    private fun markerFor(kind: ListKind, number: Int): String? = when (kind) {
        ListKind.None -> null
        ListKind.Disc -> "• "
        ListKind.Circle -> "◦ "
        ListKind.Square -> "▪ "
        ListKind.Decimal -> "$number. "
        ListKind.LowerAlpha -> alpha(number) + ". "
        ListKind.UpperAlpha -> alpha(number).uppercase() + ". "
        ListKind.LowerRoman -> roman(number).lowercase() + ". "
        ListKind.UpperRoman -> roman(number) + ". "
    }

    private fun alpha(n: Int): String {
        var x = n.coerceAtLeast(1)
        val out = StringBuilder()
        while (x > 0) {
            x--
            out.insert(0, 'a' + x % 26)
            x /= 26
        }
        return out.toString()
    }

    private fun roman(n: Int): String {
        var x = n.coerceIn(1, 3999)
        val out = StringBuilder()
        for ((value, text) in ROMAN) while (x >= value) {
            out.append(text)
            x -= value
        }
        return out.toString()
    }

    private fun fontFactor(value: String): Float? = when {
        value == "smaller" -> 0.85f
        value == "larger" -> 1.2f
        value == "x-small" || value == "xx-small" -> 0.7f
        value == "small" -> 0.85f
        value == "medium" -> 1f
        value == "large" -> 1.15f
        value == "x-large" -> 1.35f
        value == "xx-large" -> 1.6f
        value.endsWith("%") -> value.dropLast(1).toFloatOrNull()?.div(100f)
        value.endsWith("rem") -> value.dropLast(3).toFloatOrNull()
        value.endsWith("em") -> value.dropLast(2).toFloatOrNull()
        value.endsWith("px") -> value.dropLast(2).toFloatOrNull()?.div(16f)
        value.endsWith("pt") -> value.dropLast(2).toFloatOrNull()?.div(12f)
        else -> null
    }?.coerceIn(0.5f, 3f)

    private fun linkOf(a: Element): BookLink? {
        a.attr("kavita-page").toIntOrNull()?.let { return BookLink(section = it, url = null) }
        val href = a.attr("href")
        return if (href.startsWith("http://", true) || href.startsWith("https://", true)) BookLink(section = null, url = href) else null
    }

    private fun image(el: Element, decl: Map<String, String>) {
        val src = el.attr("src").trim()
        if (src.isBlank()) return
        var share: Float? = null
        var width: Float? = null
        var height: Float? = null
        // the attributes first, then the stylesheet's say
        for ((raw, isWidth) in listOf(el.attr("width") to true, decl["width"].orEmpty() to true, el.attr("height") to false, decl["height"].orEmpty() to false)) {
            val v = raw.trim().lowercase()
            if (v.isEmpty() || v == "auto") continue
            val pct = v.takeIf { it.endsWith("%") }?.dropLast(1)?.toFloatOrNull()?.div(100f)
            val dp = if (pct == null) (v.toFloatOrNull() ?: lengthEm(v)?.times(16f)) else null
            if (isWidth) {
                if (pct != null) {
                    share = pct
                    width = null
                } else if (dp != null) {
                    width = dp
                    share = null
                }
            } else if (dp != null) {
                height = dp
            }
        }
        add(ImageBlock(src, el.attr("alt"), widthShare = share?.coerceIn(0.05f, 1f), widthDp = width, heightDp = height))
    }

    private fun svg(el: Element) {
        val raster = el.selectFirst("image")
        if (raster != null) {
            val href = raster.attr("xlink:href").ifEmpty { raster.attr("href") }
            if (href.isNotBlank()) add(ImageBlock(href.trim(), ""))
            return
        }
        val markup = svgMarkup(el)
        add(
            ImageBlock(
                src = "inline-svg-${svgCount++}",
                alt = el.selectFirst("title")?.text().orEmpty().ifBlank { el.attr("aria-label") },
                svg = markup,
                natural = svgSize(el),
            ),
        )
    }

    private fun svgMarkup(el: Element): String {
        var markup = el.outerHtml()
        // the parser lowercases attribute names the picture needs in their own case
        for ((lower, proper) in SVG_ATTRIBUTES) markup = markup.replace(" $lower=", " $proper=")
        if (!markup.contains("xmlns=")) markup = markup.replaceFirst("<svg", "<svg xmlns=\"http://www.w3.org/2000/svg\"")
        return markup
    }

    // the size the picture asks for, or the shape of its view box
    private fun svgSize(el: Element): IntSize {
        fun px(attr: String) = el.attr(attr).trim().lowercase().takeIf { it.isNotEmpty() && !it.endsWith("%") }
            ?.let { it.removeSuffix("px").toFloatOrNull() ?: lengthEm(it)?.times(16f) }
        val box = el.attr("viewbox").ifEmpty { el.attr("viewBox") }.trim().split(Regex("[\\s,]+")).mapNotNull { it.toFloatOrNull() }
        val vw = box.getOrNull(2)
        val vh = box.getOrNull(3)
        var w = px("width")
        var h = px("height")
        if (w == null && h == null && vw != null && vh != null) {
            w = vw
            h = vh
        } else if (w != null && h == null && vw != null && vh != null && vw > 0f) {
            h = w * vh / vw
        } else if (h != null && w == null && vw != null && vh != null && vh > 0f) {
            w = h * vw / vh
        }
        return IntSize((w ?: 300f).toInt().coerceAtLeast(1), (h ?: 150f).toInt().coerceAtLeast(1))
    }

    private fun table(el: Element, parent: Ctx) {
        flush()
        el.children().firstOrNull { it.normalName() == "caption" }?.let { element(it, parent) }
        val rows = ArrayList<List<TableCell>>()
        for (tr in el.select("tr")) {
            if (tr.closest("table") !== el) continue
            val cells = tr.children().filter { it.normalName() == "td" || it.normalName() == "th" }.mapNotNull { cell(it, parent) }
            if (cells.isNotEmpty()) rows += cells
        }
        if (rows.isNotEmpty()) add(TableBlock(rows))
    }

    // a cell is one block of text: whatever is inside it, joined by line breaks
    private fun cell(c: Element, parent: Ctx): TableCell? {
        val decl = styles.resolve(c)
        if (decl["display"] == "none") return null
        val tag = c.normalName()
        val ctx = ctxOf(c, tag, parent.copy(indent = 0, indentBook = 0, leftEm = 0f, rightEm = 0f, firstEm = null, hang = 0f), isBlock = true, decl = decl)
        val inner = SectionParser(styles).fragment(c, ctx.copy(align = ctx.align ?: if (tag == "th") Align.Center else null)).filterIsInstance<TextBlock>()
        val span = c.attr("colspan").toIntOrNull()?.coerceIn(1, 12) ?: 1
        if (inner.isEmpty()) return TableCell(null, span, tag == "th")
        val text = StringBuilder()
        val spans = ArrayList<Span>()
        for ((i, b) in inner.withIndex()) {
            if (i > 0) text.append('\n')
            val shift = text.length
            text.append(b.text)
            b.spans.mapTo(spans) { Span(it.start + shift, it.end + shift, it.fmt) }
        }
        val first = inner.first()
        val block = TextBlock(text.toString(), spans, Kind.Para, first.scale, 0, first.align, 0f, 0, BookBox())
        return TableCell(block, span, tag == "th")
    }

    private fun append(raw: String, ctx: Ctx) {
        when (ctx.white) {
            WHITE_PRE -> {
                appendStyled(raw.replace("\r\n", "\n").replace('\r', '\n'), ctx)
                return
            }

            WHITE_LINES -> {
                // spaces collapse, the line breaks stay
                val text = raw.replace("\r\n", "\n").replace('\r', '\n').replace(Regex("[ \\t\\u000C]*\\n[ \\t\\u000C]*"), "\n")
                    .replace(Regex("[ \\t\\u000C]+"), " ")
                appendStyled(text, ctx)
                return
            }
        }
        val tmp = StringBuilder(raw.length)
        for (ch in raw) {
            if (ch == ' ' || ch == '\n' || ch == '\t' || ch == '\r' || ch == '\u000C') {
                val last = if (tmp.isNotEmpty()) tmp.last() else if (buf.isNotEmpty()) buf.last() else '\n'
                if (last != ' ' && last != '\n') tmp.append(' ')
            } else {
                tmp.append(ch)
            }
        }
        if (tmp.isNotEmpty()) appendStyled(tmp.toString(), ctx)
    }

    // text-transform and small capitals change the letters, one for one, so the places in the text stay the same
    private fun appendStyled(text: String, ctx: Ctx) {
        if (ctx.transform == Transform.None && !ctx.smallCaps) {
            appendRaw(text, ctx)
            return
        }
        val chars = StringBuilder(text.length)
        var wordStart = buf.isEmpty() || !buf.last().isLetterOrDigit()
        for (ch in text) {
            chars.append(
                when (ctx.transform) {
                    Transform.Upper -> Character.toUpperCase(ch)
                    Transform.Lower -> Character.toLowerCase(ch)
                    Transform.Capitalize -> if (wordStart && ch.isLetter()) Character.toUpperCase(ch) else ch
                    Transform.None -> ch
                },
            )
            wordStart = !ch.isLetterOrDigit() && ch != '\''
        }
        val shaped = chars.toString()
        if (!ctx.smallCaps) {
            appendRaw(shaped, ctx)
            return
        }
        // lower case letters become smaller capitals
        var i = 0
        while (i < text.length) {
            val lower = text[i].isLowerCase()
            var j = i
            while (j < text.length && text[j].isLowerCase() == lower) j++
            if (lower) {
                appendRaw(shaped.substring(i, j).map { Character.toUpperCase(it) }.joinToString(""), ctx.copy(fmt = ctx.fmt.copy(sizeRel = ctx.fmt.sizeRel * 0.8f)))
            } else {
                appendRaw(shaped.substring(i, j), ctx)
            }
            i = j
        }
    }

    private fun appendRaw(text: String, ctx: Ctx) {
        if (text.isEmpty()) return
        if (buf.isEmpty()) {
            // A block starts with its first real character; spaces in front of it are not part of it.
            if (text.isBlank() && ctx.white == WHITE_COLLAPSE) return
            open = ctx
            val top = before ?: pendingBefore
            openBox = BookBox(
                indent = ctx.indentBook,
                leftEm = ctx.leftEm,
                rightEm = ctx.rightEm,
                firstEm = ctx.firstEm,
                beforeEm = if (before != null || pendingBefore != null) maxOf(before ?: 0f, pendingBefore ?: 0f) else null,
                afterEm = after,
            )
            if (top != null) pendingBefore = null
            marker?.let {
                buf.append(it)
                spans += Span(0, it.length, ctx.fmt.copy(link = null, color = null))
                marker = null
            }
        }
        val start = buf.length
        buf.append(text)
        val last = spans.lastOrNull()
        if (last != null && last.end == start && last.fmt == ctx.fmt) {
            spans[spans.lastIndex] = Span(last.start, buf.length, last.fmt)
        } else {
            spans += Span(start, buf.length, ctx.fmt)
        }
    }

    private fun flush() {
        val ctx = open
        var end = buf.length
        while (end > 0 && (buf[end - 1] == ' ' || buf[end - 1] == '\n' || buf[end - 1] == ' ' && ctx?.white != WHITE_PRE)) end--
        if (ctx != null && end > 0) {
            val text = buf.substring(0, end)
            val kept = spans.mapNotNull { s ->
                if (s.start >= end) null else if (s.end > end) Span(s.start, end, s.fmt) else s
            }
            add(TextBlock(text, kept, ctx.kind, ctx.scale, ctx.indent, ctx.align, ctx.hang, 0, openBox))
        }
        buf.setLength(0)
        spans.clear()
        open = null
    }

    private companion object {
        // elements that only hold blocks: their margins go to the first and last block inside
        val CONTAINERS = setOf("div", "section", "article", "aside", "header", "footer", "nav", "main", "figure", "details", "form", "fieldset")

        val ROMAN = listOf(
            1000 to "M", 900 to "CM", 500 to "D", 400 to "CD", 100 to "C", 90 to "XC", 50 to "L", 40 to "XL", 10 to "X", 9 to "IX",
            5 to "V", 4 to "IV", 1 to "I",
        )

        val SVG_ATTRIBUTES = listOf(
            "viewbox" to "viewBox", "preserveaspectratio" to "preserveAspectRatio", "gradientunits" to "gradientUnits",
            "gradienttransform" to "gradientTransform", "patternunits" to "patternUnits", "patterntransform" to "patternTransform",
            "clippathunits" to "clipPathUnits", "maskunits" to "maskUnits", "stddeviation" to "stdDeviation",
            "textlength" to "textLength", "lengthadjust" to "lengthAdjust", "startoffset" to "startOffset",
            "spreadmethod" to "spreadMethod", "markerwidth" to "markerWidth", "markerheight" to "markerHeight",
            "refx" to "refX", "refy" to "refY",
        )
    }
}

// a length in em of the text size, when it is one that can be told: em, rem, px, pt and the like
internal fun lengthEm(value: String): Float? {
    val v = value.trim().lowercase()
    if (v == "0") return 0f
    return when {
        v.endsWith("rem") -> v.dropLast(3).toFloatOrNull()
        v.endsWith("em") -> v.dropLast(2).toFloatOrNull()
        v.endsWith("ex") || v.endsWith("ch") -> v.dropLast(2).toFloatOrNull()?.div(2f)
        v.endsWith("px") -> v.dropLast(2).toFloatOrNull()?.div(16f)
        v.endsWith("pt") -> v.dropLast(2).toFloatOrNull()?.div(12f)
        v.endsWith("pc") -> v.dropLast(2).toFloatOrNull()
        v.endsWith("mm") -> v.dropLast(2).toFloatOrNull()?.times(0.2362f)
        v.endsWith("cm") -> v.dropLast(2).toFloatOrNull()?.times(2.362f)
        v.endsWith("in") -> v.dropLast(2).toFloatOrNull()?.times(6f)
        else -> null
    }
}

private val NAMED_COLORS = mapOf(
    "black" to 0xFF000000, "white" to 0xFFFFFFFF, "red" to 0xFFFF0000, "green" to 0xFF008000, "blue" to 0xFF0000FF,
    "gray" to 0xFF808080, "grey" to 0xFF808080, "silver" to 0xFFC0C0C0, "maroon" to 0xFF800000, "navy" to 0xFF000080,
    "olive" to 0xFF808000, "purple" to 0xFF800080, "teal" to 0xFF008080, "orange" to 0xFFFFA500, "brown" to 0xFFA52A2A,
    "yellow" to 0xFFFFFF00, "aqua" to 0xFF00FFFF, "cyan" to 0xFF00FFFF, "fuchsia" to 0xFFFF00FF, "magenta" to 0xFFFF00FF,
    "lime" to 0xFF00FF00, "darkred" to 0xFF8B0000, "darkgreen" to 0xFF006400, "darkblue" to 0xFF00008B, "crimson" to 0xFFDC143C,
    "gold" to 0xFFFFD700, "pink" to 0xFFFFC0CB, "dimgray" to 0xFF696969, "darkgray" to 0xFFA9A9A9,
)

// #rgb, #rrggbb, rgb(), rgba() and the common names; null for anything else (inherit, transparent, currentcolor...)
internal fun cssColor(value: String): Color? {
    val v = value.trim().lowercase()
    NAMED_COLORS[v]?.let { return Color(it) }
    if (v.startsWith("#")) {
        val hex = v.drop(1)
        return when (hex.length) {
            3 -> hex.map { "$it$it" }.joinToString("").toLongOrNull(16)?.let { Color(0xFF000000 or it) }
            6 -> hex.toLongOrNull(16)?.let { Color(0xFF000000 or it) }
            else -> null
        }
    }
    if (v.startsWith("rgb")) {
        val parts = v.substringAfter('(').substringBefore(')').split(',', '/', ' ').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size < 3) return null
        fun channel(s: String): Int? =
            if (s.endsWith("%")) s.dropLast(1).toFloatOrNull()?.times(2.55f)?.toInt() else s.toFloatOrNull()?.toInt()
        val r = channel(parts[0]) ?: return null
        val g = channel(parts[1]) ?: return null
        val b = channel(parts[2]) ?: return null
        return Color(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
    }
    return null
}
