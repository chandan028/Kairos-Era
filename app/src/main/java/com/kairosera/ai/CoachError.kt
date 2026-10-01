package com.kairosera.ai

/**
 * Every way the speaking coach can fail, so each one gets a calm message instead of a crash.
 * The UI maps these to strings; nothing here holds user text.
 */
enum class CoachError {
    MIC_PERMISSION,
    MIC_UNAVAILABLE,
    RECORDING_FAILED,
    /** A call, another app taking the microphone, or leaving the app stopped the recording. */
    INTERRUPTED,
    TOO_SHORT,
    NO_SPEECH,
    MODEL_MISSING,
    MODEL_INVALID_EXTENSION,
    MODEL_MALFORMED,
    LOW_STORAGE,
    LOW_MEMORY,
    /** The engine would not start on GPU or CPU. */
    UNSUPPORTED_DEVICE,
    INFERENCE_FAILED,
    BAD_RESPONSE,
    TIMEOUT,
    IMPORT_FAILED,
}

/** Carries a [CoachError] through coroutine code. The cause is kept for logs only. */
class CoachException(val error: CoachError, cause: Throwable? = null) : Exception(error.name, cause)
