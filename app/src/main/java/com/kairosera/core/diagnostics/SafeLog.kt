package com.kairosera.core.diagnostics

import android.util.Log
import com.kairosera.BuildConfig

/**
 * The only logging entry point. It accepts an event name and non-personal numbers or enum
 * names only: never task titles, notes, journal text, health values or notification contents.
 * Verbose output exists only in debug builds.
 */
object SafeLog {
    private const val TAG = "Kairos"

    fun event(name: String, vararg fields: Pair<String, Any?>) {
        if (!BuildConfig.DEBUG) return
        Log.d(TAG, format(name, fields))
    }

    fun error(name: String, error: Throwable, vararg fields: Pair<String, Any?>) {
        // The exception class is logged, never its message: messages can echo user data.
        Log.w(TAG, format(name, fields) + " error=" + error.javaClass.name)
    }

    private fun format(name: String, fields: Array<out Pair<String, Any?>>) =
        name + fields.joinToString(prefix = " ", separator = " ") { (k, v) -> "$k=${sanitize(v)}" }

    private fun sanitize(v: Any?): String = when (v) {
        null -> "null"
        is Number, is Boolean, is Enum<*> -> v.toString()
        else -> "<redacted>"
    }
}
