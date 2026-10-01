package com.kairosera.ai

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Where the main model runs. Audio is always encoded on CPU, as AI Edge Gallery does. */
enum class AiBackend { GPU, CPU }

/**
 * The few things the coach needs from an on-device model. Production uses LiteRT-LM; tests use a
 * fake, so no test ever loads the 2.6 GB model.
 */
interface LlmEngine : AutoCloseable {
    val backend: AiBackend

    /**
     * One fresh conversation: [system] instruction, optional [audio] (a 16 kHz mono WAV of at most
     * 30 seconds), then [prompt]. Returns the full reply. Cancelling the coroutine stops generation.
     */
    suspend fun generate(system: String, audio: File?, prompt: String): String
}

/** Creates and initialises an engine. Blocking and slow (seconds); call off the main thread. */
fun interface LlmEngineFactory {
    fun create(modelPath: String, backend: AiBackend): LlmEngine
}

/**
 * LiteRT-LM (com.google.ai.edge.litertlm, Kotlin API as documented in
 * docs/api/kotlin/getting_started.md): Engine(EngineConfig).initialize(), one Conversation per
 * request, multimodal Contents with Content.AudioFile.
 */
class LiteRtEngineFactory(private val maxTokens: Int = MAX_TOKENS) : LlmEngineFactory {
    override fun create(modelPath: String, backend: AiBackend): LlmEngine {
        val config = EngineConfig(
            modelPath = modelPath,
            backend = when (backend) {
                AiBackend.GPU -> Backend.GPU()
                AiBackend.CPU -> Backend.CPU()
            },
            // No images: leaving the vision encoder out saves memory.
            visionBackend = null,
            // AI Edge Gallery runs the audio encoder on CPU for every audio model.
            audioBackend = Backend.CPU(),
            maxNumTokens = maxTokens,
        )
        val engine = Engine(config)
        try {
            engine.initialize()
        } catch (t: Throwable) {
            runCatching { engine.close() }
            throw t
        }
        return LiteRtEngine(engine, backend)
    }

    companion object {
        /** Prompt plus reply budget: two 30-second clips are about 750 audio tokens each. */
        const val MAX_TOKENS = 4096
    }
}

private class LiteRtEngine(private val engine: Engine, override val backend: AiBackend) : LlmEngine {
    override suspend fun generate(system: String, audio: File?, prompt: String): String {
        val conversation = engine.createConversation(
            ConversationConfig(
                systemInstruction = Contents.of(system),
                // Low temperature: the coach must return the same JSON shape every time.
                samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.2),
            ),
        )
        val parts = buildList {
            // Audio before text, as AI Edge Gallery orders multimodal prompts.
            if (audio != null) add(Content.AudioFile(audio.absolutePath))
            add(Content.Text(prompt))
        }
        val closed = AtomicBoolean(false)
        fun closeOnce() { if (closed.compareAndSet(false, true)) runCatching { conversation.close() } }
        val out = StringBuilder()
        return try {
            suspendCancellableCoroutine { cont ->
                conversation.sendMessageAsync(
                    Contents.of(parts),
                    object : MessageCallback {
                        override fun onMessage(message: Message) {
                            synchronized(out) { out.append(message.toString()) }
                        }

                        override fun onDone() {
                            closeOnce()
                            if (cont.isActive) cont.resume(synchronized(out) { out.toString() })
                        }

                        override fun onError(throwable: Throwable) {
                            closeOnce()
                            if (cont.isActive) {
                                cont.resumeWithException(if (throwable is CancellationException) throwable else RuntimeException("litertlm_error", throwable))
                            }
                        }
                    },
                    // Gemma 4 can think before answering; the coach wants the answer only.
                    mapOf("enable_thinking" to false),
                )
                // The conversation is closed by onDone/onError once the native side has stopped.
                cont.invokeOnCancellation { runCatching { conversation.cancelProcess() } }
            }
        } catch (t: Throwable) {
            if (t !is CancellationException) closeOnce()
            throw t
        }
    }

    override fun close() = engine.close()
}
