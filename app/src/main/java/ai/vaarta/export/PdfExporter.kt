package ai.vaarta.export

import ai.vaarta.core.complaint.ComplaintDraft
import ai.vaarta.core.complaint.ComplaintRenderers
import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream

/**
 * Renders a [ComplaintDraft] to a paginated PDF, using [ComplaintRenderers.toText] as the single
 * source of truth for content (DATABASE_DESIGN.md §5 — "one source of truth, four renderers").
 * `android.graphics.pdf.PdfDocument` is Android-only, which is why this renderer lives in `:app`
 * rather than `core:complaint` (which stays pure Kotlin/JVM so it's testable without a device).
 */
object PdfExporter {
    private const val PAGE_WIDTH = 595 // A4 at 72dpi points
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f
    private const val LINE_HEIGHT = 14f
    private const val FONT_SIZE = 10f

    fun export(context: Context, draft: ComplaintDraft, fileName: String = "vaarta_complaint.pdf"): File {
        val paint = Paint().apply { textSize = FONT_SIZE; isAntiAlias = true }
        val wrapped = ComplaintRenderers.toText(draft).lines().flatMap { wrapLine(it, paint, PAGE_WIDTH - 2 * MARGIN) }
        val linesPerPage = ((PAGE_HEIGHT - 2 * MARGIN) / LINE_HEIGHT).toInt()

        val document = PdfDocument()
        var pageNumber = 1
        var lineIndex = 0
        do {
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            val page = document.startPage(pageInfo)
            var y = MARGIN + FONT_SIZE
            var linesOnPage = 0
            while (lineIndex < wrapped.size && linesOnPage < linesPerPage) {
                page.canvas.drawText(wrapped[lineIndex], MARGIN, y, paint)
                y += LINE_HEIGHT
                lineIndex++
                linesOnPage++
            }
            document.finishPage(page)
            pageNumber++
        } while (lineIndex < wrapped.size)

        val outFile = File(context.cacheDir, fileName)
        FileOutputStream(outFile).use { document.writeTo(it) }
        document.close()
        return outFile
    }

    /** Greedy word-wrap so lines never overflow the page width (measured, not char-counted). */
    private fun wrapLine(line: String, paint: Paint, maxWidth: Float): List<String> {
        if (line.isEmpty()) return listOf("")
        if (paint.measureText(line) <= maxWidth) return listOf(line)
        val words = line.split(" ")
        val result = mutableListOf<String>()
        var current = StringBuilder()
        for (word in words) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) <= maxWidth) {
                current = StringBuilder(candidate)
            } else {
                if (current.isNotEmpty()) result.add(current.toString())
                current = StringBuilder(word)
            }
        }
        if (current.isNotEmpty()) result.add(current.toString())
        return result
    }
}
