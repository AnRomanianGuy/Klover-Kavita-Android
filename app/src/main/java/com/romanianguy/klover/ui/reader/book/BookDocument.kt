package com.romanianguy.klover.ui.reader.book

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

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
)

internal class Span(val start: Int, val end: Int, val fmt: Fmt)

internal enum class Kind { Para, Heading, ListItem, Pre }

internal enum class Align { Start, Center, End, Justify }

internal sealed interface BookBlock

internal class TextBlock(
    val text: String,
    val spans: List<Span>,
    val kind: Kind,
    val scale: Float,
    val indent: Int,
    val align: Align?,
    val hang: Float,
    val speechStart: Int,
) : BookBlock

internal class ImageBlock(val src: String, val alt: String) : BookBlock

internal object RuleBlock : BookBlock

internal class BookModel(val blocks: List<BookBlock>, val speech: String) {
    val images: List<String> = blocks.filterIsInstance<ImageBlock>().map { it.src }.distinct()
}

internal fun parseBookSection(html: String): BookModel {
    val doc = Jsoup.parseBodyFragment(html)
    val css = BookCss.parse(doc.select("style").joinToString("\n") { it.data() })
    return SectionParser(css).parse(doc.body())
}

// text length as the reader counts it, which is what the time-left estimate works with
fun sectionTextLength(html: String): Int = parseBookSection(html).speech.length

internal class BookCss private constructor(private val rules: List<Rule>) {
    private class Rule(val tag: String?, val classes: Set<String>, val specificity: Int, val decl: Map<String, String>)

    fun declarationsFor(el: Element): Map<String, String> {
        val inline = el.attr("style").takeIf { it.isNotBlank() }?.let(::parseDeclarations)
        if (rules.isEmpty()) return inline ?: emptyMap()
        val tag = el.normalName()
        val classes = el.classNames()
        val out = HashMap<String, String>()
        for (rule in rules) {
            if (rule.tag != null && rule.tag != tag) continue
            if (rule.classes.isNotEmpty() && !classes.containsAll(rule.classes)) continue
            out.putAll(rule.decl)
        }
        if (inline != null) out.putAll(inline)
        return out
    }

    companion object {
        val Empty = BookCss(emptyList())

        private val COMMENT = Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL)
        private val RULE = Regex("([^{}]+)\\{([^{}]*)\\}")
        private val COMPOUND = Regex("^([a-zA-Z][a-zA-Z0-9]*)?((?:\\.[a-zA-Z_][\\w-]*)*)$")

        fun parse(text: String): BookCss {
            if (text.isBlank()) return Empty
            val rules = ArrayList<Rule>()
            for (m in RULE.findAll(text.replace(COMMENT, ""))) {
                val decl = parseDeclarations(m.groupValues[2])
                if (decl.isEmpty() || m.groupValues[1].trim().startsWith("@")) continue
                for (selector in m.groupValues[1].split(',')) rule(selector.trim(), decl)?.let(rules::add)
            }
            // Stable: later rules of the same weight win.
            return BookCss(rules.sortedBy { it.specificity })
        }

        private fun rule(selector: String, decl: Map<String, String>): Rule? {
            if (selector.isEmpty() || ':' in selector || '[' in selector || '#' in selector || '*' in selector) return null
            val parts = selector.split(Regex("\\s+"))
            val compound = parts.last()
            if (parts.dropLast(1).any { it !in ANCESTORS }) return null
            val m = COMPOUND.matchEntire(compound) ?: return null
            val tag = m.groupValues[1].takeIf { it.isNotEmpty() }?.lowercase()
            val classes = m.groupValues[2].split('.').filter { it.isNotEmpty() }.toSet()
            if (tag == null && classes.isEmpty()) return null
            return Rule(tag, classes, (if (tag != null) 1 else 0) + 10 * classes.size, decl)
        }

        private val ANCESTORS = setOf(
            ".reading-section",
            ".book-content",
            "body",
            "html",
            ".reading-section.book-content",
        )

        fun parseDeclarations(text: String): Map<String, String> {
            val out = HashMap<String, String>()
            for (part in text.split(';')) {
                val i = part.indexOf(':')
                if (i <= 0) continue
                val value = part.substring(i + 1).replace("!important", "", ignoreCase = true).trim().lowercase()
                if (value.isNotEmpty()) out[part.substring(0, i).trim().lowercase()] = value
            }
            return out
        }
    }
}

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
)

private val BLOCKS = setOf(
    "p", "div", "section", "article", "aside", "header", "footer", "nav", "main", "figure", "figcaption", "blockquote",
    "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol", "li", "dl", "dt", "dd", "table", "thead", "tbody", "tfoot", "tr", "td", "th",
    "caption", "pre", "address", "details", "summary", "form", "fieldset", "center",
)

private val HEADING_SCALE = mapOf("h1" to 1.6f, "h2" to 1.4f, "h3" to 1.2f, "h4" to 1.1f, "h5" to 1.0f, "h6" to 0.9f)

private data class Ctx(
    val fmt: Fmt = Fmt(),
    val kind: Kind = Kind.Para,
    val scale: Float = 1f,
    val indent: Int = 0,
    val align: Align? = null,
    val pre: Boolean = false,
    val hang: Float = 0f,
)

