package com.kairosera.feature.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kairosera.R
import com.kairosera.core.ui.theme.Kairos
import com.kairosera.domain.stats.StatsRange

@Composable
fun rangeShort(r: StatsRange): String = stringResource(
    when (r) {
        StatsRange.WEEK -> R.string.stats_range_7d
        StatsRange.MONTH -> R.string.stats_range_30d
        StatsRange.QUARTER -> R.string.stats_range_90d
        StatsRange.YEAR -> R.string.stats_range_1y
    },
)

@Composable
fun rangeLong(r: StatsRange): String = stringResource(
    when (r) {
        StatsRange.WEEK -> R.string.stats_last_7
        StatsRange.MONTH -> R.string.stats_last_30
        StatsRange.QUARTER -> R.string.stats_last_90
        StatsRange.YEAR -> R.string.stats_last_year
    },
)

/** 7D / 30D / 90D / 1Y as one quiet segmented row; the chosen range is filled with gold. */
@Composable
fun RangeSelector(selected: StatsRange, onSelect: (StatsRange) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Kairos.colors.card).padding(4.dp).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        StatsRange.entries.forEach { r ->
            val on = r == selected
            Box(
                Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(10.dp))
                    .background(if (on) Kairos.colors.accent else Color.Transparent)
                    .selectable(on, role = Role.Tab) { onSelect(r) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    rangeShort(r), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (on) Color(0xFF1A2233) else Kairos.colors.muted,
                )
            }
        }
    }
}

/** Less ░ ▒ ▓ █ More, for the activity map and the year view. */
@Composable
fun HeatLegend(modifier: Modifier = Modifier) {
    Row(modifier.clearAndSetSemantics { }, verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.stats_less), style = MaterialTheme.typography.labelSmall, color = Kairos.colors.muted)
        Kairos.colors.heat.forEach { c ->
            Spacer(Modifier.width(4.dp))
            Box(Modifier.size(11.dp).clip(RoundedCornerShape(3.dp)).background(c))
        }
        Spacer(Modifier.width(4.dp))
        Text(stringResource(R.string.stats_more), style = MaterialTheme.typography.labelSmall, color = Kairos.colors.muted)
    }
}

@Composable
fun PrivacyFooter(modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Column(modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Outlined.Lock, contentDescription = null, tint = Kairos.colors.muted, modifier = Modifier.size(16.dp))
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.stats_private), style = MaterialTheme.typography.bodySmall, color = Kairos.colors.muted, textAlign = TextAlign.Center)
    }
}

data class MenuAction(val label: String, val icon: ImageVector, val onClick: () -> Unit)

/** The ⋮ menu in a screen header. */
@Composable
fun OverflowMenu(actions: List<MenuAction>) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.more_options)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            actions.forEach { a ->
                DropdownMenuItem(
                    text = { Text(a.label) },
                    leadingIcon = { Icon(a.icon, contentDescription = null) },
                    onClick = { open = false; a.onClick() },
                )
            }
        }
    }
}

/** "1h 25m" or "40 min". */
@Composable
fun formatMinutes(minutes: Int): String =
    if (minutes >= 60) stringResource(R.string.duration_hm, minutes / 60, minutes % 60) else stringResource(R.string.duration_m, minutes)

@Composable
fun pagesText(pages: Int): String = pluralStringResource(R.plurals.pages_count, pages, pages)

/** A small dot, used for the calendar and legends. */
@Composable
fun Dot(color: Color, size: Int = 5) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(color))
}

/** Grouped, locale-aware numbers: 6,432 steps, 2.5 litres. */
@Composable
fun rememberNumberText(): (Double) -> String {
    val locale = com.kairosera.core.ui.components.currentLocale()
    return remember(locale) {
        val nf = java.text.NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 2 }
        return@remember { n: Double -> nf.format(n) }
    }
}

/** How one activity reads in a list: an icon (the tracker's own emoji where there is one), a title and a detail. */
data class ItemText(val emoji: String?, val icon: ImageVector?, val title: String, val detail: String)

@Composable
fun itemText(item: com.kairosera.domain.activity.ActivityItem, number: (Double) -> String): ItemText {
    val previous = stringResource(R.string.previous_activity)
    val title = if (item.missingSource || item.title.isBlank()) previous else item.title
    val value = item.value?.let { v ->
        when (item.valueType) {
            com.kairosera.domain.tracker.MeasurementType.DURATION -> formatMinutes(v.toInt())
            com.kairosera.domain.tracker.MeasurementType.PAGES -> pagesText(v.toInt())
            com.kairosera.domain.tracker.MeasurementType.PERCENTAGE -> "${number(v)}%"
            else -> listOf(number(v), item.unit).filter { it.isNotBlank() }.joinToString(" ")
        }
    }
    val minutes = item.minutes.takeIf { it > 0 && item.valueType != com.kairosera.domain.tracker.MeasurementType.DURATION }?.let { formatMinutes(it) }
    val detail = listOfNotNull(value, minutes).joinToString(" · ")
    return when (item.type) {
        com.kairosera.domain.activity.ActivityType.TASK_COMPLETED -> ItemText(null, Icons.Outlined.CheckCircle, item.title, stringResource(R.string.activity_task_done))
        com.kairosera.domain.activity.ActivityType.STUDY_TOPIC -> ItemText(null, Icons.Outlined.School, title, stringResource(R.string.activity_topic_done))
        com.kairosera.domain.activity.ActivityType.READING_SESSION -> ItemText(null, Icons.AutoMirrored.Outlined.MenuBook, title, detail)
        com.kairosera.domain.activity.ActivityType.JOURNAL_ENTRY -> ItemText(null, Icons.Outlined.EditNote, stringResource(R.string.activity_reflection), "")
        else -> ItemText(item.icon.takeUnless { item.missingSource }, if (item.missingSource) Icons.Outlined.History else null, title, detail)
    }
}
