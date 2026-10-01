package com.kairosera.ai

import android.util.Log
import com.kairosera.BuildConfig
import com.kairosera.core.diagnostics.SafeLog
import com.kairosera.speech.AudioFileManager
import com.kairosera.speech.AudioMetrics
import com.kairosera.speech.SpeechMetrics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.Locale

/** What the person said and how long for. */
data class SpeechInput(
    val pcm: ShortArray,
    val durationSeconds: Int,
    val topic: String,
    /** Language for the written feedback ("English", "Kannada"). Scores are the same either way. */
    val feedbackLanguage: String = "English",
)

/** Measured, not guessed: the numbers the report shows under Speech Stats. */
data class SpeechStats(
    val audio: AudioMetrics,
    val wordCount: Int,
    val wordsPerMinute: Int?,
    /** Hesitation sounds found in the transcript; null when there is no transcript. */
    val fillerSounds: Int?,
)

sealed interface SpeechAnalysisResult {
    data class Success(
        val feedback: SpeechFeedback,
        val transcript: String,
        val stats: SpeechStats,
        val backend: AiBackend,
    ) : SpeechAnalysisResult

    data class Failure(val error: CoachError) : SpeechAnalysisResult
}

/** Where analysis is, for the "Analyzing speech" screen. */
sealed interface AnalysisStep {
    data object LoadingModel : AnalysisStep
    data class Listening(val part: Int, val parts: Int) : AnalysisStep
    data object WritingFeedback : AnalysisStep
}

/** The coach. Production: [GemmaSpeechAnalyzer]. Tests: a fake that returns fixed feedback. */
interface SpeechAiAnalyzer {
    suspend fun analyze(input: SpeechInput, onStep: (AnalysisStep) -> Unit = {}): SpeechAnalysisResult
}

/**
 * Sends the recording to Gemma-4-E2B-it on the phone, in two steps:
 *
 * 1. Listening: each 30-second part of the recording goes to Gemma as a WAV file
 *    (Content.AudioFile). Gemma 4 accepts at most 30 seconds of audio per clip, which is why a
 *    one-minute speech is split. Gemma writes down exactly what was said, hesitations included.
 * 2. Coaching: Gemma reads the full transcript, the topic and the timing facts the app measured
 *    from the audio (length, voiced time, long pauses, words per minute) and returns the JSON
 *    feedback.
 *
 * Nothing leaves the phone. No step uses a network or a cloud speech service.
 */
