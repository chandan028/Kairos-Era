package com.kairosera.feature.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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

@OptIn(ExperimentalMaterial3Api::class)
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
) {
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_more)) }) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(Modifier.widthIn(max = ContentMaxWidth), contentPadding = PaddingValues(bottom = 24.dp)) {
                item { SectionLabel(stringResource(R.string.more_reflect), Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp)) }
                item { MoreRow(Icons.Outlined.BarChart, R.string.statistics, R.string.statistics_summary, onStatistics) }
                item { MoreRow(Icons.Outlined.CalendarMonth, R.string.life_calendar, R.string.life_calendar_summary, onCalendar) }
                item { MoreRow(Icons.Outlined.EditNote, R.string.journal, R.string.journal_summary, onJournal) }
                item { MoreRow(Icons.AutoMirrored.Outlined.MenuBook, R.string.nav_read, R.string.reading_summary, onReading) }
                item { SectionLabel(stringResource(R.string.more_app), Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)) }
                item { MoreRow(Icons.Outlined.Settings, R.string.settings, R.string.settings_summary, onSettings) }
                item { MoreRow(Icons.Outlined.Backup, R.string.backup_title, R.string.backup_summary, onBackup) }
                item { MoreRow(Icons.Outlined.DeleteOutline, R.string.trash, R.string.trash_summary, onTrash) }
                item { MoreRow(Icons.Outlined.Lock, R.string.privacy_policy, R.string.privacy_summary, onPrivacy) }
                item { MoreRow(Icons.Outlined.Info, R.string.about, R.string.about_summary, onAbout) }
                item { SectionLabel(stringResource(R.string.coming_next), Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)) }
                item { MoreRow(Icons.Outlined.BugReport, R.string.diagnostics, R.string.soon, null) }
            }
        }
    }
}

@Composable
private fun MoreRow(icon: ImageVector, title: Int, summary: Int, onClick: (() -> Unit)?) {
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        supportingContent = { Text(stringResource(summary)) },
        leadingContent = { Icon(icon, null) },
        colors = ListItemDefaults.colors(
            headlineColor = if (onClick == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        ),
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubScreen(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.widthIn(max = ContentMaxWidth)) { content() }
        }
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    SubScreen(stringResource(R.string.about), onBack) {
        TextPage(
            stringResource(R.string.app_name_display),
            listOf(
                stringResource(R.string.tagline),
                stringResource(R.string.about_philosophy),
                stringResource(R.string.about_meaning),
                stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
            ),
        )
    }
}

@Composable
private fun TextPage(headline: String, paragraphs: List<String>) {
    Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text(headline, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        paragraphs.forEach {
            Text(it, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(12.dp))
        }
    }
}
