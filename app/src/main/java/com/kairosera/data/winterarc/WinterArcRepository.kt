package com.kairosera.data.winterarc

import android.content.Context
import androidx.room.withTransaction
import com.kairosera.core.database.KairosDatabase
import com.kairosera.core.database.WaFocusSessionEntity
import com.kairosera.core.database.WaHabitEntity
import com.kairosera.core.database.WaHabitLogEntity
import com.kairosera.core.database.WaSpeakingSessionEntity
import com.kairosera.core.database.WaStudyTaskEntity
import com.kairosera.core.database.WinterArcEntity
import com.kairosera.core.diagnostics.SafeLog
import com.kairosera.data.repository.RoomBookRepository
import com.kairosera.domain.reading.Book
import com.kairosera.domain.reading.BookStatus
import com.kairosera.domain.reading.ReadingSession
import com.kairosera.domain.winterarc.ArcStatus
import com.kairosera.domain.winterarc.DayInputs
import com.kairosera.domain.winterarc.FocusSession
import com.kairosera.domain.winterarc.Habit
import com.kairosera.domain.winterarc.HabitKind
import com.kairosera.domain.winterarc.HabitLog
import com.kairosera.domain.winterarc.HabitRules
import com.kairosera.domain.winterarc.HabitType
import com.kairosera.domain.winterarc.SpeakingSession
import com.kairosera.domain.winterarc.StudyCategory
import com.kairosera.domain.winterarc.StudyPlanCsv
import com.kairosera.domain.winterarc.StudyTask
import com.kairosera.domain.winterarc.TopicPicker
import com.kairosera.domain.winterarc.WinterArc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlin.random.Random

/** Where speaking topics come from. The bundled bank always works offline; others are optional extras. */
interface SpeakingTopicSource {
    suspend fun topics(): List<String>
}

/**
 * The 365+ topics bundled in the app (assets/winterarc/speaking_topics.json).
 *
 * A web source such as unprompted.cool can be added later as another [SpeakingTopicSource] whose
 * topics are fetched, cached into this bank and then used offline. Kairos Era deliberately has no
 * internet permission today, so no such source is wired in, and nothing ever depends on one.
 */
class BundledTopicSource(private val context: Context) : SpeakingTopicSource {
    private var cache: List<String>? = null
    override suspend fun topics(): List<String> = cache ?: withContext(Dispatchers.IO) {
        runCatching {
            context.assets.open(PATH).bufferedReader().use { Json.decodeFromString<List<String>>(it.readText()) }
        }.onFailure { SafeLog.error("topics_load_failed", it) }.getOrDefault(emptyList()).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    }.also { cache = it }

    companion object { const val PATH = "winterarc/speaking_topics.json" }
}

