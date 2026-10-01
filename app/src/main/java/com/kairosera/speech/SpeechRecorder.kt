package com.kairosera.speech

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import com.kairosera.ai.CoachError
import com.kairosera.core.diagnostics.SafeLog
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/** A source of 16 kHz mono PCM. The microphone in the app, a fake in tests. */
interface PcmSource : AutoCloseable {
    /** Starts capture. Throws if the microphone cannot be opened. */
    fun start()

    /** Fills [buffer]; returns samples read, or a negative AudioRecord error code. Blocks like AudioRecord.read. */
    fun read(buffer: ShortArray): Int
}

/** The phone's microphone through AudioRecord, already in Gemma's format: 16 kHz, mono, 16-bit. */
class MicPcmSource : PcmSource {
    private var record: AudioRecord? = null

    @SuppressLint("MissingPermission") // The screen asks for RECORD_AUDIO before starting.
    override fun start() {
        val min = AudioRecord.getMinBufferSize(Wav.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (min <= 0) throw IllegalStateException("no_mic_config")
        val r = AudioRecord(MediaRecorder.AudioSource.MIC, Wav.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, Wav.SAMPLE_RATE))
        if (r.state != AudioRecord.STATE_INITIALIZED) {
            r.release()
            throw IllegalStateException("mic_not_initialized")
        }
        r.startRecording()
        if (r.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            r.release()
            throw IllegalStateException("mic_busy")
        }
        record = r
    }

    override fun read(buffer: ShortArray): Int = record?.read(buffer, 0, buffer.size) ?: AudioRecord.ERROR_INVALID_OPERATION

    override fun close() {
        record?.let { r -> runCatching { r.stop() }; r.release() }
        record = null
    }
}

sealed interface RecorderState {
    data object Idle : RecorderState
    data class Recording(val elapsedSeconds: Int) : RecorderState
    /** [autoStopped] is true when the 60 seconds ran out. */
    data class Finished(val file: File, val pcm: ShortArray, val seconds: Int, val autoStopped: Boolean) : RecorderState
    data object Cancelled : RecorderState
    data class Failed(val error: CoachError) : RecorderState
}

/**
 * Records up to [maxSeconds] of speech into an app-private WAV. Elapsed time is counted from the
 * audio itself (samples captured), so the 60-second stop is exact even if the UI stutters.
 *
 * start() → Recording → Finished (stop() or time up) | Cancelled (cancel()) | Failed (mic error,
 * a call, or interrupt()). Every path releases the microphone.
 */
class SpeechRecorder(
    private val files: AudioFileManager,
    private val newSource: () -> PcmSource = ::MicPcmSource,
    /** True while a phone or VoIP call has the audio. Checked between reads. */
    private val inCall: () -> Boolean = { false },
    val maxSeconds: Int = MAX_SECONDS,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private enum class Request { NONE, STOP, CANCEL, INTERRUPT }

    private val _state = MutableStateFlow<RecorderState>(RecorderState.Idle)
    val state: StateFlow<RecorderState> = _state.asStateFlow()
    private val request = AtomicReference(Request.NONE)
    private var job: Job? = null

    val isRecording: Boolean get() = _state.value is RecorderState.Recording

    /** Begins recording. Returns false if already recording. */
    fun start(scope: CoroutineScope): Boolean {
        if (job?.isActive == true) return false
        request.set(Request.NONE)
        _state.value = RecorderState.Recording(0)
        job = scope.launch(io) { run() }
        return true
    }

    fun stop() { request.compareAndSet(Request.NONE, Request.STOP) }
    fun cancel() { request.set(Request.CANCEL); if (job?.isActive != true && _state.value is RecorderState.Recording) _state.value = RecorderState.Cancelled }

    /** The app went to the background or another app took the microphone. */
    fun interrupt() { request.compareAndSet(Request.NONE, Request.INTERRUPT) }

    fun reset() { if (job?.isActive != true) _state.value = RecorderState.Idle }

    private suspend fun run() {
        val total = maxSeconds * Wav.SAMPLE_RATE
        val pcm = ShortArray(total)
        var have = 0
        val source = try {
            newSource().also { it.start() }
        } catch (t: Throwable) {
            SafeLog.error("mic_start_failed", t)
            _state.value = RecorderState.Failed(if (t is SecurityException) CoachError.MIC_PERMISSION else CoachError.MIC_UNAVAILABLE)
            return
        }
        val buf = ShortArray(Wav.SAMPLE_RATE / 10) // 100 ms
        var outcome: RecorderState? = null
        try {
            while (kotlin.coroutines.coroutineContext.isActive && have < total) {
                when (request.get()) {
                    Request.CANCEL -> { outcome = RecorderState.Cancelled; break }
                    Request.INTERRUPT -> { outcome = RecorderState.Failed(CoachError.INTERRUPTED); break }
                    Request.STOP -> break
                    Request.NONE -> Unit
                }
                if (inCall()) { outcome = RecorderState.Failed(CoachError.INTERRUPTED); break }
                val n = source.read(buf)
                if (n < 0) {
                    // ERROR_DEAD_OBJECT and friends: the system took the microphone away.
                    SafeLog.event("mic_read_error", "code" to n)
                    outcome = RecorderState.Failed(if (have > 0) CoachError.INTERRUPTED else CoachError.RECORDING_FAILED)
                    break
                }
                val take = minOf(n, total - have)
                System.arraycopy(buf, 0, pcm, have, take)
                val before = have / Wav.SAMPLE_RATE
                have += take
                val now = have / Wav.SAMPLE_RATE
                if (now != before) _state.value = RecorderState.Recording(now)
            }
        } catch (t: Throwable) {
            SafeLog.error("mic_read_failed", t)
            outcome = RecorderState.Failed(CoachError.RECORDING_FAILED)
        } finally {
            runCatching { source.close() }
        }
        if (outcome == null && request.get() == Request.CANCEL) outcome = RecorderState.Cancelled
        _state.value = outcome ?: finish(pcm, have, autoStopped = have >= total)
    }

    private fun finish(pcm: ShortArray, have: Int, autoStopped: Boolean): RecorderState = try {
        val samples = pcm.copyOf(have)
        val file = files.newRecordingFile()
        Wav.write(file, samples)
        RecorderState.Finished(file, samples, have / Wav.SAMPLE_RATE, autoStopped)
    } catch (t: Throwable) {
        SafeLog.error("recording_save_failed", t)
        RecorderState.Failed(if (t is java.io.IOException && t.message?.contains("ENOSPC") == true) CoachError.LOW_STORAGE else CoachError.RECORDING_FAILED)
    }

    companion object {
        const val MAX_SECONDS = 60

        /** True while a phone or VoIP call is active. Needs no permission. */
        fun callChecker(context: Context): () -> Boolean {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            return { am != null && (am.mode == AudioManager.MODE_IN_CALL || am.mode == AudioManager.MODE_IN_COMMUNICATION || am.mode == AudioManager.MODE_RINGTONE) }
        }
    }
}
