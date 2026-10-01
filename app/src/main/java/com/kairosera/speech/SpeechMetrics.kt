package com.kairosera.speech

import kotlin.math.log10
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Facts measured from the audio signal itself, not guessed by the model. */
data class AudioMetrics(
    val durationSeconds: Double,
    /** Time with voice-level sound in it. */
    val voicedSeconds: Double,
    /** Silences of 1.5 s or more between stretches of speech. */
    val longPauses: Int,
    val longestPauseSeconds: Double,
)

enum class Pace { SLOW, COMFORTABLE, FAST }

/**
 * Plain signal and text measurements for the report. Pauses come from loudness in 50 ms frames
 * against the recording's own noise floor; pace and filler sounds come from Gemma's transcript.
 */
object SpeechMetrics {
    private const val FRAME_MS = 50
    const val LONG_PAUSE_SECONDS = 1.5

    fun analyze(pcm: ShortArray, sampleRate: Int = Wav.SAMPLE_RATE): AudioMetrics {
        val frame = sampleRate * FRAME_MS / 1000
        val frames = pcm.size / frame
        val duration = pcm.size / sampleRate.toDouble()
        if (frames == 0) return AudioMetrics(duration, 0.0, 0, 0.0)
        val db = DoubleArray(frames) { f ->
            var sum = 0.0
            for (i in f * frame until (f + 1) * frame) { val s = pcm[i] / 32768.0; sum += s * s }
            20 * log10(max(sqrt(sum / frame), 1e-6))
        }
        val floor = db.sorted()[(frames * 0.1).toInt().coerceAtMost(frames - 1)]
        // Voice is clearly above the room's own noise, and never quieter than -50 dBFS.
        val threshold = max(floor + 12.0, -50.0)
        val voiced = BooleanArray(frames) { db[it] > threshold }

        val voicedFrames = voiced.count { it }
        var pauses = 0
        var longest = 0
        val first = voiced.indexOfFirst { it }
        val last = voiced.indexOfLast { it }
        if (first >= 0) {
            var run = 0
            for (i in first..last) {
                if (voiced[i]) {
                    if (run * FRAME_MS / 1000.0 >= LONG_PAUSE_SECONDS) pauses++
                    longest = max(longest, run)
                    run = 0
                } else run++
            }
        }
        return AudioMetrics(
            durationSeconds = duration,
            voicedSeconds = voicedFrames * FRAME_MS / 1000.0,
            longPauses = pauses,
            longestPauseSeconds = longest * FRAME_MS / 1000.0,
        )
    }

    /** Less than this much voice means nobody really spoke. */
    const val MIN_VOICED_SECONDS = 2.0

    fun wordCount(transcript: String): Int = WORD.findAll(transcript).count()

    fun wordsPerMinute(words: Int, durationSeconds: Double): Int? =
        if (words <= 0 || durationSeconds < 5) null else (words / (durationSeconds / 60.0)).roundToInt()

    /** Typical conversational English is roughly 120 to 160 words a minute. */
    fun pace(wpm: Int?): Pace? = when {
        wpm == null -> null
        wpm < 110 -> Pace.SLOW
        wpm > 170 -> Pace.FAST
        else -> Pace.COMFORTABLE
    }

    /**
     * Hesitation sounds ("um", "uh", "er", "hmm") as written in the transcript. Only these are
     * counted: words like "like" or "basically" are often meaningful, so the app does not count
     * them. Speech recognition can drop hesitations, so this is a floor, not an exact count.
     */
    fun fillerSounds(transcript: String): Int = FILLER.findAll(transcript.lowercase()).count()

    private val WORD = Regex("[\\p{L}\\p{N}']+")
    private val FILLER = Regex("(?<![\\p{L}])(um+|uh+m*|erm+|er+|ah+m*|hmm+|mm+)(?![\\p{L}])")
}
