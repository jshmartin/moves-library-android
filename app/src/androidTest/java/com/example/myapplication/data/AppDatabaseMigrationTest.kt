package com.example.myapplication.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test
    fun migrate2To3KeepsVideosAndLeavesThemUnlinked() {
        helper.createDatabase(DB_NAME, 2).use { db ->
            db.execSQL(
                """
                INSERT INTO videos (id, uri, displayName, sizeBytes, durationMs, mimeType, fingerprint, addedAt)
                VALUES (7, 'content://clip', 'clip.mp4', 123, 4000, 'video/mp4', 'abc', 99)
                """
            )
        }

        helper.runMigrationsAndValidate(DB_NAME, 3, true).use { db ->
            db.query("SELECT uri, addedAt, moveId, setId, routineId, linkedAt FROM videos WHERE id = 7").use { row ->
                assertTrue(row.moveToFirst())
                assertEquals("content://clip", row.getString(0))
                assertEquals(99L, row.getLong(1))
                (2..5).forEach { assertTrue(row.isNull(it)) }
            }
        }
    }

    private companion object {
        const val DB_NAME = "migration-test"
    }
}
