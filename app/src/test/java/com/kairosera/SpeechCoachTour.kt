package com.kairosera

import android.graphics.Bitmap
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kairosera.data.speech.SpeechSession
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Instant
import java.time.LocalDate

/**
 * Walks the speaking coach screens with saved reports (Gemma itself can't run here): the speak
 * screen, the four report tabs, progress and coach settings. With `-Pscreens=<dir>` it saves PNGs.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class SpeechCoachTour {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val outDir: File? = System.getProperty("kairos.screens")?.let(::File)

    private fun waitForText(text: String, timeoutMs: Long = 15_000) {
        rule.waitUntil(timeoutMs) { rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun shot(name: String) {
        rule.waitForIdle()
        val dir = outDir ?: return
        dir.mkdirs()
        var bmp: Bitmap? = null
        rule.activityRule.scenario.onActivity { a ->
            val v = a.window.decorView
            bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).also { v.draw(android.graphics.Canvas(it)) }
        }
        File(dir, "sc-$name.png").outputStream().use { bmp!!.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("SCREEN saved sc-$name")
    }

    private fun back() = rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
    private fun click(text: String) = rule.onAllNodesWithText(text, substring = true).onFirst().performScrollTo().performClick().also { rule.mainClock.advanceTimeBy(800) }

    @After
    fun tearDown(): Unit = (rule.activity.application as KairosApp).container.let { c ->
        ArcSeed.reset(c)
        runBlocking { c.speechPrefs.clear() }
    }

    @Test(timeout = 300_000)
    fun tour() {
        val c = (rule.activity.application as KairosApp).container
        ArcSeed.seed(c)
        runBlocking {
            val today = LocalDate.now()
            listOf(5.4, 6.1, 6.0, 6.8, 7.2).forEachIndexed { i, score ->
                c.speechSessions.save(report(today.minusDays((8 - 2 * i).toLong()), score, last = i == 4))
            }
        }
        rule.mainClock.advanceTimeBy(2500)
        waitForText("Discipline today")
        rule.onAllNodesWithText("Speak", substring = true).onFirst().performScrollTo().performClick()
        rule.waitUntil(15_000) { rule.onAllNodes(hasContentDescription("Another topic")).fetchSemanticsNodes().isNotEmpty() }
        shot("01-speak-no-model")

        click("Latest report"); waitForText("Detailed Scores"); shot("02-report-overview")
        rule.onAllNodesWithText("Long pauses", substring = true).onFirst().performScrollTo(); shot("02b-report-stats")
        click("Did Well"); waitForText("Your strengths"); shot("03-report-well")
        click("Improve"); waitForText("Actionable feedback"); shot("04-report-improve")
        rule.onAllNodesWithText("Try this next time").onFirst().performScrollTo(); shot("04b-report-improve-more")
        click("Next"); waitForText("Your Next 1-Minute Challenge"); shot("05-report-next")
        click("View my progress"); waitForText("History"); shot("06-progress")
        back(); rule.mainClock.advanceTimeBy(800)
        back(); rule.mainClock.advanceTimeBy(800)

        rule.onNode(hasContentDescription("Coach settings")).performClick()
        waitForText("Import model file"); shot("07-coach-setup")
        rule.onAllNodesWithText("Keep my recordings").onFirst().performScrollTo(); shot("07b-coach-setup-more")
        back(); rule.mainClock.advanceTimeBy(800)

        // Stats links to speech progress once reports exist.
        rule.onNodeWithText("Stats").performClick(); rule.mainClock.advanceTimeBy(800)
        waitForText("habits completed")
        rule.onAllNodesWithText("Speech Progress").onFirst().performScrollTo(); shot("08-stats-card")
    }

    private fun report(date: LocalDate, score: Double, last: Boolean) = SpeechSession(
        date = date, createdAt = Instant.now().minusSeconds((LocalDate.now().toEpochDay() - date.toEpochDay()) * 86_400), topic = "Why mornings matter",
        speakingSessionId = null, durationSeconds = 60, overallScore = score, modelOverallScore = score.toInt(),
        clarityScore = 8, structureScore = 6, vocabularyScore = 7, grammarScore = if (last) null else 8, concisenessScore = 7,
        fillerWords = listOf("um", "like", "you know"), strengths = listOf("Clear and confident opening.", "Good use of a personal example.", "You stayed on topic."),
        improvements = listOf("Reduce filler words: pause instead of saying \"um\".", "Add one more example to support your point.", "End with a short summary of your main point."),
        nextExercise = "Explain one idea using point, example, conclusion with no fillers.", summary = "A clear speech with a good example; the ending could be stronger.",
        transcript = "So um today I want to talk about why mornings matter to me…", wordsPerMinute = 132, fillerSounds = 5, longPauses = 2,
        voicedSeconds = 51.0, backend = "GPU",
    )
}
