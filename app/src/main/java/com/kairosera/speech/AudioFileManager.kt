package com.kairosera.speech

import java.io.DataInputStream
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/**
 * 16 kHz, mono, 16-bit PCM WAV: the format Gemma 4's audio preprocessor works in
 * (AudioPreprocessorConfig::CreateDefaultGemma4Config: 16000 Hz, 1 channel), which LiteRT-LM
 * decodes with miniaudio. Recording in it means nothing is resampled or converted.
 */
object Wav {
    const val SAMPLE_RATE = 16_000
    private const val HEADER_BYTES = 44

    fun header(dataBytes: Int, sampleRate: Int = SAMPLE_RATE): ByteArray =
        ByteBuffer.allocate(HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII)); putInt(36 + dataBytes)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII)); putInt(16)
            putShort(1) // PCM
            putShort(1) // mono
            putInt(sampleRate)
            putInt(sampleRate * 2) // byte rate
            putShort(2) // block align
            putShort(16) // bits per sample
            put("data".toByteArray(Charsets.US_ASCII)); putInt(dataBytes)
        }.array()

    fun write(file: File, pcm: ShortArray, count: Int = pcm.size, from: Int = 0) {
        file.parentFile?.mkdirs()
        val bytes = ByteBuffer.allocate(count * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in from until from + count) bytes.putShort(pcm[i])
        file.outputStream().buffered().use { out ->
            out.write(header(count * 2))
            out.write(bytes.array())
        }
    }

    /** Reads a 16-bit mono WAV written by [write] (or any plain PCM16 mono WAV). */
    fun read(file: File): ShortArray {
        DataInputStream(file.inputStream().buffered()).use { input ->
            val riff = ByteArray(12)
            input.readFully(riff)
            if (String(riff, 0, 4, Charsets.US_ASCII) != "RIFF" || String(riff, 8, 4, Charsets.US_ASCII) != "WAVE") throw IOException("not_wav")
            var channels = 1
            var bits = 16
            while (true) {
                val chunk = ByteArray(8)
                input.readFully(chunk)
                val id = String(chunk, 0, 4, Charsets.US_ASCII)
                val size = ByteBuffer.wrap(chunk, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
                if (id == "fmt ") {
                    val fmt = ByteArray(size)
                    input.readFully(fmt)
                    val b = ByteBuffer.wrap(fmt).order(ByteOrder.LITTLE_ENDIAN)
                    b.short
                    channels = b.short.toInt()
                    b.int; b.int; b.short
                    bits = b.short.toInt()
                } else if (id == "data") {
                    if (channels != 1 || bits != 16) throw IOException("unsupported_wav")
                    val data = ByteArray(size)
                    input.readFully(data)
                    val shorts = ShortArray(size / 2)
                    ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
                    return shorts
                } else {
                    input.skipBytes(size)
                }
            }
        }
    }
}

/**
 * Recordings live in app-private storage only: cache/speech while they are analysed, and
 * files/speech/recordings if the person chose to keep them. Nothing else can read either place.
 */
class AudioFileManager(cacheDir: File, filesDir: File) {
    val workDir = File(cacheDir, "speech")
    val keptDir = File(filesDir, "speech/recordings")

    fun newRecordingFile(): File = File(workDir.apply { mkdirs() }, "rec-${UUID.randomUUID()}.wav")

    /**
     * Gemma 4 accepts at most 30 seconds of audio per clip (model card), so a one-minute speech is
     * cut into 30-second WAV parts. A last part with under a second of audio is dropped.
     */
    fun splitForModel(pcm: ShortArray, maxSeconds: Int = MODEL_CLIP_SECONDS): List<File> {
        val per = maxSeconds * Wav.SAMPLE_RATE
        val parts = mutableListOf<File>()
        var from = 0
        while (from < pcm.size) {
            val count = minOf(per, pcm.size - from)
            if (count >= Wav.SAMPLE_RATE || parts.isEmpty()) {
                val f = File(workDir.apply { mkdirs() }, "part-${UUID.randomUUID()}.wav")
                Wav.write(f, pcm, count, from)
                parts += f
            }
            from += count
        }
        return parts
    }

    /** Moves a finished recording into kept storage; returns where it went. */
    fun keep(recording: File, sessionId: Long): File? {
        keptDir.mkdirs()
        val target = File(keptDir, "speech-$sessionId.wav")
        return if (recording.renameTo(target) || runCatching { recording.copyTo(target, overwrite = true); recording.delete() }.isSuccess) target else null
    }

    fun delete(vararg files: File?) { files.forEach { it?.delete() } }

    /** Removes every temporary recording and part (for example after a crash mid-analysis). */
    fun clearWork() { workDir.listFiles()?.forEach { it.delete() } }

    fun clearKept() { keptDir.deleteRecursively() }

    companion object {
        const val MODEL_CLIP_SECONDS = 30
    }
}
