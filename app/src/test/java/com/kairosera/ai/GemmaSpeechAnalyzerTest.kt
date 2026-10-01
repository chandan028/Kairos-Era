package com.kairosera.ai

import com.kairosera.speech.AudioFileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GemmaSpeechAnalyzerTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun analyzer(engine: (AiBackend) -> LlmEngine, coachTimeout: Long = 240_000): Pair<GemmaSpeechAnalyzer, AudioFileManager> {
        val files = AudioFileManager(tmp.newFolder("cache"), tmp.newFolder("files"))
        val model = CoachFakes.fakeModel(tmp.newFolder("models"))
        val m = GemmaEngineManager(CoachFakes.provider { model.absolutePath }, { _, b -> engine(b) }, CoachFakes.device(), { AiBackend.CPU }, io = Dispatchers.Unconfined)
        return GemmaSpeechAnalyzer(m, files, coachTimeoutMs = coachTimeout) to files
    }

    @Test fun oneMinuteIsSentAsTwoThirtySecondClipsThenCoached() = runBlocking {
        val engine = FakeEngine(AiBackend.CPU)
        val (a, files) = analyzer({ engine })
        val steps = mutableListOf<AnalysisStep>()
        val r = a.analyze(SpeechInput(CoachFakes.speechPcm(60), 60, "Mornings", "English")) { steps += it }
        assertTrue("$r", r is SpeechAnalysisResult.Success)
        r as SpeechAnalysisResult.Success
        assertEquals(2, engine.audioCalls.size)
        assertEquals(1, engine.textCalls)
        assertEquals(7, r.feedback.overallScore)
        assertEquals(AiBackend.CPU, r.backend)
        assertEquals(2, r.stats.fillerSounds) // "um" in each part's transcript
        assertTrue(r.transcript.startsWith("So um today"))
        assertEquals(listOf(AnalysisStep.LoadingModel, AnalysisStep.Listening(1, 2), AnalysisStep.Listening(2, 2), AnalysisStep.WritingFeedback), steps)
        assertTrue("clips are deleted", engine.audioCalls.none { it.exists() })
        assertTrue(files.workDir.listFiles().isNullOrEmpty())
    }

    @Test fun silenceNeverReachesTheModel() = runBlocking {
        val engine = FakeEngine(AiBackend.CPU)
        val (a, _) = analyzer({ engine })
        val r = a.analyze(SpeechInput(ShortArray(16_000 * 30), 30, "", "English"))
        assertEquals(SpeechAnalysisResult.Failure(CoachError.NO_SPEECH), r)
        assertTrue(engine.audioCalls.isEmpty())
    }

    @Test fun modelHearingNothingIsNoSpeech() = runBlocking {
        val (a, _) = analyzer({ FakeEngine(it, transcript = { "[no speech]" }) })
        assertEquals(SpeechAnalysisResult.Failure(CoachError.NO_SPEECH), a.analyze(SpeechInput(CoachFakes.speechPcm(20), 20, "", "English")))
    }

    @Test fun badJsonIsRetriedOnce() = runBlocking {
        val engine = FakeEngine(AiBackend.CPU, replies = ArrayDeque(listOf("Sure! Your speech was nice.", CoachFakes.GOOD_JSON)))
        val (a, _) = analyzer({ engine })
        assertTrue(a.analyze(SpeechInput(CoachFakes.speechPcm(20), 20, "", "English")) is SpeechAnalysisResult.Success)
        assertEquals(2, engine.textCalls)
    }

    @Test fun badJsonTwiceIsABadResponse() = runBlocking {
        val engine = FakeEngine(AiBackend.CPU, replies = ArrayDeque(listOf("nope", "{\"overall_score\": 7")))
        val (a, _) = analyzer({ engine })
        assertEquals(SpeechAnalysisResult.Failure(CoachError.BAD_RESPONSE), a.analyze(SpeechInput(CoachFakes.speechPcm(20), 20, "", "English")))
    }

    @Test fun slowModelTimesOut() = runBlocking {
        val slow = object : LlmEngine {
            override val backend = AiBackend.CPU
            override suspend fun generate(system: String, audio: java.io.File?, prompt: String): String {
                if (audio == null) delay(10_000)
                return "words here"
            }
            override fun close() {}
        }
        val (a, _) = analyzer({ slow }, coachTimeout = 50)
        assertEquals(SpeechAnalysisResult.Failure(CoachError.TIMEOUT), a.analyze(SpeechInput(CoachFakes.speechPcm(20), 20, "", "English")))
    }

    @Test fun engineCrashIsReportedNotThrown() = runBlocking {
        val (a, _) = analyzer({ FakeEngine(it, failWith = RuntimeException("native")) })
        assertEquals(SpeechAnalysisResult.Failure(CoachError.INFERENCE_FAILED), a.analyze(SpeechInput(CoachFakes.speechPcm(20), 20, "", "English")))
    }

    @Test fun missingModelIsReported() = runBlocking {
        val files = AudioFileManager(tmp.newFolder("c2"), tmp.newFolder("f2"))
        val m = GemmaEngineManager(CoachFakes.provider { null }, { _, b -> FakeEngine(b) }, CoachFakes.device(), { AiBackend.GPU })
        assertEquals(SpeechAnalysisResult.Failure(CoachError.MODEL_MISSING), GemmaSpeechAnalyzer(m, files).analyze(SpeechInput(CoachFakes.speechPcm(20), 20, "", "English")))
    }
}