/** All Winter Arc data: the challenge, habits and their logs, study plan, focus and speaking sessions. */
class WinterArcRepository(
    private val context: Context,
    private val db: KairosDatabase,
    private val books: RoomBookRepository,
    private val clock: Clock,
    private val topicSource: SpeakingTopicSource = BundledTopicSource(context),
) {
    private val dao = db.winterArcDao()
    private val speakingLock = Mutex()
    private fun now() = Instant.now(clock).toEpochMilli()

    // ---- Challenge ------------------------------------------------------------------------------

    val arc: Flow<WinterArc?> = dao.observeLatestArc().map { it?.toDomain() }
    val arcRow: Flow<WinterArcEntity?> = dao.observeLatestArc()

    suspend fun currentArc(): WinterArc? = dao.latestArc()?.toDomain()

    /** Starts a new challenge (or restarts it) with the chosen habits. Earlier logs stay in the database. */
    suspend fun startArc(start: LocalDate, enabled: Set<HabitKind>): Long = db.withTransaction {
        ensureHabits()
        HabitKind.entries.forEach { k -> setHabitActive(k, k in enabled) }
        val bookId = ensureCurrentBook()
        val prev = dao.latestArc()
        if (prev != null && prev.status != ArcStatus.ENDED.name) {
            dao.updateArc(prev.copy(status = ArcStatus.ENDED.name, updatedAt = now()))
        }
        val arc = WinterArc(startDate = start)
        dao.insertArc(
            WinterArcEntity(
                startDate = start.toEpochDay(),
                endDate = arc.endDate(start).toEpochDay(),
                durationDays = arc.durationDays,
                status = ArcStatus.ACTIVE.name,
                pauses = "",
                bookId = prev?.bookId ?: bookId,
                createdAt = now(),
                updatedAt = now(),
            ),
        )
    }.also { importBundledPlanIfEmpty() }

    private suspend fun updateArc(change: (WinterArc) -> WinterArc) {
        val row = dao.latestArc() ?: return
        val today = LocalDate.now(clock.withZone(java.time.ZoneId.systemDefault()))
        val next = change(row.toDomain())
        dao.updateArc(
            row.copy(
                startDate = next.startDate.toEpochDay(),
                endDate = next.endDate(today).toEpochDay(),
                status = next.status.name,
                pauses = WinterArc.encodePauses(next.pauses),
                updatedAt = now(),
            ),
        )
    }

    suspend fun setStartDate(date: LocalDate) = updateArc { it.copy(startDate = date, pauses = it.pauses.filter { p -> !p.start.isBefore(date) }) }
    suspend fun pause(today: LocalDate) = updateArc { if (it.isPaused) it else it.paused(today) }
    suspend fun resume(today: LocalDate) = updateArc { it.resumed(today) }

    /** Starts over from [start] with the same habits; history stays in the database and in Statistics. */
    suspend fun reset(start: LocalDate) {
        val enabled = dao.habits().filter { it.active }.mapNotNull { HabitKind.fromKey(it.id) }.toSet().ifEmpty { HabitKind.entries.toSet() }
        startArc(start, enabled)
    }

    suspend fun setBook(bookId: Long) {
        val row = dao.latestArc() ?: return
        dao.updateArc(row.copy(bookId = bookId, updatedAt = now()))
    }

    // ---- Habits ---------------------------------------------------------------------------------

    val habits: Flow<List<Habit>> = dao.observeHabits().map { rows ->
        val byId = rows.associateBy { it.id }
        // Habits not yet seeded show with defaults, so the dashboard never starts empty.
        HabitKind.entries.map { k -> byId[k.name]?.toDomain(k) ?: Habit(k) }.sortedBy { it.sortOrder }
    }

    suspend fun ensureHabits() {
        dao.insertHabits(HabitKind.entries.map { k ->
            WaHabitEntity(k.name, k.category, k.type.name, k.defaultTarget, k.unit, true, k.ordinal)
        })
    }

    suspend fun setHabitActive(kind: HabitKind, active: Boolean) {
        ensureHabits()
        val row = dao.habits().first { it.id == kind.name }
        dao.upsertHabit(row.copy(active = active))
    }

    suspend fun setTarget(kind: HabitKind, target: Double) {
        ensureHabits()
        val row = dao.habits().first { it.id == kind.name }
        dao.upsertHabit(row.copy(target = target))
    }

    fun observeLogs(from: LocalDate, to: LocalDate): Flow<List<HabitLog>> =
        dao.observeLogs(from.toEpochDay(), to.toEpochDay()).map { l -> l.map { it.toDomain() } }

    private suspend fun upsertLog(kind: HabitKind, date: LocalDate, change: (WaHabitLogEntity) -> WaHabitLogEntity) = db.withTransaction {
        val old = dao.log(kind.name, date.toEpochDay()) ?: WaHabitLogEntity(kind.name, date.toEpochDay(), 0.0, false, "", 0.0, 0)
        dao.upsertLog(change(old).copy(updatedAt = now()))
    }

    /** Adds water (negative to undo), never below zero. */
    suspend fun addWater(date: LocalDate, ml: Int) = upsertLog(HabitKind.WATER, date) { it.copy(value = (it.value + ml).coerceIn(0.0, 20_000.0)) }
    suspend fun setWater(date: LocalDate, ml: Int) = upsertLog(HabitKind.WATER, date) { it.copy(value = ml.toDouble().coerceIn(0.0, 20_000.0)) }

    suspend fun setSteps(date: LocalDate, steps: Int) = upsertLog(HabitKind.STEPS, date) { it.copy(value = steps.toDouble().coerceIn(0.0, 200_000.0)) }
    suspend fun addSteps(date: LocalDate, steps: Int) = upsertLog(HabitKind.STEPS, date) { it.copy(value = (it.value + steps).coerceIn(0.0, 200_000.0)) }
    suspend fun setRunning(date: LocalDate, km: Double, ran: Boolean) = upsertLog(HabitKind.STEPS, date) {
        it.copy(extra = km.coerceIn(0.0, 200.0), completed = ran)
    }

    /** Steps from the phone's sensor only ever raise the day's count, so a manual entry is never lowered. */
    suspend fun sensorSteps(date: LocalDate, steps: Int) = upsertLog(HabitKind.STEPS, date) { if (steps > it.value) it.copy(value = steps.toDouble()) else it }

    suspend fun setChecked(kind: HabitKind, date: LocalDate, done: Boolean) {
        require(kind.type == HabitType.CHECK || kind == HabitKind.READING || kind == HabitKind.STUDY || kind == HabitKind.DEEP_WORK)
        upsertLog(kind, date) { it.copy(completed = done, value = if (kind.type == HabitType.CHECK) (if (done) 1.0 else 0.0) else it.value) }
    }

    suspend fun setWake(date: LocalDate, time: LocalTime?) = if (time == null) {
        dao.deleteLog(HabitKind.WAKE_EARLY.name, date.toEpochDay())
    } else {
        upsertLog(HabitKind.WAKE_EARLY, date) { it.copy(value = (time.hour * 60 + time.minute).toDouble(), completed = time.hour * 60 + time.minute < HabitRules.WAKE_LIMIT_MINUTE, notes = "") }
    }

    // ---- Reading (shared with the Read section) ---------------------------------------------------

    /** The challenge's book, created on first use so the reading habit always has one. */
    suspend fun ensureCurrentBook(): Long {
        dao.latestArc()?.bookId?.let { id -> if (books.get(id)?.isActive == true) return id }
        val all = books.observeBooksOnce()
        all.firstOrNull { it.title.equals(DEFAULT_BOOK_TITLE, ignoreCase = true) }?.let { return it.id }
        return books.save(
            Book(
                title = DEFAULT_BOOK_TITLE,
                author = DEFAULT_BOOK_AUTHOR,
                status = BookStatus.READING,
                startDate = LocalDate.now(),
                coverColor = 0xFF62519F,
                createdAt = Instant.now(clock),
                updatedAt = Instant.now(clock),
            ),
        )
    }

    suspend fun logReading(date: LocalDate, minutes: Int, pages: Int) {
        val bookId = ensureCurrentBook()
        books.logSession(bookId, pages.coerceAtLeast(0), minutes.coerceIn(0, 24 * 60), date, Instant.now(clock))
    }

    fun observeReading(from: LocalDate, to: LocalDate): Flow<List<ReadingSession>> = books.observeSessionsBetween(from, to)

    // ---- Study plan -----------------------------------------------------------------------------

    fun observeStudy(from: LocalDate, to: LocalDate): Flow<List<StudyTask>> =
        dao.observeStudy(from.toEpochDay(), to.toEpochDay()).map { l -> l.map { it.toDomain() } }

    val allStudy: Flow<List<StudyTask>> = dao.observeAllStudy().map { l -> l.map { it.toDomain() } }

    suspend fun setStudyDone(id: Long, done: Boolean) {
        val t = dao.studyTask(id) ?: return
        dao.updateStudy(t.copy(completed = done, updatedAt = now()))
    }

    suspend fun studyTask(id: Long): StudyTask? = dao.studyTask(id)?.toDomain()

    suspend fun addStudyMinutes(id: Long, minutes: Int) {
        val t = dao.studyTask(id) ?: return
        dao.updateStudy(t.copy(spentMinutes = (t.spentMinutes + minutes).coerceIn(0, 24 * 60), updatedAt = now()))
    }

    suspend fun saveStudyTask(task: StudyTask): Long =
        if (task.id == 0L) dao.insertStudyTask(task.toEntity(now())) else { dao.updateStudy(task.toEntity(now())); task.id }

    suspend fun deleteStudyTask(id: Long) = dao.deleteStudy(id)

    /**
     * Imports a plan from CSV text. Only rows on or after [from] are used, so topics already covered
     * are never recreated. Imported rows replace earlier imported rows from that date on (ticks are
     * kept); tasks added by hand are never touched. Returns the number of tasks imported.
     */
    suspend fun importPlan(csv: String, from: LocalDate = PLAN_START): StudyPlanCsv.Result {
        val result = withContext(Dispatchers.Default) { StudyPlanCsv.parse(csv, from) }
        if (result.tasks.isNotEmpty()) {
            val first = result.tasks.minOf { it.date }
            dao.replacePlan(first.toEpochDay(), result.tasks.map { it.copy(source = StudyTask.SOURCE_PLAN).toEntity(now()) })
        }
        return result
    }

    /** First run: load the bundled plan (if this build has one) when no study tasks exist yet. */
    suspend fun importBundledPlanIfEmpty() {
        if (dao.studyCount() > 0) return
        val text = runCatching { context.assets.open(BUNDLED_PLAN).bufferedReader().use { it.readText() } }.getOrNull() ?: return
        runCatching { importPlan(text) }.onFailure { SafeLog.error("plan_import_failed", it) }
    }

    fun hasBundledPlan(): Boolean = runCatching { context.assets.open(BUNDLED_PLAN).close(); true }.getOrDefault(false)

    suspend fun reimportBundledPlan(): StudyPlanCsv.Result? {
        val text = runCatching { context.assets.open(BUNDLED_PLAN).bufferedReader().use { it.readText() } }.getOrNull() ?: return null
        return importPlan(text)
    }

    // ---- Focus ----------------------------------------------------------------------------------

    fun observeFocus(from: LocalDate, to: LocalDate): Flow<List<FocusSession>> =
        dao.observeFocus(from.toEpochDay(), to.toEpochDay()).map { l -> l.map { it.toDomain() } }

    suspend fun saveFocus(s: FocusSession) {
        if (s.durationSeconds < 30 && !s.completed) return // a mis-tap is not a session
        dao.insertFocus(
            WaFocusSessionEntity(
                date = s.date.toEpochDay(), durationSeconds = s.durationSeconds.coerceIn(0, 24 * 3600), completed = s.completed,
                interruptions = s.interruptions.coerceIn(0, 999), task = s.task.take(200), kind = s.kind, startedAt = s.startedAt.toEpochMilli(),
                studyTaskId = s.studyTaskId,
            ),
        )
        s.studyTaskId?.let { if (s.minutes > 0) addStudyMinutes(it, s.minutes) }
    }

    // ---- Speaking -------------------------------------------------------------------------------

    fun observeSpeaking(from: LocalDate, to: LocalDate): Flow<List<SpeakingSession>> =
        dao.observeSpeaking(from.toEpochDay(), to.toEpochDay()).map { l -> l.map { it.toDomain() } }

    fun observeRecentSpeaking(limit: Int = 8): Flow<List<SpeakingSession>> = dao.observeRecentSpeaking(limit).map { l -> l.map { it.toDomain() } }

    suspend fun topicCount(): Int = topicSource.topics().size

    /** Today's topic, chosen once per day and kept. */
    suspend fun topicFor(date: LocalDate): SpeakingSession? = speakingLock.withLock {
        dao.speakingOn(date.toEpochDay())?.let { return it.toDomain() }
        val topic = TopicPicker.pick(topicSource.topics(), dao.usedTopics(), Random(System.nanoTime())) ?: return null
        val id = dao.insertSpeaking(WaSpeakingSessionEntity(date = date.toEpochDay(), topic = topic, durationSeconds = 0, completed = false, rating = null, updatedAt = now()))
        SpeakingSession(id, date, topic)
    }

    /** A different topic, before speaking. A finished day keeps its topic. */
    suspend fun shuffleTopic(date: LocalDate): SpeakingSession? = speakingLock.withLock {
        val current = dao.speakingOn(date.toEpochDay()) ?: return@withLock null
        if (current.completed) return current.toDomain()
        val used = dao.usedTopics()
        val topic = TopicPicker.pick(topicSource.topics(), used, Random(System.nanoTime()), avoid = current.topic) ?: return current.toDomain()
        val next = current.copy(topic = topic, updatedAt = now())
        dao.updateSpeaking(next)
        next.toDomain()
    }

    suspend fun completeSpeaking(id: Long, seconds: Int) = speakingLock.withLock {
        val s = dao.speakingById(id) ?: return@withLock
        dao.updateSpeaking(s.copy(completed = true, durationSeconds = seconds.coerceIn(0, 600), updatedAt = now()))
    }

    suspend fun rateSpeaking(id: Long, rating: Int?) = speakingLock.withLock {
        val s = dao.speakingById(id) ?: return@withLock
        dao.updateSpeaking(s.copy(rating = rating?.coerceIn(1, 5), updatedAt = now()))
    }

    // ---- Days -----------------------------------------------------------------------------------

    /** Every input the habit rules need, per day, for [from]..[to]. */
    fun observeDays(from: LocalDate, to: LocalDate): Flow<Map<LocalDate, DayInputs>> = combine(
        observeLogs(from, to),
        observeReading(from, to),
        observeStudy(from, to),
        observeFocus(from, to),
        observeSpeaking(from, to),
    ) { logs, reading, study, focus, speaking ->
        val out = HashMap<LocalDate, DayInputs>()
        var d = from
        val logsByDay = logs.groupBy { it.date }
        val readByDay = reading.groupBy { it.date }
        val studyByDay = study.groupBy { it.date }
        val focusByDay = focus.groupBy { it.date }
        val spokeDays = speaking.filter { it.completed }.map { it.date }.toSet()
        while (!d.isAfter(to)) {
            val tasks = studyByDay[d].orEmpty()
            val f = focusByDay[d].orEmpty()
            out[d] = DayInputs(
                date = d,
                logs = logsByDay[d].orEmpty().associateBy { it.habitId },
                readingMinutes = readByDay[d].orEmpty().sumOf { it.minutes },
                readingPages = readByDay[d].orEmpty().sumOf { it.pages },
                studyTasksDone = tasks.count { it.completed },
                studyTasksTotal = tasks.size,
                studyMinutes = tasks.sumOf { it.spentMinutes } + f.filter { it.kind == FocusSession.KIND_STUDY && it.studyTaskId == null }.sumOf { it.minutes },
                focusMinutes = f.filter { it.kind == FocusSession.KIND_FOCUS }.sumOf { it.minutes },
                focusSessions = f.count { it.kind == FocusSession.KIND_FOCUS },
                interruptions = f.sumOf { it.interruptions },
                spoke = d in spokeDays,
            )
            d = d.plusDays(1)
        }
        out
    }

    companion object {
        /** The study plan starts here; everything before was already covered. */
        val PLAN_START: LocalDate = LocalDate.of(2026, 10, 5)
        const val BUNDLED_PLAN = "winterarc/study_plan.csv"
        const val DEFAULT_BOOK_TITLE = "Silence Your Mind"
        const val DEFAULT_BOOK_AUTHOR = "Ramesh Manocha"
    }
}

