package com.kairosera.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.kairosera.core.ui.theme.Kairos
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/*
 * Kairos toast: one feedback component for the whole app.
 *
 * Built on Material 3's SnackbarHost (so TalkBack announces it and it respects system timeouts),
 * drawn as a small navy capsule with a semantic icon: green check for "done", blue for info,
 * amber for undoable changes, red for problems. It auto-dismisses (short, or long when there is an
 * Undo), never blocks the screen, and only one shows at a time.
 */
enum class ToastKind { SUCCESS, INFO, UNDO, ERROR }

private class ToastVisuals(
    override val message: String,
    override val actionLabel: String?,
    val kind: ToastKind,
) : SnackbarVisuals {
    override val withDismissAction: Boolean = false
    override val duration: SnackbarDuration = if (actionLabel != null) SnackbarDuration.Long else SnackbarDuration.Short
}

@Stable
class Toaster internal constructor(internal val host: SnackbarHostState, private val scope: CoroutineScope) {
    /** Shows a toast, replacing any toast already visible. [onAction] runs if the person taps the action. */
    fun show(message: String, kind: ToastKind = ToastKind.INFO, action: String? = null, onAction: () -> Unit = {}) {
        scope.launch {
            host.currentSnackbarData?.dismiss()
            val result = host.showSnackbar(ToastVisuals(message, action, kind))
            if (result == SnackbarResult.ActionPerformed) onAction()
        }
    }
}

/** Provided once at the app root; every screen can call `LocalToaster.current.show(...)`. */
val LocalToaster = staticCompositionLocalOf<Toaster> { error("No Toaster provided") }

@Composable
fun rememberToaster(): Toaster {
    val scope = rememberCoroutineScope()
    return remember { Toaster(SnackbarHostState(), scope) }
}

@Composable
fun KairosToastHost(toaster: Toaster, modifier: Modifier = Modifier) {
    SnackbarHost(toaster.host, modifier) { data -> KairosToast(data) }
}

@Composable
private fun KairosToast(data: SnackbarData) {
    val visuals = data.visuals
    val kind = (visuals as? ToastVisuals)?.kind ?: ToastKind.INFO
    val haptics = LocalHapticFeedback.current
    val colors = Kairos.colors
    val (icon: ImageVector, tint) = when (kind) {
        ToastKind.SUCCESS -> Icons.Outlined.Check to colors.success
        ToastKind.INFO -> Icons.Outlined.Info to colors.info
        ToastKind.UNDO -> Icons.AutoMirrored.Outlined.Undo to colors.motivation
        ToastKind.ERROR -> Icons.Outlined.ErrorOutline to colors.activity
    }
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            shadowElevation = 6.dp,
            modifier = Modifier.widthIn(max = 560.dp),
        ) {
            Row(
                Modifier.heightIn(min = 56.dp).padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(28.dp).clip(CircleShape).background(tint.soft), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = tint.strong, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(visuals.message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp))
                val action = visuals.actionLabel
                if (action != null) {
                    TextButton(onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        data.performAction()
                    }) {
                        Text(action, style = MaterialTheme.typography.labelLarge, color = colors.sun)
                    }
                } else {
                    Spacer(Modifier.width(8.dp))
                }
            }
        }
    }
}
