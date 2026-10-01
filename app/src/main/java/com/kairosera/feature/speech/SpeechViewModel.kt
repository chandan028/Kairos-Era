package com.kairosera.feature.speech

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairosera.AppContainer
import com.kairosera.ai.AnalysisStep
import com.kairosera.ai.CoachError
import com.kairosera.ai.DeviceFit
import com.kairosera.ai.GemmaEngineManager
import com.kairosera.ai.LocalModelProvider
import com.kairosera.ai.ModelRequirements
import com.kairosera.ai.SpeechAiAnalyzer
import com.kairosera.ai.SpeechAnalysisResult
import com.kairosera.ai.SpeechInput
import com.kairosera.core.diagnostics.SafeLog
import com.kairosera.core.settings.SpeechCoachSettings
import com.kairosera.data.speech.SpeechSession
import com.kairosera.data.speech.SpeechSessionRepository
import com.kairosera.speech.AudioFileManager
import com.kairosera.speech.RecorderState
import com.kairosera.speech.SpeechRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Clock
import java.time.Instant

/** Everything the speech flow needs, so tests can swap the microphone and the model. */
class SpeechDeps(
    val recorder: SpeechRecorder,
    val analyzer: () -> SpeechAiAnalyzer,
    val engines: GemmaEngineManager?,
    val models: LocalModelProvider,
    val sessions: SpeechSessionRepository,
    val files: AudioFileManager,
    val settings: suspend () -> SpeechCoachSettings,
    val totalRam: () -> Long,
    /** Releasing the engine must outlive this screen's ViewModel. */
    val appScope: CoroutineScope,
    val clock: Clock = Clock.systemDefaultZone(),
    val feedbackLanguage: () -> String = { "English" },
)

/**
 * The 1 Minute Speech flow: Ready → Recording → Analyzing → Done (report) or Failed.
 * Lives as long as the Speak screen. The Gemma engine is warmed up while the person speaks,
 * reused for "Practise again", and released when the screen closes.
 */
class SpeechViewModel(private val d: SpeechDeps) : ViewModel() {
    constructor(c: AppContainer) : this(
        SpeechDeps(
            recorder = SpeechRecorder(c.audioFiles, inCall = SpeechRecorder.callChecker(c.appContext)),
            analyzer = { c.analyzer() },
            engines = c.gemma,
            models = c.modelStore,
            sessions = c.speechSessions,
            files = c.audioFiles,
            settings = { c.speechPrefs.current() },
            totalRam = { c.deviceInfo.totalRamBytes() },
            appScope = c.appScope,
            feedbackLanguage = {
                val tag = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales().toLanguageTags()
                if (tag.startsWith("kn")) "Kannada" else "English"
            },
        ),
    )

    private val _state = MutableStateFlow(SpeechUiState())
    val state: StateFlow<SpeechUiState> = _state.asStateFlow()

    private var topic: String = ""
    private var speakingId: Long? = null
    private var lastRecording: Pair<File, ShortArray>? = null
    private var lastSeconds = 0
    private var work: Job? = null

    /** Seconds actually recorded, for the habit (the old timer counted 45 s as done). */
    private val _completed = MutableStateFlow<Pair<Long, Int>?>(null)
    val completedSpeaking: StateFlow<Pair<Long, Int>?> = _completed.asStateFlow()

