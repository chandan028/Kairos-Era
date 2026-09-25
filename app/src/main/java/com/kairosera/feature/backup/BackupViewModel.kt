package com.kairosera.feature.backup

import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairosera.AppContainer
import com.kairosera.R
import com.kairosera.core.backup.BackupCrypto
import com.kairosera.core.backup.BackupManager
import com.kairosera.core.backup.BackupPreview
import com.kairosera.core.diagnostics.SafeLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Instant

enum class BackupBusy { CREATING, OPENING, RESTORING, UNDOING }

enum class BackupDone { SAVED, RESTORED, UNDONE }

data class BackupUi(
    val busy: BackupBusy? = null,
    val preview: BackupPreview? = null,
    @StringRes val error: Int? = null,
    val snapshotAt: Instant? = null,
    val done: BackupDone? = null,
)

/**
 * Drives the Backup and restore screen. Passphrases are held as char arrays only for as long as
 * the work takes and are wiped afterwards; they are never logged, saved or put in the UI state.
 */
class BackupViewModel(private val c: AppContainer) : ViewModel() {
    private val _ui = MutableStateFlow(BackupUi(snapshotAt = c.backup.snapshotTakenAt()))
    val ui: StateFlow<BackupUi> = _ui.asStateFlow()

    private var pending: CharArray? = null

    /** Keeps the new passphrase until the person has chosen where to save the file. */
    fun setPassphrase(passphrase: CharArray) {
        pending?.fill('\u0000')
        pending = passphrase
    }

    fun create(uri: Uri) {
        val pass = pending ?: return
        pending = null
        run(BackupBusy.CREATING) {
            try {
                val bytes = c.backup.create(pass)
                withContext(Dispatchers.IO) {
                    c.appContext.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } ?: throw IOException("no_stream")
                }
                c.backup.markBackedUp()
                _ui.update { it.copy(done = BackupDone.SAVED) }
            } finally {
                pass.fill('\u0000')
            }
        }
    }

    fun open(uri: Uri, passphrase: CharArray) = run(BackupBusy.OPENING) {
        try {
            val bytes = withContext(Dispatchers.IO) { read(uri) }
            val preview = c.backup.open(passphrase, bytes)
            _ui.update { it.copy(preview = preview) }
        } finally {
            passphrase.fill('\u0000')
        }
    }

    fun restore() {
        val preview = _ui.value.preview ?: return
        run(BackupBusy.RESTORING) {
            c.backup.restore(preview)
            _ui.update { it.copy(preview = null, snapshotAt = c.backup.snapshotTakenAt(), done = BackupDone.RESTORED) }
        }
    }

    fun undo() = run(BackupBusy.UNDOING) {
        c.backup.undoRestore()
        _ui.update { it.copy(snapshotAt = c.backup.snapshotTakenAt(), done = BackupDone.UNDONE) }
    }

    fun cancelPreview() = _ui.update { it.copy(preview = null) }
    fun clearError() = _ui.update { it.copy(error = null) }
    fun doneShown() = _ui.update { it.copy(done = null) }

    override fun onCleared() {
        pending?.fill('\u0000')
    }

    private fun read(uri: Uri): ByteArray {
        val input = c.appContext.contentResolver.openInputStream(uri) ?: throw IOException("no_stream")
        return input.use { s ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = s.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                if (out.size() > MAX_FILE) throw BackupCrypto.OpenError.NotABackup()
            }
            out.toByteArray()
        }
    }

    private fun run(busy: BackupBusy, block: suspend () -> Unit) {
        if (_ui.value.busy != null) return
        _ui.update { it.copy(busy = busy, error = null) }
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                SafeLog.error("backup_${busy.name.lowercase()}_failed", e)
                _ui.update { it.copy(error = messageFor(e, busy)) }
            } finally {
                _ui.update { it.copy(busy = null) }
            }
        }
    }

    @StringRes
    private fun messageFor(e: Exception, busy: BackupBusy): Int = when (e) {
        is BackupCrypto.OpenError.CannotDecrypt -> R.string.backup_error_passphrase
        is BackupCrypto.OpenError.NotABackup -> R.string.backup_error_not_backup
        is BackupCrypto.OpenError.NewerFormat, is BackupManager.Failure.NewerApp -> R.string.backup_error_newer
        is BackupManager.Failure.Damaged -> R.string.backup_error_damaged
        is IOException -> if (busy == BackupBusy.CREATING) R.string.backup_error_write else R.string.backup_error_read
        else -> R.string.backup_error_generic
    }

    private companion object {
        const val MAX_FILE = 512L * 1024 * 1024
    }
}
