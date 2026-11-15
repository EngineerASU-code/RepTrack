package app.reptrack

import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal object WorkoutReportPdf {
    private const val pageWidth = 595
    private const val pageHeight = 842
    private const val margin = 42f
    private const val pageBottom = 790f

    fun create(session: TrainingSession, profileName: String, unit: WeightUnit): ByteArray {
        require(session.status == SessionStatus.COMPLETED && session.endedAt != null) { "Отчёт можно создать только для завершённой тренировки" }
        val endedAt = session.endedAt ?: error("Дата завершения тренировки не сохранена")
        val document = PdfDocument()
        val output = ByteArrayOutputStream()
        val dateFormat = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.forLanguageTag("ru"))
        val body = paint(11f, false)
        val muted = paint(10f, false).apply { color = 0xff66636d.toInt() }
        val accent = paint(12f, true).apply { color = 0xff6843d6.toInt() }
        val title = paint(22f, true).apply { color = 0xff211c31.toInt() }
        var pageNumber = 0
        var y = margin
        lateinit var page: PdfDocument.Page

        fun startPage() {
            pageNumber++
            page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            if (pageNumber > 1) {
                page.canvas.drawText("RepTrack · ${session.planName}", margin, margin, accent)
                y = margin + 24f
            } else y = margin
        }

        fun finishPage() {
            page.canvas.drawText("RepTrack · $pageNumber", margin, pageHeight - 24f, muted)
            document.finishPage(page)
        }

        fun nextPage() { finishPage(); startPage() }

        fun paragraph(value: String, textPaint: Paint, extraGap: Float = 0f) {
            val maxWidth = pageWidth - margin * 2
            val lines = value.lines().flatMap { source ->
                if (source.isBlank()) listOf("") else {
                    val wrapped = mutableListOf<String>()
                    var line = ""
                    source.split(Regex("\\s+")).forEach { word ->
                        val candidate = if (line.isEmpty()) word else "$line $word"
                        if (line.isNotEmpty() && textPaint.measureText(candidate) > maxWidth) {
                            wrapped += line
                            line = word
                        } else line = candidate
                    }
                    if (line.isNotEmpty()) wrapped += line
                    wrapped
                }
            }
            val lineHeight = textPaint.textSize + 5f
            lines.forEach { line ->
                if (y + lineHeight > pageBottom) nextPage()
                page.canvas.drawText(line, margin, y + textPaint.textSize, textPaint)
                y += lineHeight
            }
            y += extraGap
        }

        fun weightLabel(kilograms: Double): String {
            val displayed = fromKilograms(kilograms, unit)
            val number = if (displayed % 1.0 == 0.0) displayed.toLong().toString()
                else String.format(Locale.US, "%.2f", displayed).trimEnd('0').trimEnd('.')
            return "$number ${if (unit == WeightUnit.KG) "кг" else "lb"}"
        }

        try {
            startPage()
            paragraph("RepTrack · отчёт тренировки", title, 8f)
            paragraph(session.planName, accent, 4f)
            paragraph("Профиль: $profileName", body)
            paragraph("Начало: ${dateFormat.format(Date(session.startedAt))}", body)
            paragraph("Завершение: ${dateFormat.format(Date(endedAt))}", body)
            val duration = ((endedAt - session.startedAt).coerceAtLeast(0) / 60_000L)
            paragraph("Длительность: $duration мин · упражнений: ${session.exercises.size} · рабочих подходов: ${session.completedSets}", body)
            session.maxWeightKg?.let { paragraph("Максимальный рабочий вес: ${weightLabel(it)}", accent, 4f) }
            if (session.comment.isNotBlank()) paragraph("Комментарий: ${session.comment}", muted, 8f)

            session.exercises.forEachIndexed { exerciseIndex, exercise ->
                paragraph("${exerciseIndex + 1}. ${exercise.name}", accent, 2f)
                if (exercise.skipped) paragraph("Упражнение пропущено", muted, 2f)
                else exercise.sets.forEachIndexed { setIndex, set ->
                    val parts = buildList {
                        add(if (set.kind == SetKind.WARMUP) "Разминка" else "Рабочий")
                        parseFiniteWeight(set.weight)?.let { add(weightLabel(it)) }
                        if (set.reps.isNotBlank()) add("${set.reps} повт.")
                        add(when { set.skipped -> "пропуск"; set.done -> "выполнен"; else -> "не отмечен" })
                    }
                    paragraph("  ${setIndex + 1}. ${parts.joinToString(" · ")}", body)
                }
                y += 5f
            }
            finishPage()
            document.writeTo(output)
            return output.toByteArray()
        } finally {
            document.close()
        }
    }

    private fun paint(size: Float, bold: Boolean) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xff211c31.toInt()
        textSize = size
        typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
    }
}
