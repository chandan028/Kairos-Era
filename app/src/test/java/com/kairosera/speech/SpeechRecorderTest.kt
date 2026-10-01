package com.kairosera.speech

import com.kairosera.ai.CoachError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SpeechRecorderTest {
    @get:Rule val tmp = TemporaryFolder()

    /** A microphone that hands out tone samples; [pauseMs] per 100 ms read slows it down for stop tests. */
    private class FakeMic(private val pauseMs: Long = 0, private val failAfterReads: Int = -1, private val startError: Throwable? = null) : PcmSource {
        var reads = 0
        var closed = false
        override fun start() { startError?.let { throw it } }
        override fun read(buffer: ShortArray): Int {
            if (pauseMs > 0) Thread.sleep(pauseMs)
            if (failAfterReads >= 0 && reads >= failAfterReads) return -6 // AudioRecord.ERROR_DEAD_OBJECT
            reads++
            for (i in buffer.indices) buffer[i] = ((i % 40) * 400 - 8000).toShort()
            return buffer.size
        }
        override fun close() { closed = true }
    }

    private fun recorder(mic: FakeMic, inCall: () -> Boolean = { false }) =
        SpeechRecorder(AudioFileManager(tmp.newFolder(), tmp.newFolder()), { mic }, inCall, io = Dispatchers.Default)

    private suspend fun SpeechRecorder.awaitEnd() = withTimeout(20_000) { state.first { it !is RecorderState.Recording && it != RecorderState.Idle } }

    @Test fun stopsByItselfAtSixtySeconds() = runBlocking {
        val mic = FakeMic()
        val r = recorder(mic)
        assertEquals(RecorderState.Idle, r.state.value)
        assertTrue(r.start(this))
        assertTrue(r.isRecording)
        val end = r.awaitEnd() as RecorderState.Finished
        assertEquals(60, end.seconds)
        assertTrue(end.autoStopped)
        assertEquals(60 * Wav.SAMPLE_RATE, end.pcm.size)
        assertTrue(mic.closed)
        // The saved file is a 16 kHz mono PCM16 WAV Gemma can read, and holds the same samples.
        assertEquals(end.pcm.toList(), Wav.read(end.file).toList())
        assertEquals(44L + 2L * end.pcm.size, end.file.length())
    }

    @Test fun stopKeepsWhatWasSaid() = runBlocking {
        val r = recorder(FakeMic(pauseMs = 2))
        r.start(this)
        withTimeout(20_000) { r.state.first { it is RecorderState.Recording && it.elapsedSeconds >= 2 } }
        r.stop()
        val end = r.awaitEnd() as RecorderState.Finished
        assertFalse(end.autoStopped)
        assertTrue(end.seconds in 2..59)
    }

    @Test fun cancelLeavesNoFile() = runBlocking {
        val files = AudioFileManager(tmp.newFolder(), tmp.newFolder())
        val r = SpeechRecorder(files, { FakeMic(pauseMs = 2) }, io = Dispatchers.Default)
        r.start(this)
        withTimeout(20_000) { r.state.first { it is RecorderState.Recording && it.elapsedSeconds >= 1 } }
        r.cancel()
        assertEquals(RecorderState.Cancelled, r.awaitEnd())
        assertTrue(files.workDir.listFiles().isNullOrEmpty())
    }

    @Test fun interruptIsReported() = runBlocking {
        val r = recorder(FakeMic(pauseMs = 2))
        r.start(this)
        r.interrupt()
        assertEquals(RecorderState.Failed(CoachError.INTERRUPTED), r.awaitEnd())
    }

    @Test fun incomingCallStopsRecording() = runBlocking {
        var call = false
        val r = recorder(FakeMic(pauseMs = 2)) { call }
        r.start(this)
        withTimeout(20_000) { r.state.first { it is RecorderState.Recording && it.elapsedSeconds >= 1 } }
        call = true
        assertEquals(RecorderState.Failed(CoachError.INTERRUPTED), r.awaitEnd())
    }

    @Test fun micThatWontOpen() = runBlocking {
        val r = recorder(FakeMic(startError = IllegalStateException("busy")))
        r.start(this)
        assertEquals(RecorderState.Failed(CoachError.MIC_UNAVAILABLE), r.awaitEnd())
        val denied = recorder(FakeMic(startError = SecurityException("no permission")))
        denied.start(this)
        assertEquals(RecorderState.Failed(CoachError.MIC_PERMISSION), denied.awaitEnd())
    }

    @Test fun micLostMidway() = runBlocking {
        val r = recorder(FakeMic(failAfterReads = 15))
        r.start(this)
        assertEquals(RecorderState.Failed(CoachError.INTERRUPTED), r.awaitEnd())
        val never = recorder(FakeMic(failAfterReads = 0))
        never.start(this)
        assertEquals(RecorderState.Failed(CoachError.RECORDING_FAILED), never.awaitEnd())
    }

    @Test fun cannotStartTwice() = runBlocking {
        val r = recorder(FakeMic(pauseMs = 2))
        assertTrue(r.start(this))
        assertFalse(r.start(this))
        r.cancel()
        r.awaitEnd()
        r.reset()
        assertEquals(RecorderState.Idle, r.state.value)
    }
}
