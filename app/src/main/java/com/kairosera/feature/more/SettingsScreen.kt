package com.kairosera.feature.more

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kairosera.R
import com.kairosera.core.settings.AppSettings
import com.kairosera.core.settings.ThemeMode
import com.kairosera.core.ui.components.SectionLabel
import com.kairosera.feature.tasks.SELECTABLE_SOUNDS
import com.kairosera.feature.tasks.soundLabel
import com.kairosera.ui.appContainer
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onHomeCards: () -> Unit) {
    val c = appContainer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by c.settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }
    val canNotify = remember(refresh) { c.scheduler.canPostNotifications() }
    val canExact = remember(refresh) { c.scheduler.canScheduleExact() }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    var confirmRemoveSamples by remember { mutableStateOf(false) }
    var nameDraft by remember(settings.name) { mutableStateOf(settings.name) }

    SubScreen(stringResource(R.string.settings), onBack) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
            // General
            SectionLabel(stringResource(R.string.settings_general), Modifier.padding(top = 8.dp, bottom = 8.dp))
            Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleSmall)
            val currentTag = AppCompatDelegate.getApplicationLocales().toLanguageTags()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("" to R.string.language_system, "en" to R.string.language_english, "kn" to R.string.language_kannada).forEach { (tag, label) ->
                    FilterChip(
                        selected = currentTag.startsWith(tag) && (tag.isNotEmpty() || currentTag.isEmpty()),
                        onClick = { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag)) },
                        label = { Text(stringResource(label)) },
                    )
                }
            }
            OutlinedTextField(
                value = nameDraft,
                onValueChange = { nameDraft = it.take(40); scope.launch { c.settings.setName(nameDraft) } },
                label = { Text(stringResource(R.string.onboarding_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )

            // Appearance
            SectionLabel(stringResource(R.string.settings_appearance), Modifier.padding(top = 24.dp, bottom = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { m ->
                    FilterChip(
                        selected = settings.themeMode == m,
                        onClick = { scope.launch { c.settings.setThemeMode(m) } },
                        label = { Text(stringResource(when (m) { ThemeMode.SYSTEM -> R.string.theme_system; ThemeMode.LIGHT -> R.string.theme_light; ThemeMode.DARK -> R.string.theme_dark })) },
                    )
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SwitchRow(stringResource(R.string.dynamic_color), stringResource(R.string.dynamic_color_summary), settings.dynamicColor) { v ->
                    scope.launch { c.settings.setDynamicColor(v) }
                }
            }
            ListItem(
                headlineContent = { Text(stringResource(R.string.home_customize)) },
                supportingContent = { Text(stringResource(R.string.home_customize_summary)) },
                modifier = Modifier.clickable(onClick = onHomeCards),
            )

            // Notifications
            SectionLabel(stringResource(R.string.settings_notifications), Modifier.padding(top = 24.dp, bottom = 8.dp))
            StatusRow(
                title = stringResource(R.string.notif_permission),
                status = stringResource(if (canNotify) R.string.status_allowed else R.string.status_off),
                explanation = if (canNotify) null else stringResource(R.string.notif_permission_off_body),
                actionLabel = if (canNotify) null else stringResource(R.string.allow),
                onAction = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !c.scheduler.canPostNotifications()) {
                        permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    openAppNotificationSettings(context)
                },
            )
            StatusRow(
                title = stringResource(R.string.exact_alarms),
                status = stringResource(if (canExact) R.string.status_allowed else R.string.status_off),
                explanation = if (canExact) null else stringResource(R.string.exact_alarms_off_body),
                actionLabel = if (canExact) null else stringResource(R.string.open_settings),
                onAction = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        startSafely(context, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
                    }
                },
            )
            StatusRow(
                title = stringResource(R.string.battery_title),
                status = null,
                explanation = stringResource(R.string.battery_body),
                actionLabel = stringResource(R.string.open_settings),
                onAction = { startSafely(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.test_notification)) },
                supportingContent = { Text(stringResource(R.string.test_notification_summary)) },
                modifier = Modifier.clickable { c.scheduler.postTest() },
            )

            // Sounds
            SectionLabel(stringResource(R.string.settings_sounds), Modifier.padding(top = 24.dp, bottom = 8.dp))
            Text(stringResource(R.string.default_sound), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SELECTABLE_SOUNDS.forEach { s ->
                    FilterChip(selected = settings.defaultSound == s, onClick = { scope.launch { c.settings.setDefaultSound(s) } }, label = { Text(stringResource(soundLabel(s))) })
                }
            }
            Text(stringResource(R.string.sound_channel_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 4.dp))
            TextButton(onClick = { openAppNotificationSettings(context) }) { Text(stringResource(R.string.open_sound_settings)) }
            SwitchRow(stringResource(R.string.default_persistent), stringResource(R.string.default_persistent_summary), settings.defaultPersistent) { v ->
                scope.launch { c.settings.setDefaultPersistent(v) }
            }
            Text(stringResource(R.string.snooze_length), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(5, 10, 15, 30, 60).forEach { m ->
                    FilterChip(selected = settings.snoozeMinutes == m, onClick = { scope.launch { c.settings.setSnoozeMinutes(m) } }, label = { Text(stringResource(R.string.minutes_short, m)) })
                }
            }

            // Data
            SectionLabel(stringResource(R.string.settings_data), Modifier.padding(top = 24.dp, bottom = 8.dp))
            ListItem(
                headlineContent = { Text(stringResource(R.string.add_examples)) },
                supportingContent = { Text(stringResource(R.string.add_examples_summary)) },
                modifier = Modifier.clickable { scope.launch { c.addSampleContent() } },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.remove_examples)) },
                supportingContent = { Text(stringResource(R.string.remove_examples_summary)) },
                modifier = Modifier.clickable { confirmRemoveSamples = true },
            )
            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            Text(stringResource(R.string.settings_footer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (confirmRemoveSamples) {
        AlertDialog(
            onDismissRequest = { confirmRemoveSamples = false },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemoveSamples = false
                    scope.launch { runCatching { c.tasks.trashSampleData(java.time.Instant.now()); c.scheduler.rebuild("samples_removed") } }
                }) { Text(stringResource(R.string.remove_examples)) }
            },
            dismissButton = { TextButton(onClick = { confirmRemoveSamples = false }) { Text(stringResource(R.string.cancel)) } },
            text = { Text(stringResource(R.string.remove_examples_confirm)) },
        )
    }
}

@Composable
private fun SwitchRow(title: String, summary: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        modifier = Modifier.clickable { onChange(!checked) },
    )
}

@Composable
private fun StatusRow(title: String, status: String?, explanation: String?, actionLabel: String?, onAction: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 16.dp)) {
        Row {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (status != null) Text(status, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        if (explanation != null) Text(explanation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        if (actionLabel != null) TextButton(onClick = onAction, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) { Text(actionLabel) }
    }
}

private fun openAppNotificationSettings(context: Context) {
    startSafely(context, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
}

private fun startSafely(context: Context, intent: Intent) {
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        runCatching {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
