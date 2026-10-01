package com.kairosera

import android.graphics.Bitmap
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kairosera.domain.winterarc.HabitKind
import com.kairosera.feature.widgets.ArcWidgetSnapshot
import com.kairosera.feature.widgets.ArcWidgets
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.LocalTime

/**
 * Renders every Winter Arc widget from a real snapshot and inflates it the way a launcher does,
 * so a RemoteViews call the platform rejects fails here. With `-Pscreens=<dir>` it saves PNGs.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class ArcWidgetsTest {
    private val outDir: File? = System.getProperty("kairos.screens")?.let(::File)

    // Preferences are process-wide in tests; leave Winter Arc off for the test classes that follow.
    @After
    fun tearDown(): Unit = runBlocking { ApplicationProvider.getApplicationContext<KairosApp>().container.winterPrefs.clear(); Unit }

    @Test
    fun everyWidgetRenders() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<KairosApp>()
        val c = app.container
        val today = LocalDate.now()
        c.winterArc.startArc(today.minusDays(5), HabitKind.BUILT_IN.toSet())
        c.winterPrefs.setOnboarded()
        for (i in 5 downTo 0) {
            val d = today.minusDays(i.toLong())
            c.winterArc.addWater(d, if (i % 2 == 0) 3000 else 1250)
            c.winterArc.setSteps(d, 6240 + i * 900)
            c.winterArc.setChecked(HabitKind.ZERO_SUGAR, d, true)
            c.winterArc.setWake(d, LocalTime.of(4, 32))
        }
        val s = ArcWidgetSnapshot.load(c, today)
        assertTrue(s.on)
        val density = app.resources.displayMetrics.density
        ArcWidgets.PROVIDERS.forEach { cls ->
            val provider = cls.getDeclaredConstructor().newInstance()
            val views = provider.render(app, s)
            val parent = FrameLayout(app)
            val view = views.apply(app, parent)
            val w = (360 * density).toInt()
            val h = (if (cls.simpleName in setOf("ArcHabitsWidget", "ArcCalendarWidget", "ArcStudyWidget")) 300 else 170) * density
            view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h.toInt(), View.MeasureSpec.EXACTLY))
            view.layout(0, 0, w, h.toInt())
            val dir = outDir ?: return@forEach
            dir.mkdirs()
            val bmp = Bitmap.createBitmap(w, h.toInt(), Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bmp))
            File(dir, "widget-${cls.simpleName}.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
