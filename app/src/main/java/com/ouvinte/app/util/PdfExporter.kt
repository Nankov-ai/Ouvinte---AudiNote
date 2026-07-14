package com.ouvinte.app.util

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import com.ouvinte.app.data.repository.FullSession
import com.ouvinte.app.domain.model.FactVerdict
import com.ouvinte.app.domain.model.QuestionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.format.DateTimeFormatter

object PdfExporter {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 48f
    private const val LINE_HEIGHT_EXTRA = 3f

    private data class PageState(
        val document: PdfDocument,
        var pageNumber: Int,
        var page: PdfDocument.Page,
        var canvas: Canvas,
        var y: Float
    ) {
        fun checkNewPage(needed: Float) {
            if (y + needed > PAGE_HEIGHT - MARGIN) {
                document.finishPage(page)
                pageNumber++
                page = document.startPage(
                    PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                )
                canvas = page.canvas
                y = MARGIN
            }
        }

        fun drawLine(text: String, size: Float, bold: Boolean = false, gray: Boolean = false, primary: Boolean = false) {
            checkNewPage(size + LINE_HEIGHT_EXTRA + 4f)
            val paint = makePaint(size, bold, gray, primary)
            canvas.drawText(text, MARGIN, y + size, paint)
            y += size + 4f
        }

        fun drawWrapped(text: String, size: Float, gray: Boolean = false) {
            val paint = makePaint(size, gray = gray)
            val maxWidth = (PAGE_WIDTH - MARGIN * 2).toInt()
            val words = text.split(" ")
            var line = StringBuilder()
            y += size

            for (word in words) {
                val test = if (line.isEmpty()) word else "$line $word"
                if (paint.measureText(test) > maxWidth) {
                    checkNewPage(size + LINE_HEIGHT_EXTRA)
                    canvas.drawText(line.toString(), MARGIN, y, paint)
                    y += size + LINE_HEIGHT_EXTRA
                    line = StringBuilder(word)
                } else {
                    line = StringBuilder(test)
                }
            }
            if (line.isNotEmpty()) {
                checkNewPage(size + LINE_HEIGHT_EXTRA)
                canvas.drawText(line.toString(), MARGIN, y, paint)
                y += size + LINE_HEIGHT_EXTRA
            }
        }
    }

    private fun makePaint(size: Float, bold: Boolean = false, gray: Boolean = false, primary: Boolean = false) =
        Paint().apply {
            textSize = size
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            color = when {
                primary -> Color.rgb(13, 71, 161)
                gray -> Color.GRAY
                else -> Color.BLACK
            }
            isAntiAlias = true
        }

    suspend fun export(context: Context, full: FullSession): String =
        withContext(Dispatchers.IO) {
            val document = PdfDocument()
            val firstPage = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create())
            val state = PageState(document, 1, firstPage, firstPage.canvas, MARGIN)

