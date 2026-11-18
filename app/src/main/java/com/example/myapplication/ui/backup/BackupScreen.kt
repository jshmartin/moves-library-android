package com.example.myapplication.ui.backup

import android.content.Intent
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.example.myapplication.data.backup.BackupStatus
import com.example.myapplication.data.backup.BackupSummary
import com.example.myapplication.data.backup.FolderWithBackup

object BackupRoutes {
    const val HOME = "backup"
}

fun NavGraphBuilder.backupScreen(onBack: () -> Unit) {
    composable(BackupRoutes.HOME) { BackupScreen(onBack = onBack) }
}

/** Shown on first launch and at the top of the backup screen, so nobody finds out the hard way. */
private const val DATA_NOTICE =
    "Cypher has no account and no cloud. Your moves, sets, practices, logs and video links are saved only on " +
        "this phone, so uninstalling the app, clearing its storage or losing the phone erases them. " +
        "Your video files aren't affected."

/** The first-launch notice that the app's data lives only on this phone. */
@Composable
fun DataNoticeDialog(onSetUp: () -> Unit, onNotNow: () -> Unit) {
    AlertDialog(
        onDismissRequest = onNotNow,
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text("Your data stays on this phone") },
        text = {
            Text(
                "$DATA_NOTICE\n\nTurn on automatic backups to keep a copy in a folder you choose. " +
                    "If you've used Cypher before, you can restore your backup there too."
            )
        },
        confirmButton = { TextButton(onClick = onSetUp) { Text("Set up backup") } },
        dismissButton = { TextButton(onClick = onNotNow) { Text("Not now") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(onBack: () -> Unit, viewModel: BackupViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message.text,
            actionLabel = "Undo".takeIf { message.canUndo },
            duration = if (message.canUndo) SnackbarDuration.Long else SnackbarDuration.Short,
        )
        viewModel.messageShown()
        if (result == SnackbarResult.ActionPerformed) viewModel.undoRestore()
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        tree?.let(viewModel::chooseFolder)
    }
    // Backup files have no standard type, and what they arrive as depends on how they were sent.
    val backupPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::pickBackupFile)
    }
    val send = {
        viewModel.prepareShare { uri ->
            val intent = Intent(Intent.ACTION_SEND)
                .setType("application/octet-stream")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(Intent.createChooser(intent, "Send backup"))
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Backup & transfer") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(Modifier.padding(innerPadding)) {
            if (state.working) LinearProgressIndicator(Modifier.fillMaxWidth())
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { Section("Your data stays on this phone") { Body(DATA_NOTICE) } }
                item {
                    AutomaticBackupSection(
                        status = state.status,
                        working = state.working,
                        onChooseFolder = { folderPicker.launch(null) },
                        onBackUpNow = viewModel::backUpNow,
                        onTurnOff = viewModel::turnOff,
                    )
                }
                item {
                    Section("Restore") {
                        Body(
                            "Replace everything in the app with a backup file. Use this after reinstalling Cypher " +
                                "or on a new phone. You can undo it straight after."
                        )
                        OutlinedButton(onClick = { backupPicker.launch(arrayOf("*/*")) }, enabled = !state.working) {
                            Text("Restore from a backup file")
                        }
                    }
                }
                item {
                    Section("Move to a new phone") {
                        Step(1, "Move your videos to the new phone, for example with your phone's transfer tool.")
                        Step(2, "Send a backup to the new phone. Quick Share sends it straight across without the internet.")
                        OutlinedButton(onClick = send, enabled = !state.working) { Text("Send backup") }
                        Step(3, "On the new phone, install Cypher and open Backup & transfer. Tap Restore from a backup file.")
                        Step(4, "In the Videos tab, tap Search device to reconnect your videos.")
                    }
                }
            }
        }
    }

    state.folderWithBackup?.let { choice ->
        FolderWithBackupDialog(
            choice = choice,
            onRestore = viewModel::restoreFolderBackup,
            onReplace = viewModel::replaceFolderBackup,
            onCancel = viewModel::cancelFolderChoice,
        )
    }

    state.picked?.let { picked ->
        AlertDialog(
            onDismissRequest = viewModel::cancelRestore,
            title = { Text("Restore this backup?") },
            text = {
                Text(
                    "It has ${describeBackup(picked.summary)}. Everything in the app now will be replaced. " +
                        "You can undo this straight after."
                )
            },
            confirmButton = { TextButton(onClick = viewModel::confirmRestore) { Text("Restore") } },
            dismissButton = { TextButton(onClick = viewModel::cancelRestore) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AutomaticBackupSection(
    status: BackupStatus,
    working: Boolean,
    onChooseFolder: () -> Unit,
    onBackUpNow: () -> Unit,
    onTurnOff: () -> Unit,
) {
    Section("Automatic backup") {
        if (status.folder == null) {
            Body(
                "Choose a folder, such as Documents, and Cypher keeps a backup file there that updates by itself " +
                    "whenever you make a change. The file stays even if the app is uninstalled."
            )
            Button(onClick = onChooseFolder, enabled = !working) { Text("Choose folder") }
            return@Section
        }
        Body("Backing up to ${status.folderName ?: "your chosen folder"}")
        val lastBackupAt = status.lastBackupAt
        when {
            status.failed -> Text(
                "The last backup didn't work. The folder may have been moved or deleted. Choose it again.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            lastBackupAt != null -> Text(
                "Last backup ${timeAgo(lastBackupAt)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onBackUpNow, enabled = !working) { Text("Back up now") }
            TextButton(onClick = onChooseFolder, enabled = !working) { Text("Change folder") }
            TextButton(onClick = onTurnOff, enabled = !working) { Text("Turn off") }
        }
    }
}

@Composable
private fun FolderWithBackupDialog(
    choice: FolderWithBackup,
    onRestore: () -> Unit,
    onReplace: () -> Unit,
    onCancel: () -> Unit,
) {
    val existing = choice.existing
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("This folder already has a backup") },
        text = {
            Text(
                if (existing != null) {
                    "It has ${describeBackup(existing.summary)}. Restore it to bring that data into the app, " +
                        "or replace it with what's in the app now."
                } else {
                    "There's a backup file here that Cypher can't read. Replace it with what's in the app now?"
                }
            )
        },
        confirmButton = {
            Row {
                TextButton(onClick = onReplace) { Text("Replace it") }
                if (existing != null) TextButton(onClick = onRestore) { Text("Restore it") }
            }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun Body(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun Step(number: Int, text: String) {
    Row {
        Text("$number.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(24.dp))
        Body(text)
    }
}

private fun timeAgo(time: Long): String {
    val now = System.currentTimeMillis()
    return if (now - time < DateUtils.MINUTE_IN_MILLIS) {
        "just now"
    } else {
        DateUtils.getRelativeTimeSpanString(time, now, DateUtils.MINUTE_IN_MILLIS).toString()
    }
}

/** "3 moves, 1 set, 2 practices, 10 logged sessions and 4 videos". */
internal fun describeBackup(summary: BackupSummary): String {
    val parts = listOf(
        count(summary.moves, "move"),
        count(summary.sets, "set"),
        count(summary.practices, "practice"),
        count(summary.sessions, "logged session"),
        count(summary.videos, "video"),
    )
    return parts.dropLast(1).joinToString(", ") + " and " + parts.last()
}

private fun count(n: Int, noun: String) = if (n == 1) "1 $noun" else "$n ${noun}s"
