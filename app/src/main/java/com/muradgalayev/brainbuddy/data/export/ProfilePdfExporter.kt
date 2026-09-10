package com.muradgalayev.brainbuddy.data.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.muradgalayev.brainbuddy.data.health.WeeklyHealthReport
import com.muradgalayev.brainbuddy.domain.model.AdhdProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

// where the finished document ended up, so the UI can tell the user
sealed class PdfExportResult {
    data class Saved(val displayPath: String, val file: File?) : PdfExportResult()
    data class Failed(val message: String) : PdfExportResult()
}

// renders 'my profile' to a PDF: identity, survey answers, and the last seven days of wellness
// figures. uses the platform's own PdfDocument rather than a PDF library, since the document
// is a few pages of text and a table and pulling in a dependency for that would be more code
// to audit for something holding health data.
// everything happens on-device: this is the user exporting their own record for GDPR art. 20
// portability, so nothing is uploaded and no third party sees it
@Singleton
class ProfilePdfExporter @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    suspend fun export(
        displayName: String?,
        email: String?,
        profile: AdhdProfile?,
        report: WeeklyHealthReport,
    ): PdfExportResult = withContext(Dispatchers.IO) {
        runCatching {
            val doc = PdfDocument()
            val renderer = PageRenderer(doc)

            renderer.heading("Myndora — My Profile")
            renderer.caption("Generated ${LocalDate.now().format(DATE)}")
            renderer.gap()

            renderer.section("Who this belongs to")
            renderer.field("Name", displayName?.takeIf { it.isNotBlank() } ?: "Not set")
            renderer.field("Email", email?.takeIf { it.isNotBlank() } ?: "Not set")
            renderer.gap()

            renderer.section("ADHD profile")
            if (profile == null || !profile.surveyCompleted) {
                renderer.body("The survey hasn't been completed yet.")
            } else {
                surveyRows(profile).forEach { (label, value) -> renderer.field(label, value) }
            }
            renderer.gap()

            renderer.section("Weekly wellness log")
            renderer.caption(
                "${report.from.format(DATE)} – ${report.to.format(DATE)} · " +
                    "${report.daysWithAnyData} of 7 days with data"
            )
            renderer.gap(6f)

            if (report.daysWithAnyData == 0) {
                renderer.body("No wellness data was recorded in this period.")
            } else {
                renderer.tableHeader(COLUMNS)
                report.days.forEach { day ->
                    renderer.tableRow(
                        listOf(
                            day.date.format(SHORT_DATE),
                            day.steps?.toString() ?: "—",
                            day.averageHeartRate?.let { "$it" } ?: "—",
                            day.restingHeartRate?.let { "$it" } ?: "—",
                            day.caloriesKcal?.toString() ?: "—",
                            day.sleepHours?.let { "%.1f".format(it) } ?: "—",
                            day.exerciseMinutes?.toString() ?: "—",
                        ),
                    )
                }
                renderer.gap()
                renderer.section("Averages over the week")
                renderer.field("Steps per day", report.averageSteps?.toString() ?: "—")
                renderer.field("Heart rate", report.averageHeartRate?.let { "$it bpm" } ?: "—")
                renderer.field(
                    "Resting heart rate",
                    report.averageRestingHeartRate?.let { "$it bpm" } ?: "—",
                )
                renderer.field(
                    "Sleep per night",
                    report.averageSleepHours?.let { "%.1f h".format(it) } ?: "—",
                )
                renderer.field("Exercise total", "${report.totalExerciseMinutes} min")
                renderer.field("Calories total", "${report.totalCalories} kcal")
            }

            renderer.gap()
            renderer.caption(
                "This document is a personal record. It is not a medical assessment " +
                    "and must not be used to diagnose any condition."
            )
            renderer.finish()

            val name = "Myndora-Profile-${LocalDate.now()}.pdf"
            val result = writeDocument(doc, name)
            doc.close()
            result
        }.getOrElse { PdfExportResult.Failed(it.message ?: "Could not create the PDF") }
    }

    // Q and above write straight into the public Downloads collection, which needs no permission
    // and is where a download should land. below Q that API doesn't exist, so the file goes to
    // app-scoped external storage instead, reachable and still with no runtime permission prompt
    private fun writeDocument(doc: PdfDocument, fileName: String): PdfExportResult {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return PdfExportResult.Failed("Couldn't open Downloads")
            resolver.openOutputStream(uri).use { out ->
                if (out == null) return PdfExportResult.Failed("Couldn't write to Downloads")
                doc.writeTo(out)
            }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return PdfExportResult.Saved("Downloads/$fileName", null)
        }

        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: return PdfExportResult.Failed("No storage available")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, fileName)
        file.outputStream().use { doc.writeTo(it) }
        return PdfExportResult.Saved(file.absolutePath, file)
    }

    private fun surveyRows(p: AdhdProfile): List<Pair<String, String>> = buildList {
        p.diagnosisStatus?.let { add("Diagnosis" to it.label) }
        if (p.ageRange.isNotBlank()) add("Age range" to p.ageRange)
        if (p.topGoals.isNotEmpty()) {
            add(
                (if (p.topGoals.size == 1) "Main goal" else "Main goals")
                    to p.topGoals.joinToString(", ") { it.label },
            )
        }
        if (p.primarySymptoms.isNotEmpty()) {
            add("Main struggles" to p.primarySymptoms.joinToString(", ") { it.label })
        }
        p.productiveTime?.let { add("Most productive" to it.label) }
        p.focusDurationMinutes?.takeIf { it > 0 }?.let { add("Focus block" to "$it minutes") }
        if (p.sleepBedtime.isNotBlank() && p.sleepWakeTime.isNotBlank()) {
            add("Usual sleep" to "${p.sleepBedtime} → ${p.sleepWakeTime}")
        }
        p.chronotype?.let { add("Chronotype" to it.label) }
        p.sleepScheduleOrigin?.let { add("Sleep schedule" to it.label) }
        p.presentation?.let { add("Presentation" to it.label) }
        if (p.coOccurring.isNotEmpty()) {
            add("Also applies" to p.coOccurring.joinToString(", ") { it.label })
        }
        p.interruptionRecall?.let { add("Loses track when interrupted" to it.label) }
        p.captureNeed?.let { add("Needs to write things down" to it.label) }
        p.planChangeImpact?.let { add("When plans change" to it.label) }
        p.taskReturnEffort?.let { add("Returning to a task" to it.label) }
        if (p.impulseAreas.isNotEmpty()) {
            add("Hardest to regulate" to p.impulseAreas.joinToString(", ") { it.label })
        }
        p.nudgeTone?.let { add("Preferred reminder tone" to it.label) }
        p.checkInCeiling?.let { add("Check-in limit" to it.label) }
        p.missedTaskResponse?.let { add("When something is missed" to it.label) }
        p.workEnvironment?.let { add("Usual work setting" to it.label) }
        if (p.pastStrategies.isNotEmpty()) {
            add("Already tried" to p.pastStrategies.joinToString(", ") { it.label })
        }
        p.bodyDoublingInterest?.let { add("Body doubling" to it.label) }
        p.medicationStatus?.let { add("Medication status" to it.label) }
        if (p.medications.isNotEmpty()) {
            add(
                "Medications" to p.medications.joinToString(", ") { m ->
                    if (m.doseLabel.isBlank()) m.name else "${m.name} ${m.doseLabel}"
                },
            )
        }
        if (p.copingStrategies.isNotEmpty()) {
            add("What helps" to p.copingStrategies.joinToString(", ") { it.label })
        }
        if (p.painPoint.isNotBlank()) add("Hardest part" to p.painPoint.trim())
        p.aiTonePreference?.let { add("Preferred tone" to it.label) }
    }

    private companion object {
        val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")
        val SHORT_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM")
        val COLUMNS = listOf("Date", "Steps", "HR", "Rest HR", "kcal", "Sleep", "Exer.")
    }
}