            with(state) {
                // Title
                drawLine(full.session.name, 20f, bold = true)
                drawLine(full.session.createdAt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")), 11f, gray = true)
                y += 16f

                // Transcript
                drawLine("TRANSCRIÇÃO", 14f, bold = true, primary = true)
                y += 8f
                full.segments.forEach { seg ->
                    drawLine("[${seg.speakerLabel}]  ${formatTs(seg.timestampSeconds)}", 10f, gray = true)
                    drawWrapped(seg.text, 11f)
                    y += 6f
                }

                // Analysis
                full.analysis?.let { analysis ->
                    y += 16f
                    drawLine("RESUMO", 14f, bold = true, primary = true)
                    y += 8f
                    drawWrapped(analysis.summary, 11f)

                    if (analysis.factChecks.isNotEmpty()) {
                        y += 16f
                        drawLine("VERIFICAÇÃO DE FACTOS", 14f, bold = true, primary = true)
                        y += 8f
                        analysis.factChecks.forEach { fc ->
                            val label = when (fc.verdict) {
                                FactVerdict.VERIFIED -> "[VERIFICADO]"
                                FactVerdict.UNVERIFIED -> "[NÃO VERIFICADO]"
                                FactVerdict.INCORRECT -> "[INCORRETO]"
                            }
                            drawLine(label, 10f, bold = true)
                            drawWrapped(fc.claim, 11f)
                            if (fc.evidence.isNotBlank()) drawWrapped(fc.evidence, 10f, gray = true)
                            y += 6f
                        }
                    }

                    if (analysis.sources.isNotEmpty()) {
                        y += 16f
                        drawLine("FONTES", 14f, bold = true, primary = true)
                        y += 8f
                        analysis.sources.forEach { src ->
                            drawLine(src.title, 11f, bold = true)
                            drawLine(src.url, 10f, gray = true)
                            y += 4f
                        }
                    }
                }

                // Questions
                if (full.questions.isNotEmpty()) {
                    y += 16f
                    drawLine("PERGUNTAS", 14f, bold = true, primary = true)
                    y += 8f
                    full.questions.forEach { q ->
                        val typeLabel = when (q.type) {
                            QuestionType.CHALLENGE -> "[DESAFIO]"
                            QuestionType.FUTURE -> "[FUTURO]"
                            QuestionType.DEEPEN -> "[APROFUNDAMENTO]"
                        }
                        drawLine(typeLabel, 10f, bold = true)
                        drawWrapped(q.text, 11f)
                        y += 6f
                    }
                }

                document.finishPage(page)
            }

            val dir = File(context.getExternalFilesDir(null), "exports").also { it.mkdirs() }
            val file = File(dir, "ouvinte_${System.currentTimeMillis()}.pdf")
            file.outputStream().use { document.writeTo(it) }
            document.close()

            file.absolutePath
        }

    suspend fun exportProject(context: Context, projectName: String, sessions: List<FullSession>): String =
        withContext(Dispatchers.IO) {
            val document = PdfDocument()
            val firstPage = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create())
            val state = PageState(document, 1, firstPage, firstPage.canvas, MARGIN)

            with(state) {
                drawLine(projectName, 22f, bold = true, primary = true)
                drawLine("${sessions.size} sessão(ões)", 11f, gray = true)
                y += 24f

                sessions.forEachIndexed { index, full ->
                    drawLine("── Sessão ${index + 1}: ${full.session.name} ──", 13f, bold = true)
                    y += 8f

                    if (full.segments.isNotEmpty()) {
                        drawLine("TRANSCRIÇÃO", 12f, bold = true, primary = true)
                        y += 6f
                        full.segments.forEach { seg ->
                            drawLine("[${seg.speakerLabel}]  ${formatTs(seg.timestampSeconds)}", 10f, gray = true)
                            drawWrapped(seg.text, 11f)
                            y += 4f
                        }
                    }

                    full.analysis?.let { analysis ->
                        y += 12f
                        drawLine("RESUMO", 12f, bold = true, primary = true)
                        y += 6f
                        drawWrapped(analysis.summary, 11f)
                    }

                    if (full.questions.isNotEmpty()) {
                        y += 12f
                        drawLine("PERGUNTAS", 12f, bold = true, primary = true)
                        y += 6f
                        full.questions.forEach { q ->
                            val typeLabel = when (q.type) {
                                QuestionType.CHALLENGE -> "[DESAFIO]"
                                QuestionType.FUTURE -> "[FUTURO]"
                                QuestionType.DEEPEN -> "[APROFUNDAMENTO]"
                            }
                            drawLine(typeLabel, 10f, bold = true)
                            drawWrapped(q.text, 11f)
                            y += 4f
                        }
                    }

                    y += 20f
                }

                document.finishPage(page)
            }

            val dir = File(context.getExternalFilesDir(null), "exports").also { it.mkdirs() }
            val file = File(dir, "projeto_${System.currentTimeMillis()}.pdf")
            file.outputStream().use { document.writeTo(it) }
            document.close()

            file.absolutePath
        }

    private fun formatTs(seconds: Int) = "%02d:%02d".format(seconds / 60, seconds % 60)
}
