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
 * creating a task, and the More/Settings/Trash screens. Catches crashes on real screens.
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

    @Test(timeout = 240_000)
    fun firstRunThroughCoreScreens() {
        waitForText("Your time is yours.")
        rule.onNodeWithText("Begin").performClick()
        rule.onNode(hasSetTextAction() and hasText("Name or nickname")).performTextInput("Chan")
        rule.onNode(hasSetTextAction() and hasText("One thing to do today")).performTextInput("Plan the week")
        rule.onNodeWithText("Next").performScrollTo().performClick()
        rule.onNodeWithText("Not now").performClick()

        // Home
        waitForText("Today's progress".uppercase())
        waitForText("Chan")
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Open today's plan"))
        rule.onNodeWithText("Open today's plan").assertIsDisplayed()

        // Today, with the example content and the first task
        rule.onAllNodesWithText("Today").onFirst().performClick()
        waitForText("Plan the week")
        waitForText("Workout")

        // Week and month views render
        rule.onNodeWithText("Week").performClick()
        waitForText("Workout")
        rule.onNodeWithText("Month").performClick()
        rule.onNodeWithText("Day").performClick()

        // Create a task
        rule.onNodeWithContentDescription("New task").performClick()
        waitForText("New task")
        rule.onNode(hasSetTextAction() and hasText("Title")).performTextInput("Smoke test task")
        rule.onNodeWithText("Save").performClick()
        waitForDesc("New task")
        waitForText("Smoke test task")

        // Validation: empty title is rejected inline
        rule.onNodeWithContentDescription("New task").performClick()
        waitForText("Save")
        rule.onNodeWithText("Save").performClick()
        waitForText("Give the task a title.")
        rule.onNodeWithContentDescription("Close").performClick()

        // More, Settings, Trash
        rule.onNodeWithText("More").performClick()
        rule.onNodeWithText("Settings").performClick()
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
        rule.onAllNodes(isToggleable()).onFirst().performClick() // first switch is the main goal, "Workout done"
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Done").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("Back").performClick()
        waitForText("Java backend")
        rule.onNodeWithText("Java backend").performClick()
        waitForText("Study plan".uppercase())
        rule.onNodeWithContentDescription("Back").performClick()
        rule.onNodeWithText("New tracker", useUnmergedTree = true).performClick()
        waitForText("Start a tracker")
        rule.onNodeWithText("Habit").performClick()
        waitForText("Save")
        rule.onNodeWithText("Save").performClick()
        waitForText("Last 5 weeks".uppercase(), timeoutMs = 15_000)
        rule.onNodeWithContentDescription("Back").performClick()

        // Read: example book and its reading status
        rule.onNodeWithText("Read").performClick()
        waitForText("Atomic Habits")
        rule.onNodeWithText("Atomic Habits").performClick()
        waitForText("Page 48 of 320")
        rule.onNodeWithText("Paused", useUnmergedTree = true).performClick()
        rule.onNodeWithText("Reading", useUnmergedTree = true).performClick()
        // Dialogs that contain text fields never reach idle under Robolectric at phone densities
        // (a minimal AlertDialog + OutlinedTextField reproduces it), so the "Log reading" dialog is
        // checked by hand (docs/TESTING.md) and session logging is covered by MigrationTest.
        rule.onNodeWithText("Finished", useUnmergedTree = true).performClick()
        waitForText("Page 320 of 320")
        rule.onNodeWithContentDescription("Back").performClick()

        // Home's trackers card shows today's trackers and the new streak
        rule.onNodeWithText("Home").performClick()
        waitForText("Java backend")
        waitForText("1 day")
    }
}
