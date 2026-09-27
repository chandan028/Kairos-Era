package com.kairosera.core.diagnostics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.Instant

class CrashReportsTest {
    private fun report(e: Throwable) = CrashReports.format(e, Instant.parse("2026-09-25T10:15:30.123Z"), onMainThread = true, app = "0.7.0 (9)", android = "15 (API 35)")

    @Test
    fun messagesNeverReachTheReport() {
        val e = IllegalStateException("Journal: my private evening thoughts", IOException("weight 72.4 kg"))
        val text = report(e)
        assertFalse(text.contains("private"))
        assertFalse(text.contains("72.4"))
        assertTrue(text.contains("java.lang.IllegalStateException"))
        assertTrue(text.contains("Caused by: java.io.IOException"))
    }

    @Test
    fun holdsVersionsTimeAndFrames() {
        val text = report(RuntimeException())
        assertTrue(text.contains("App: 0.7.0 (9)"))
        assertTrue(text.contains("Android: 15 (API 35)"))
        assertTrue(text.contains("Time: 2026-09-25T10:15:30Z"))
        assertTrue(text.contains("  at com.kairosera.core.diagnostics.CrashReportsTest"))
    }

    @Test
    fun longTracesAreCut() {
        val e = RuntimeException().apply { stackTrace = Array(200) { StackTraceElement("C", "m$it", "C.kt", it) } }
        val text = report(e)
        assertTrue(text.contains("… 160 more"))
        assertFalse(text.contains("m150"))
    }

    @Test
    fun aCauseLoopDoesNotHang() {
        val a = RuntimeException()
        val b = IllegalArgumentException(a)
        a.initCause(b)
        assertTrue(report(a).lines().count { it.startsWith("Caused by") } <= 5)
    }
}