// minimal top-down text layout over PdfDocument, starting a new page whenever the cursor runs
// past the bottom margin. A4 at 72dpi
private class PageRenderer(private val doc: PdfDocument) {
    private var pageNumber = 0
    private var page: PdfDocument.Page? = null
    private var canvas: Canvas? = null
    private var y = 0f

    private val headingPaint = Paint().apply {
        color = Color.BLACK; textSize = 20f; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
    }
    private val sectionPaint = Paint().apply {
        color = Color.rgb(0xD9, 0x7A, 0x3D); textSize = 13f
        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
    }
    private val labelPaint = Paint().apply {
        color = Color.rgb(0x6B, 0x6B, 0x6B); textSize = 10f; isAntiAlias = true
    }
    private val valuePaint = Paint().apply {
        color = Color.BLACK; textSize = 11f; isAntiAlias = true
    }
    private val captionPaint = Paint().apply {
        color = Color.rgb(0x8A, 0x8A, 0x8A); textSize = 9f; isAntiAlias = true
    }
    private val cellPaint = Paint().apply {
        color = Color.BLACK; textSize = 9.5f; isAntiAlias = true
    }
    private val cellHeaderPaint = Paint().apply {
        color = Color.rgb(0x2A, 0x2A, 0x2A); textSize = 9.5f
        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
    }
    private val rulePaint = Paint().apply {
        color = Color.rgb(0xE0, 0xD8, 0xCC); strokeWidth = 0.7f
    }

