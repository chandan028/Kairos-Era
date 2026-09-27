package com.kairosera.feature.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.ui.draw.clip
import com.kairosera.core.ui.components.Hairline
import com.kairosera.core.ui.components.KCard
import com.kairosera.core.ui.components.ScreenHeader
import com.kairosera.core.ui.theme.Kairos
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kairosera.BuildConfig
import com.kairosera.R
import com.kairosera.core.ui.components.ContentMaxWidth
import com.kairosera.core.ui.components.SectionLabel

@Composable
fun MoreScreen(
    onSettings: () -> Unit,
    onTrash: () -> Unit,
    onPrivacy: () -> Unit,
    onAbout: () -> Unit,
    onCalendar: () -> Unit,
    onStatistics: () -> Unit,
    onJournal: () -> Unit,
    onReading: () -> Unit,
    onBackup: () -> Unit = {},
    onDiagnostics: () -> Unit = {},
) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(stringResource(R.string.nav_more))
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = ContentMaxWidth).fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 112.dp),
            ) {
                SectionLabel(stringResource(R.string.more_reflect), Modifier.padding(bottom = 8.dp))
                MoreGroup(
                    Triple(Icons.Outlined.BarChart, R.string.statistics to R.string.statistics_summary, onStatistics),
                    Triple(Icons.Outlined.CalendarMonth, R.string.life_calendar to R.string.life_calendar_summary, onCalendar),
                    Triple(Icons.Outlined.EditNote, R.string.journal to R.string.journal_summary, onJournal),
                    Triple(Icons.AutoMirrored.Outlined.MenuBook, R.string.nav_read to R.string.reading_summary, onReading),
                )
                SectionLabel(stringResource(R.string.more_your_data), Modifier.padding(top = 24.dp, bottom = 8.dp))
                MoreGroup(
                    Triple(Icons.Outlined.Backup, R.string.backup_title to R.string.backup_summary, onBackup),
                    Triple(Icons.Outlined.DeleteOutline, R.string.trash to R.string.trash_summary, onTrash),
                    Triple(Icons.Outlined.Lock, R.string.privacy_policy to R.string.privacy_summary, onPrivacy),
                )
                SectionLabel(stringResource(R.string.more_app), Modifier.padding(top = 24.dp, bottom = 8.dp))
                MoreGroup(
                    Triple(Icons.Outlined.Settings, R.string.settings to R.string.settings_summary, onSettings),
                    Triple(Icons.Outlined.BugReport, R.string.diagnostics to R.string.diagnostics_summary, onDiagnostics),
                    Triple(Icons.Outlined.Info, R.string.about to R.string.about_summary, onAbout),
                )
            }
        }
    }
}

/** One card per group, rows split by hairlines, so the page reads as a few calm blocks rather than a long list. */
@Composable
private fun MoreGroup(vararg rows: Triple<ImageVector, Pair<Int, Int>, () -> Unit>) {
    KCard(padding = 0.dp) {
        rows.forEachIndexed { i, (icon, text, onClick) ->
            if (i > 0) Hairline(Modifier.padding(start = 72.dp))
            MoreRow(icon, text.first, text.second, onClick)
        }
    }
}

@Composable
private fun MoreRow(icon: ImageVector, title: Int, summary: Int, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Kairos.colors.accentSoft),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = Kairos.colors.accentText, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(summary), style = MaterialTheme.typography.bodySmall, color = Kairos.colors.muted)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Kairos.colors.muted, modifier = Modifier.size(20.dp))
    }
}

/** A secondary screen: serif title with Back, content centred and capped at a readable width. */
@Composable
fun SubScreen(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title, onBack = onBack)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.widthIn(max = ContentMaxWidth)) { content() }
        }
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    SubScreen(stringResource(R.string.about), onBack) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 112.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            com.kairosera.core.ui.components.KairosLogo(Modifier.size(96.dp))
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.app_name_display), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.tagline),
                style = com.kairosera.core.ui.theme.QuoteStyle,
                color = Kairos.colors.accentText,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            KCard(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.about_philosophy), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.about_meaning), style = MaterialTheme.typography.bodyLarge, color = Kairos.colors.muted)
            }
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodySmall, color = Kairos.colors.muted)
        }
    }
}
