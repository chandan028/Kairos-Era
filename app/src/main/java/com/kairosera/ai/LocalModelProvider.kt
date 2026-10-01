package com.kairosera.ai

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.kairosera.core.diagnostics.SafeLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import kotlin.coroutines.coroutineContext

/** Where the Gemma model file is. Kairos never reads another app's private storage. */
interface LocalModelProvider {
    /** Absolute path of a usable .litertlm file, or null when none is installed. */
    suspend fun getGemmaModelPath(): String?
}

/** The model this feature is built and tested for (Google AI Edge Gallery's Gemma-4-E2B-it). */
object GemmaModel {
    const val FILE_NAME = "gemma-4-E2B-it.litertlm"
    const val EXTENSION = ".litertlm"
    const val MODEL_ID = "litert-community/gemma-4-E2B-it-litert-lm"

    /** Size listed in AI Edge Gallery's model allowlist (1_0_16.json). */
    const val EXPECTED_BYTES = 2_588_147_712L

    /** Every .litertlm file starts with these 8 bytes (LiteRT-LM schema/core/litertlm_read.cc). */
    val MAGIC = "LITERTLM".toByteArray(Charsets.US_ASCII)

    /** Anything this small cannot be a Gemma model, whatever its name. */
    const val MIN_PLAUSIBLE_BYTES = 50L * 1024 * 1024

    fun hasValidExtension(name: String?): Boolean = name != null && name.lowercase().endsWith(EXTENSION)

    fun startsWithMagic(head: ByteArray): Boolean = head.size >= MAGIC.size && MAGIC.indices.all { head[it] == MAGIC[it] }

    /** Checks the name, size and header of a model file before the engine ever sees it. */
    fun validate(file: File): CoachError? {
        if (!file.isFile) return CoachError.MODEL_MISSING
        if (!hasValidExtension(file.name)) return CoachError.MODEL_INVALID_EXTENSION
        if (file.length() < MIN_PLAUSIBLE_BYTES) return CoachError.MODEL_MALFORMED
        val head = ByteArray(MAGIC.size)
        val read = runCatching { file.inputStream().use { it.read(head) } }.getOrDefault(-1)
        return if (read == MAGIC.size && startsWithMagic(head)) null else CoachError.MODEL_MALFORMED
    }
}

/**
 * Keeps the model in Kairos's own storage:
 * - files/models/gemma-4-E2B-it.litertlm (imported with the system file picker), or
 * - Android/data/<package>/files/models/gemma-4-E2B-it.litertlm (copied there with `adb push`).
 * Both are private to Kairos and need no storage permission.
 */
class AppModelStore(
    private val internalDir: File,
    private val externalDir: File?,
) : LocalModelProvider {
    constructor(context: Context) : this(File(context.filesDir, "models"), context.getExternalFilesDir(null)?.let { File(it, "models") })

    val importTarget: File get() = File(internalDir, GemmaModel.FILE_NAME)
    val adbTarget: File? get() = externalDir?.let { File(it, GemmaModel.FILE_NAME) }

    /** The installed model file, valid or not, so the setup screen can say what is wrong with it. */
    fun installedFile(): File? = listOfNotNull(importTarget, adbTarget).firstOrNull { it.isFile }

    override suspend fun getGemmaModelPath(): String? = withContext(Dispatchers.IO) {
        listOfNotNull(importTarget, adbTarget).firstOrNull { GemmaModel.validate(it) == null }?.absolutePath
    }

    suspend fun remove() = withContext(Dispatchers.IO) {
        importTarget.delete()
        File(internalDir, GemmaModel.FILE_NAME + ".part").delete()
    }
}

/** Progress of copying a picked model file into Kairos. */
sealed interface ImportState {
    data object Idle : ImportState
    data class Copying(val copiedBytes: Long, val totalBytes: Long?) : ImportState
    data object Done : ImportState
    data class Failed(val error: CoachError) : ImportState
}

/**
 * Copies a .litertlm file the person picked (Storage Access Framework) into files/models, keeping
 * its name gemma-4-E2B-it.litertlm. Checks the extension, the free space and the file header, and
 * writes to a .part file first so a cancelled copy never leaves a broken model behind.
 */
