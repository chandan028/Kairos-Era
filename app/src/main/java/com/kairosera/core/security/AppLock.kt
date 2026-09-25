package com.kairosera.core.security

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Decides when Kairos Era is locked. It holds no secret: unlocking is done by the phone's own
 * fingerprint, face or screen lock (see [DeviceAuth]), so the app never sees or stores a PIN.
 *
 * Locks on a cold start and whenever the app returns after being away for at least the chosen
 * delay. Time is measured on the monotonic clock, so changing the phone's clock can't skip it.
 */
class AppLock(private val now: () -> Long = SystemClock::elapsedRealtime) {
    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    private var enabled = false
    private var afterMillis = 60_000L
    private var configured = false
    private var leftAt: Long? = null

    /** True while the system unlock prompt is showing, so the app hiding behind it doesn't count as leaving. */
    var authenticating = false

    /** Applies the saved settings. The first call after process start locks straight away if enabled. */
    fun configure(enabled: Boolean, afterSeconds: Int) {
        this.enabled = enabled
        this.afterMillis = afterSeconds * 1000L
        if (!configured) {
            configured = true
            _locked.value = enabled
        }
        if (!enabled) _locked.value = false
    }

    fun onAppHidden() {
        if (!authenticating) leftAt = now()
    }

    fun onAppShown() {
        val left = leftAt ?: return
        leftAt = null
        if (enabled && !authenticating && now() - left >= afterMillis) _locked.value = true
    }

    /** Locks straight away, for example from a "Lock now" action. */
    fun lockNow() {
        if (enabled) _locked.value = true
    }

    fun unlock() {
        _locked.value = false
        leftAt = null
    }
}
