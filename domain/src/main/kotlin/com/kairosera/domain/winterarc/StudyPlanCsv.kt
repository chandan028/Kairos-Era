package com.kairosera.domain.winterarc

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * Reads a study plan from CSV. Two layouts are understood:
 *
 *  1. The prep-plan sheet layout (one row per day): Date, Day, Wk, Phase, Hrs, Track, Topic,
 *     Detailed Sub-Topics, Resource, Practice Problems, Spaced Revision, ... Each day becomes up to
 *     three tasks: learn the topic, practise the problems, and the spaced revision.
 *  2. A simple layout (one row per task): Date, Topic, Category, Duration, Notes[, Start].
 *
 * Header names decide the layout, so column order does not matter. Rows before [from] are skipped,
 * so a plan that is already underway is never recreated.
 */
object StudyPlanCsv {
    data class Result(val tasks: List<StudyTask>, val skippedBefore: Int, val unreadable: Int)

    fun parse(text: String, from: LocalDate?): Result {
        val rows = readRows(text).filter { r -> r.any { it.isNotBlank() } }
        if (rows.isEmpty()) return Result(emptyList(), 0, 0)
        val header = rows.first().map { it.trim().lowercase(Locale.ROOT) }
        val body = rows.drop(1)
        val sheet = header.any { it == "track" } && header.any { it.startsWith("practice") }
        return if (sheet) parseSheet(header, body, from) else parseSimple(header, body, from)
    }

    private fun col(header: List<String>, vararg names: String): Int =
        header.indexOfFirst { h -> names.any { n -> h == n || h.startsWith(n) } }

    private fun parseSheet(header: List<String>, body: List<List<String>>, from: LocalDate?): Result {
        val cDate = col(header, "date")
        val cHrs = col(header, "hrs", "hours")
        val cTrack = col(header, "track")
        val cTopic = col(header, "topic")
        val cSub = col(header, "detailed")
        val cRes = col(header, "resource")
        val cPractice = col(header, "practice")
        val cRevision = col(header, "spaced", "revision")
        val out = ArrayList<StudyTask>()
        var skipped = 0
        var bad = 0
        for (r in body) {
            val date = parseDate(r.getOrNull(cDate)) ?: run { bad++; null } ?: continue
            if (from != null && date.isBefore(from)) { skipped++; continue }
            val topic = r.getOrNull(cTopic).clean()
            if (topic.isEmpty()) { bad++; continue }
            val category = categoryFor(r.getOrNull(cTrack).clean())
            val total = ((r.getOrNull(cHrs)?.trim()?.toDoubleOrNull() ?: 1.0) * 60).toInt().coerceIn(15, 12 * 60)
            val practice = r.getOrNull(cPractice).clean().takeUnless { it == "-" }.orEmpty()
            val revision = r.getOrNull(cRevision).clean().takeUnless { it == "-" }.orEmpty()
            // Estimated split of the day's hours: revision 15 min, practice ~40%, the rest for the topic.
            val revMin = if (revision.isNotEmpty()) 15 else 0
            val practiceMin = if (practice.isNotEmpty()) ((total - revMin) * 0.4).toInt().coerceAtLeast(15) else 0
            val learnMin = (total - revMin - practiceMin).coerceAtLeast(15)
            val learnNotes = listOf(r.getOrNull(cSub).clean(), r.getOrNull(cRes).clean()).filter { it.isNotEmpty() }.joinToString("\n\n")
            var pos = 0
            out += StudyTask(date = date, title = topic, category = category, durationMinutes = learnMin, notes = learnNotes, position = pos++)
            if (practice.isNotEmpty()) {
                out += StudyTask(date = date, title = practiceTitle(practice), category = category, durationMinutes = practiceMin, notes = practice, position = pos++)
            }
            if (revision.isNotEmpty()) {
                out += StudyTask(date = date, title = revisionTitle(revision), category = category, durationMinutes = revMin, notes = revision, position = pos)
            }
        }
        return Result(out, skipped, bad)
    }