    init { newPage() }

    private fun newPage() {
        page?.let { doc.finishPage(it) }
        pageNumber++
        val info = PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNumber).create()
        page = doc.startPage(info)
        canvas = page!!.canvas
        y = MARGIN
    }

    private fun ensure(space: Float) {
        if (y + space > PAGE_H - MARGIN) newPage()
    }

    fun heading(text: String) {
        ensure(28f); canvas?.drawText(text, MARGIN, y, headingPaint); y += 24f
    }

    fun section(text: String) {
        ensure(26f)
        canvas?.drawText(text.uppercase(), MARGIN, y, sectionPaint)
        y += 6f
        canvas?.drawLine(MARGIN, y, PAGE_W - MARGIN, y, rulePaint)
        y += 14f
    }

    fun field(label: String, value: String) {
        // long free-text answers (the 'hardest part' box) must wrap, not clip
        val lines = wrap(value, valuePaint, PAGE_W - MARGIN - VALUE_X)
        ensure(14f * lines.size)
        canvas?.drawText(label, MARGIN, y, labelPaint)
        lines.forEachIndexed { i, line ->
            canvas?.drawText(line, VALUE_X, y + i * 12f, valuePaint)
        }
        y += 12f * lines.size + 4f
    }

    fun body(text: String) {
        wrap(text, valuePaint, PAGE_W - 2 * MARGIN).forEach {
            ensure(14f); canvas?.drawText(it, MARGIN, y, valuePaint); y += 13f
        }
    }

    fun caption(text: String) {
        wrap(text, captionPaint, PAGE_W - 2 * MARGIN).forEach {
            ensure(12f); canvas?.drawText(it, MARGIN, y, captionPaint); y += 11f
        }
    }

    fun tableHeader(columns: List<String>) {
        ensure(22f)
        columns.forEachIndexed { i, c -> canvas?.drawText(c, columnX(i), y, cellHeaderPaint) }
        y += 5f
        canvas?.drawLine(MARGIN, y, PAGE_W - MARGIN, y, rulePaint)
        y += 12f
    }

    fun tableRow(cells: List<String>) {
        ensure(16f)
        cells.forEachIndexed { i, c -> canvas?.drawText(c, columnX(i), y, cellPaint) }
        y += 14f
    }

    fun gap(amount: Float = 14f) { y += amount }

    fun finish() { page?.let { doc.finishPage(it) }; page = null }

    private fun columnX(index: Int): Float =
        MARGIN + when (index) {
            0 -> 0f
            else -> FIRST_COL_W + (index - 1) * OTHER_COL_W
        }

    private fun wrap(text: String, paint: Paint, maxWidth: Float): List<String> {
        if (text.isBlank()) return listOf("—")
        val words = text.split(' ')
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        words.forEach { word ->
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) <= maxWidth) {
                current = StringBuilder(candidate)
            } else {
                if (current.isNotEmpty()) lines += current.toString()
                current = StringBuilder(word)
            }
        }
        if (current.isNotEmpty()) lines += current.toString()
        return lines.ifEmpty { listOf(text) }
    }

    private companion object {
        const val PAGE_W = 595   // A4 width at 72dpi
        const val PAGE_H = 842   // A4 height at 72dpi
        const val MARGIN = 40f
        const val VALUE_X = 170f
        const val FIRST_COL_W = 95f
        const val OTHER_COL_W = 68f
    }
}
