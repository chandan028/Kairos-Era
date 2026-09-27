package com.kairosera.feature.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kairosera.R
import com.kairosera.core.backup.BackupCrypto
import com.kairosera.core.backup.BackupPreview
import com.kairosera.core.settings.AppSettings
import com.kairosera.core.ui.components.ContentMaxWidth
import com.kairosera.core.ui.components.KCard
import com.kairosera.core.ui.components.LocalToaster
import com.kairosera.core.ui.components.ScreenHeader
import com.kairosera.core.ui.components.SectionLabel
import com.kairosera.core.ui.components.ToastKind
import com.kairosera.core.ui.components.rememberDateFormatter
import com.kairosera.core.ui.theme.Kairos
import com.kairosera.core.ui.theme.SerifFamily
import com.kairosera.ui.appContainer
import com.kairosera.ui.kairosViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun BackupScreen(onBack: () -> Unit) {
    val c = appContainer()
    val vm = kairosViewModel(key = "backup") { BackupViewModel(it) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val settings by c.settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val toaster = LocalToaster.current
    var askNew by rememberSaveable { mutableStateOf(false) }
    var openUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var confirmRestore by rememberSaveable { mutableStateOf(false) }
    var confirmUndo by rememberSaveable { mutableStateOf(false) }

    val saveTo = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) vm.create(uri) else vm.setPassphrase(CharArray(0))
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) openUri = uri }

    val saved = stringResource(R.string.backup_saved)
    val restored = stringResource(R.string.backup_restored)
    val undone = stringResource(R.string.backup_undone)
    LaunchedEffect(ui.done) {
        when (ui.done) {
            BackupDone.SAVED -> toaster.show(saved, ToastKind.SUCCESS)
            BackupDone.RESTORED -> toaster.show(restored, ToastKind.SUCCESS)
            BackupDone.UNDONE -> toaster.show(undone, ToastKind.SUCCESS)
            null -> return@LaunchedEffect
        }
        vm.doneShown()
    }

    val date = rememberDateFormatter("d MMM yyyy")
    val dateTime = rememberDateFormatter("d MMM yyyy")
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(stringResource(R.string.backup_title), onBack = onBack)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = ContentMaxWidth).fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 112.dp),
            ) {
                KCard(color = Kairos.colors.brand) {
                    Icon(Icons.Outlined.Lock, contentDescription = null, tint = Kairos.colors.accent, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.backup_hero_title),
                        fontFamily = SerifFamily,
                        style = MaterialTheme.typography.headlineSmall,
                        color = Kairos.colors.onBrand,
                        modifier = Modifier.semantics { heading() },
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.backup_hero_body), style = MaterialTheme.typography.bodyMedium, color = Kairos.colors.onBrand.copy(alpha = 0.85f))
                    Spacer(Modifier.height(16.dp))
                    val last = settings.lastBackupAt
                    Text(
                        if (last == null) stringResource(R.string.backup_never) else stringResource(R.string.backup_last, date(Instant.ofEpochMilli(last).atZone(ZoneId.systemDefault()).toLocalDate())),
                        style = MaterialTheme.typography.labelLarge,
                        color = Kairos.colors.accent,
                    )
                }
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = { askNew = true },
                    enabled = ui.busy == null,
                    colors = ButtonDefaults.buttonColors(containerColor = Kairos.colors.accent, contentColor = Kairos.colors.brand),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) { Text(stringResource(R.string.backup_create), style = MaterialTheme.typography.labelLarge) }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { vm.clearError(); pick.launch(arrayOf("*/*")) },
                    enabled = ui.busy == null,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) { Text(stringResource(R.string.backup_restore), style = MaterialTheme.typography.labelLarge) }

                ui.busy?.let { busy ->
                    Spacer(Modifier.height(20.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            stringResource(
                                when (busy) {
                                    BackupBusy.CREATING -> R.string.backup_busy_creating
                                    BackupBusy.OPENING -> R.string.backup_busy_opening
                                    BackupBusy.RESTORING -> R.string.backup_busy_restoring
                                    BackupBusy.UNDOING -> R.string.backup_busy_undoing
                                },
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                ui.error?.let { err ->
                    Spacer(Modifier.height(20.dp))
                    KCard(color = Kairos.colors.activity.soft, padding = 16.dp) {
                        Row {
                            Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = Kairos.colors.activity.strong, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(stringResource(err), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                ui.preview?.let { p ->
                    Spacer(Modifier.height(24.dp))
                    PreviewCard(p, dateTime, onRestore = { confirmRestore = true }, onCancel = vm::cancelPreview, enabled = ui.busy == null)
                }
                ui.snapshotAt?.let { at ->
                    Spacer(Modifier.height(28.dp))
                    SectionLabel(stringResource(R.string.backup_undo_label))
                    Spacer(Modifier.height(8.dp))
                    KCard(onClick = { if (ui.busy == null) confirmUndo = true }, padding = 16.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Restore, contentDescription = null, tint = Kairos.colors.accentText, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.backup_undo), style = MaterialTheme.typography.titleSmall)
                                Text(
                                    stringResource(R.string.backup_undo_body, dateTime(at.atZone(ZoneId.systemDefault()).toLocalDate())),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Kairos.colors.muted,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(28.dp))
                SectionLabel(stringResource(R.string.backup_tips_label))
                Spacer(Modifier.height(8.dp))
                listOf(R.string.backup_tip_elsewhere, R.string.backup_tip_passphrase, R.string.backup_tip_regular).forEach {
                    Row(Modifier.padding(vertical = 4.dp)) {
                        Text("•", color = Kairos.colors.accentText, modifier = Modifier.width(16.dp))
                        Text(stringResource(it), style = MaterialTheme.typography.bodyMedium, color = Kairos.colors.muted)
                    }
                }
            }
        }
    }

    if (askNew) {
        NewPassphraseDialog(
            onDismiss = { askNew = false },
            onConfirm = { pass ->
                askNew = false
                vm.setPassphrase(pass)
                saveTo.launch("kairos-era-${LocalDate.now()}.kairos")
            },
        )
    }
    openUri?.let { uri ->
        PassphraseDialog(
            onDismiss = { openUri = null },
            onConfirm = { pass -> openUri = null; vm.open(uri, pass) },
        )
    }
    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text(stringResource(R.string.backup_confirm_title)) },
            text = { Text(stringResource(R.string.backup_confirm_body)) },
            confirmButton = { TextButton(onClick = { confirmRestore = false; vm.restore() }) { Text(stringResource(R.string.backup_replace), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (confirmUndo) {
        AlertDialog(
            onDismissRequest = { confirmUndo = false },
            title = { Text(stringResource(R.string.backup_undo_confirm_title)) },
            text = { Text(stringResource(R.string.backup_undo_confirm_body)) },
            confirmButton = { TextButton(onClick = { confirmUndo = false; vm.undo() }) { Text(stringResource(R.string.backup_undo)) } },
            dismissButton = { TextButton(onClick = { confirmUndo = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun PreviewCard(p: BackupPreview, date: (LocalDate) -> String, onRestore: () -> Unit, onCancel: () -> Unit, enabled: Boolean) {
    KCard {
        SectionLabel(stringResource(R.string.backup_preview_label), color = Kairos.colors.accentText)
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.backup_preview_from, date(p.createdAt.atZone(ZoneId.systemDefault()).toLocalDate())),
            fontFamily = SerifFamily,
            style = MaterialTheme.typography.titleLarge,
        )
        Text(stringResource(R.string.backup_preview_version, p.manifest.appVersion), style = MaterialTheme.typography.bodySmall, color = Kairos.colors.muted)
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(pluralStringResource(R.plurals.task_count, p.count("tasks"), p.count("tasks")), style = MaterialTheme.typography.bodyMedium)
            Text(pluralStringResource(R.plurals.tracker_count, p.count("trackers"), p.count("trackers")), style = MaterialTheme.typography.bodyMedium)
            Text(pluralStringResource(R.plurals.book_count, p.count("books"), p.count("books")), style = MaterialTheme.typography.bodyMedium)
            Text(pluralStringResource(R.plurals.reflections, p.count("journal_entries"), p.count("journal_entries")), style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.backup_preview_note), style = MaterialTheme.typography.bodySmall, color = Kairos.colors.muted)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onCancel, enabled = enabled, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(stringResource(R.string.cancel)) }
            Button(
                onClick = onRestore,
                enabled = enabled,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.backup_replace)) }
        }
    }
}

@Composable
private fun NewPassphraseDialog(onDismiss: () -> Unit, onConfirm: (CharArray) -> Unit) {
    var pass by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    var shown by remember { mutableStateOf(false) }
    val tooShort = pass.length < BackupCrypto.MIN_PASSPHRASE
    val mismatch = again.isNotEmpty() && again != pass
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_passphrase_new_title)) },
        text = {
            Column {
                Text(stringResource(R.string.backup_passphrase_new_body), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                SecretField(pass, { pass = it }, stringResource(R.string.backup_passphrase), shown, { shown = !shown },
                    supporting = stringResource(R.string.backup_passphrase_min, BackupCrypto.MIN_PASSPHRASE), imeAction = ImeAction.Next)
                Spacer(Modifier.height(8.dp))
                SecretField(again, { again = it }, stringResource(R.string.backup_passphrase_again), shown, { shown = !shown },
                    supporting = if (mismatch) stringResource(R.string.backup_passphrase_mismatch) else null, isError = mismatch)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.backup_passphrase_warning), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(pass.toCharArray()) }, enabled = !tooShort && again == pass) { Text(stringResource(R.string.backup_choose_place)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun PassphraseDialog(onDismiss: () -> Unit, onConfirm: (CharArray) -> Unit) {
    var pass by remember { mutableStateOf("") }
    var shown by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_passphrase_open_title)) },
        text = { SecretField(pass, { pass = it }, stringResource(R.string.backup_passphrase), shown, { shown = !shown }) },
        confirmButton = { TextButton(onClick = { onConfirm(pass.toCharArray()) }, enabled = pass.isNotEmpty()) { Text(stringResource(R.string.backup_open)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun SecretField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    shown: Boolean,
    onToggle: () -> Unit,
    supporting: String? = null,
    isError: Boolean = false,
    imeAction: ImeAction = ImeAction.Done,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.take(256)) },
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        supportingText = supporting?.let { { Text(it) } },
        visualTransformation = if (shown) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false, imeAction = imeAction),
        trailingIcon = {
            IconButton(onClick = onToggle) {
                Icon(
                    if (shown) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = stringResource(if (shown) R.string.backup_hide_passphrase else R.string.backup_show_passphrase),
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}