class ModelImporter(
    private val store: AppModelStore,
    private val freeBytes: (File) -> Long,
) {
    private val _state = MutableStateFlow<ImportState>(ImportState.Idle)
    val state: StateFlow<ImportState> = _state.asStateFlow()

    @Volatile private var job: kotlinx.coroutines.Job? = null

    /** Stops a copy in progress; the half-copied file is deleted. */
    fun cancel() { job?.cancel() }

    suspend fun import(context: Context, uri: Uri) {
        val resolver = context.contentResolver
        val (name, size) = runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val n = c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let(c::getString)
                    val s = c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !c.isNull(it) }?.let(c::getLong)
                    n to s
                } else null
            }
        }.getOrNull() ?: (null to null)
        importStream(name, size) { resolver.openInputStream(uri) }
    }

    /** The copy itself, separate from Android's ContentResolver so tests can drive it. */
    suspend fun importStream(displayName: String?, size: Long?, open: () -> InputStream?) {
        val result = coroutineScope {
            val copying = async { runCatching { copy(displayName, size, open) } }
            job = copying
            try { copying.await() } catch (e: kotlinx.coroutines.CancellationException) { Result.failure(e) } finally { job = null }
        }
        result.exceptionOrNull()?.let { e ->
            if (e is kotlinx.coroutines.CancellationException) {
                _state.value = ImportState.Idle
                // Cancelled from the screen: just stop. Cancelled with the caller: keep propagating.
                currentCoroutineContext().ensureActive()
                return
            }
            SafeLog.error("model_import_failed", e)
        }
        _state.value = when (val e = result.exceptionOrNull()) {
            null -> ImportState.Done
            is CoachException -> ImportState.Failed(e.error)
            else -> ImportState.Failed(CoachError.IMPORT_FAILED)
        }
    }

    fun reset() { if (_state.value !is ImportState.Copying) _state.value = ImportState.Idle }

    private suspend fun copy(displayName: String?, size: Long?, open: () -> InputStream?) = withContext(Dispatchers.IO) {
        if (!GemmaModel.hasValidExtension(displayName)) throw CoachException(CoachError.MODEL_INVALID_EXTENSION)
        val target = store.importTarget
        target.parentFile?.mkdirs()
        val needed = (size ?: GemmaModel.EXPECTED_BYTES) + SAFETY_MARGIN_BYTES
        val existing = if (target.isFile) target.length() else 0L
        if (freeBytes(target.parentFile ?: target) + existing < needed) throw CoachException(CoachError.LOW_STORAGE)
        val part = File(target.parentFile, target.name + ".part")
        _state.value = ImportState.Copying(0, size)
        try {
            val input = open() ?: throw CoachException(CoachError.IMPORT_FAILED)
            input.use { inp ->
                part.outputStream().use { out ->
                    val buf = ByteArray(1 shl 20)
                    var copied = 0L
                    var lastReport = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = inp.read(buf)
                        if (n < 0) break
                        if (copied == 0L && n > 0 && !GemmaModel.startsWithMagic(buf.copyOf(minOf(n, GemmaModel.MAGIC.size)))) {
                            throw CoachException(CoachError.MODEL_MALFORMED)
                        }
                        out.write(buf, 0, n)
                        copied += n
                        if (copied - lastReport >= REPORT_EVERY_BYTES) {
                            lastReport = copied
                            _state.value = ImportState.Copying(copied, size)
                        }
                    }
                    out.fd.sync()
                }
            }
            // The header was checked on the first block; now the size.
            if (part.length() < GemmaModel.MIN_PLAUSIBLE_BYTES) throw CoachException(CoachError.MODEL_MALFORMED)
            if (size != null && part.length() != size) throw CoachException(CoachError.IMPORT_FAILED)
            target.delete()
            if (!part.renameTo(target)) throw CoachException(CoachError.IMPORT_FAILED)
        } finally {
            part.delete()
        }
    }

    companion object {
        /** Room for the engine's own cache files next to the model. */
        const val SAFETY_MARGIN_BYTES = 300L * 1024 * 1024
        private const val REPORT_EVERY_BYTES = 16L * 1024 * 1024
    }
}