private suspend fun RoomBookRepository.observeBooksOnce(): List<Book> = observeBooks().first()

private fun WinterArcEntity.toDomain() = WinterArc(
    id = id,
    startDate = LocalDate.ofEpochDay(startDate),
    durationDays = durationDays,
    status = ArcStatus.entries.firstOrNull { it.name == status } ?: ArcStatus.ACTIVE,
    pauses = WinterArc.decodePauses(pauses),
    createdAt = Instant.ofEpochMilli(createdAt),
)

private fun WaHabitEntity.toDomain(kind: HabitKind) = Habit(kind = kind, target = target, active = active, sortOrder = sortOrder)

private fun WaHabitLogEntity.toDomain() = HabitLog(habitId, LocalDate.ofEpochDay(date), value, completed, notes, extra)

private fun WaStudyTaskEntity.toDomain() = StudyTask(
    id = id,
    date = LocalDate.ofEpochDay(date),
    title = title,
    category = StudyCategory.entries.firstOrNull { it.name == category } ?: StudyCategory.DSA,
    durationMinutes = durationMinutes,
    completed = completed,
    notes = notes,
    position = position,
    source = source,
    spentMinutes = spentMinutes,
    startTime = startMinute?.let { LocalTime.of(it / 60, it % 60) },
)

private fun StudyTask.toEntity(now: Long) = WaStudyTaskEntity(
    id = id,
    date = date.toEpochDay(),
    title = title.take(300),
    category = category.name,
    durationMinutes = durationMinutes.coerceIn(5, 12 * 60),
    completed = completed,
    notes = notes.take(4000),
    position = position,
    source = source,
    spentMinutes = spentMinutes,
    startMinute = startTime?.let { it.hour * 60 + it.minute },
    updatedAt = now,
)

private fun WaFocusSessionEntity.toDomain() = FocusSession(id, LocalDate.ofEpochDay(date), durationSeconds, completed, interruptions, task, kind, Instant.ofEpochMilli(startedAt), studyTaskId)

private fun WaSpeakingSessionEntity.toDomain() = SpeakingSession(id, LocalDate.ofEpochDay(date), topic, durationSeconds, completed, rating)
