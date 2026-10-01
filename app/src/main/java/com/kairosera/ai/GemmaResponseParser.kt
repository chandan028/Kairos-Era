package com.kairosera.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlin.math.roundToInt

/** The coach's feedback, as Gemma returned it. A null score means "not_available". */
data class SpeechFeedback(
    val overallScore: Int?,
    val clarityScore: Int?,
    val structureScore: Int?,
    val vocabularyScore: Int?,
    val grammarScore: Int?,
    val concisenessScore: Int?,
    /** Null when Gemma said it could not detect filler words reliably. */
    val fillerWords: List<String>?,
    val strengths: List<String>,
    val improvements: List<String>,
    val nextExercise: String?,
    val summary: String?,
) {
    val categoryScores: List<Int?> get() = listOf(clarityScore, structureScore, vocabularyScore, grammarScore, concisenessScore)

    /**
     * The headline number ("7.2 / 10"): the mean of the category scores Gemma could give, so it
     * always matches the cards below it. Falls back to Gemma's own overall score.
     */
    val displayScore: Double?
        get() {
            val known = categoryScores.filterNotNull()
            if (known.isEmpty()) return overallScore?.toDouble()
            return (known.average() * 10).roundToInt() / 10.0
        }
}

sealed interface ParseResult {
    data class Success(val feedback: SpeechFeedback, val warnings: List<String>) : ParseResult
    data class Failure(val reason: ParseFailure, val missing: List<String> = emptyList()) : ParseResult
}

enum class ParseFailure { EMPTY, NO_JSON, INVALID_JSON, MISSING_FIELDS, NO_SCORES }

/**
 * Turns Gemma's reply into [SpeechFeedback] without ever throwing. Small models sometimes wrap
 * JSON in markdown fences, add a sentence before it, leave a trailing comma, write a score as
 * "8/10" or 7.5, or skip a list. All of that is tolerated; a reply with no usable scores is not.
 */
object GemmaResponseParser {
    const val NOT_AVAILABLE = "not_available"

    /** Must be present (a score may be "not_available"). Lists and the exercise may be missing. */
    val REQUIRED = listOf("overall_score", "clarity_score", "structure_score", "vocabulary_score", "grammar_score", "conciseness_score", "summary")

    private val json = Json { isLenient = true; ignoreUnknownKeys = true }

    fun parse(raw: String?): ParseResult {
        if (raw.isNullOrBlank()) return ParseResult.Failure(ParseFailure.EMPTY)
        val candidate = extractJsonObject(raw) ?: return ParseResult.Failure(ParseFailure.NO_JSON)
        val obj = decode(candidate) ?: decode(repair(candidate)) ?: return ParseResult.Failure(ParseFailure.INVALID_JSON)

        // Some models nest everything under one key, e.g. {"feedback": {...}}.
        val root = if (REQUIRED.none { it in obj } && obj.size == 1) (obj.values.first() as? JsonObject) ?: obj else obj
        val missing = REQUIRED.filter { it !in root }
        if (missing.isNotEmpty()) return ParseResult.Failure(ParseFailure.MISSING_FIELDS, missing)

        val warnings = mutableListOf<String>()
        fun score(key: String): Int? {
            val s = scoreOf(root[key])
            if (s == null && !isNotAvailable(root[key])) warnings += "unreadable:$key"
            return s
        }

        val feedback = SpeechFeedback(
            overallScore = score("overall_score"),
            clarityScore = score("clarity_score"),
            structureScore = score("structure_score"),
            vocabularyScore = score("vocabulary_score"),
            grammarScore = score("grammar_score"),
            concisenessScore = score("conciseness_score"),
            fillerWords = root["filler_words"].let { if (it == null || isNotAvailable(it)) null else stringList(it) },
            strengths = stringList(root["strengths"]),
            improvements = stringList(root["improvements"]),
            nextExercise = text(root["next_exercise"]),
            summary = text(root["summary"]),
        )
        if (feedback.overallScore == null && feedback.categoryScores.all { it == null }) return ParseResult.Failure(ParseFailure.NO_SCORES)
        return ParseResult.Success(feedback, warnings)
    }

    /** The first balanced {...} in [raw], ignoring braces inside strings and markdown fences around it. */
    fun extractJsonObject(raw: String): String? {
        val text = raw.replace("```json", "```").replace("```JSON", "```")
        var start = text.indexOf('{')
        while (start >= 0) {
            var depth = 0
            var inString = false
            var escaped = false
            for (i in start until text.length) {
                val c = text[i]
                if (inString) {
                    when {
                        escaped -> escaped = false
                        c == '\\' -> escaped = true
                        c == '"' -> inString = false
                    }
                    continue
                }
                when (c) {
                    '"' -> inString = true
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) return text.substring(start, i + 1)
                    }
                }
            }
            // Unbalanced (the reply was cut off): close it and let the decoder try.
            if (depth > 0) return text.substring(start) + "}".repeat(depth)
            start = text.indexOf('{', start + 1)
        }
        return null
    }

    private fun decode(s: String): JsonObject? = runCatching { json.parseToJsonElement(s) as? JsonObject }.getOrNull()

    /** Common slips: trailing commas and smart quotes. */
    private fun repair(s: String): String = s
        .replace(Regex(",\\s*([}\\]])"), "$1")
        .replace('“', '"').replace('”', '"')

    private fun isNotAvailable(e: JsonElement?): Boolean =
        e == null || e is JsonNull || (e is JsonPrimitive && e.isString && e.content.trim().lowercase().replace(' ', '_').let { it == NOT_AVAILABLE || it == "n/a" || it.isEmpty() })

    /** 1..10 from 8, 8.0, "8", "8/10", "8 / 10"; anything else (including 0 or 11) is not a score. */
    fun scoreOf(e: JsonElement?): Int? {
        if (e !is JsonPrimitive || isNotAvailable(e)) return null
        val number = e.doubleOrNull ?: e.contentOrNull?.trim()?.substringBefore('/')?.trim()?.toDoubleOrNull() ?: return null
        val rounded = number.roundToInt()
        return rounded.takeIf { it in 1..10 }
    }

    private fun text(e: JsonElement?): String? =
        (e as? JsonPrimitive)?.takeIf { it.isString && !isNotAvailable(it) }?.content?.trim()?.takeIf { it.isNotEmpty() }

    private fun stringList(e: JsonElement?): List<String> = when (e) {
        is JsonArray -> e.mapNotNull { item ->
            when (item) {
                is JsonPrimitive -> item.contentOrNull
                // {"title": "...", "detail": "..."} style items: keep the words.
                is JsonObject -> item.values.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.joinToString(": ")
                else -> null
            }?.trim()?.takeIf { it.isNotEmpty() && it.lowercase() != NOT_AVAILABLE }
        }
        is JsonPrimitive -> text(e)?.let(::listOf) ?: emptyList()
        else -> emptyList()
    }
}
