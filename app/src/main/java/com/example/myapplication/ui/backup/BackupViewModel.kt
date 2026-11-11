package com.example.myapplication.ui.backup

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.BreakingApp
import com.example.myapplication.data.backup.BackupStatus
import com.example.myapplication.data.backup.FolderWithBackup
import com.example.myapplication.data.backup.PickedBackup
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BackupMessage(val text: String, val canUndo: Boolean = false)

data class BackupUiState(
    val status: BackupStatus = BackupStatus(),
    val working: Boolean = false,
    /** A picked folder that already holds a backup, waiting for the user to restore or replace it. */
    val folderWithBackup: FolderWithBackup? = null,
    /** A backup file waiting for the user to confirm restoring it. */
    val picked: PickedBackup? = null,
    val message: BackupMessage? = null,
)

class BackupViewModel(application: Application) : AndroidViewModel(application) {
    private val backup = (application as BreakingApp).backup
    private val local = MutableStateFlow(BackupUiState())

    val uiState: StateFlow<BackupUiState> =
        combine(backup.status, local) { status, local -> local.copy(status = status) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BackupUiState(status = backup.status.value))

    fun chooseFolder(tree: Uri) = work {
        val found = try {
            backup.chooseFolder(tree)
        } catch (e: SecurityException) {
            show("Cypher can't use that folder. Pick another one.")
            return@work
        } catch (e: IllegalArgumentException) {
            show("Cypher can't use that folder. Pick another one.")
            return@work
        }
        if (found != null) local.update { it.copy(folderWithBackup = found) } else reportBackup()
    }

    /** Keeps the picked folder and overwrites its backup with what's in the app now. */
    fun replaceFolderBackup() = resolveFolderChoice { choice ->
        backup.useFolder(choice.folder)
        reportBackup()
    }

    /** Restores the backup found in the picked folder, then keeps backing up there. */
    fun restoreFolderBackup() = resolveFolderChoice { choice ->
        val existing = choice.existing ?: return@resolveFolderChoice
        if (restoreFrom(existing)) backup.useFolder(choice.folder) else backup.cancelFolder(choice.folder)
    }

    fun cancelFolderChoice() {
        local.value.folderWithBackup?.let { backup.cancelFolder(it.folder) }
        local.update { it.copy(folderWithBackup = null) }
    }

    fun backUpNow() = work { reportBackup() }

    fun turnOff() {
        backup.turnOff()
        show("Automatic backups are off. The last backup file is still in the folder.")
    }

    fun pickBackupFile(uri: Uri) = work {
        val picked = backup.inspect(uri)
        if (picked == null) {
            show("That file isn't a Cypher backup, or it's from a newer version of the app.")
        } else {
            local.update { it.copy(picked = picked) }
        }
    }

    fun confirmRestore() {
        val picked = local.value.picked ?: return
        local.update { it.copy(picked = null) }
        work { restoreFrom(picked) }
    }

    fun cancelRestore() = local.update { it.copy(picked = null) }

    fun undoRestore() = work {
        show(if (backup.undoRestore()) "Put back what was there before the restore" else "Couldn't undo the restore")
    }

    /** Writes a fresh backup and hands [send] a link to it for the share sheet. */
    fun prepareShare(send: (Uri) -> Unit) = work {
        val uri = try {
            backup.shareableBackup()
        } catch (e: IOException) {
            null
        } catch (e: IllegalStateException) {
            null
        }
        if (uri != null) send(uri) else show("Couldn't make a backup to send")
    }

    fun messageShown() = local.update { it.copy(message = null) }

    private suspend fun restoreFrom(picked: PickedBackup): Boolean {
        val restored = backup.restore(picked)
        when {
            !restored -> show("Couldn't restore that backup. Nothing was changed.")
            picked.summary.videos > 0 -> show("Restored. Open the Videos tab to reconnect your videos.", canUndo = true)
            else -> show("Restored", canUndo = true)
        }
        return restored
    }

    /** Writes a backup now and says how it went. */
    private suspend fun reportBackup() {
        val written = backup.backUpNow()
        val folder = backup.status.value.folderName ?: "your folder"
        show(if (written) "Backed up to $folder" else "Couldn't write to $folder. Choose the folder again.")
    }

    private fun resolveFolderChoice(block: suspend (FolderWithBackup) -> Unit) {
        val choice = local.value.folderWithBackup ?: return
        local.update { it.copy(folderWithBackup = null) }
        work { block(choice) }
    }

    private fun show(text: String, canUndo: Boolean = false) =
        local.update { it.copy(message = BackupMessage(text, canUndo)) }

    private fun work(block: suspend () -> Unit) {
        viewModelScope.launch {
            local.update { it.copy(working = true) }
            try {
                block()
            } finally {
                local.update { it.copy(working = false) }
            }
        }
    }
}
