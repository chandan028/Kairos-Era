package com.kairosera.ai

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream

class ModelImporterTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun store() = AppModelStore(tmp.newFolder("models"), tmp.newFolder("external"))

    /** A stream of [size] bytes that starts with [head] and is zeros after, without holding it in memory. */
    private fun stream(size: Long, head: ByteArray = GemmaModel.MAGIC): InputStream = object : InputStream() {
        var pos = 0L
        override fun read(): Int = if (pos >= size) -1 else (if (pos < head.size) head[pos.toInt()].toInt() and 0xff else 0).also { pos++ }
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (pos >= size) return -1
            val n = minOf(len.toLong(), size - pos).toInt()
            for (i in 0 until n) b[off + i] = if (pos + i < head.size) head[(pos + i).toInt()] else 0
            pos += n
            return n
        }
    }

    private val size = GemmaModel.MIN_PLAUSIBLE_BYTES + 4096

    @Test fun importsUnderTheExactModelName() = runBlocking {
        val s = store()
        val imp = ModelImporter(s) { Long.MAX_VALUE }
        imp.importStream(GemmaModel.FILE_NAME, size) { stream(size) }
        assertEquals(ImportState.Done, imp.state.value)
        assertEquals(File(tmp.root, "models/gemma-4-E2B-it.litertlm"), s.installedFile())
        assertEquals(s.importTarget.absolutePath, s.getGemmaModelPath())
        assertFalse(File(tmp.root, "models/gemma-4-E2B-it.litertlm.part").exists())
    }

    @Test fun rejectsBinAndTaskFiles() = runBlocking {
        val imp = ModelImporter(store()) { Long.MAX_VALUE }
        for (name in listOf("gemma-4-E2B-it.bin", "gemma-4-E2B-it.task", null)) {
            imp.importStream(name, size) { stream(size) }
            assertEquals(ImportState.Failed(CoachError.MODEL_INVALID_EXTENSION), imp.state.value)
        }
    }

    @Test fun rejectsAFileThatIsNotLiteRtLm() = runBlocking {
        val s = store()
        val imp = ModelImporter(s) { Long.MAX_VALUE }
        imp.importStream("renamed.litertlm", size) { stream(size, head = "PK\u0003\u0004zip!".toByteArray()) }
        assertEquals(ImportState.Failed(CoachError.MODEL_MALFORMED), imp.state.value)
        assertEquals(null, s.installedFile())
    }

    @Test fun checksFreeSpaceFirst() = runBlocking {
        var opened = false
        val imp = ModelImporter(store()) { 1024L * 1024 * 1024 }
        imp.importStream(GemmaModel.FILE_NAME, GemmaModel.EXPECTED_BYTES) { opened = true; stream(size) }
        assertEquals(ImportState.Failed(CoachError.LOW_STORAGE), imp.state.value)
        assertFalse(opened)
    }

    @Test fun truncatedCopyIsNotInstalled() = runBlocking {
        val s = store()
        val imp = ModelImporter(s) { Long.MAX_VALUE }
        imp.importStream(GemmaModel.FILE_NAME, size + 10) { stream(size) }
        assertEquals(ImportState.Failed(CoachError.IMPORT_FAILED), imp.state.value)
        assertEquals(null, s.installedFile())
    }

    @Test fun validateCatchesEveryBadFile() {
        assertEquals(CoachError.MODEL_MISSING, GemmaModel.validate(File(tmp.root, "none.litertlm")))
        assertEquals(CoachError.MODEL_INVALID_EXTENSION, GemmaModel.validate(tmp.newFile("model.task")))
        assertEquals(CoachError.MODEL_MALFORMED, GemmaModel.validate(tmp.newFile("tiny.litertlm").apply { writeBytes(GemmaModel.MAGIC) }))
        assertEquals(null, GemmaModel.validate(CoachFakes.fakeModel(tmp.newFolder())))
        assertTrue(GemmaModel.hasValidExtension("GEMMA.LITERTLM"))
    }
}
