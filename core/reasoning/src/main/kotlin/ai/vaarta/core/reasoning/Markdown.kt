package ai.vaarta.core.reasoning

/** One run of inline text with optional emphasis. */
data class MdSpan(val text: String, val bold: Boolean = false, val italic: Boolean = false)

/** A rendered block. Deliberately tiny — covers exactly the markdown Gemini emits in prose. */
sealed interface MdBlock {
    data class Paragraph(val spans: List<MdSpan>) : MdBlock
    data class Heading(val spans: List<MdSpan>) : MdBlock
    data class Bullets(val items: List<List<MdSpan>>) : MdBlock
    data class Numbered(val items: List<List<MdSpan>>) : MdBlock
}

private val BULLET = Regex("""^\s*[-*]\s+(.*)$""")
private val NUMBERED = Regex("""^\s*\d+[.)]\s+(.*)$""")
private val HEADING = Regex("""^\s*#{1,6}\s+(.*)$""")

/**
 * A deliberately small, dependency-free markdown reader for AI prose. Handles what Gemini actually
 * produces — bold/italic inline, dash/asterisk bullets, numbered lists, ATX headings — and strips
 * anything else (backticks, blockquote `>`, stray markers) so no raw markup ever reaches the UI.
 */
fun parseMarkdown(src: String): List<MdBlock> {
    val lines = src.replace("\r\n", "\n").split("\n")
    val out = ArrayList<MdBlock>()
    val para = ArrayList<String>()
    var bullets: ArrayList<List<MdSpan>>? = null
    var numbered: ArrayList<List<MdSpan>>? = null

    fun flushPara() {
        if (para.isNotEmpty()) {
            out += MdBlock.Paragraph(parseInline(para.joinToString(" ")))
            para.clear()
        }
    }
    fun flushLists() {
        bullets?.let { if (it.isNotEmpty()) out += MdBlock.Bullets(it) }; bullets = null
        numbered?.let { if (it.isNotEmpty()) out += MdBlock.Numbered(it) }; numbered = null
    }

    for (raw in lines) {
        val line = raw.trimEnd()
        when {
            line.isBlank() -> { flushPara(); flushLists() }
            HEADING.matches(line) -> {
                flushPara(); flushLists()
                out += MdBlock.Heading(parseInline(HEADING.find(line)!!.groupValues[1]))
            }
            BULLET.matches(line) -> {
                flushPara(); numbered?.let { flushLists() }
                (bullets ?: ArrayList<List<MdSpan>>().also { bullets = it })
                    .add(parseInline(BULLET.find(line)!!.groupValues[1]))
            }
            NUMBERED.matches(line) -> {
                flushPara(); bullets?.let { flushLists() }
                (numbered ?: ArrayList<List<MdSpan>>().also { numbered = it })
                    .add(parseInline(NUMBERED.find(line)!!.groupValues[1]))
            }
            else -> { flushLists(); para += line }
        }
    }
    flushPara(); flushLists()
    return out
}

/** Inline pass: **bold**, *italic*, drop stray backticks and blockquote markers. */
private fun parseInline(textIn: String): List<MdSpan> {
    val text = textIn.replace("`", "").removePrefix(">").trimStart()
    val spans = ArrayList<MdSpan>()
    var i = 0
    val sb = StringBuilder()
    fun push(bold: Boolean = false, italic: Boolean = false, s: String) {
        if (s.isNotEmpty()) spans += MdSpan(s, bold, italic)
    }
    fun flushPlain() { if (sb.isNotEmpty()) { push(s = sb.toString()); sb.clear() } }

    while (i < text.length) {
        if (text.startsWith("**", i)) {
            val end = text.indexOf("**", i + 2)
            if (end > i + 1) { flushPlain(); push(bold = true, s = text.substring(i + 2, end)); i = end + 2; continue }
        }
        if (text[i] == '*') {
            val end = text.indexOf('*', i + 1)
            if (end > i) { flushPlain(); push(italic = true, s = text.substring(i + 1, end)); i = end + 1; continue }
        }
        sb.append(text[i]); i++
    }
    flushPlain()
    return if (spans.isEmpty()) listOf(MdSpan(text)) else spans
}
