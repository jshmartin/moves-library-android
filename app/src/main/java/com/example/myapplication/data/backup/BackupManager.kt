package com.example.myapplication.data.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.core.net.toUri
import com.example.myapplication.data.AppDatabase
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Where automatic backups go and how the last one went. */
data class BackupStatus(
    /** The folder automatic backups go to, or null if they're off. */
    val folder: Uri? = null,
    val folderName: String? = null,
    val lastBackupAt: Long? = null,
    /** The last automatic backup failed. The next one that works clears it. */
    val failed: Boolean = false,
    /** The user has seen the notice that their data lives only on this phone. */
    val noticeSeen: Boolean = false,
)

/** A backup file copied into the app and checked, waiting for the user to confirm the restore. */
data class PickedBackup(val summary: BackupSummary, val file: File)

/**
 * A folder the user picked that already holds a backup, possibly from before a reinstall or from
 * another phone. Nothing is written there until the user chooses to restore or replace it.
 * [existing] is null when the file there can't be read.
 */
data class FolderWithBackup(val folder: Uri, val existing: PickedBackup?)

/**
 * Keeps the app's database backed up to a file in a folder the user chose, and restores from
 * backup files. Everything stays on the device: the folder is on the phone's own storage, and a
 * backup reaches another phone only when the user sends it there.
 */
