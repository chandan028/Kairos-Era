package com.kairosera.core.diagnostics

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.kairosera.BuildConfig
import java.io.File
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Keeps a record of the last crash on the phone, and nothing else. A report holds the app and
 * Android versions, the time, and the exception types with their stack frames. Exception messages
 * are never written: they can repeat whatever the person typed. Nothing is sent anywhere; the
 * person may choose to email a report they have read, through their own email app.
 */
object CrashReports {
    const val EMAIL = "wondersparksmedia@gmail.com"
    private const val MAX_FRAMES = 40
    private const val MAX_CAUSES = 5

    class Report(val at: Instant, val text: String, val prompted: Boolean)

    private fun dir(context: Context) = File(context.filesDir, "diagnostics").apply { mkdirs() }
    private fun file(context: Context) = File(dir(context), "last-crash.txt")
    private fun promptedMarker(context: Context) = File(dir(context), "last-crash.prompted")

    /** Records uncaught crashes, then lets Android handle them as usual. */
    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val text = format(error, Instant.now(), thread.name == "main")
                val tmp = File(dir(app), "last-crash.tmp")
                tmp.writeText(text)
                promptedMarker(app).delete()
                tmp.renameTo(file(app))
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun pending(context: Context): Report? {
        val f = file(context)
        if (!f.exists()) return null
        return runCatching { Report(Instant.ofEpochMilli(f.lastModified()), f.readText(), promptedMarker(context).exists()) }.getOrNull()
    }

    /** Remembers that the person has been told about this crash, so they are asked only once. */
    fun markPrompted(context: Context) {
        runCatching { promptedMarker(context).createNewFile() }
    }

    fun clear(context: Context) {
        file(context).delete()
        promptedMarker(context).delete()
    }

    /** Opens the person's email app with the report filled in. They see and send it themselves. */
    fun emailIntent(report: Report, subject: String): Intent =
        Intent(Intent.ACTION_SEND)
            .setType("message/rfc822")
            .putExtra(Intent.EXTRA_EMAIL, arrayOf(EMAIL))
            .putExtra(Intent.EXTRA_SUBJECT, subject)
            .putExtra(Intent.EXTRA_TEXT, report.text)
            .apply { selector = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")) }

    /** The report text. Types and frames only; see the class comment for why messages are left out. */
    fun format(
        error: Throwable,
        at: Instant,
        onMainThread: Boolean,
        app: String = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
        android: String = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
    ): String = buildString {
        appendLine("Kairos Era crash report")
        appendLine("App: $app")
        appendLine("Android: $android")
        appendLine("Time: ${at.truncatedTo(ChronoUnit.SECONDS)}")
        appendLine("Thread: ${if (onMainThread) "main" else "background"}")
        appendLine()
        var t: Throwable? = error
        var depth = 0
        val seen = HashSet<Throwable>()
        while (t != null && depth <= MAX_CAUSES && seen.add(t)) {
            appendLine(if (depth == 0) t.javaClass.name else "Caused by: ${t.javaClass.name}")
            t.stackTrace.take(MAX_FRAMES).forEach { f ->
                appendLine("  at ${f.className}.${f.methodName}(${f.fileName ?: "?"}:${f.lineNumber})")
            }
            if (t.stackTrace.size > MAX_FRAMES) appendLine("  … ${t.stackTrace.size - MAX_FRAMES} more")
            t = t.cause
            depth++
        }
    }
}
