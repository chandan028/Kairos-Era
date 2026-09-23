package com.kairosera.feature.placeholder

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.kairosera.R
import com.kairosera.core.ui.components.MessageState
import com.kairosera.ui.ComingSoonKind

@Composable
fun ComingSoonScreen(kind: ComingSoonKind) {
    Box(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()), contentAlignment = Alignment.Center) {
        when (kind) {
            ComingSoonKind.TRACK -> MessageState(Icons.Outlined.Insights, stringResource(R.string.track_soon_title), stringResource(R.string.track_soon_body))
            ComingSoonKind.READ -> MessageState(Icons.Outlined.AutoStories, stringResource(R.string.read_soon_title), stringResource(R.string.read_soon_body))
        }
    }
}