    init {
        refreshCoach()
        viewModelScope.launch { d.recorder.state.collect(::onRecorder) }
        // Leftovers from a crash or a killed process mid-analysis.
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) { runCatching { d.files.clearWork() } }
    }

    fun refreshCoach() {
        viewModelScope.launch {
            val available = runCatching { d.models.getGemmaModelPath() != null }.getOrDefault(false)
            val fit = ModelRequirements.fit(d.totalRam())
            _state.update { it.copy(coachAvailable = available && fit != DeviceFit.TOO_SMALL, deviceFit = fit) }
        }
    }

    /** Starts recording the speech for [topic]. The screen has already checked the microphone permission. */
    fun start(topic: String, speakingSessionId: Long?) {
        if (d.recorder.isRecording || _state.value.phase is SpeechPhase.Analyzing) return
        this.topic = topic
        speakingId = speakingSessionId
        discardLast()
        if (!d.recorder.start(viewModelScope)) return
        _state.update { it.copy(phase = SpeechPhase.Recording(0, d.recorder.maxSeconds)) }
        // Load Gemma while the person speaks, so feedback comes sooner. Errors show after recording.
        d.engines?.let { e -> viewModelScope.launch { e.warmUp() } }
    }

    fun stop() = d.recorder.stop()

    fun discard() {
        d.recorder.cancel()
        discardLast()
        _state.update { it.copy(phase = SpeechPhase.Ready) }
    }

    /** The app left the foreground: a recording cannot continue unseen. Analysis carries on. */
    fun onBackground() {
        if (d.recorder.isRecording) d.recorder.interrupt()
    }

    fun permissionDenied() = _state.update { it.copy(phase = SpeechPhase.Failed(CoachError.MIC_PERMISSION, canRetry = false)) }

    fun cancelAnalysis() {
        work?.cancel()
        discardLast()
        _state.update { it.copy(phase = SpeechPhase.Ready) }
    }

    fun retryAnalysis() {
        val (file, pcm) = lastRecording ?: return
        analyze(file, pcm, lastSeconds)
    }

    /** Back to the start card (after an error or after opening the report). */
    fun reset() {
        if (_state.value.phase is SpeechPhase.Recording || _state.value.phase is SpeechPhase.Analyzing) return
        d.recorder.reset()
        _state.update { it.copy(phase = SpeechPhase.Ready) }
    }

    fun habitRecorded() { _completed.value = null }

    private fun onRecorder(r: RecorderState) {
        when (r) {
            is RecorderState.Recording -> _state.update { it.copy(phase = SpeechPhase.Recording(r.elapsedSeconds, d.recorder.maxSeconds)) }
            is RecorderState.Failed -> {
                _state.update { it.copy(phase = SpeechPhase.Failed(r.error, canRetry = false)) }
            }
            is RecorderState.Cancelled -> _state.update { it.copy(phase = SpeechPhase.Ready) }
            is RecorderState.Finished -> {
                d.recorder.reset()
                // Speaking most of the minute completes the day's habit, with or without feedback.
                if (r.seconds >= HABIT_SECONDS) speakingId?.let { _completed.value = it to r.seconds }
                if (r.seconds < MIN_SECONDS) {
                    d.files.delete(r.file)
                    _state.update { it.copy(phase = SpeechPhase.Failed(CoachError.TOO_SHORT, canRetry = false)) }
                } else {
                    analyze(r.file, r.pcm, r.seconds)
                }
            }
            RecorderState.Idle -> Unit
        }
    }

    private fun analyze(file: File, pcm: ShortArray, seconds: Int) {
        lastRecording = file to pcm
        lastSeconds = seconds
        work?.cancel()
        _state.update { it.copy(phase = SpeechPhase.Analyzing(AnalysisStep.LoadingModel)) }
        work = viewModelScope.launch {
            val result = runCatching {
                d.analyzer().analyze(SpeechInput(pcm, seconds, topic, d.feedbackLanguage())) { step ->
                    _state.update { s -> if (s.phase is SpeechPhase.Analyzing) s.copy(phase = SpeechPhase.Analyzing(step)) else s }
                }
            }.getOrElse { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                SafeLog.error("speech_analyze_crashed", e)
                SpeechAnalysisResult.Failure(CoachError.INFERENCE_FAILED)
            }
            when (result) {
                is SpeechAnalysisResult.Failure -> {
                    val retry = result.error !in setOf(CoachError.NO_SPEECH, CoachError.TOO_SHORT)
                    if (!retry) discardLast()
                    _state.update { it.copy(phase = SpeechPhase.Failed(result.error, canRetry = retry)) }
                    if (result.error == CoachError.MODEL_MISSING || result.error == CoachError.MODEL_MALFORMED) refreshCoach()
                }
                is SpeechAnalysisResult.Success -> {
                    val id = withContext(NonCancellable) { save(result, seconds, file) }
                    if (id == null) {
                        _state.update { it.copy(phase = SpeechPhase.Failed(CoachError.LOW_STORAGE, canRetry = true)) }
                    } else {
                        _state.update { it.copy(phase = SpeechPhase.Done(id)) }
                    }
                }
            }
        }
    }

    private suspend fun save(r: SpeechAnalysisResult.Success, seconds: Int, file: File): Long? = runCatching {
        val f = r.feedback
        val now = Instant.now(d.clock)
        val id = d.sessions.save(
            SpeechSession(
                date = now.atZone(d.clock.zone).toLocalDate(),
                createdAt = now,
                topic = topic,
                speakingSessionId = speakingId,
                durationSeconds = seconds,
                overallScore = f.displayScore,
                modelOverallScore = f.overallScore,
                clarityScore = f.clarityScore,
                structureScore = f.structureScore,
                vocabularyScore = f.vocabularyScore,
                grammarScore = f.grammarScore,
                concisenessScore = f.concisenessScore,
                fillerWords = f.fillerWords,
                strengths = f.strengths,
                improvements = f.improvements,
                nextExercise = f.nextExercise.orEmpty(),
                summary = f.summary.orEmpty(),
                transcript = r.transcript,
                wordsPerMinute = r.stats.wordsPerMinute,
                fillerSounds = r.stats.fillerSounds,
                longPauses = r.stats.audio.longPauses,
                voicedSeconds = r.stats.audio.voicedSeconds,
                backend = r.backend.name,
            ),
        )
        if (d.settings().keepRecordings) {
            d.files.keep(file, id)?.let { d.sessions.setAudioPath(id, it.absolutePath) }
        } else {
            d.files.delete(file)
        }
        lastRecording = null
        id
    }.onFailure { SafeLog.error("speech_save_failed", it) }.getOrNull()

    private fun discardLast() {
        lastRecording?.first?.let { d.files.delete(it) }
        lastRecording = null
    }

    override fun onCleared() {
        d.recorder.cancel()
        discardLast()
        // The engine holds gigabytes; free it once nobody is on the speaking screen.
        d.engines?.let { e -> d.appScope.launch { e.release() } }
    }

    companion object {
        const val MIN_SECONDS = 10
        const val HABIT_SECONDS = 45
    }
}

/** The report and progress screens only read saved sessions. */
class SpeechReportViewModel(private val sessions: SpeechSessionRepository) : ViewModel() {
    fun session(id: Long): Flow<SpeechSession?> = sessions.observe(id)
    val all: Flow<List<SpeechSession>> = sessions.observeAll()
    fun delete(id: Long) { viewModelScope.launch { runCatching { sessions.get(id)?.audioPath?.let { File(it).delete() }; sessions.delete(id) } } }
    suspend fun latestId(): Long? = runCatching { sessions.observeAll().first().firstOrNull()?.id }.getOrNull()
}
