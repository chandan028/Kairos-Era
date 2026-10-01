package com.kairosera.ai

import com.kairosera.core.diagnostics.SafeLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** What the engine is doing, for loading states and the setup screen. */
sealed interface EngineState {
    data object Idle : EngineState
    data class Loading(val backend: AiBackend) : EngineState
    data class Ready(val backend: AiBackend, val loadMillis: Long, val fellBackToCpu: Boolean) : EngineState
    data class Failed(val error: CoachError) : EngineState
}

/**
 * Owns the one LiteRT-LM engine in the app. Loading Gemma takes seconds and gigabytes, so it is
 * loaded once, reused while the speaking screen is open, and released when it closes.
 *
 * - Never more than one engine: every use goes through one [Mutex].
 * - GPU first when the person chose it; if GPU fails to start, or fails while generating, the
 *   engine is rebuilt on CPU and the work is retried once. CPU stays in use for the rest of the
 *   session.
 * - All loading happens on [io]; callers can be on the main thread.
 */
class GemmaEngineManager(
    private val models: LocalModelProvider,
    private val factory: LlmEngineFactory,
    private val device: DeviceInfo,
    private val preferredBackend: suspend () -> AiBackend,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private var engine: LlmEngine? = null
    private var gpuFailed = false

    private val _state = MutableStateFlow<EngineState>(EngineState.Idle)
    val state: StateFlow<EngineState> = _state.asStateFlow()

    /** Loads the engine if needed, then runs [block] with it, retrying once on CPU after a GPU failure. */
    suspend fun <T> withEngine(block: suspend (LlmEngine) -> T): T = mutex.withLock {
        val e = ensureLoaded()
        try {
            block(e)
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            // CoachExceptions (an unreadable reply, no speech) are not the GPU's fault.
            if (e.backend != AiBackend.GPU || t is CoachException) throw t
            SafeLog.error("gemma_gpu_inference_failed", t)
            gpuFailed = true
            closeEngine()
            val cpu = load(AiBackend.CPU, fellBack = true)
            block(cpu)
        }
    }

    /** Starts loading early (for example while the person is still speaking). Failures surface later. */
    suspend fun warmUp() {
        runCatching { mutex.withLock { ensureLoaded() } }.onFailure { if (it is CancellationException) throw it }
    }

    /** Frees the model's memory. The next use loads it again. */
    suspend fun release() = mutex.withLock {
        closeEngine()
        gpuFailed = false
        _state.value = EngineState.Idle
    }

    private suspend fun ensureLoaded(): LlmEngine {
        val wanted = if (gpuFailed) AiBackend.CPU else preferredBackend()
        engine?.let { current ->
            if (current.backend == wanted || (wanted == AiBackend.GPU && gpuFailed)) return current
            closeEngine() // the person switched backend in settings
        }
        val path = models.getGemmaModelPath()
            ?: throw fail(CoachError.MODEL_MISSING)
        GemmaModel.validate(File(path))?.let { throw fail(it) }
        if (ModelRequirements.fit(device.totalRamBytes()) == DeviceFit.TOO_SMALL) throw fail(CoachError.LOW_MEMORY)

        if (wanted == AiBackend.GPU) {
            try {
                return load(AiBackend.GPU, fellBack = false, path = path)
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                SafeLog.error("gemma_gpu_init_failed", t)
                gpuFailed = true
            }
        }
        return try {
            load(AiBackend.CPU, fellBack = wanted == AiBackend.GPU, path = path)
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            SafeLog.error("gemma_cpu_init_failed", t)
            throw fail(if (t is OutOfMemoryError) CoachError.LOW_MEMORY else CoachError.UNSUPPORTED_DEVICE, t)
        }
    }

    private suspend fun load(backend: AiBackend, fellBack: Boolean, path: String? = null): LlmEngine {
        val modelPath = path ?: models.getGemmaModelPath() ?: throw fail(CoachError.MODEL_MISSING)
        _state.value = EngineState.Loading(backend)
        val started = clock()
        // Not cancellable: a half-way cancel would drop an engine that already holds gigabytes.
        val created = withContext(io + NonCancellable) { factory.create(modelPath, backend) }
        engine = created
        val took = clock() - started
        SafeLog.event("gemma_ready", "backend" to backend, "load_ms" to took, "fell_back" to fellBack)
        _state.value = EngineState.Ready(backend, took, fellBack)
        return created
    }

    private fun fail(error: CoachError, cause: Throwable? = null): CoachException {
        _state.value = EngineState.Failed(error)
        return CoachException(error, cause)
    }

    private fun closeEngine() {
        engine?.let { e -> runCatching { e.close() }.onFailure { SafeLog.error("gemma_close_failed", it) } }
        engine = null
    }
}
