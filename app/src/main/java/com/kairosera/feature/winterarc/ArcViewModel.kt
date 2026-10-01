package com.kairosera.feature.winterarc

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairosera.AppContainer
import com.kairosera.core.diagnostics.SafeLog
import com.kairosera.core.settings.ArcReminder
import com.kairosera.core.settings.ReminderConfig
import com.kairosera.core.settings.WinterArcSettings
import com.kairosera.domain.reading.Book
import com.kairosera.domain.winterarc.ArcStats
import com.kairosera.domain.winterarc.DailySummary
import com.kairosera.domain.winterarc.DayInputs
import com.kairosera.domain.winterarc.FocusSession
import com.kairosera.domain.winterarc.Habit
import com.kairosera.domain.winterarc.HabitKind
import com.kairosera.domain.winterarc.HabitRules
import com.kairosera.domain.winterarc.SpeakingSession
import com.kairosera.domain.winterarc.StudyTask
import com.kairosera.domain.winterarc.WinterArc
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** Everything one day of the challenge shows. */
data class ArcDay(
    val date: LocalDate,
    val today: LocalDate,
    val arc: WinterArc?,
    val habits: List<Habit>,
    val inputs: DayInputs,
    val summary: DailySummary,
    val study: List<StudyTask>,
    val focus: List<FocusSession>,
) {
    val editable: Boolean get() = !date.isAfter(today)
    fun habit(kind: HabitKind) = summary.habits.firstOrNull { it.kind == kind }
    fun target(kind: HabitKind) = habits.firstOrNull { it.kind == kind }?.target ?: kind.defaultTarget
    val dayNumber: Int get() = arc?.dayNumber(date, today) ?: 1
    val rawDay: Int get() = arc?.rawDayNumber(date, today) ?: 1
    val duration: Int get() = arc?.durationDays ?: WinterArc.DEFAULT_DURATION
}

/** Many days at once, for the calendar, stats and the 90-day view. */
data class ArcRange(
    val from: LocalDate,
    val to: LocalDate,
    val today: LocalDate,
    val arc: WinterArc?,
    val habits: List<Habit>,
    val inputs: Map<LocalDate, DayInputs>,
) {
    private val cache = HashMap<LocalDate, DailySummary>()
    fun summary(date: LocalDate): DailySummary = cache.getOrPut(date) { HabitRules.summarize(habits, inputs[date] ?: DayInputs(date)) }
    fun state(date: LocalDate) = HabitRules.stateOf(arc, date, today, if (date.isAfter(today)) null else summary(date))
    fun inputsOn(date: LocalDate) = inputs[date] ?: DayInputs(date)
}

/** The ticking "today", so the dashboard rolls over at midnight without a restart. */
internal fun todayTicker(): Flow<LocalDate> = flow {
    while (true) { emit(LocalDate.now()); delay(30_000) }
}.distinctUntilChanged()

/** Shared by every Winter Arc screen: reads the data and applies one-tap actions. */
@OptIn(ExperimentalCoroutinesApi::class)
class ArcViewModel(private val c: AppContainer) : ViewModel() {
    private val repo = c.winterArc

