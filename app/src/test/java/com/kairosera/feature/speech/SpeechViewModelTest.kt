package com.kairosera.feature.speech

import com.kairosera.ai.AiBackend
import com.kairosera.ai.AnalysisStep
import com.kairosera.ai.CoachError
import com.kairosera.ai.CoachFakes
import com.kairosera.ai.DeviceFit
import com.kairosera.ai.GemmaResponseParser
import com.kairosera.ai.ParseResult
import com.kairosera.ai.SpeechAiAnalyzer
import com.kairosera.ai.SpeechAnalysisResult
import com.kairosera.ai.SpeechInput
import com.kairosera.ai.SpeechStats
import com.kairosera.core.settings.SpeechCoachSettings
import com.kairosera.data.speech.SpeechSession
import com.kairosera.data.speech.SpeechSessionRepository
import com.kairosera.speech.AudioFileManager
import com.kairosera.speech.AudioMetrics
import com.kairosera.speech.PcmSource
import com.kairosera.speech.SpeechRecorder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Stands in for Gemma: returns a fixed result, or waits until the test lets it finish. */
class FakeSpeechAnalyzer(var result: SpeechAnalysisResult) : SpeechAiAnalyzer {
    var gate: CompletableDeferred<Unit>? = null
    val inputs = mutableListOf<SpeechInput>()
    override suspend fun analyze(input: SpeechInput, onStep: (AnalysisStep) -> Unit): SpeechAnalysisResult {
        inputs += input
        onStep(AnalysisStep.Listening(1, 2))
        gate?.await()
        return result
    }

    companion object {
        fun success(): SpeechAnalysisResult.Success {
            val f = (GemmaResponseParser.parse(CoachFakes.GOOD_JSON) as ParseResult.Success).feedback
            return SpeechAnalysisResult.Success(f, "So um today…", SpeechStats(AudioMetrics(60.0, 50.0, 2, 2.0), 130, 130, 1), AiBackend.GPU)
        }
    }
}