    private fun parseSimple(header: List<String>, body: List<List<String>>, from: LocalDate?): Result {
        val cDate = col(header, "date").takeIf { it >= 0 } ?: 0
        val cTopic = col(header, "topic", "title", "task").takeIf { it >= 0 } ?: 1
        val cCat = col(header, "category", "track")
        val cDur = col(header, "duration", "minutes", "estimated")
        val cNotes = col(header, "notes", "note")
        val cStart = col(header, "start", "time")
        val cDone = col(header, "done", "completed", "status")
        val out = ArrayList<StudyTask>()
        val positions = HashMap<LocalDate, Int>()
        var skipped = 0
        var bad = 0
        for (r in body) {
            val date = parseDate(r.getOrNull(cDate)) ?: run { bad++; null } ?: continue
            if (from != null && date.isBefore(from)) { skipped++; continue }
            val title = r.getOrNull(cTopic).clean()
            if (title.isEmpty()) { bad++; continue }
            val pos = positions.merge(date, 1, Int::plus)!! - 1
            out += StudyTask(
                date = date,
                title = title.take(300),
                category = categoryFor(r.getOrNull(cCat).clean()),
                durationMinutes = parseDuration(r.getOrNull(cDur)) ?: 60,
                notes = r.getOrNull(cNotes).clean().take(4000),
                position = pos,
                completed = r.getOrNull(cDone).clean().lowercase(Locale.ROOT) in setOf("yes", "y", "true", "done", "1", "completed", "✓"),
                startTime = parseTime(r.getOrNull(cStart)),
            )
        }
        return Result(out, skipped, bad)
    }

    private fun practiceTitle(practice: String): String {
        val problems = Regex("""\bLC\s*\d+""").findAll(practice).count()
        return if (problems > 0) "Practice: $problems problems" else "Practice: " + practice.substringBefore(';').substringBefore('.').take(80)
    }

    private fun revisionTitle(revision: String): String = "Revision: " + revision.substringBefore('|').trim().take(90)

    /** Sheet tracks and free-text categories onto the five study categories. */
    fun categoryFor(track: String): StudyCategory {
        val t = track.lowercase(Locale.ROOT)
        return when {
            t.isEmpty() -> StudyCategory.DSA
            "ai" in t.split(' ', '/', '-', '+').map { it.trim() } || "llm" in t -> StudyCategory.AI_LLM
            "hld" in t || "lld" in t || "design" in t -> StudyCategory.SYSTEM_DESIGN
            t.startsWith("dsa") || "dsa" in t -> StudyCategory.DSA
            "java" in t || "spring" in t || "concurrency" in t || "jvm" in t || "collections" in t -> StudyCategory.JAVA
            else -> StudyCategory.INTERVIEW
        }
    }

    private val DATE_FORMATS = listOf("EEE dd MMM yyyy", "EEE d MMM yyyy", "d MMM yyyy", "dd MMM yyyy", "yyyy-MM-dd", "dd/MM/yyyy", "d/M/yyyy", "dd-MM-yyyy", "MMM d, yyyy")
        .map { DateTimeFormatter.ofPattern(it, Locale.ENGLISH) }

    fun parseDate(raw: String?): LocalDate? {
        val s = raw?.trim()?.removeSuffix(" 00:00:00")?.takeIf { it.isNotEmpty() } ?: return null
        for (f in DATE_FORMATS) {
            try { return LocalDate.parse(s, f) } catch (_: DateTimeParseException) { }
        }
        return null
    }

    private fun parseDuration(raw: String?): Int? {
        val s = raw?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() } ?: return null
        val n = Regex("""\d+(\.\d+)?""").find(s)?.value?.toDoubleOrNull() ?: return null
        val minutes = if ("h" in s && "min" !in s) n * 60 else n
        return minutes.toInt().coerceIn(5, 12 * 60)
    }

    private fun parseTime(raw: String?): LocalTime? {
        val s = raw?.trim()?.substringBefore('-')?.substringBefore('–')?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return listOf("H:mm", "HH:mm", "h:mm a", "h:mma").firstNotNullOfOrNull { p ->
            try { LocalTime.parse(s.uppercase(Locale.ROOT), DateTimeFormatter.ofPattern(p, Locale.ENGLISH)) } catch (_: DateTimeParseException) { null }
        }
    }

    private fun String?.clean(): String = this?.trim()?.replace("\r", "").orEmpty()

    /** RFC 4180 CSV: quoted fields, doubled quotes, commas and newlines inside quotes. */
    fun readRows(text: String): List<List<String>> {
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        val s = text.removePrefix("﻿")
        while (i < s.length) {
            val c = s[i]
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < s.length && s[i + 1] == '"') { field.append('"'); i++ } else quoted = false
                } else field.append(c)
            } else when (c) {
                '"' -> quoted = true
                ',' -> { row.add(field.toString()); field.clear() }
                '\n' -> { row.add(field.toString()); field.clear(); rows.add(row); row = ArrayList() }
                '\r' -> Unit
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) { row.add(field.toString()); rows.add(row) }
        return rows
    }
}
