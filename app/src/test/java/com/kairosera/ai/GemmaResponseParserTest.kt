package com.kairosera.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GemmaResponseParserTest {
    private fun ok(raw: String): SpeechFeedback {
        val r = GemmaResponseParser.parse(raw)
        assertTrue("expected success, got $r", r is ParseResult.Success)
        return (r as ParseResult.Success).feedback
    }

    @Test fun parsesTheExactSchema() {
        val f = ok(CoachFakes.GOOD_JSON)
        assertEquals(7, f.overallScore)
        assertEquals(listOf(8, 6, 7, 8, 6), f.categoryScores)
        assertEquals(listOf("um", "like"), f.fillerWords)
        assertEquals(listOf("Clear opening"), f.strengths)
        assertEquals("Point, example, conclusion.", f.nextExercise)
        assertEquals(7.0, f.displayScore!!, 0.0)
    }

    @Test fun toleratesFencesProseAndTrailingCommas() {
        val raw = "Here is the feedback:\n```json\n" + CoachFakes.GOOD_JSON.replace("\"summary\":\"Good start.\"", "\"summary\":\"Good {start}.\",") + "\n```"
        assertEquals("Good {start}.", ok(raw).summary)
    }

    @Test fun scoresInOtherShapesAreRoundedOrDropped() {
        val f = ok("""{"overall_score":"8/10","clarity_score":7.6,"structure_score":"6","vocabulary_score":0,"grammar_score":11,"conciseness_score":"great","summary":"x"}""")
        assertEquals(8, f.overallScore)
        assertEquals(8, f.clarityScore)
        assertEquals(6, f.structureScore)
        assertNull(f.vocabularyScore)
        assertNull(f.grammarScore)
        assertNull(f.concisenessScore)
    }

    @Test fun notAvailableIsNullNotZero() {
        val f = ok("""{"overall_score":6,"clarity_score":"not_available","structure_score":6,"vocabulary_score":null,"grammar_score":"N/A","conciseness_score":6,"filler_words":"not_available","summary":"x"}""")
        assertNull(f.clarityScore)
        assertNull(f.vocabularyScore)
        assertNull(f.grammarScore)
        assertNull("filler words must not be invented", f.fillerWords)
        assertEquals(6.0, f.displayScore!!, 0.0)
    }

    @Test fun missingFieldsAreReported() {
        val r = GemmaResponseParser.parse("""{"overall_score":7,"clarity_score":7,"summary":"x"}""")
        assertTrue(r is ParseResult.Failure)
        r as ParseResult.Failure
        assertEquals(ParseFailure.MISSING_FIELDS, r.reason)
        assertTrue(r.missing.containsAll(listOf("structure_score", "grammar_score")))
    }

    @Test fun invalidAndEmptyRepliesFailWithoutThrowing() {
        assertEquals(ParseFailure.EMPTY, (GemmaResponseParser.parse("  ") as ParseResult.Failure).reason)
        assertEquals(ParseFailure.NO_JSON, (GemmaResponseParser.parse("I can't help with that.") as ParseResult.Failure).reason)
        assertEquals(ParseFailure.INVALID_JSON, (GemmaResponseParser.parse("{ overall_score: [ }") as ParseResult.Failure).reason)
        val allNa = """{"overall_score":"not_available","clarity_score":"not_available","structure_score":"not_available","vocabulary_score":"not_available","grammar_score":"not_available","conciseness_score":"not_available","summary":""}"""
        assertEquals(ParseFailure.NO_SCORES, (GemmaResponseParser.parse(allNa) as ParseResult.Failure).reason)
    }

    @Test fun truncatedReplyIsClosedAndParsed() {
        val cut = CoachFakes.GOOD_JSON.substringBefore("\"filler_words\"")
        val r = GemmaResponseParser.parse(cut)
        // Scores survive; summary is missing, so it is reported rather than invented.
        assertEquals(ParseFailure.MISSING_FIELDS, (r as ParseResult.Failure).reason)
        assertEquals(listOf("summary"), r.missing)
    }

    @Test fun nestedUnderOneKeyIsUnwrapped() {
        assertEquals(7, ok("""{"feedback": ${CoachFakes.GOOD_JSON}}""").overallScore)
    }
}
