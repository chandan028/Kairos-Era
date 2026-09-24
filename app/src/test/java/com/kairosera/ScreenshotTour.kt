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

    @Test(timeout = 300_000)
    fun tour() {
        assumeTrue(outDir != null)
        waitForText("Your time is yours.")
        rule.onNodeWithText("Begin").performClick()
        rule.onNode(hasSetTextAction() and hasText("Name or nickname")).performTextInput("Chandan")
        rule.onNode(hasSetTextAction() and hasText("One thing to do today")).performTextInput("Plan the week")
        rule.onNodeWithText("Next").performScrollTo().performClick()
        rule.onNodeWithText("Not now").performClick()
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
        step("read") { tab("Read"); waitForText("CURRENTLY"); shot("09-read"); scrollTo(hasText("MY LIBRARY", substring = true)); shot("09b-read") }
        step("book") {
            rule.onNodeWithText("Continue reading").performClick(); waitForText("Log reading"); shot("10-book")
            scrollTo(hasText("NOTES", substring = true)); shot("10b-book")
            rule.onNode(hasContentDescription("Back")).performClick()
        }
        step("more") { tab("More"); rule.mainClock.advanceTimeBy(1000); shot("14-more") }
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

    private fun scrollTo(matcher: androidx.compose.ui.test.SemanticsMatcher) {
        rule.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(matcher)
    }

    private fun step(name: String, block: () -> Unit) {
        runCatching(block).onFailure { println("SCREEN fail $name: $it") }
    }
}
