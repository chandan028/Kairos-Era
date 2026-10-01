package com.kairosera

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Winter Arc Home in the dark theme, where the polar-night palette and the custom habits must read well. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-night-xxhdpi")
class WinterArcDarkTour {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val outDir: File? = System.getProperty("kairos.screens")?.let(::File)

    private fun shot(name: String) {
        rule.waitForIdle()
        val dir = outDir ?: return
        dir.mkdirs()
        var bmp: Bitmap? = null
        rule.activityRule.scenario.onActivity { a ->
            val v = a.window.decorView
            bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).also { v.draw(android.graphics.Canvas(it)) }
        }
        File(dir, "wa-$name.png").outputStream().use { bmp!!.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @After
    fun tearDown(): Unit = ArcSeed.reset((rule.activity.application as KairosApp).container)

    @Test(timeout = 300_000)
    fun darkHome() {
        ArcSeed.seed((rule.activity.application as KairosApp).container)
        rule.mainClock.advanceTimeBy(2500)
        rule.waitUntil(15_000) { rule.onAllNodesWithText("Discipline today", substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.waitUntil(15_000) { rule.onAllNodesWithText("Push-ups").fetchSemanticsNodes().isNotEmpty() }
        shot("10-home-dark")
        rule.onAllNodesWithText("Add habit").onFirst().performScrollTo()
        shot("10b-home-dark-grid")
    }
}
