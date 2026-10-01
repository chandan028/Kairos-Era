package com.kairosera.ai

import java.io.File
import java.io.RandomAccessFile
import kotlin.math.PI
import kotlin.math.sin

/** Test doubles for the coach: no model, no microphone, no Android. */
object CoachFakes {
    const val GB = 1024L * 1024 * 1024

    /** A file that passes [GemmaModel.validate]: right name, LITERTLM header, plausible (sparse) size. */
    fun fakeModel(dir: File): File {
        dir.mkdirs()
        val f = File(dir, GemmaModel.FILE_NAME)
        RandomAccessFile(f, "rw").use {
            it.write(GemmaModel.MAGIC)
            it.setLength(GemmaModel.MIN_PLAUSIBLE_BYTES + 1024)
        }
        return f
    }

    fun device(ram: Long = 8 * GB) = object : DeviceInfo {
        override fun totalRamBytes() = ram
        override fun availableRamBytes() = ram / 2
        override fun freeBytes(dir: File) = 64 * GB
    }

    fun provider(path: () -> String?) = object : LocalModelProvider {
        override suspend fun getGemmaModelPath(): String? = path()
    }

    /** [seconds] of 16 kHz speech-like audio: 1 s of tone, then 0.25 s of near silence, repeated. */
    fun speechPcm(seconds: Int): ShortArray {
        val rate = 16_000
        return ShortArray(seconds * rate) { i ->
            val inCycle = i % (rate + rate / 4)
            if (inCycle < rate) (8000 * sin(2 * PI * 220 * i / rate)).toInt().toShort() else ((i % 7) - 3).toShort()
        }
    }

    const val GOOD_JSON = """{"overall_score":7,"clarity_score":8,"structure_score":6,"vocabulary_score":7,"grammar_score":8,"conciseness_score":6,
        "filler_words":["um","like"],"strengths":["Clear opening"],"improvements":["Add an example"],"next_exercise":"Point, example, conclusion.","summary":"Good start."}"""
}

/** Records every call; answers from [replies] (audio calls get [transcript]). */
class FakeEngine(
    override val backend: AiBackend,
    private val transcript: (File) -> String = { "So um today I want to talk about mornings and why they matter." },
    private val replies: ArrayDeque<String> = ArrayDeque(listOf(CoachFakes.GOOD_JSON)),
    private val failWith: Throwable? = null,
) : LlmEngine {
    val audioCalls = mutableListOf<File>()
    var textCalls = 0
    var closed = false

    override suspend fun generate(system: String, audio: File?, prompt: String): String {
        failWith?.let { throw it }
        if (audio != null) {
            check(audio.isFile) { "audio part missing" }
            audioCalls += audio
            return transcript(audio)
        }
        textCalls++
        return replies.removeFirstOrNull() ?: CoachFakes.GOOD_JSON
    }

    override fun close() { closed = true }
}