    val today: StateFlow<LocalDate> = todayTicker().stateIn(viewModelScope, SharingStarted.Eagerly, LocalDate.now())
    val settings: StateFlow<WinterArcSettings?> = c.winterPrefs.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val arc: StateFlow<WinterArc?> = repo.arc.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val habits: StateFlow<List<Habit>> = repo.habits.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val book: StateFlow<Book?> = repo.arcRow.flatMapLatest { row ->
        val id = row?.bookId ?: return@flatMapLatest flowOf(null)
        c.books.observeBook(id)
    }.catch { emit(null) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val books: StateFlow<List<Book>> = c.books.observeBooks().catch { emit(emptyList()) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun day(date: LocalDate): Flow<ArcDay> = combine(
        repo.observeDays(date, date),
        repo.habits,
        repo.arc,
        repo.observeStudy(date, date),
        repo.observeFocus(date, date),
    ) { days, habits, arc, study, focus ->
        val inputs = days[date] ?: DayInputs(date)
        ArcDay(date, LocalDate.now(), arc, habits, inputs, HabitRules.summarize(habits, inputs), study, focus)
    }

    fun range(from: LocalDate, to: LocalDate): Flow<ArcRange> = combine(repo.observeDays(from, to), repo.habits, repo.arc) { days, habits, arc ->
        ArcRange(from, to, LocalDate.now(), arc, habits, days)
    }.catch { e -> SafeLog.error("arc_range_failed", e) }

    val home: StateFlow<ArcDay?> = today.flatMapLatest { day(it) }
        .catch { e -> SafeLog.error("arc_home_failed", e) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Reading streak and per-habit streaks need a little history: the last 120 days. */
    val habitStreaks: StateFlow<Map<HabitKind, Int>> = today.flatMapLatest { t ->
        combine(repo.observeDays(t.minusDays(120), t), repo.habits) { days, habits ->
            val dates = (120 downTo 0).map { t.minusDays(it.toLong()) }
            habits.associate { h ->
                h.kind to ArcStats.habitStreak(dates, { d -> HabitRules.evaluate(h, days[d] ?: DayInputs(d)).done }, t)
            }
        }
    }.catch { emit(emptyMap()) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun recentSpeaking(): Flow<List<SpeakingSession>> = repo.observeRecentSpeaking(8)

    private fun act(name: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }.onFailure { SafeLog.error("arc_$name", it) }
        }
    }

    private fun okDate(date: LocalDate) = !date.isAfter(LocalDate.now())

    fun addWater(date: LocalDate, ml: Int) { if (okDate(date)) act("water") { repo.addWater(date, ml) } }
    fun setWater(date: LocalDate, ml: Int) { if (okDate(date)) act("water_set") { repo.setWater(date, ml) } }
    fun setSteps(date: LocalDate, steps: Int) { if (okDate(date)) act("steps") { repo.setSteps(date, steps) } }
    fun addSteps(date: LocalDate, steps: Int) { if (okDate(date)) act("steps_add") { repo.addSteps(date, steps) } }
    fun setRunning(date: LocalDate, km: Double, ran: Boolean) { if (okDate(date)) act("running") { repo.setRunning(date, km, ran) } }
    fun setChecked(kind: HabitKind, date: LocalDate, done: Boolean) { if (okDate(date)) act("check") { repo.setChecked(kind, date, done) } }
    fun setWake(date: LocalDate, time: LocalTime?) { if (okDate(date)) act("wake") { repo.setWake(date, time) } }
    fun logReading(date: LocalDate, minutes: Int, pages: Int) { if (okDate(date) && (minutes > 0 || pages > 0)) act("reading") { repo.logReading(date, minutes, pages) } }
    fun setBook(id: Long) = act("book") { repo.setBook(id) }
    fun setStudyDone(id: Long, done: Boolean) = act("study") { repo.setStudyDone(id, done) }
    fun saveFocus(s: FocusSession) = act("focus") { repo.saveFocus(s) }
    fun saveStudyTask(t: StudyTask) = act("study_save") { repo.saveStudyTask(t) }
    fun deleteStudyTask(id: Long) = act("study_delete") { repo.deleteStudyTask(id) }
    fun addStudyMinutes(id: Long, minutes: Int) = act("study_minutes") { repo.addStudyMinutes(id, minutes) }

    fun pause() = act("pause") { repo.pause(LocalDate.now()); c.winterReminders.rebuild() }
    fun resume() = act("resume") { repo.resume(LocalDate.now()); c.winterReminders.rebuild() }
    fun setStartDate(d: LocalDate) = act("start_date") { repo.setStartDate(d) }
    fun reset(d: LocalDate) = act("reset") { repo.reset(d); c.winterReminders.rebuild() }
    fun exit() = act("exit") { c.winterPrefs.setEnabled(false); c.winterReminders.rebuild() }
    fun setReminder(r: ArcReminder, config: ReminderConfig) = act("reminder") { c.winterPrefs.setReminder(r, config); c.winterReminders.rebuild() }
    fun setHabitActive(kind: HabitKind, on: Boolean) = act("habit_active") { repo.setHabitActive(kind, on) }
    fun setTarget(kind: HabitKind, target: Double) = act("habit_target") { repo.setTarget(kind, target) }
    fun setStepSensor(on: Boolean) = act("step_sensor") { c.winterPrefs.setStepSensor(on) }

    suspend fun studyTask(id: Long): StudyTask? = runCatching { repo.studyTask(id) }.getOrNull()

    /** Today's speaking topic, created on first look. */
    suspend fun topicFor(date: LocalDate): SpeakingSession? = runCatching { repo.topicFor(date) }.getOrNull()
    suspend fun shuffleTopic(date: LocalDate): SpeakingSession? = runCatching { repo.shuffleTopic(date) }.getOrNull()
    fun completeSpeaking(id: Long, seconds: Int) = act("speak_done") { repo.completeSpeaking(id, seconds) }
    fun rateSpeaking(id: Long, rating: Int?) = act("speak_rate") { repo.rateSpeaking(id, rating) }

    fun now(): Instant = Instant.now(c.clock)
}
