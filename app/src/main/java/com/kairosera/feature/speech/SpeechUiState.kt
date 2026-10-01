package com.kairosera.feature.speech

import com.kairosera.ai.AnalysisStep
import com.kairosera.ai.CoachError
import com.kairosera.ai.DeviceFit

/** Where the one-minute speech flow is. */
sealed interface SpeechPhase {
    data object Ready : SpeechPhase
    data class Recording(val elapsedSeconds: Int, val maxSeconds: Int) : SpeechPhase
    data class Analyzing(val step: AnalysisStep) : SpeechPhase
    /** [canRetry]: the recording is still here, so analysis can run again without re-recording. */
    data class Failed(val error: CoachError, val canRetry: Boolean) : SpeechPhase
    /** Analysed and saved; the screen opens the report once. */
    data class Done(val sessionId: Long) : SpeechPhase
}

data class SpeechUiState(
    val phase: SpeechPhase = SpeechPhase.Ready,
    /** A usable model file is installed. Without it, Start runs the plain timer. */
    val coachAvailable: Boolean = false,
    val deviceFit: DeviceFit = DeviceFit.OK,
)
