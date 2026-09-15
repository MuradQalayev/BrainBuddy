package com.muradgalayev.brainbuddy.data.export

import com.muradgalayev.brainbuddy.R
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

            renderer.heading(context.getString(R.string.pdf_heading))
            renderer.caption(context.getString(R.string.pdf_generated, LocalDate.now().format(DATE)))
            renderer.gap()

            renderer.section(context.getString(R.string.pdf_who))
            renderer.field(context.getString(R.string.med_name), displayName?.takeIf { it.isNotBlank() } ?: context.getString(R.string.common_not_set))
            renderer.field(context.getString(R.string.common_email), email?.takeIf { it.isNotBlank() } ?: context.getString(R.string.common_not_set))
            renderer.gap()

            renderer.section(context.getString(R.string.pdf_adhd_profile))
            if (profile == null || !profile.surveyCompleted) {
                renderer.body(context.getString(R.string.pdf_survey_incomplete))
            } else {
                surveyRows(profile).forEach { (label, value) -> renderer.field(label, value) }
            }
            renderer.gap()

            renderer.section(context.getString(R.string.pdf_weekly_log))
            renderer.caption(
                context.getString(R.string.pdf_range, report.from.format(DATE), report.to.format(DATE), report.daysWithAnyData)
            )
            renderer.gap(6f)

            if (report.daysWithAnyData == 0) {
                renderer.body(context.getString(R.string.pdf_no_data))
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
                renderer.section(context.getString(R.string.pdf_averages))
                renderer.field(context.getString(R.string.pdf_steps_day), report.averageSteps?.toString() ?: "—")
                renderer.field(context.getString(R.string.health_heart_rate), report.averageHeartRate?.let { "$it bpm" } ?: "—")
                renderer.field(
                    context.getString(R.string.wellness_resting_hr),
                    report.averageRestingHeartRate?.let { "$it bpm" } ?: "—",
                )
                renderer.field(
                    context.getString(R.string.pdf_sleep_night),
                    report.averageSleepHours?.let { "%.1f h".format(it) } ?: "—",
                )
                renderer.field(context.getString(R.string.pdf_exercise_total), context.getString(R.string.common_minutes_short, report.totalExerciseMinutes))
                renderer.field(context.getString(R.string.pdf_calories_total), "${report.totalCalories} kcal")
            }

            renderer.gap()
            renderer.caption(
                context.getString(R.string.pdf_disclaimer)
            )
            renderer.finish()

            val name = "Myndora-Profile-${LocalDate.now()}.pdf"
            val result = writeDocument(doc, name)
            doc.close()
            result
        }.getOrElse { PdfExportResult.Failed(it.message ?: context.getString(R.string.pdf_create_failed)) }
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
                ?: return PdfExportResult.Failed(context.getString(R.string.pdf_open_downloads_failed))
            resolver.openOutputStream(uri).use { out ->
                if (out == null) return PdfExportResult.Failed(context.getString(R.string.pdf_write_downloads_failed))
                doc.writeTo(out)
            }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return PdfExportResult.Saved("Downloads/$fileName", null)
        }

        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: return PdfExportResult.Failed(context.getString(R.string.pdf_no_storage))
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, fileName)
        file.outputStream().use { doc.writeTo(it) }
        return PdfExportResult.Saved(file.absolutePath, file)
    }

    private fun surveyRows(p: AdhdProfile): List<Pair<String, String>> = buildList {
        p.diagnosisStatus?.let { add(context.getString(R.string.onboarding_diagnosis) to context.getString(it.labelRes)) }
        if (p.ageRange.isNotBlank()) add(context.getString(R.string.pdf_age_range) to if (p.ageRange == "Under 18") context.getString(R.string.age_under_18) else p.ageRange)
        if (p.topGoals.isNotEmpty()) {
            add(
                (if (p.topGoals.size == 1) context.getString(R.string.pdf_main_goal) else context.getString(R.string.pdf_main_goals))
                    to p.topGoals.joinToString(", ") { context.getString(it.labelRes) },
            )
        }
        if (p.primarySymptoms.isNotEmpty()) {
            add(context.getString(R.string.pdf_struggles) to p.primarySymptoms.joinToString(", ") { context.getString(it.labelRes) })
        }
        p.productiveTime?.let { add(context.getString(R.string.pdf_most_productive) to context.getString(it.labelRes)) }
        p.focusDurationMinutes?.takeIf { it > 0 }?.let { add(context.getString(R.string.offline_focus_block) to context.getString(R.string.pdf_n_minutes, it)) }
        if (p.sleepBedtime.isNotBlank() && p.sleepWakeTime.isNotBlank()) {
            add(context.getString(R.string.pdf_usual_sleep) to "${p.sleepBedtime} → ${p.sleepWakeTime}")
        }
        p.chronotype?.let { add(context.getString(R.string.pdf_chronotype) to context.getString(it.labelRes)) }
        p.sleepScheduleOrigin?.let { add(context.getString(R.string.pdf_sleep_schedule) to context.getString(it.labelRes)) }
        p.presentation?.let { add(context.getString(R.string.pdf_presentation) to context.getString(it.labelRes)) }
        if (p.coOccurring.isNotEmpty()) {
            add(context.getString(R.string.pdf_also_applies) to p.coOccurring.joinToString(", ") { context.getString(it.labelRes) })
        }
        p.interruptionRecall?.let { add(context.getString(R.string.pdf_loses_track) to context.getString(it.labelRes)) }
        p.captureNeed?.let { add(context.getString(R.string.pdf_write_down) to context.getString(it.labelRes)) }
        p.planChangeImpact?.let { add(context.getString(R.string.pdf_plans_change) to context.getString(it.labelRes)) }
        p.taskReturnEffort?.let { add(context.getString(R.string.pdf_returning) to context.getString(it.labelRes)) }
        if (p.impulseAreas.isNotEmpty()) {
            add(context.getString(R.string.pdf_hardest_regulate) to p.impulseAreas.joinToString(", ") { context.getString(it.labelRes) })
        }
        p.nudgeTone?.let { add(context.getString(R.string.pdf_reminder_tone) to context.getString(it.labelRes)) }
        p.checkInCeiling?.let { add(context.getString(R.string.pdf_checkin_limit) to context.getString(it.labelRes)) }
        p.missedTaskResponse?.let { add(context.getString(R.string.pdf_when_missed) to context.getString(it.labelRes)) }
        p.workEnvironment?.let { add(context.getString(R.string.pdf_work_setting) to context.getString(it.labelRes)) }
        if (p.pastStrategies.isNotEmpty()) {
            add(context.getString(R.string.pdf_already_tried) to p.pastStrategies.joinToString(", ") { context.getString(it.labelRes) })
        }
        p.bodyDoublingInterest?.let { add(context.getString(R.string.intake_body_doubling) to context.getString(it.labelRes)) }
        p.medicationStatus?.let { add(context.getString(R.string.pdf_med_status) to context.getString(it.labelRes)) }
        if (p.medications.isNotEmpty()) {
            add(
                context.getString(R.string.ws_medications) to p.medications.joinToString(", ") { m ->
                    m.doseLabel(context.resources).let { dose -> if (dose.isBlank()) m.name else "${m.name} $dose" }
                },
            )
        }
        if (p.copingStrategies.isNotEmpty()) {
            add(context.getString(R.string.pdf_what_helps) to p.copingStrategies.joinToString(", ") { context.getString(it.labelRes) })
        }
        if (p.painPoint.isNotBlank()) add(context.getString(R.string.pdf_hardest_part) to p.painPoint.trim())
        p.aiTonePreference?.let { add(context.getString(R.string.pdf_preferred_tone) to context.getString(it.labelRes)) }
    }

    // getters, not stored values, so month and day names follow the language at export time
    private val DATE: DateTimeFormatter get() = DateTimeFormatter.ofPattern("d MMM yyyy")
    private val SHORT_DATE: DateTimeFormatter get() = DateTimeFormatter.ofPattern("EEE d MMM")
    private val COLUMNS: List<String>
        get() = listOf(
            R.string.pdf_col_date, R.string.pdf_col_steps, R.string.pdf_col_hr, R.string.pdf_col_rest_hr,
            R.string.pdf_col_kcal, R.string.pdf_col_sleep, R.string.pdf_col_exercise,
        ).map(context::getString)
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