class InMemorySpeechSessions : SpeechSessionRepository {
    val rows = MutableStateFlow<List<SpeechSession>>(emptyList())
    override suspend fun save(session: SpeechSession): Long {
        val id = (rows.value.maxOfOrNull { it.id } ?: 0) + 1
        rows.value = listOf(session.copy(id = id)) + rows.value
        return id
    }
    override fun observeAll(): Flow<List<SpeechSession>> = rows
    override fun observe(id: Long): Flow<SpeechSession?> = rows.map { l -> l.firstOrNull { it.id == id } }
    override suspend fun get(id: Long) = rows.value.firstOrNull { it.id == id }
    override suspend fun setAudioPath(id: Long, path: String?) { rows.value = rows.value.map { if (it.id == id) it.copy(audioPath = path) else it } }
    override suspend fun delete(id: Long) { rows.value = rows.value.filterNot { it.id == id } }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SpeechViewModelTest {
    @get:Rule val tmp = TemporaryFolder()

    @Before fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun reset() = Dispatchers.resetMain()

    /** A microphone that delivers [seconds] of audio at once, then real-time-ish 100 ms reads. */
    private class Mic(private val seconds: Int) : PcmSource {
        private var given = 0
        override fun start() {}
        override fun read(buffer: ShortArray): Int {
            if (given >= seconds * 16_000) { Thread.sleep(5); return buffer.size.also { given += it } }
            given += buffer.size
            for (i in buffer.indices) buffer[i] = ((i % 40) * 400 - 8000).toShort()
            return buffer.size
        }
        override fun close() {}
    }

    private val sessions = InMemorySpeechSessions()
    private lateinit var files: AudioFileManager
    private var keep = false

    private fun vm(analyzer: SpeechAiAnalyzer, modelPresent: Boolean = true, ram: Long = 8 * CoachFakes.GB, maxSeconds: Int = 60, fastSeconds: Int = maxSeconds): SpeechViewModel {
        files = AudioFileManager(tmp.newFolder(), tmp.newFolder())
        val recorder = SpeechRecorder(files, { Mic(fastSeconds) }, maxSeconds = maxSeconds, io = Dispatchers.Default)
        return SpeechViewModel(
            SpeechDeps(
                recorder = recorder,
                analyzer = { analyzer },
                engines = null,
                models = CoachFakes.provider { if (modelPresent) "/models/gemma-4-E2B-it.litertlm" else null },
                sessions = sessions,
                files = files,
                settings = { SpeechCoachSettings(keepRecordings = keep) },
                totalRam = { ram },
                appScope = CoroutineScope(SupervisorJob()),
            ),
        )
    }

    private suspend fun SpeechViewModel.await(pred: (SpeechPhase) -> Boolean): SpeechPhase =
        withTimeout(20_000) { state.first { pred(it.phase) } }.phase

    @Test fun readyShowsWhetherTheCoachCanRun() {
        assertTrue(vm(FakeSpeechAnalyzer(FakeSpeechAnalyzer.success())).state.value.coachAvailable)
        assertEquals(false, vm(FakeSpeechAnalyzer(FakeSpeechAnalyzer.success()), modelPresent = false).state.value.coachAvailable)
        val small = vm(FakeSpeechAnalyzer(FakeSpeechAnalyzer.success()), ram = 4 * CoachFakes.GB).state.value
        assertEquals(false, small.coachAvailable)
        assertEquals(DeviceFit.TOO_SMALL, small.deviceFit)
    }

    @Test fun fullMinuteGoesRecordingAnalyzingDoneAndIsSaved() = runBlocking {
        val analyzer = FakeSpeechAnalyzer(FakeSpeechAnalyzer.success()).apply { gate = CompletableDeferred() }
        val vm = vm(analyzer)
        vm.start("Mornings", speakingSessionId = 42)
        assertTrue(vm.state.value.phase is SpeechPhase.Recording)
        assertEquals(AnalysisStep.Listening(1, 2), (vm.await { it is SpeechPhase.Analyzing && it.step is AnalysisStep.Listening } as SpeechPhase.Analyzing).step)
        analyzer.gate!!.complete(Unit)
        val done = vm.await { it is SpeechPhase.Done } as SpeechPhase.Done
        val saved = sessions.get(done.sessionId)!!
        assertEquals("Mornings", saved.topic)
        assertEquals(42L, saved.speakingSessionId)
        assertEquals(60, saved.durationSeconds)
        assertEquals(7.0, saved.overallScore!!, 0.0)
        assertEquals("GPU", saved.backend)
        assertEquals("Mornings", analyzer.inputs.single().topic)
        assertEquals(42L to 60, vm.completedSpeaking.value)
        assertTrue("recording deleted when not kept", files.workDir.listFiles().isNullOrEmpty())
    }

    @Test fun keptRecordingIsLinkedToTheReport() = runBlocking {
        keep = true
        val vm = vm(FakeSpeechAnalyzer(FakeSpeechAnalyzer.success()), maxSeconds = 12)
        vm.start("", null)
        val done = vm.await { it is SpeechPhase.Done } as SpeechPhase.Done
        assertTrue(File(sessions.get(done.sessionId)!!.audioPath!!).isFile)
    }

    @Test fun failureCanBeRetriedWithoutRecordingAgain() = runBlocking {
        val analyzer = FakeSpeechAnalyzer(SpeechAnalysisResult.Failure(CoachError.BAD_RESPONSE))
        val vm = vm(analyzer, maxSeconds = 12)
        vm.start("", null)
        assertEquals(SpeechPhase.Failed(CoachError.BAD_RESPONSE, canRetry = true), vm.await { it is SpeechPhase.Failed })
        analyzer.result = FakeSpeechAnalyzer.success()
        vm.retryAnalysis()
        vm.await { it is SpeechPhase.Done }
        assertEquals(2, analyzer.inputs.size)
    }

    @Test fun noSpeechCannotBeRetried() = runBlocking {
        val vm = vm(FakeSpeechAnalyzer(SpeechAnalysisResult.Failure(CoachError.NO_SPEECH)), maxSeconds = 12)
        vm.start("", null)
        assertEquals(SpeechPhase.Failed(CoachError.NO_SPEECH, canRetry = false), vm.await { it is SpeechPhase.Failed })
        assertTrue(sessions.rows.value.isEmpty())
    }

    @Test fun underTenSecondsIsTooShort() = runBlocking {
        val analyzer = FakeSpeechAnalyzer(FakeSpeechAnalyzer.success())
        val vm = vm(analyzer, maxSeconds = 5)
        vm.start("", null)
        assertEquals(SpeechPhase.Failed(CoachError.TOO_SHORT, canRetry = false), vm.await { it is SpeechPhase.Failed })
        assertTrue(analyzer.inputs.isEmpty())
    }

    @Test fun cancellingAnalysisReturnsToReadyAndSavesNothing() = runBlocking {
        val analyzer = FakeSpeechAnalyzer(FakeSpeechAnalyzer.success()).apply { gate = CompletableDeferred() }
        val vm = vm(analyzer, maxSeconds = 12)
        vm.start("", null)
        vm.await { it is SpeechPhase.Analyzing }
        vm.cancelAnalysis()
        assertEquals(SpeechPhase.Ready, vm.state.value.phase)
        analyzer.gate!!.complete(Unit)
        assertTrue(sessions.rows.value.isEmpty())
    }

    @Test fun leavingTheAppMidRecordingIsAnInterruption() = runBlocking {
        val vm = vm(FakeSpeechAnalyzer(FakeSpeechAnalyzer.success()), maxSeconds = 60, fastSeconds = 0)
        vm.start("", null)
        vm.onBackground()
        assertEquals(SpeechPhase.Failed(CoachError.INTERRUPTED, canRetry = false), vm.await { it is SpeechPhase.Failed })
    }

    @Test fun deniedPermissionIsShown() {
        val vm = vm(FakeSpeechAnalyzer(FakeSpeechAnalyzer.success()))
        vm.permissionDenied()
        assertEquals(SpeechPhase.Failed(CoachError.MIC_PERMISSION, canRetry = false), vm.state.value.phase)
        vm.reset()
        assertEquals(SpeechPhase.Ready, vm.state.value.phase)
    }
}