class GemmaSpeechAnalyzer(
    private val engines: GemmaEngineManager,
    private val files: AudioFileManager,
    private val listenTimeoutMs: Long = 180_000,
    private val coachTimeoutMs: Long = 240_000,
) : SpeechAiAnalyzer {

    override suspend fun analyze(input: SpeechInput, onStep: (AnalysisStep) -> Unit): SpeechAnalysisResult {
        val audio = SpeechMetrics.analyze(input.pcm)
        if (audio.voicedSeconds < SpeechMetrics.MIN_VOICED_SECONDS) return SpeechAnalysisResult.Failure(CoachError.NO_SPEECH)
        val parts = runCatching { files.splitForModel(input.pcm) }.getOrElse {
            SafeLog.error("speech_split_failed", it)
            return SpeechAnalysisResult.Failure(CoachError.RECORDING_FAILED)
        }
        onStep(AnalysisStep.LoadingModel)
        return try {
            engines.withEngine { engine ->
                val transcript = parts.mapIndexed { i, part ->
                    onStep(AnalysisStep.Listening(i + 1, parts.size))
                    val raw = withTimeout(listenTimeoutMs) { engine.generate(SpeechPrompts.LISTEN_SYSTEM, part, SpeechPrompts.listenPrompt(i + 1, parts.size)) }
                    SpeechPrompts.cleanTranscript(raw)
                }.filter { it.isNotEmpty() }.joinToString(" ")
                debugLog("transcript", transcript)
                if (transcript.isBlank()) throw CoachException(CoachError.NO_SPEECH)

                val words = SpeechMetrics.wordCount(transcript)
                val stats = SpeechStats(audio, words, SpeechMetrics.wordsPerMinute(words, audio.durationSeconds), SpeechMetrics.fillerSounds(transcript))
                onStep(AnalysisStep.WritingFeedback)
                val prompt = SpeechPrompts.coachPrompt(input.topic, stats, transcript, input.feedbackLanguage)
                var parsed = ask(engine, prompt)
                if (parsed !is ParseResult.Success) {
                    // One more try with a firmer reminder; small models usually comply the second time.
                    parsed = ask(engine, prompt + "\n\n" + SpeechPrompts.RETRY_SUFFIX)
                }
                when (parsed) {
                    is ParseResult.Success -> SpeechAnalysisResult.Success(parsed.feedback, transcript, stats, engine.backend)
                    is ParseResult.Failure -> {
                        SafeLog.event("gemma_bad_json", "reason" to parsed.reason)
                        throw CoachException(CoachError.BAD_RESPONSE)
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            SpeechAnalysisResult.Failure(CoachError.TIMEOUT)
        } catch (e: CancellationException) {
            throw e
        } catch (e: CoachException) {
            SpeechAnalysisResult.Failure(e.error)
        } catch (e: OutOfMemoryError) {
            SafeLog.error("gemma_oom", e)
            SpeechAnalysisResult.Failure(CoachError.LOW_MEMORY)
        } catch (e: Throwable) {
            SafeLog.error("gemma_inference_failed", e)
            SpeechAnalysisResult.Failure(CoachError.INFERENCE_FAILED)
        } finally {
            parts.forEach { it.delete() }
        }
    }

    private suspend fun ask(engine: LlmEngine, prompt: String): ParseResult {
        val raw = withTimeout(coachTimeoutMs) { engine.generate(SpeechPrompts.COACH_SYSTEM, null, prompt) }
        debugLog("coach_raw", raw)
        return GemmaResponseParser.parse(raw)
    }

    /** Raw model output can contain what the person said, so it is logged in debug builds only. */
    private fun debugLog(what: String, text: String) {
        if (BuildConfig.DEBUG) runCatching { Log.d("KairosCoach", "$what: $text") }
    }
}

/** Every word Gemma is told. Kept together so the coach's behaviour is easy to review. */
object SpeechPrompts {
    val LISTEN_SYSTEM = """
        You are a careful transcriber. You write down exactly what a speaker says in an audio clip.
    """.trimIndent()

    fun listenPrompt(part: Int, parts: Int): String = """
        This is part $part of $parts of a one-minute practice speech.
        Transcribe the speech word for word, in the language spoken.
        Keep hesitation sounds such as "um", "uh", "er" and repeated words exactly where they occur.
        Do not correct grammar, do not summarise, and do not add comments, labels or quotation marks.
        If nobody is speaking, reply with exactly: [no speech]
    """.trimIndent()

    fun cleanTranscript(raw: String): String {
        val t = raw.trim()
            .removePrefix("```").removeSuffix("```").trim()
            .replace(Regex("^(transcript(ion)?|part \\d+)\\s*:\\s*", RegexOption.IGNORE_CASE), "")
            .trim().trim('"', '“', '”').trim()
        return if (t.equals("[no speech]", ignoreCase = true) || t.isEmpty()) "" else t
    }

    val COACH_SYSTEM = """
        You are a speaking coach.

        Analyze the user's 60-second speech.

        Evaluate:
        1. clarity
        2. organization/structure
        3. vocabulary
        4. grammar
        5. conciseness
        6. filler words if detectable
        7. pacing/pauses if detectable from the audio
        8. strengths
        9. areas to improve
        10. one specific exercise for the next attempt

        Do not judge the person's accent or regional pronunciation negatively.
        Do not evaluate appearance.
        Do not make assumptions about intelligence or personality.
        Do not score confidence or any quality you cannot observe in the speech itself.

        Only evaluate observable speech/content characteristics.

        You get a transcript you made from the speaker's audio, so a few words may be misheard; do not
        punish likely recognition mistakes. The timing facts were measured by the app from the audio;
        use them for pacing and pauses and do not invent other numbers.

        Return ONLY valid JSON with exactly these keys:
        {
          "overall_score": 0,
          "clarity_score": 0,
          "structure_score": 0,
          "vocabulary_score": 0,
          "grammar_score": 0,
          "conciseness_score": 0,
          "filler_words": [],
          "strengths": [],
          "improvements": [],
          "next_exercise": "",
          "summary": ""
        }

        Rules:
        - Scores are integers from 1 to 10.
        - If something cannot reliably be determined, use the string "not_available" instead of inventing it.
        - filler_words lists the filler words you can actually see in the transcript (for example "um", "basically"); never give a count. Use "not_available" if you cannot tell.
        - strengths and improvements: 2 to 4 short, specific sentences each, referring to what the speaker actually said.
        - next_exercise: one concrete exercise for the next one-minute attempt.
        - summary: one or two encouraging, honest sentences.
        - No markdown, no text before or after the JSON.
    """.trimIndent()

    fun coachPrompt(topic: String, stats: SpeechStats, transcript: String, language: String): String {
        val a = stats.audio
        fun s(x: Double) = String.format(Locale.ROOT, "%.1f", x)
        return """
            Topic the speaker was given: "$topic"

            Timing facts measured from the audio:
            - Recording length: ${s(a.durationSeconds)} seconds
            - Time with voice: ${s(a.voicedSeconds)} seconds
            - Pauses of ${s(SpeechMetrics.LONG_PAUSE_SECONDS)} seconds or longer: ${a.longPauses} (longest ${s(a.longestPauseSeconds)} seconds)
            - Words: ${stats.wordCount}${stats.wordsPerMinute?.let { " ($it words per minute)" } ?: ""}

            Transcript:
            $transcript

            Write every text value in $language. Keep the JSON keys in English.
        """.trimIndent()
    }

    const val RETRY_SUFFIX = "Your last reply could not be read. Reply with the JSON object only, starting with { and ending with }."
}
