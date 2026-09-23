package com.kairosera.data.tracker

import android.content.Context
import com.kairosera.R
import com.kairosera.domain.tracker.MeasurementType
import com.kairosera.domain.tracker.MeasurementType.CHECKBOX
import com.kairosera.domain.tracker.MeasurementType.DECIMAL
import com.kairosera.domain.tracker.MeasurementType.DURATION
import com.kairosera.domain.tracker.MeasurementType.NUMBER
import com.kairosera.domain.tracker.MeasurementType.PAGES
import com.kairosera.domain.tracker.MeasurementType.PERCENTAGE
import com.kairosera.domain.tracker.MeasurementType.RATING
import com.kairosera.domain.tracker.MeasurementType.TEXT
import com.kairosera.domain.tracker.Tracker
import com.kairosera.domain.tracker.TrackerField
import com.kairosera.domain.tracker.TrackerTemplate

/**
 * Starting points only. A tracker created from a template is an ordinary tracker: every field
 * can be renamed, retargeted, switched off or removed. Labels are written in the current app
 * language at creation time and then belong to the user.
 */
object TrackerTemplates {
    val ICONS = listOf("📚", "💪", "📖", "✅", "🚀", "💰", "🧠", "❤️", "🌱", "⭐", "🏃", "🧘", "💧", "😴", "🎯", "✍️", "🎵", "🧑‍💻", "🍎", "🙏")
    val COLORS = listOf(0xFF2E4A7D, 0xFF3F5E96, 0xFF4E8A64, 0xFFB5652B, 0xFF6B5B95, 0xFF9A6B4F, 0xFF2F7F86, 0xFFA23B5B)

    fun icon(template: TrackerTemplate): String = when (template) {
        TrackerTemplate.STUDY -> "📚"
        TrackerTemplate.FITNESS -> "💪"
        TrackerTemplate.READING -> "📖"
        TrackerTemplate.HABIT -> "✅"
        TrackerTemplate.PROJECT -> "🚀"
        TrackerTemplate.FINANCE -> "💰"
        TrackerTemplate.LEARNING -> "🧠"
        TrackerTemplate.HEALTH -> "❤️"
        TrackerTemplate.PERSONAL -> "🌱"
        TrackerTemplate.CUSTOM -> "⭐"
    }

    fun nameRes(template: TrackerTemplate): Int = when (template) {
        TrackerTemplate.STUDY -> R.string.template_study
        TrackerTemplate.FITNESS -> R.string.template_fitness
        TrackerTemplate.READING -> R.string.template_reading
        TrackerTemplate.HABIT -> R.string.template_habit
        TrackerTemplate.PROJECT -> R.string.template_project
        TrackerTemplate.FINANCE -> R.string.template_finance
        TrackerTemplate.LEARNING -> R.string.template_learning
        TrackerTemplate.HEALTH -> R.string.template_health
        TrackerTemplate.PERSONAL -> R.string.template_personal
        TrackerTemplate.CUSTOM -> R.string.template_custom
    }

    fun summaryRes(template: TrackerTemplate): Int = when (template) {
        TrackerTemplate.STUDY -> R.string.template_study_summary
        TrackerTemplate.FITNESS -> R.string.template_fitness_summary
        TrackerTemplate.READING -> R.string.template_reading_summary
        TrackerTemplate.HABIT -> R.string.template_habit_summary
        TrackerTemplate.PROJECT -> R.string.template_project_summary
        TrackerTemplate.FINANCE -> R.string.template_finance_summary
        TrackerTemplate.LEARNING -> R.string.template_learning_summary
        TrackerTemplate.HEALTH -> R.string.template_health_summary
        TrackerTemplate.PERSONAL -> R.string.template_personal_summary
        TrackerTemplate.CUSTOM -> R.string.template_custom_summary
    }

    fun create(context: Context, template: TrackerTemplate): Tracker {
        fun f(label: Int, type: MeasurementType, unit: Int? = null, target: Double? = null, enabled: Boolean = true) =
            TrackerField(label = context.getString(label), type = type, unit = unit?.let(context::getString).orEmpty(), target = target, enabled = enabled)
        val fields = when (template) {
            TrackerTemplate.STUDY -> listOf(
                f(R.string.tf_study_time, DURATION, target = 60.0),
                f(R.string.tf_practice, NUMBER, target = 5.0),
                f(R.string.tf_revision, CHECKBOX),
                f(R.string.tf_notes, TEXT),
            )
            TrackerTemplate.FITNESS -> listOf(
                f(R.string.tf_workout, CHECKBOX),
                f(R.string.tf_steps, NUMBER, R.string.unit_steps, 8000.0),
                f(R.string.tf_workout_minutes, DURATION, target = 30.0),
                f(R.string.tf_sleep, DECIMAL, R.string.unit_hours, 7.5),
                f(R.string.tf_water, DECIMAL, R.string.unit_litres, 2.5),
                f(R.string.tf_walk, DURATION, target = 20.0),
                f(R.string.tf_stretching, CHECKBOX),
                f(R.string.tf_energy, RATING),
                f(R.string.tf_mood, RATING),
                // Off by default: weight is personal and never judged.
                f(R.string.tf_weight, DECIMAL, R.string.unit_kg, enabled = false),
            )
            TrackerTemplate.READING -> listOf(f(R.string.tf_pages, PAGES, R.string.unit_pages, 20.0), f(R.string.tf_reading_minutes, DURATION, target = 30.0))
            TrackerTemplate.HABIT -> listOf(f(R.string.tf_done, CHECKBOX))
            TrackerTemplate.PROJECT -> listOf(
                f(R.string.tf_worked_on_it, CHECKBOX),
                f(R.string.tf_time_spent, DURATION, target = 60.0),
                f(R.string.tf_progress, PERCENTAGE),
                f(R.string.tf_next_step, TEXT),
            )
            TrackerTemplate.FINANCE -> listOf(
                f(R.string.tf_tracked_spending, CHECKBOX),
                f(R.string.tf_no_spend, CHECKBOX),
                f(R.string.tf_notes, TEXT),
            )
            TrackerTemplate.LEARNING -> listOf(
                f(R.string.tf_practice_time, DURATION, target = 30.0),
                f(R.string.tf_learned_count, NUMBER, target = 1.0),
                f(R.string.tf_notes, TEXT),
            )
            TrackerTemplate.HEALTH -> listOf(
                f(R.string.tf_water, DECIMAL, R.string.unit_litres, 2.5),
                f(R.string.tf_sleep, DECIMAL, R.string.unit_hours, 7.5),
                f(R.string.tf_medicine, CHECKBOX),
                f(R.string.tf_energy, RATING),
            )
            TrackerTemplate.PERSONAL -> listOf(
                f(R.string.tf_meditation, DURATION, target = 10.0),
                f(R.string.tf_screen_free, CHECKBOX),
                f(R.string.tf_gratitude, TEXT),
            )
            TrackerTemplate.CUSTOM -> listOf(f(R.string.tf_done, CHECKBOX))
        }
        return Tracker(
            name = if (template == TrackerTemplate.CUSTOM) "" else context.getString(nameRes(template)),
            icon = icon(template),
            colorArgb = COLORS[template.ordinal % COLORS.size],
            template = template,
            fields = fields.mapIndexed { i, fld -> fld.copy(position = i) },
        )
    }
}
