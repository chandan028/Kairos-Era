package com.kairosera

import android.graphics.Bitmap
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import com.kairosera.domain.journal.JournalEntry
import com.kairosera.domain.journal.Mood
import com.kairosera.domain.model.Task
import com.kairosera.domain.tracker.FieldValue
import com.kairosera.domain.tracker.MeasurementType
import com.kairosera.domain.tracker.TrackerEntry
import com.kairosera.domain.usecase.SaveTask
import androidx.compose.ui.test.onLast
import kotlinx.coroutines.flow.first

/**
 * Design review aid, not a regression test: walks the main screens with example data and saves
 * PNGs. Runs only with `./gradlew :app:testDebugUnitTest --tests '*ScreenshotTour*' -Pscreens=/some/dir`.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTour {
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
        File(dir, "$name.png").outputStream().use { bmp!!.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("SCREEN saved $name")
    }

    private fun tab(label: String) = rule.onAllNodesWithText(label).onFirst().performClick()

    /** The bottom bar item itself: a dismissed sheet can leave same-named nodes in the tree. */
    private fun navTab(label: String) {
        val nodes = rule.onAllNodesWithText(label).fetchSemanticsNodes()
        val root = rule.onAllNodes(androidx.compose.ui.test.isRoot()).fetchSemanticsNodes().first().boundsInRoot
        val i = nodes.indexOfFirst { it.boundsInRoot.bottom <= root.bottom && it.boundsInRoot.top > root.bottom * 0.85f && it.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.Text) { null }?.size == 1 }
        rule.onAllNodesWithText(label)[i].performClick()
    }

    @Test(timeout = 300_000)
    fun tour() {
        assumeTrue(outDir != null)
        waitForText("Your time is yours.")
        rule.mainClock.advanceTimeBy(2500) // past the splash
        shot("00a-welcome")
        rule.onNodeWithText("Let's begin").performClick()
        waitForText("What matters to you")
        rule.onNodeWithText("Learn & study").performClick()
        rule.onNodeWithText("Health & movement").performClick()
        rule.mainClock.advanceTimeBy(600)
        shot("00b-focus")
        rule.onNodeWithText("Continue").performClick()
        waitForText("Start with one thing.")
        rule.onAllNodes(hasSetTextAction()).onFirst().performTextInput("Plan the week")
        shot("00c-first")
        rule.onNodeWithText("Add to my day").performClick()
        waitForText("Want Kairos Era")
        shot("00d-reminders")
        rule.onNodeWithText("Not now").performClick()
        waitForText("Your day is ready.")
        rule.mainClock.advanceTimeBy(600)
        shot("00e-ready")
        val c = (rule.activity.application as KairosApp).container
        kotlinx.coroutines.runBlocking { c.settings.setName("Chandan"); c.addSampleContent() }
        rule.onNodeWithText("Open Kairos Era").performClick()
        waitForText("Chandan")
        shot("01-home")
        step("quick add") {
            scrollTo(hasText("Quick add")); rule.onNodeWithText("Quick add").performClick(); waitForText("Log progress"); shot("02-quick-add")
            rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        }
        step("today") { tab("Today"); rule.mainClock.advanceTimeBy(1000); shot("03-today") }
        step("add task") {
            scrollTo(hasContentDescription("New task")); rule.onNode(hasContentDescription("New task")).performClick()
            waitForText("What needs to be done?"); shot("04-add-task")
            rule.onNode(hasContentDescription("Close")).performClick()
        }
        step("calendar") {
            rule.onNode(hasContentDescription("Show calendar")).performClick(); rule.mainClock.advanceTimeBy(800); shot("13-calendar")
            rule.onNode(hasContentDescription("Show timeline")).performClick()
        }
        step("track") { tab("Track"); waitForText("ACTIVE"); shot("05-track"); scrollTo(hasText("Create your own tracker")); shot("05b-track") }
        step("study") {
            rule.onNodeWithText("Java backend").performClick(); waitForText("OVERALL"); shot("06-study")
            scrollTo(hasText("TOPICS")); shot("06b-study-topics")
            rule.onNode(hasContentDescription("Back")).performClick()
        }
        step("fitness") {
            rule.onNodeWithText("Fitness").performClick(); waitForText("DAILY LOG"); shot("08-fitness")
            scrollTo(hasText("Save")); shot("08b-fitness")
            rule.onNode(hasContentDescription("Back")).performClick()
        }
        step("templates") {
            rule.onNode(hasContentDescription("New tracker")).performClick(); waitForText("Start from blank"); shot("07-create")
            rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        }
        step("more") { tab("More"); rule.mainClock.advanceTimeBy(1000); shot("14-more") }
        step("read") { rule.onNodeWithText("Read").performClick(); waitForText("CURRENTLY"); shot("09-read"); scrollTo(hasText("MY LIBRARY", substring = true)); shot("09b-read") }
        step("book") {
            rule.onNodeWithText("Continue reading").performClick(); waitForText("Log reading"); shot("10-book")
            scrollTo(hasText("NOTES", substring = true)); shot("10b-book")
            rule.onNode(hasContentDescription("Back")).performClick()
            rule.onNode(hasContentDescription("Back")).performClick()
        }
        // Six weeks of history, then the reflection screens in the night theme of Chan's mockups.
        kotlinx.coroutines.runBlocking { seedHistory(c); c.settings.setThemeMode(com.kairosera.core.settings.ThemeMode.DARK) }
        rule.mainClock.advanceTimeBy(1000)
        step("journal") {
            navTab("Journal"); waitForText("Continue writing"); rule.mainClock.advanceTimeBy(800); shot("16-journal")
            scrollTo(hasText("AUGUST 2026")); rule.mainClock.advanceTimeBy(500); shot("16b-journal")
            scrollTo(hasText("Continue writing"))
        }
        step("journal editor") {
            rule.onNodeWithText("Continue writing").performClick()
            waitForText("Save reflection"); rule.mainClock.advanceTimeBy(800); shot("17-journal-editor")
            rule.onNode(hasContentDescription("Close")).performClick()
        }
        step("statistics") {
            navTab("More"); waitForText("Statistics"); rule.onNodeWithText("Statistics").performClick()
            waitForText("YOUR CONSISTENCY"); rule.mainClock.advanceTimeBy(1200); shot("18-stats")
            rule.onNodeWithText("YOUR AREAS").performScrollTo(); rule.mainClock.advanceTimeBy(800); shot("18b-stats-areas")
            rule.onNodeWithText("YOUR MOMENTUM").performScrollTo(); shot("18c-stats-momentum")
            rule.onNodeWithText("Your data stays", substring = true).performScrollTo(); shot("18d-stats-milestones")
            rule.onNodeWithText("1Y").performScrollTo().performClick(); rule.mainClock.advanceTimeBy(1200)
            rule.onNodeWithText("ACTIVITY").performScrollTo(); shot("18e-stats-year")
            rule.onNode(hasContentDescription("Back")).performClick()
        }
        step("life calendar") {
            waitForText("Life Calendar"); rule.onNodeWithText("Life Calendar").performClick()
            waitForText("Today's story"); rule.mainClock.advanceTimeBy(800); shot("19-calendar")
            rule.onNodeWithText("View full day").performScrollTo(); shot("19b-calendar-story")
            rule.onNodeWithText("View full day").performClick(); waitForText("TIMELINE"); rule.mainClock.advanceTimeBy(800); shot("20-day")
            rule.onNode(hasContentDescription("Back")).performClick()
            waitForText("Today's story")
            rule.onNode(hasContentDescription("More options")).performClick(); rule.onNodeWithText("View year").performClick()
            waitForText("active days in"); rule.mainClock.advanceTimeBy(800); shot("21-year")
            rule.onNode(hasContentDescription("Back")).performClick()
        }
        step("stats light") {
            kotlinx.coroutines.runBlocking { c.settings.setThemeMode(com.kairosera.core.settings.ThemeMode.LIGHT) }
            rule.onNodeWithText("Statistics").performClick(); waitForText("YOUR CONSISTENCY"); rule.mainClock.advanceTimeBy(1200); shot("22-stats-light")
            rule.onNode(hasContentDescription("Back")).performClick()
        }
        step("widgets") { widgets() }
    }

    /** Renders the four widgets as a launcher would, stacked on a cream wallpaper stand-in. */
    private fun widgets() {
        val dir = outDir ?: return
        rule.activityRule.scenario.onActivity { a ->
            val c = (a.application as KairosApp).container
            val snap = kotlinx.coroutines.runBlocking { com.kairosera.feature.widgets.WidgetSnapshot.load(c, java.time.LocalDate.now()) }
            val d = a.resources.displayMetrics.density
            val specs = listOf(
                com.kairosera.feature.widgets.MotivationWidget() to 150,
                com.kairosera.feature.widgets.TasksWidget() to 290,
                com.kairosera.feature.widgets.ProgressWidget() to 250,
                com.kairosera.feature.widgets.QuickAddWidget() to 84,
            )
            val w = (360 * d).toInt()
            val gap = (16 * d).toInt()
            val total = specs.sumOf { (it.second * d).toInt() + gap } + gap
            val bmp = Bitmap.createBitmap(w + 2 * gap, total, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bmp)
            canvas.drawColor(0xFF7A8BA8.toInt())
            var y = gap
            specs.forEach { (provider, h) ->
                val hp = (h * d).toInt()
                val parent = android.widget.FrameLayout(a)
                val host = android.view.ContextThemeWrapper(a.applicationContext, android.R.style.Theme_DeviceDefault_Light)
                val v = provider.render(a, snap).apply(host, parent)
                v.measure(android.view.View.MeasureSpec.makeMeasureSpec(w, android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(hp, android.view.View.MeasureSpec.EXACTLY))
                v.layout(0, 0, w, hp)
                canvas.save(); canvas.translate(gap.toFloat(), y.toFloat()); v.draw(canvas); canvas.restore()
                y += hp + gap
            }
            dir.mkdirs()
            File(dir, "15-widgets.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            println("SCREEN saved 15-widgets")
        }
    }

    /** Past days with tasks done, study and fitness logs, reading and reflections, with a few quiet days between. */
    private suspend fun seedHistory(c: AppContainer) {
        val today = java.time.LocalDate.now()
        val trackers = c.trackers.observeActive().first()
        val study = trackers.first { it.name == "Java backend" }
        val fit = trackers.first { it.name == "Fitness" }
        val book = c.books.observeBooks().first().first { it.status == com.kairosera.domain.reading.BookStatus.READING }
        val notes = listOf(
            "Finally understood HashMap internals. Felt good to see it click.",
            "Slow start, but the evening walk reset my head.",
            "Read before bed instead of scrolling. Small win.",
            "Too many meetings. Still finished the one thing that mattered.",
            "Revised generics and wrote two small examples.",
        )
        val moods = listOf(Mood.GOOD, Mood.OKAY, Mood.GREAT, Mood.LOW, Mood.GOOD)
        for (ago in 0L..44L) {
            val d = today.minusDays(ago)
            val quiet = ago % 9 == 4L || ago % 13 == 6L || ago > 30 && ago % 3 == 0L
            if (quiet) continue
            val at = d.atTime(19, 30).atZone(java.time.ZoneId.systemDefault()).toInstant()
            repeat((ago % 4 + 1).toInt()) { i ->
                val r = c.saveTask(Task(title = "Focus block ${i + 1}", date = d))
                if (r is SaveTask.Result.Saved && (i < 3 || ago % 2 == 0L)) c.setOccurrenceDone(r.id, d, true)
            }
            if (ago % 2 == 1L || ago % 5 == 0L) {
                val f = study.enabledFields.first { it.type == MeasurementType.DURATION }
                c.trackers.saveEntry(TrackerEntry(study.id, d, mapOf(f.id to FieldValue(f.id, number = 30.0 + (ago % 4) * 15)), at))
            }
            if (ago % 3 != 2L) {
                val values = fit.enabledFields.mapNotNull { f ->
                    when (f.type) {
                        MeasurementType.NUMBER -> f.id to FieldValue(f.id, number = 4200.0 + ago * 173 % 5200)
                        MeasurementType.DURATION -> f.id to FieldValue(f.id, number = 20.0 + ago % 3 * 10)
                        MeasurementType.CHECKBOX -> f.id to FieldValue(f.id, number = 1.0)
                        else -> null
                    }
                }.toMap()
                c.trackers.saveEntry(TrackerEntry(fit.id, d, values, at))
            }
            if (ago == 0L) {
                c.journal.save(
                    JournalEntry(
                        date = d, mood = Mood.GOOD, createdAt = at, updatedAt = at,
                        text = "Finally understood how HashMap handles collisions. The evening walk cleared my head, and I read before bed.",
                        lesson = "Short, focused sessions work better than one long one.",
                        tomorrow = "Revise TreeMap before lunch",
                    ),
                )
            }
            if (ago % 2 == 0L) c.books.logSession(book.id, pages = 10 + (ago % 5).toInt() * 6, minutes = 20 + (ago % 4).toInt() * 10, date = d, at = at)
            if (ago % 3 == 0L && ago > 0) {
                val k = (ago / 3 % notes.size).toInt()
                c.journal.save(JournalEntry(date = d, mood = moods[k], text = notes[k], lesson = "Short sessions beat long ones.", createdAt = at, updatedAt = at))
            }
        }
    }

    private fun scrollTo(matcher: androidx.compose.ui.test.SemanticsMatcher) {
        rule.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(matcher)
    }

    private fun step(name: String, block: () -> Unit) {
        runCatching(block).onFailure { println("SCREEN fail $name: $it"); runCatching { shot("fail-${name.replace(' ', '-')}") } }
    }
}
