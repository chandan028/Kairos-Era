package com.kairosera.core.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockTest {
    private var now = 1_000_000L
    private val lock = AppLock { now }

    @Test
    fun locksOnColdStartOnlyWhenEnabled() {
        AppLock { now }.apply { configure(enabled = false, afterSeconds = 60) }.also { assertFalse(it.locked.value) }
        lock.configure(enabled = true, afterSeconds = 60)
        assertTrue(lock.locked.value)
    }

    @Test
    fun turningTheLockOnLaterDoesNotLockStraightAway() {
        lock.configure(enabled = false, afterSeconds = 60)
        lock.configure(enabled = true, afterSeconds = 60)
        assertFalse(lock.locked.value)
    }

    @Test
    fun locksAfterTheChosenDelayAway() {
        lock.configure(enabled = true, afterSeconds = 60)
        lock.unlock()
        lock.onAppHidden(); now += 59_000; lock.onAppShown()
        assertFalse(lock.locked.value)
        lock.onAppHidden(); now += 60_000; lock.onAppShown()
        assertTrue(lock.locked.value)
    }

    @Test
    fun immediatelyMeansAnyTimeAway() {
        lock.configure(enabled = true, afterSeconds = 0)
        lock.unlock()
        lock.onAppHidden(); lock.onAppShown()
        assertTrue(lock.locked.value)
    }

    @Test
    fun theUnlockPromptItselfDoesNotCountAsLeaving() {
        lock.configure(enabled = true, afterSeconds = 0)
        lock.authenticating = true
        lock.onAppHidden(); now += 5_000; lock.onAppShown()
        lock.authenticating = false
        lock.unlock()
        assertFalse(lock.locked.value)
    }

    @Test
    fun turningTheLockOffUnlocks() {
        lock.configure(enabled = true, afterSeconds = 60)
        lock.configure(enabled = false, afterSeconds = 60)
        assertFalse(lock.locked.value)
    }
}
