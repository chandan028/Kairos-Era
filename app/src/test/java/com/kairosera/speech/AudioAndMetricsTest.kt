package com.kairosera.speech

import com.kairosera.ai.CoachFakes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AudioAndMetricsTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun wavHeaderIsStandardPcm16Mono16k() {
        val f = tmp.newFile("a.wav")
        Wav.write(f, ShortArray(16_000) { (it % 100).toShort() })
        val b = f.readBytes()
        assertEquals("RIFF", String(b, 0, 4))
        assertEquals("WAVE", String(b, 8, 4))
        assertEquals(1, b[20].toInt()) // PCM
        assertEquals(1, b[22].toInt()) // mono
        assertEquals(16_000, (b[24].toInt() and 0xff) or ((b[25].toInt() and 0xff) shl 8))
        assertEquals(16, b[34].toInt())
        assertEquals(16_000, Wav.read(f).size)
    }

    @Test fun splitsIntoClipsOfAtMostThirtySeconds() {
        val files = AudioFileManager(tmp.newFolder(), tmp.newFolder())
        assertEquals(listOf(30, 30), files.splitForModel(CoachFakes.speechPcm(60)).map { Wav.read(it).size / Wav.SAMPLE_RATE })
        assertEquals(listOf(30, 15), files.splitForModel(CoachFakes.speechPcm(45)).map { Wav.read(it).size / Wav.SAMPLE_RATE })
        // A sliver under a second at the end is dropped rather than sent as its own clip.
        val sliver = ShortArray(30 * 16_000 + 4_000)
        assertEquals(1, files.splitForModel(sliver).size)
    }

    @Test fun keptRecordingsMoveOutOfTheCache() {
        val files = AudioFileManager(tmp.newFolder(), tmp.newFolder())
        val rec = files.newRecordingFile().also { Wav.write(it, ShortArray(100)) }
        val kept = files.keep(rec, 7)!!
        assertTrue(kept.isFile)
        assertTrue(!rec.exists())
        files.clearKept()
        assertTrue(!kept.exists())
    }

    @Test fun measuresVoiceAndPauses() {
        val rate = 16_000
        // 3 s talk, 2 s silence, 3 s talk.
        val pcm = tone(3) + ShortArray(2 * rate) + tone(3)
        val m = SpeechMetrics.analyze(pcm)
        assertEquals(8.0, m.durationSeconds, 0.01)
        assertTrue("voiced ${m.voicedSeconds}", m.voicedSeconds in 5.5..6.5)
        assertEquals(1, m.longPauses)
        assertTrue(m.longestPauseSeconds in 1.8..2.2)
        assertTrue(SpeechMetrics.analyze(ShortArray(rate * 10)).voicedSeconds < SpeechMetrics.MIN_VOICED_SECONDS)
    }

    @Test fun wordsPaceAndFillers() {
        val t = "Um, so I think, uh, that mornings matter. Er... hmm, yes."
        assertEquals(11, SpeechMetrics.wordCount("Um so I think uh that mornings matter er hmm yes"))
        assertEquals(4, SpeechMetrics.fillerSounds(t))
        assertEquals(120, SpeechMetrics.wordsPerMinute(120, 60.0))
        assertNull(SpeechMetrics.wordsPerMinute(0, 60.0))
        assertEquals(Pace.SLOW, SpeechMetrics.pace(90))
        assertEquals(Pace.COMFORTABLE, SpeechMetrics.pace(140))
        assertEquals(Pace.FAST, SpeechMetrics.pace(190))
        assertNull(SpeechMetrics.pace(null))
    }

    private fun tone(seconds: Int) = ShortArray(seconds * 16_000) { (8000 * kotlin.math.sin(2 * Math.PI * 200 * it / 16_000)).toInt().toShort() }
}
