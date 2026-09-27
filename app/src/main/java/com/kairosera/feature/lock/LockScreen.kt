package com.kairosera.feature.lock

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairosera.R
import com.kairosera.core.ui.components.KairosLogo
import com.kairosera.core.ui.theme.SerifFamily
import com.kairosera.core.ui.theme.Stage

/**
 * Covers the whole app while it is locked. If the phone no longer has any screen lock, there is
 * nothing to unlock with; the screen says so and lets the person continue with the lock turned off,
 * rather than changing the setting behind their back.
 *
 * Covers the whole app while it is locked. Always the night stage, whatever the theme, so the
 * moment reads the same as the splash. Back leaves the app instead of revealing it.
 */
@Composable
fun LockScreen(available: Boolean, onUnlock: () -> Unit, onLeave: () -> Unit) {
    BackHandler(onBack = onLeave)
    Column(
        Modifier.fillMaxSize().background(Stage.night0)
            // Swallow every touch so nothing behind the lock can be reached.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
            .safeDrawingPadding().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        KairosLogo(Modifier.size(88.dp))
        Spacer(Modifier.height(28.dp))
        Text(
            stringResource(R.string.lock_title),
            fontFamily = SerifFamily,
            fontSize = 28.sp,
            lineHeight = 36.sp,
            color = Stage.cream,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(if (available) R.string.lock_body else R.string.lock_unavailable_body),
            style = MaterialTheme.typography.bodyLarge,
            color = Stage.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 320.dp),
        )
        Spacer(Modifier.height(36.dp))
        Button(
            onClick = onUnlock,
            colors = ButtonDefaults.buttonColors(containerColor = Stage.gold, contentColor = Stage.onGold),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth().heightIn(min = 52.dp),
        ) {
            if (available) {
                Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
            }
            Text(stringResource(if (available) R.string.lock_unlock else R.string.lock_continue_without), style = MaterialTheme.typography.labelLarge)
        }
    }
}
