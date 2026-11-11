package com.example.myapplication

import android.app.Application
import android.util.Log
import com.example.myapplication.data.AppDatabase
import com.example.myapplication.data.backup.BackupManager
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class BreakingApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.build(this) }

    /** Lives as long as the app, so automatic backups keep running whichever screen is open. */
    val backup: BackupManager by lazy {
        val logFailures = CoroutineExceptionHandler { _, e -> Log.e("Backup", "Background backup work failed", e) }
        BackupManager(this, database, CoroutineScope(SupervisorJob() + Dispatchers.Default + logFailures))
    }
}
