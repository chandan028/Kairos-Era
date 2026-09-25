package com.kairosera.feature.diagnostics

import android.content.ActivityNotFoundException
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.kairosera.BuildConfig
import com.kairosera.R
import com.kairosera.core.diagnostics.CrashReports
import com.kairosera.core.diagnostics.SafeLog
import com.kairosera.core.ui.components.ContentMaxWidth
import com.kairosera.core.ui.components.Hairline
import com.kairosera.core.ui.components.KCard
import com.kairosera.core.ui.components.ScreenHeader
import com.kairosera.core.ui.components.SectionLabel
import com.kairosera.core.ui.theme.Kairos
import com.kairosera.ui.appContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId

private enum class DataCheck { RUNNING, HEALTHY, PROBLEM }

/**
 * What a person can check or share when something seems wrong. Everything here is read on the
 * phone; the only way anything leaves is the person emailing a report they have seen.
 */
@Composable
fun DiagnosticsScreen(onBack: () -> Unit) {
    val c = appContainer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }
    val report = remember(refresh) { CrashReports.pending(context) }
    var showReport by remember { mutableStateOf(false) }
    var check by remember { mutableStateOf<DataCheck?>(null) }
    val canNotify = remember(refresh) { c.scheduler.canPostNotifications() }
    val canExact = remember(refresh) { c.scheduler.canScheduleExact() }
    val subject = stringResource(R.string.diag_email_subject)
    val locale = com.kairosera.core.ui.components.currentLocale()
    val stamp = remember(locale) { java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", locale) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(stringResource(R.string.diagnostics), onBack = onBack)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = ContentMaxWidth).fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 112.dp),
            ) {
                Text(stringResource(R.string.diag_intro), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(24.dp))

                SectionLabel(stringResource(R.string.diag_crash_label))
                Spacer(Modifier.height(8.dp))
                KCard {
                    if (report == null) {
                        StatusLine(Icons.Outlined.CheckCircle, Kairos.colors.success.strong, stringResource(R.string.diag_no_crash))
                    } else {
                        StatusLine(
                            Icons.Outlined.ErrorOutline, Kairos.colors.activity.strong,
                            stringResource(R.string.diag_crash_at, stamp.format(report.at.atZone(ZoneId.systemDefault()))),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.diag_crash_body), style = MaterialTheme.typography.bodyMedium, color = Kairos.colors.muted)
                        Spacer(Modifier.height(12.dp))
                        TextButton(onClick = { showReport = !showReport }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(stringResource(if (showReport) R.string.diag_hide_report else R.string.diag_view_report))
                        }
                        if (showReport) {
                            Box(
                                Modifier.fillMaxWidth().background(Kairos.colors.track, RoundedCornerShape(12.dp))
                                    .horizontalScroll(rememberScrollState()).padding(12.dp),
                            ) {
                                Text(report.text, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp)
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(
                                onClick = { CrashReports.clear(context); refresh++ },
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            ) { Text(stringResource(R.string.diag_delete_report)) }
                            Button(
                                onClick = {
                                    try {
                                        context.startActivity(CrashReports.emailIntent(report, subject))
                                    } catch (_: ActivityNotFoundException) {
                                        showReport = true
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Kairos.colors.accent, contentColor = Kairos.colors.brand),
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            ) { Text(stringResource(R.string.diag_email_report)) }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.diag_email_note, CrashReports.EMAIL), style = MaterialTheme.typography.bodySmall, color = Kairos.colors.muted)
                    }
                }

                Spacer(Modifier.height(28.dp))
                SectionLabel(stringResource(R.string.diag_checks_label))
                Spacer(Modifier.height(8.dp))
                KCard {
                    CheckRow(stringResource(R.string.notif_permission), canNotify)
                    Hairline(Modifier.padding(vertical = 10.dp))
                    CheckRow(stringResource(R.string.exact_alarms), canExact)
                    Hairline(Modifier.padding(vertical = 10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.diag_data_check), style = MaterialTheme.typography.titleSmall)
                            Text(
                                stringResource(
                                    when (check) {
                                        null -> R.string.diag_data_check_summary
                                        DataCheck.RUNNING -> R.string.diag_data_checking
                                        DataCheck.HEALTHY -> R.string.diag_data_healthy
                                        DataCheck.PROBLEM -> R.string.diag_data_problem
                                    },
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (check == DataCheck.PROBLEM) MaterialTheme.colorScheme.error else Kairos.colors.muted,
                            )
                        }
                        if (check == DataCheck.RUNNING) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            TextButton(
                                onClick = {
                                    check = DataCheck.RUNNING
                                    scope.launch {
                                        val ok = withContext(Dispatchers.IO) {
                                            runCatching {
                                                val db = c.database.openHelper.readableDatabase
                                                val integrity = db.query("PRAGMA integrity_check").use { it.moveToFirst() && it.getString(0) == "ok" }
                                                val keys = db.query("PRAGMA foreign_key_check").use { !it.moveToFirst() }
                                                integrity && keys
                                            }.getOrElse { SafeLog.error("data_check_failed", it); false }
                                        }
                                        SafeLog.event("data_check", "ok" to ok)
                                        check = if (ok) DataCheck.HEALTHY else DataCheck.PROBLEM
                                    }
                                },
                                modifier = Modifier.heightIn(min = 48.dp),
                            ) { Text(stringResource(R.string.diag_data_check_run)) }
                        }
                    }
                }

                Spacer(Modifier.height(28.dp))
                SectionLabel(stringResource(R.string.diag_about_label))
                Spacer(Modifier.height(8.dp))
                KCard {
                    InfoRow(stringResource(R.string.diag_app_version), "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                    Hairline(Modifier.padding(vertical = 10.dp))
                    InfoRow(stringResource(R.string.diag_android_version), "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                }
                Spacer(Modifier.height(24.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Outlined.Lock, contentDescription = null, tint = Kairos.colors.muted, modifier = Modifier.size(16.dp).padding(top = 2.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.diag_privacy), style = MaterialTheme.typography.bodySmall, color = Kairos.colors.muted)
                }
            }
        }
    }
}

@Composable
private fun StatusLine(icon: ImageVector, tint: androidx.compose.ui.graphics.Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun CheckRow(title: String, ok: Boolean) {
    Row(Modifier.heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Text(
            stringResource(if (ok) R.string.status_allowed else R.string.status_off),
            style = MaterialTheme.typography.labelLarge,
            color = if (ok) Kairos.colors.success.strong else Kairos.colors.motivation.strong,
        )
    }
}

@Composable
private fun InfoRow(title: String, value: String) {
    Row(Modifier.heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = Kairos.colors.muted)
    }
}
