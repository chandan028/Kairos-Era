package com.kairosera.feature.onboarding

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairosera.AppContainer
import com.kairosera.R
import com.kairosera.core.diagnostics.SafeLog
import com.kairosera.core.settings.FocusArea
import com.kairosera.core.settings.OnboardingDraft
import com.kairosera.core.settings.OnboardingRepository
import com.kairosera.core.settings.OnboardingStep
import com.kairosera.core.settings.ReminderChoice
import com.kairosera.data.tracker.TrackerTemplates
import com.kairosera.domain.model.Reminder
import com.kairosera.domain.model.ReminderTiming
import com.kairosera.domain.model.Task
import com.kairosera.domain.quote.DailyQuote
import com.kairosera.domain.tracker.TrackerTemplate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** A tracker the chosen focus areas will start, shown on the last step before it exists. */
data class Starter(val focus: FocusArea, val template: TrackerTemplate, val icon: String, val title: Int, val subtitle: Int)

data class OnboardingState(
    val draft: OnboardingDraft,
    val quote: DailyQuote? = null,
    val finishing: Boolean = false,
) {
    val step: OnboardingStep get() = draft.step
    val starters: List<Starter> get() = STARTERS.filter { it.focus in draft.focus }

    companion object {
        val STARTERS = listOf(
            Starter(FocusArea.LEARN, TrackerTemplate.STUDY, "📚", R.string.onb_starter_study, R.string.onb_starter_study_sub),
            Starter(FocusArea.HABITS, TrackerTemplate.HABIT, "✅", R.string.onb_starter_habit, R.string.onb_starter_habit_sub),
            Starter(FocusArea.HEALTH, TrackerTemplate.FITNESS, "🏃", R.string.onb_starter_move, R.string.onb_starter_move_sub),
        )
    }
}

/**
 * Five steps: understand, personalize, experience, enable, preview. Every answer is saved as it
 * changes (see [OnboardingRepository]), so leaving the app never loses the person's place.
 */
class OnboardingViewModel(private val c: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow<OnboardingState?>(null)
    val state: StateFlow<OnboardingState?> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val draft = runCatching { c.onboarding.draft.first() }.getOrDefault(OnboardingDraft())
            _state.value = OnboardingState(draft)
            val quote = runCatching { c.quotes.forDate(LocalDate.now()) }.getOrNull()
            _state.value = _state.value?.copy(quote = quote)
        }
    }

    private fun update(change: (OnboardingDraft) -> OnboardingDraft) {
        val current = _state.value ?: return
        if (current.finishing) return
        val next = change(current.draft)
        _state.value = current.copy(draft = next)
        viewModelScope.launch { runCatching { c.onboarding.save(next) }.onFailure { SafeLog.error("onboarding_save_failed", it) } }
    }

    fun next() = update { d -> d.copy(step = OnboardingStep.entries.getOrElse(d.step.ordinal + 1) { d.step }) }

    /** Returns false on the first step, so the system handles Back (leaving the app keeps progress). */
    fun back(): Boolean {
        val step = _state.value?.step ?: return false
        if (step == OnboardingStep.WELCOME) return false
        update { d -> d.copy(step = OnboardingStep.entries[d.step.ordinal - 1]) }
        return true
    }

    fun toggle(area: FocusArea) = update { d -> d.copy(focus = if (area in d.focus) d.focus - area else d.focus + area) }
    fun skipFocus() = update { d -> d.copy(focus = emptySet(), step = OnboardingStep.FIRST_ACTION) }

    fun setFirstThing(text: String) = update { it.copy(firstThing = text.take(OnboardingRepository.MAX_TITLE)) }
    fun setTime(time: LocalTime?) = update { it.copy(time = time) }
    fun skipFirstThing() = update { it.copy(firstThing = "", time = null, step = OnboardingStep.REMINDERS) }

    fun markPermissionAsked() = update { it.copy(permissionAsked = true) }

    /** Records the answer and moves on; a "no" (or a denied permission) never blocks the flow. */
    fun reminders(choice: ReminderChoice) = update { it.copy(reminders = choice, step = OnboardingStep.READY) }

    /**
     * Creates the first thing and any starter trackers, then marks onboarding done. [context]
     * is the screen's, so tracker labels are written in the language the person is using.
     */
    fun finish(context: Context) {
        val current = _state.value ?: return
        if (current.finishing) return
        _state.value = current.copy(finishing = true)
        val d = current.draft
        viewModelScope.launch {
            val now = Instant.now(c.clock)
            runCatching {
                val existing = c.trackers.observeActive().first().map { it.template }.toSet()
                current.starters.filter { it.template !in existing }.forEach { s ->
                    c.trackers.save(
                        TrackerTemplates.create(context, s.template).copy(
                            name = context.getString(s.title), icon = s.icon, createdAt = now, updatedAt = now,
                        ),
                    )
                }
            }.onFailure { SafeLog.error("onboarding_starters_failed", it) }
            val settings = c.settings.settings.first()
            if (d.firstThing.isNotBlank()) {
                runCatching {
                    val remind = d.time != null && d.reminders == ReminderChoice.ALLOWED
                    c.saveTask(
                        Task(
                            title = d.firstThing.trim(),
                            date = LocalDate.now(),
                            startTime = d.time,
                            reminders = if (remind) {
                                listOf(Reminder(timing = ReminderTiming.BeforeStart(0), persistent = settings.defaultPersistent, sound = settings.defaultSound))
                            } else {
                                emptyList()
                            },
                            createdAt = now,
                        ),
                    )
                }.onFailure { SafeLog.error("onboarding_first_task_failed", it) }
            }
            c.settings.completeOnboarding(settings.name, d.focus.map { it.key }.toSet())
            runCatching { c.onboarding.clear() }
        }
    }
}
