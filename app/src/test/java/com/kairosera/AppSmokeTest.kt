package com.kairosera

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * End-to-end smoke test on the JVM: first run, onboarding with examples, Home, Today,
 * creating a task, More/Settings/Trash, Track and Read. Catches crashes on real screens.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class AppSmokeTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun stage(name: String) = println("SMOKE stage: $name")

    private fun waitForText(text: String, timeoutMs: Long = 10_000) {
        stage("wait '$text'")
        try {
            rule.waitUntil(timeoutMs) { rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
        } catch (e: Throwable) {
            val texts = mutableListOf<String>()
            fun walk(n: androidx.compose.ui.semantics.SemanticsNode) {
                n.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.Text) { null }?.let { texts += it.joinToString() }
                n.children.forEach(::walk)
            }
            rule.onAllNodes(androidx.compose.ui.test.isRoot()).fetchSemanticsNodes().forEach(::walk)
            println("SMOKE screen texts: $texts")
            throw e
        }
    }

    private fun waitForDesc(desc: String, timeoutMs: Long = 10_000) {
        stage("wait desc '$desc'")
        rule.waitUntil(timeoutMs) { rule.onAllNodesWithContentDescription(desc).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun scrollTo(matcher: androidx.compose.ui.test.SemanticsMatcher) {
        rule.waitUntil(10_000) { rule.onAllNodes(hasScrollToNodeAction()).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(matcher)
    }

    @Test(timeout = 240_000)
    fun firstRunThroughCoreScreens() {
        waitForText("Your time is yours.")
        rule.onNodeWithText("Let's begin").performClick()
        waitForText("What matters to you")
        rule.onNodeWithText("Skip for now").performClick()
        waitForText("Start with one thing.")
        rule.onAllNodes(hasSetTextAction()).onFirst().performTextInput("Plan the week")
        rule.onNodeWithText("Add to my day").performClick()
        waitForText("Want Kairos Era")
        rule.onNodeWithText("Not now").performClick()
        waitForText("Your day is ready.")
        waitForText("Plan the week")
        // Back returns to the previous step with answers kept, then forward again.
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        waitForText("Want Kairos Era")
        rule.onNodeWithText("Not now").performClick()
        waitForText("Your day is ready.")
        val c = (rule.activity.application as KairosApp).container
        kotlinx.coroutines.runBlocking { c.settings.setName("Chan"); c.addSampleContent() }
        rule.onNodeWithText("Open Kairos Era").performClick()

        // Home
        waitForText("Chan")
        scrollTo(hasText("Quick add"))
        rule.onNodeWithText("Quick add").assertIsDisplayed()

        // Today, with the example content and the first task
        rule.onAllNodesWithText("Today").onFirst().performClick()
        waitForText("Plan the week")
        waitForText("Workout")

        // Calendar view renders, then back to the timeline
        rule.onNodeWithContentDescription("Show calendar").performClick()
        waitForDesc("Show timeline")
        rule.onNodeWithContentDescription("Show timeline").performClick()
        waitForText("Workout")

        // Create a task
        scrollTo(hasContentDescription("New task"))
        rule.onNodeWithContentDescription("New task").performClick()
        waitForText("What needs to be done?")
        rule.onAllNodes(hasSetTextAction()).onFirst().performTextInput("Smoke test task")
        rule.onNodeWithText("Create").performScrollTo().performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("What needs to be done?").fetchSemanticsNodes().isEmpty() }
        waitForText("Smoke test task")

        // Validation: empty title is rejected inline
        scrollTo(hasContentDescription("New task"))
        rule.onNodeWithContentDescription("New task").performClick()
        waitForText("What needs to be done?")
        rule.onNodeWithText("Save").performClick()
        waitForText("Give the task a title.")
        rule.onNodeWithContentDescription("Close").performClick()

        // More, Settings, Trash
        rule.onNodeWithText("More").performClick()
        rule.onNodeWithText("Settings").performClick()
        waitForText("Language")
        rule.onNodeWithText("App lock").performScrollTo()
        rule.onNodeWithText("Backup and restore").performScrollTo().performClick()
        waitForText("Create backup")
        rule.onNodeWithContentDescription("Back").performClick()
        waitForText("Language")
        rule.onNodeWithText("Privacy policy").performScrollTo().performClick()
        waitForText("At a glance")
        rule.onNodeWithText("Contact").performScrollTo()
        rule.onNodeWithContentDescription("Back").performClick()
        waitForText("Language")
        rule.onNodeWithContentDescription("Back").performClick()
        rule.onNodeWithText("Trash").performClick()
        waitForText("Trash is empty")
        rule.onNodeWithContentDescription("Back").performClick()

        // Track: example trackers, logging a day, and a new tracker from a template
        rule.onNodeWithText("Track").performClick()
        waitForText("Java backend")
        rule.onNodeWithText("Fitness").performClick()
        waitForText("Workout done")
        rule.onAllNodes(isToggleable()).onFirst().performClick() // the main goal, "Workout done"
        scrollTo(hasText("Save"))
        rule.onNodeWithText("Save").performClick()
        waitForText("Logged. Nice work.")
        rule.onNodeWithContentDescription("Back").performClick()
        waitForText("Java backend")
        rule.onNodeWithText("Java backend").performClick()
        waitForText("OVERALL")
        rule.onNodeWithContentDescription("Back").performClick()
        waitForDesc("New tracker")
        rule.onNodeWithContentDescription("New tracker").performClick()
        waitForText("Start from blank")
        rule.onNodeWithText("Habit").performClick()
        waitForText("Save")
        rule.onNodeWithText("Save").performClick()
        waitForText("Last 5 weeks".uppercase(), timeoutMs = 15_000)
        rule.onNodeWithContentDescription("Back").performClick()

        // Read: example book and its reading status (reached from More now that Journal has the tab)
        rule.onNodeWithText("More").performClick()
        waitForText("Read")
        rule.onNodeWithText("Read").performClick()
        waitForText("Continue reading")
        rule.onNodeWithText("Continue reading").performClick()
        waitForText("Page 48 of 320")
        rule.onAllNodesWithText("Paused", useUnmergedTree = true).onLast().performClick()
        rule.onAllNodesWithText("Reading", useUnmergedTree = true).onLast().performClick()
        // Reading sessions are logged from a sheet of counters (no text fields, which never reach
        // idle under Robolectric); session logging itself is covered by MigrationTest.
        rule.onAllNodesWithText("Finished", useUnmergedTree = true).onLast().performClick()
        waitForText("Page 320 of 320")
        rule.onNodeWithContentDescription("Back").performClick()
        waitForText("Your personal library")
        rule.onNodeWithContentDescription("Back").performClick()

        // Journal: one tap on a mood starts today's reflection; saving brings it into the list
        rule.onAllNodesWithText("Journal").onLast().performClick() // the tab, not the More row
        waitForText("Write today's reflection")
        rule.onNodeWithContentDescription("Good").performClick()
        waitForText("Save reflection")
        rule.onNodeWithText("Save").performClick() // the top bar's Save; the form has text fields, which never idle under Robolectric
        waitForText("Continue writing")

        // Statistics and the Life Calendar are built from the same records
        rule.onNodeWithText("More").performClick()
        waitForText("Statistics")
        rule.onNodeWithText("Statistics").performClick()
        waitForText("Your consistency".uppercase())
        rule.onNodeWithText("90D").performClick()
        waitForText("Last 90 days".uppercase())
        rule.onNodeWithContentDescription("Back").performClick()
        waitForText("Life Calendar")
        rule.onNodeWithText("Life Calendar").performClick()
        waitForText("Today's story")
        rule.onNodeWithText("View full day").performScrollTo().performClick()
        waitForText("Timeline".uppercase())
        rule.onNodeWithContentDescription("Back").performClick()
        waitForText("Today's story")
        rule.onNodeWithContentDescription("Back").performClick()

        // Home follows today's trackers
        rule.onNodeWithText("Home").performClick()
        waitForText("Java backend")
    }
}
