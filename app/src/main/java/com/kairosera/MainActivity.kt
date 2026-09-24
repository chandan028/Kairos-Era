package com.kairosera

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.kairosera.ui.SplashOverlay
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kairosera.core.settings.AppSettings
import com.kairosera.core.ui.theme.KairosTheme
import com.kairosera.ui.KairosRoot
import com.kairosera.ui.LaunchRequest
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** AppCompatActivity (not ComponentActivity) so the per-app language switch works on Android 8-12. */
class MainActivity : AppCompatActivity() {

    private var launchRequest by mutableStateOf<LaunchRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // No keep-on-screen condition: settings load in milliseconds, and a condition that never
        // turns false (e.g. unreadable preferences) would freeze the splash screen forever.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) launchRequest = parse(intent)

        val container = (application as KairosApp).container
        val settingsFlow = container.settings.settings.map<AppSettings, AppSettings?> { it }
        // The brand moment plays once per cold start, never when opened from a reminder or widget action.
        val playSplash = savedInstanceState == null && launchRequest == null
        setContent {
            val settings by settingsFlow.collectAsStateWithLifecycle(initialValue = null)
            val current = settings
            var splash by rememberSaveable { mutableStateOf(playSplash) }
            KairosTheme(themeMode = current?.themeMode ?: com.kairosera.core.settings.ThemeMode.SYSTEM, dynamicColor = current?.dynamicColor ?: false) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    if (current != null) {
                        KairosRoot(
                            settings = current,
                            launchRequest = launchRequest,
                            onLaunchRequestHandled = { launchRequest = null },
                        )
                    }
                    if (splash) SplashOverlay(onFinished = { splash = false })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        launchRequest = parse(intent)
    }

    /** Only our own immutable PendingIntents target these actions; values are still range-checked. */
    private fun parse(intent: Intent?): LaunchRequest? {
        intent ?: return null
        val day = intent.getLongExtra(EXTRA_DATE, Long.MIN_VALUE)
        val date = if (day in MIN_DAY..MAX_DAY) LocalDate.ofEpochDay(day) else null
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L).takeIf { it > 0 }
        return when (intent.action) {
            ACTION_OPEN_DAY -> LaunchRequest.OpenDay(date ?: LocalDate.now())
            ACTION_RESCHEDULE -> if (taskId != null && date != null) LaunchRequest.Reschedule(taskId, date) else null
            ACTION_QUICK_ADD -> LaunchRequest.QuickAdd
            ACTION_NEW_TASK -> LaunchRequest.NewTask
            ACTION_OPEN_TRACK -> LaunchRequest.OpenTrack
            ACTION_OPEN_QUOTE -> LaunchRequest.OpenQuote
            else -> null
        }
    }

    companion object {
        const val ACTION_OPEN_DAY = "com.kairosera.action.OPEN_DAY"
        const val ACTION_RESCHEDULE = "com.kairosera.action.RESCHEDULE"
        const val ACTION_QUICK_ADD = "com.kairosera.action.QUICK_ADD"
        const val ACTION_NEW_TASK = "com.kairosera.action.NEW_TASK"
        const val ACTION_OPEN_TRACK = "com.kairosera.action.OPEN_TRACK"
        const val ACTION_OPEN_QUOTE = "com.kairosera.action.OPEN_QUOTE"
        const val EXTRA_DATE = "date"
        const val EXTRA_TASK_ID = "taskId"
        const val EXTRA_NOTIFICATION_ID = "notificationId"
        private val MIN_DAY = LocalDate.of(2000, 1, 1).toEpochDay()
        private val MAX_DAY = LocalDate.of(2200, 1, 1).toEpochDay()
    }
}
