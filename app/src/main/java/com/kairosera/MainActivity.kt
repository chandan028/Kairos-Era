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
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import android.os.Build
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.kairosera.core.security.DeviceAuth
import com.kairosera.feature.lock.LockScreen
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
        val lock = container.lock
        // The lock is configured before the settings reach the screen, so locked content never shows for a frame.
        val settingsFlow = container.settings.settings
            .onEach { s ->
                lock.configure(s.lockEnabled, s.lockAfterSeconds)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) setRecentsScreenshotEnabled(!s.lockEnabled)
            }
            .map<AppSettings, AppSettings?> { it }
        // The brand moment plays once per cold start, never when opened from a reminder or widget action.
        val playSplash = savedInstanceState == null && launchRequest == null
        setContent {
            val settings by settingsFlow.collectAsStateWithLifecycle(initialValue = null)
            val current = settings
            val locked by lock.locked.collectAsStateWithLifecycle()
            var splash by rememberSaveable { mutableStateOf(playSplash) }
            KairosTheme(themeMode = current?.themeMode ?: com.kairosera.core.settings.ThemeMode.SYSTEM, dynamicColor = current?.dynamicColor ?: false) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    if (current != null) {
                        // While locked, the app stays composed (so navigation is kept) but is hidden from screen readers.
                        Box(if (locked) Modifier.fillMaxSize().clearAndSetSemantics { } else Modifier.fillMaxSize()) {
                            KairosRoot(
                                settings = current,
                                launchRequest = launchRequest,
                                onLaunchRequestHandled = { launchRequest = null },
                            )
                        }
                    }
                    if (locked && current != null) {
                        val available = remember(locked) { DeviceAuth.isAvailable(this@MainActivity) }
                        // A window of its own, so it also covers any dialog that was open when the app locked.
                        Dialog(
                            onDismissRequest = {},
                            properties = DialogProperties(
                                usePlatformDefaultWidth = false,
                                dismissOnBackPress = false,
                                dismissOnClickOutside = false,
                                decorFitsSystemWindows = false,
                            ),
                        ) {
                            LockScreen(available = available, onUnlock = ::unlock, onLeave = { moveTaskToBack(true) })
                        }
                        LaunchedEffect(splash, available) { if (!splash && available) unlock() }
                    }
                    if (splash) SplashOverlay(onFinished = { splash = false })
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        (application as KairosApp).container.lock.onAppShown()
    }

    override fun onStop() {
        super.onStop()
        (application as KairosApp).container.lock.onAppHidden()
    }

    /** Asks for the phone's own unlock. If the phone no longer has a screen lock, the app lock turns itself off. */
    private fun unlock() {
        val container = (application as KairosApp).container
        val lock = container.lock
        if (lock.authenticating || !lock.locked.value) return
        if (!DeviceAuth.isAvailable(this)) {
            // Reached only by the person tapping "Continue without app lock" on the lock screen.
            lock.unlock()
            container.appScope.launch { container.settings.setLock(false) }
            return
        }
        lock.authenticating = true
        DeviceAuth.prompt(
            this,
            title = getString(R.string.lock_prompt_title),
            subtitle = getString(R.string.lock_prompt_subtitle),
            onSuccess = { lock.authenticating = false; lock.unlock() },
            onFailure = { lock.authenticating = false },
        )
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
            ACTION_NEW_JOURNAL -> LaunchRequest.NewJournal
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
        const val ACTION_NEW_JOURNAL = "com.kairosera.action.NEW_JOURNAL"
        const val EXTRA_DATE = "date"
        const val EXTRA_TASK_ID = "taskId"
        const val EXTRA_NOTIFICATION_ID = "notificationId"
        private val MIN_DAY = LocalDate.of(2000, 1, 1).toEpochDay()
        private val MAX_DAY = LocalDate.of(2200, 1, 1).toEpochDay()
    }
}
