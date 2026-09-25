package com.kairosera.core.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kairosera.R
import com.kairosera.core.ui.theme.Kairos
import com.kairosera.core.ui.theme.OverlineStyle
import com.kairosera.core.ui.theme.Space
import com.kairosera.core.ui.theme.Tone

/** Every screen renders exactly one of these. Loading always ends in Ready or Error. */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Ready<T>(val data: T) : UiState<T>
    data class Error(val retryable: Boolean = true) : UiState<Nothing>
}

/** Keeps content readable on tablets, foldables and landscape by capping line length. */
val ContentMaxWidth = 720.dp

/** Standard page padding: generous sides, room at the bottom for the floating add button. */
val PagePadding = PaddingValues(start = Space.gutter, end = Space.gutter, top = Space.l, bottom = 112.dp)

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = Kairos.colors.muted) {
    Text(
        text = text.uppercase(),
        style = OverlineStyle,
        color = color,
        modifier = modifier.semantics { heading() },
    )
}

/** Section label with an optional trailing action ("See all"). */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically) {
        SectionLabel(text, Modifier.weight(1f))
        if (action != null && onAction != null) {
            Text(
                action,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAction).padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}

/** The calm card: a slightly lighter sheet on the cream page with a hairline edge. No heavy shadows. */
@Composable
fun KCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: Dp = 20.dp,
    color: Color = Kairos.colors.card,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    val border = BorderStroke(1.dp, Kairos.colors.line)
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = shape, color = color, border = border) {
            Column(Modifier.padding(padding), content = content)
        }
    } else {
        Surface(modifier = modifier.fillMaxWidth(), shape = shape, color = color, border = border) {
            Column(Modifier.padding(padding), content = content)
        }
    }
}

/** Deep navy hero block, used once per screen at most (the emotional center). */
@Composable
fun BrandCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = MaterialTheme.shapes.large
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = shape, color = Kairos.colors.brand, contentColor = Kairos.colors.onBrand) {
            Column(Modifier.padding(22.dp), content = content)
        }
    } else {
        Surface(modifier = modifier.fillMaxWidth(), shape = shape, color = Kairos.colors.brand, contentColor = Kairos.colors.onBrand) {
            Column(Modifier.padding(22.dp), content = content)
        }
    }
}

/** Rounded progress bar in a semantic tone. Animates gently when the value changes. */
@Composable
fun KProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color = Kairos.colors.success.strong,
    track: Color = Kairos.colors.track,
    height: Dp = 8.dp,
) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(600), label = "progress")
    Box(modifier.fillMaxWidth().height(height).clip(CircleShape).background(track)) {
        if (animated > 0f) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(animated).clip(CircleShape).background(color))
        }
    }
}

/** A small stat: big number in serif, short label below. */
@Composable
fun StatTile(value: String, label: String, modifier: Modifier = Modifier, tone: Tone? = null, onClick: (() -> Unit)? = null) {
    KCard(modifier, onClick = onClick, padding = 16.dp, color = tone?.soft ?: Kairos.colors.card) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = tone?.strong ?: MaterialTheme.colorScheme.onSurface, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = Kairos.colors.muted, maxLines = 2)
    }
}

/** Round icon on a soft semantic fill: the visual anchor for trackers, books and rows. */
@Composable
fun ToneIcon(icon: ImageVector, tone: Tone, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(CircleShape).background(tone.soft), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tone.strong, modifier = Modifier.size(size * 0.5f))
    }
}

/** Small pill label, e.g. a category. */
@Composable
fun Pill(text: String, tone: Tone, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = tone.strong,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.clip(CircleShape).background(tone.soft).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** Screen title row: serif title, optional back button and trailing actions. Handles the status bar itself. */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().statusBarsPadding().padding(start = if (onBack != null) 4.dp else Space.gutter, end = 8.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) }
            Spacer(Modifier.width(4.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = if (onBack != null) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Kairos.colors.muted)
            }
        }
        actions()
    }
}

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 3.dp)
    }
}

@Composable
fun ErrorState(onRetry: (() -> Unit)?, modifier: Modifier = Modifier) {
    MessageState(
        icon = Icons.Outlined.ErrorOutline,
        title = stringResource(R.string.error_title),
        body = stringResource(R.string.error_body),
        modifier = modifier,
        action = onRetry?.let { { OutlinedButton(onClick = it) { Text(stringResource(R.string.retry)) } } },
    )
}

@Composable
fun MessageState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ToneIcon(icon, Kairos.colors.info, size = 64.dp)
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = Kairos.colors.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 420.dp),
        )
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            action()
        }
    }
}

/** The one strong action on a screen: full width, navy, generous height. */
@Composable
fun PrimaryWideButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(56.dp),
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(containerColor = Kairos.colors.brand, contentColor = Kairos.colors.onBrand),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.titleSmall)
    }
}

/** Quiet dashed-feel secondary action ("+ Add topic", "+ Create your own tracker"). */
@Composable
fun GhostAddButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp),
        shape = MaterialTheme.shapes.medium,
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        contentColor = MaterialTheme.colorScheme.primary,
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, style = MaterialTheme.typography.titleSmall)
        }
    }
}

/** Thin divider in the page's line color. */
@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Kairos.colors.line))
}