private class SectionParser(private val css: BookCss) {
    private val blocks = ArrayList<BookBlock>()
    private val buf = StringBuilder()
    private val spans = ArrayList<Span>()
    private var open: Ctx? = null

    private var marker: String? = null
    private val lists = ArrayList<IntArray>() // per open list: [ordered ? next number : 0]

    fun parse(body: Element): BookModel {
        walkChildren(body, Ctx())
        flush()
        // Speech offsets: every text block, a line break between them.
        val out = ArrayList<BookBlock>(blocks.size)
        val speech = StringBuilder()
        for (b in blocks) {
            if (b is TextBlock) {
                if (speech.isNotEmpty()) speech.append('\n')
                out += TextBlock(b.text, b.spans, b.kind, b.scale, b.indent, b.align, b.hang, speech.length)
                speech.append(b.text)
            } else {
                out += b
            }
        }
        return BookModel(out, speech.toString())
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
        val decl = css.declarationsFor(el)
        if (decl["display"] == "none" || decl["visibility"] == "hidden" || el.hasAttr("hidden")) return

        when (tag) {
            "br" -> {
                if (open != null || buf.isNotEmpty()) appendRaw("\n", parent)
                return
            }

            "img" -> {
                flush()
                image(el.attr("src"), el.attr("alt"))
                return
            }

            "svg" -> {
                flush()
                el.selectFirst("image")?.let { image(it.attr("xlink:href").ifEmpty { it.attr("href") }, "") }
                return
            }

            "hr" -> {
                flush()
                blocks += RuleBlock
                return
            }
        }

        val isBlock = tag in BLOCKS
        var ctx = style(el, tag, decl, parent, isBlock)
        when {
            tag == "ul" || tag == "ol" -> {
                flush()
                lists += intArrayOf(if (tag == "ol") (el.attr("start").toIntOrNull() ?: 1) else 0)
                walkChildren(el, ctx.copy(indent = ctx.indent + 1))
                lists.removeAt(lists.lastIndex)
                flush()
            }

            tag == "li" -> {
                flush()
                val state = lists.lastOrNull()
                val number = state?.get(0) ?: 0
                if (state != null && number > 0) state[0] = number + 1
                val text = if (number > 0) "$number. " else "• "
                marker = text
                ctx = ctx.copy(kind = Kind.ListItem, hang = if (number > 0) 0.65f * text.length else 0.95f)
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
                walkChildren(el, ctx.copy(indent = ctx.indent + 1))
                flush()
            }

            tag == "td" || tag == "th" || isBlock -> {
                flush()
                walkChildren(el, ctx)
                flush()
            }

            else -> walkChildren(el, ctx)
        }
    }

    private fun style(el: Element, tag: String, decl: Map<String, String>, parent: Ctx, isBlock: Boolean): Ctx {
        var f = parent.fmt
        var scale = parent.scale
        var kind = parent.kind
        var pre = parent.pre
        var align = parent.align

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
                pre = true
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
        decl["text-align"]?.let { a ->
            align = when (a) {
                "center" -> Align.Center
                "right", "end" -> Align.End
                "left", "start" -> Align.Start
                "justify" -> Align.Justify
                else -> align
            }
        }
        return parent.copy(fmt = f, scale = scale, kind = kind, pre = pre, align = align)
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
        else -> null
    }?.coerceIn(0.5f, 3f)

    private fun linkOf(a: Element): BookLink? {
        a.attr("kavita-page").toIntOrNull()?.let { return BookLink(section = it, url = null) }
        val href = a.attr("href")
        return if (href.startsWith("http://", true) || href.startsWith("https://", true)) BookLink(section = null, url = href) else null
    }

    private fun image(src: String, alt: String) {
        if (src.isNotBlank()) blocks += ImageBlock(src.trim(), alt)
    }

    private fun append(raw: String, ctx: Ctx) {
        if (ctx.pre) {
            appendRaw(raw.replace("\r\n", "\n").replace('\r', '\n'), ctx)
            return
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
        if (tmp.isNotEmpty()) appendRaw(tmp.toString(), ctx)
    }

    private fun appendRaw(text: String, ctx: Ctx) {
        if (text.isEmpty()) return
        if (buf.isEmpty()) {
            // A block starts with its first real character; spaces in front of it are not part of it.
            if (text.isBlank() && !ctx.pre) return
            open = ctx
            marker?.let {
                buf.append(it)
                spans += Span(0, it.length, ctx.fmt.copy(link = null))
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
        while (end > 0 && (buf[end - 1] == ' ' || buf[end - 1] == '\n' || buf[end - 1] == ' ' && ctx?.pre != true)) end--
        if (ctx != null && end > 0) {
            val text = buf.substring(0, end)
            val kept = spans.mapNotNull { s ->
                if (s.start >= end) null else if (s.end > end) Span(s.start, end, s.fmt) else s
            }
            blocks += TextBlock(text, kept, ctx.kind, ctx.scale, ctx.indent, ctx.align, ctx.hang, 0)
        }
        buf.setLength(0)
        spans.clear()
        open = null
    }
}
