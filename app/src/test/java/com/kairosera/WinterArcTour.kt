package com.kairosera

import android.graphics.Bitmap
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kairosera.domain.winterarc.FocusSession
import com.kairosera.domain.winterarc.HabitKind
import com.kairosera.domain.winterarc.StudyCategory
import com.kairosera.domain.winterarc.StudyTask
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.LocalTime

/**
 * Walks every Winter Arc screen with twelve days of history so a crash in any of them fails the
 * build. With `-Pscreens=<dir>` it also saves a PNG of each screen for design review.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class WinterArcTour {
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
            bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).also { b ->
                val canvas = android.graphics.Canvas(b)
                v.draw(canvas)
                val d = org.robolectric.shadows.ShadowDialog.getLatestDialog()
                val dv = d?.window?.decorView
                if (d != null && d.isShowing && dv != null && dv.width > 0) {
                    canvas.save()
                    if (dv.width < v.width) canvas.drawColor(android.graphics.Color.argb(150, 0, 0, 0))
                    canvas.translate((v.width - dv.width) / 2f, (v.height - dv.height) / 2f)
                    dv.draw(canvas)
                    canvas.restore()
                }
            }
        }
        File(dir, "wa-$name.png").outputStream().use { bmp!!.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("SCREEN saved wa-$name")
    }

    /** The bottom bar item itself, not a same-named header. */
    private fun navTab(label: String) {
        val nodes = rule.onAllNodesWithText(label).fetchSemanticsNodes()
        val root = rule.onAllNodes(isRoot()).fetchSemanticsNodes().first().boundsInRoot
        val i = nodes.indexOfFirst { it.boundsInRoot.top > root.bottom * 0.85f }
        rule.onAllNodesWithText(label)[if (i >= 0) i else nodes.lastIndex].performClick()
        rule.mainClock.advanceTimeBy(800)
    }

    private fun back() = rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

    private fun seed(c: AppContainer) = runBlocking {
        val today = LocalDate.now()
        c.settings.completeOnboarding("Chandan", emptySet())
        val repo = c.winterArc
        repo.startArc(today.minusDays(11), HabitKind.entries.toSet())
        c.winterPrefs.setOnboarded()
        for (i in 11 downTo 0) {
            val d = today.minusDays(i.toLong())
            val good = i % 4 != 1
            repo.addWater(d, if (good) 3000 else 1500)
            repo.setSteps(d, if (good) 10_400 else 6_240)
            repo.setChecked(HabitKind.ZERO_SUGAR, d, good)
            repo.setChecked(HabitKind.COLD_SHOWER, d, i % 3 != 0)
            repo.setChecked(HabitKind.DIGITAL_DETOX, d, true)
            repo.setWake(d, if (good) LocalTime.of(4, 32) else LocalTime.of(5, 27))
            if (i > 0) repo.logReading(d, if (good) 32 else 15, 12)
            if (i > 0 && good) repo.saveFocus(FocusSession(date = d, durationSeconds = 125 * 60, completed = true, task = "Project work"))
            if (i == 0) break
        }
        listOf("Java Collections deep dive" to StudyCategory.JAVA, "Arrays & Hashing practice" to StudyCategory.DSA, "LLM basics" to StudyCategory.AI_LLM)
            .forEachIndexed { n, (title, cat) ->
                repo.saveStudyTask(StudyTask(date = today, title = title, category = cat, durationMinutes = 45, completed = n == 0, position = n, source = StudyTask.SOURCE_MANUAL))
            }
        repo.addWater(today, 1500)
    }

    // Preferences are process-wide in tests; leave Winter Arc off for the test classes that follow.
    @After
    fun tearDown(): Unit = runBlocking { (rule.activity.application as KairosApp).container.winterPrefs.clear(); Unit }

    @Test(timeout = 300_000)
    fun tour() {
        val c = (rule.activity.application as KairosApp).container
        seed(c)
        rule.mainClock.advanceTimeBy(2500)
        waitForText("Discipline today")
        shot("01-home")
        rule.onAllNodesWithText("A day counts", substring = true).onFirst().performScrollTo()
        shot("01b-home-grid")

        navTab("Habits"); waitForText("Sleep discipline", timeoutMs = 10_000); shot("02-habits")
        rule.onAllNodesWithText("Water Drink").onFirst().performClick(); rule.mainClock.advanceTimeBy(800); shot("02b-water-sheet")
        back(); rule.mainClock.advanceTimeBy(800)

        navTab("Study"); waitForText("Study Plan"); shot("03-study")
        rule.onNodeWithText("This Week").performClick(); rule.mainClock.advanceTimeBy(600); shot("03b-study-week")
        rule.onNodeWithText("All Topics").performClick(); rule.mainClock.advanceTimeBy(600); shot("03c-study-all")
        rule.onNodeWithText("Today").performClick(); rule.mainClock.advanceTimeBy(600)
        rule.onAllNodesWithText("Start Focus Session", substring = true).onFirst().performScrollTo()
        rule.onNodeWithText("Start Focus Session").performClick(); waitForText("Start Focus"); shot("04-focus")
        back(); rule.mainClock.advanceTimeBy(800)

        navTab("Calendar"); waitForText("Today's Progress"); shot("05-calendar")
        rule.onAllNodesWithText("Edit this day", substring = true).onFirst().performScrollTo(); shot("05b-calendar-day")

        navTab("Stats"); waitForText("habits completed"); shot("06-stats")
        rule.onNodeWithText("Month").performClick(); waitForText("/ 310 habits"); shot("06b-stats-month")
        rule.onNodeWithText("All Time").performClick(); rule.mainClock.advanceTimeBy(600)
        rule.onAllNodesWithText("See all 90 days", substring = true).onFirst().performScrollTo()
        shot("06c-stats-all")
        rule.onNodeWithText("See all 90 days").performClick(); waitForText("Current streak"); shot("07-ninety")
        back(); rule.mainClock.advanceTimeBy(800)

        navTab("Home"); waitForText("Discipline today")
        rule.onNode(hasContentDescription("Winter Arc settings")).performScrollTo().performClick(); waitForText("Start date"); shot("08-settings")
        rule.onAllNodesWithText("Reminders", substring = true).onFirst().performScrollTo(); shot("08b-settings-habits")
        back(); rule.mainClock.advanceTimeBy(800)

        rule.onAllNodesWithText("Speak", substring = true).onFirst().performScrollTo()
        rule.onAllNodesWithText("Speak", substring = true).onFirst().performClick()
        rule.waitUntil(15_000) { rule.onAllNodes(hasContentDescription("Another topic")).fetchSemanticsNodes().isNotEmpty() }
        shot("09-speak")
    }
}
