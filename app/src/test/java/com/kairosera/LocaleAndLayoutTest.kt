package com.kairosera

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** First screen renders in Kannada and on a tablet-sized, landscape window. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LocaleAndLayoutTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    // DataStore is a process-wide singleton, so an earlier test class may already have finished onboarding.
    private fun waitForAny(vararg texts: String) =
        rule.waitUntil(10_000) { texts.any { rule.onAllNodesWithText(it, substring = true).fetchSemanticsNodes().isNotEmpty() } }

    @Test(timeout = 120_000)
    @Config(sdk = [34], qualifiers = "kn-w411dp-h891dp-xxhdpi")
    fun onboardingIsInKannada() {
        waitForAny("ನಿಮ್ಮ ಸಮಯ ನಿಮ್ಮದು.", "ಇಂದಿನ ಪ್ರಗತಿ")
    }

    @Test(timeout = 120_000)
    @Config(sdk = [34], qualifiers = "w1280dp-h800dp-land-mdpi")
    fun tabletLandscapeRenders() {
        waitForAny("Your time is yours.", "Open today's plan")
    }
}