class BackupManager(
    context: Context,
    private val database: AppDatabase,
    private val scope: CoroutineScope,
) {
    private val context = context.applicationContext
    private val resolver = this.context.contentResolver
    private val prefs = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val workDir get() = File(context.cacheDir, "backup")

    /** What was in the app before the last restore, so the restore can be undone. */
    private val beforeRestore get() = File(context.filesDir, "before-restore.db")

    private val _status = MutableStateFlow(readStatus())
    val status: StateFlow<BackupStatus> = _status.asStateFlow()

    /** One backup, restore or check at a time: they share the work directory and the database. */
    private val busy = Mutex()
    private var watching: Job? = null

    init {
        if (_status.value.folder != null) watchForChanges(catchUp = true)
    }

    fun markNoticeSeen() {
        prefs.edit { putBoolean(KEY_NOTICE_SEEN, true) }
        _status.update { it.copy(noticeSeen = true) }
    }

    /**
     * Takes access to [tree], a folder the user picked. If it already holds a backup, returns it so
     * the user can choose between restoring and replacing it. Otherwise starts backing up there.
     */
    suspend fun chooseFolder(tree: Uri): FolderWithBackup? = withContext(Dispatchers.IO) {
        resolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        val existing = findBackup(tree)
        if (existing == null || tree == status.value.folder) {
            useFolder(tree)
            return@withContext null
        }
        FolderWithBackup(tree, inspect(existing))
    }

    /** Starts automatic backups into [tree], writing one straight away. */
    suspend fun useFolder(tree: Uri) = withContext(Dispatchers.IO) {
        status.value.folder?.takeIf { it != tree }?.let(::release)
        val name = folderName(tree)
        prefs.edit {
            putString(KEY_FOLDER, tree.toString())
            putString(KEY_FOLDER_NAME, name)
            putBoolean(KEY_NOTICE_SEEN, true)
        }
        _status.update { it.copy(folder = tree, folderName = name, noticeSeen = true) }
        backUpNow()
        watchForChanges(catchUp = false)
    }

    /** The user backed out of a folder they picked. */
    fun cancelFolder(tree: Uri) {
        if (tree != status.value.folder) release(tree)
    }

    fun turnOff() {
        watching?.cancel()
        status.value.folder?.let(::release)
        prefs.edit {
            remove(KEY_FOLDER)
            remove(KEY_FOLDER_NAME)
            remove(KEY_FAILED)
        }
        _status.update { it.copy(folder = null, folderName = null, failed = false) }
    }

    /** Writes the backup file into the chosen folder, replacing the previous one. Returns false if it couldn't. */
    suspend fun backUpNow(): Boolean = withContext(Dispatchers.IO) {
        val folder = status.value.folder ?: return@withContext false
        val written = busy.withLock {
            try {
                val snapshot = File(workDir, "snapshot.db")
                DatabaseBackup.write(context, database, snapshot)
                val target = findBackup(folder) ?: createBackup(folder)
                val out = resolver.openOutputStream(target, "wt") ?: throw IOException("Can't write $target")
                out.use { snapshot.inputStream().use { input -> input.copyTo(it) } }
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The folder may have been moved or deleted, or its access taken away.
                Log.w(TAG, "Backup failed", e)
                false
            }
        }
        prefs.edit {
            putBoolean(KEY_FAILED, !written)
            if (written) putLong(KEY_LAST_BACKUP, System.currentTimeMillis())
        }
        _status.value = readStatus()
        written
    }

    /** Writes a fresh backup for sending to another phone and returns a link other apps can read. */
    suspend fun shareableBackup(): Uri = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, SHARE_DIR).apply {
            deleteRecursively()
            mkdirs()
        }
        val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val file = File(dir, "cypher-backup-$day.db")
        busy.withLock { DatabaseBackup.write(context, database, file) }
        FileProvider.getUriForFile(context, "${context.packageName}.backup", file)
    }

    /** Copies a backup file the user picked into the app and checks it. Null if it isn't one the app can restore. */
    suspend fun inspect(uri: Uri): PickedBackup? = withContext(Dispatchers.IO) {
        busy.withLock {
            val file = File(workDir, "picked.db")
            val copied = try {
                copyIfSqlite(uri, file)
            } catch (e: IOException) {
                false
            } catch (e: SecurityException) {
                false
            }
            if (!copied) return@withLock null
            DatabaseBackup.inspect(context, file, appVersion())?.let { PickedBackup(it, file) }
        }
    }

    /**
     * Replaces everything in the app with [backup], keeping a copy of what was there for
     * [undoRestore]. Returns false and changes nothing if the restore fails.
     */
    suspend fun restore(backup: PickedBackup): Boolean = withContext(Dispatchers.IO) {
        busy.withLock {
            runRestore {
                DatabaseBackup.write(context, database, beforeRestore)
                DatabaseBackup.restore(context, backup.file, database)
            }
        }
    }

    /** Puts back what was in the app before the last restore. */
    suspend fun undoRestore(): Boolean = withContext(Dispatchers.IO) {
        busy.withLock {
            if (!beforeRestore.exists()) return@withLock false
            runRestore { DatabaseBackup.restore(context, beforeRestore, database) }
                .also { if (it) DatabaseBackup.deleteDatabaseFiles(beforeRestore) }
        }
    }

    private inline fun runRestore(block: () -> Unit): Boolean = try {
        block()
        true
    } catch (e: RuntimeException) {
        // The restore runs in one transaction, so a failure leaves the app's data as it was.
        Log.w(TAG, "Restore failed", e)
        false
    }

    @OptIn(FlowPreview::class)
    private fun watchForChanges(catchUp: Boolean) {
        if (watching?.isActive == true) return
        watching = scope.launch {
            database.invalidationTracker.createFlow(*WATCHED_TABLES, emitInitialState = catchUp)
                // Wait for changes to settle, so counting reps doesn't write a backup for every tap.
                .debounce(SETTLE_MILLIS)
                .collect { backUpNow() }
        }
    }

    private fun appVersion() = database.openHelper.readableDatabase.version

    /** Copies [source] to [dest], unless it doesn't start like a database. Avoids copying a big file only to reject it. */
    private fun copyIfSqlite(source: Uri, dest: File): Boolean {
        DatabaseBackup.deleteDatabaseFiles(dest)
        dest.parentFile?.mkdirs()
        val looksRight = resolver.openInputStream(source)?.use(DatabaseBackup::startsLikeSqlite) ?: false
        if (!looksRight) return false
        val input = resolver.openInputStream(source) ?: return false
        input.use { dest.outputStream().use { out -> it.copyTo(out) } }
        return true
    }

    private fun findBackup(tree: Uri): Uri? {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        resolver.query(children, arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME), null, null, null)
            ?.use { row ->
                while (row.moveToNext()) {
                    if (row.getString(1) == FILE_NAME) return DocumentsContract.buildDocumentUriUsingTree(tree, row.getString(0))
                }
            }
        return null
    }

    private fun createBackup(tree: Uri): Uri =
        DocumentsContract.createDocument(resolver, folderDocument(tree), MIME_TYPE, FILE_NAME)
            ?: throw IOException("Can't create a backup file in $tree")

    private fun folderName(tree: Uri): String? = runCatching {
        resolver.query(folderDocument(tree), arrayOf(Document.COLUMN_DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }.getOrNull()

    private fun folderDocument(tree: Uri) =
        DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))

    private fun release(tree: Uri) {
        runCatching {
            resolver.releasePersistableUriPermission(
                tree,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
    }

    private fun readStatus() = BackupStatus(
        folder = prefs.getString(KEY_FOLDER, null)?.toUri(),
        folderName = prefs.getString(KEY_FOLDER_NAME, null),
        lastBackupAt = prefs.getLong(KEY_LAST_BACKUP, 0).takeIf { it > 0 },
        failed = prefs.getBoolean(KEY_FAILED, false),
        noticeSeen = prefs.getBoolean(KEY_NOTICE_SEEN, false),
    )

    companion object {
        const val FILE_NAME = "cypher-backup.db"
        private const val MIME_TYPE = "application/octet-stream"
        private const val SHARE_DIR = "shared"
        private const val SETTLE_MILLIS = 5_000L
        private const val TAG = "Backup"

        private const val PREFS = "backup"
        private const val KEY_FOLDER = "folder"
        private const val KEY_FOLDER_NAME = "folderName"
        private const val KEY_LAST_BACKUP = "lastBackupAt"
        private const val KEY_FAILED = "failed"
        private const val KEY_NOTICE_SEEN = "noticeSeen"

        /** Every table in the database. A test checks this against the schema, so a new table can't be missed. */
        internal val WATCHED_TABLES = arrayOf(
            "categories", "moves", "video_clips", "sets", "set_moves", "rounds", "round_items",
            "practices", "practice_drills", "practice_sessions", "session_drills", "session_sets",
            "videos", "practice_videos", "session_videos",
        )
    }
}
