package com.example.myapplication.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
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

    private fun SupportSQLiteDatabase.seedVersion3() {
        execSQL("INSERT INTO categories (id, name, sortOrder) VALUES (1, 'Power', 0)")
        execSQL(
            """
            INSERT INTO moves (id, name, categoryId, parentMoveId, status, notes, createdAt)
            VALUES (2, 'Windmill', 1, NULL, 'LEARNING', '', 5)
            """
        )
        execSQL("INSERT INTO routines (id, name, notes, createdAt) VALUES (3, 'Power day', 'n', 6)")
        execSQL(
            """
            INSERT INTO routine_drills (routineId, position, type, moveId, setId, roundId, label, reps, durationSec)
            VALUES (3, 0, 'MOVE', 2, NULL, NULL, '', 12, 30),
                   (3, 1, 'ROUND', NULL, NULL, NULL, '', NULL, NULL)
            """
        )
        execSQL("INSERT INTO practice_sessions (id, routineId, startedAt, endedAt, notes) VALUES (4, 3, 7, 8, 'good')")
        execSQL("INSERT INTO practice_sessions (id, routineId, startedAt, endedAt, notes) VALUES (5, NULL, 9, NULL, '')")
        execSQL(
            """
            INSERT INTO videos (id, uri, displayName, sizeBytes, durationMs, mimeType, fingerprint, addedAt,
                moveId, setId, routineId, linkedAt)
            VALUES (10, 'content://a', 'a.mp4', 1, NULL, NULL, 'a', 50, 2, NULL, NULL, 60),
                   (11, 'content://b', 'b.mp4', 1, NULL, NULL, 'b', 51, NULL, NULL, 3, 61)
            """
        )
    }

    private fun SupportSQLiteDatabase.rowCount(table: String): Int =
        query("SELECT COUNT(*) FROM $table").use {
            it.moveToFirst()
            it.getInt(0)
        }

    @Test
    fun migrate3To4TurnsRoutinesIntoPracticesAndKeepsEverything() {
        helper.createDatabase(DB_NAME, 3).use { it.seedVersion3() }

        helper.runMigrationsAndValidate(DB_NAME, 4, true, AppDatabase.MIGRATION_3_4).use { db ->
            db.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
            db.query("SELECT name, notes, rounds, activeSec, restSec FROM practices WHERE id = 3").use { row ->
                assertTrue(row.moveToFirst())
                assertEquals("Power day", row.getString(0))
                assertEquals("n", row.getString(1))
                assertEquals(5, row.getInt(2))
                assertEquals(60, row.getInt(3))
                assertEquals(30, row.getInt(4))
            }
            db.query("SELECT moveId, targetSets, targetReps FROM practice_drills WHERE practiceId = 3").use { row ->
                assertTrue(row.moveToFirst())
                assertEquals(2L, row.getLong(0))
                assertEquals(1, row.getInt(1))
                assertEquals(12, row.getInt(2))
            }
            db.query("SELECT practiceId, name, lastActiveAt, endedAt, notes FROM practice_sessions ORDER BY id").use { row ->
                assertTrue(row.moveToFirst())
                assertEquals(3L, row.getLong(0))
                assertEquals("Power day", row.getString(1))
                assertEquals(8L, row.getLong(2))
                assertEquals(8L, row.getLong(3))
                assertEquals("good", row.getString(4))
                assertTrue(row.moveToNext())
                assertTrue(row.isNull(0))
                assertEquals("Practice", row.getString(1))
                assertEquals(9L, row.getLong(2))
                assertTrue(row.isNull(3))
            }
            db.query("SELECT moveId, linkedAt FROM videos WHERE id = 10").use { row ->
                assertTrue(row.moveToFirst())
                assertEquals(2L, row.getLong(0))
                assertEquals(60L, row.getLong(1))
            }
            db.query("SELECT moveId, setId, linkedAt FROM videos WHERE id = 11").use { row ->
                assertTrue(row.moveToFirst())
                (0..2).forEach { assertTrue(row.isNull(it)) }
            }
            db.query("SELECT practiceId, videoId, linkedAt FROM practice_videos").use { row ->
                assertEquals(1, row.count)
                assertTrue(row.moveToFirst())
                assertEquals(3L, row.getLong(0))
                assertEquals(11L, row.getLong(1))
                assertEquals(61L, row.getLong(2))
            }
            db.query("SELECT name FROM sqlite_master WHERE name IN ('routines', 'routine_drills', 'routine_video_links')").use {
                assertEquals(0, it.count)
            }
        }
    }

    /** Room runs migrations with foreign keys off; dropping tables must be safe even if that changes. */
    @Test
    fun migrate3To4KeepsEveryRowWithForeignKeysEnforced() {
        helper.createDatabase(DB_NAME, 3).use { db ->
            db.execSQL("PRAGMA foreign_keys = ON")
            db.seedVersion3()

            AppDatabase.MIGRATION_3_4.migrate(db)

            assertEquals(1, db.rowCount("practices"))
            assertEquals(1, db.rowCount("practice_drills"))
            assertEquals(2, db.rowCount("practice_sessions"))
            assertEquals(2, db.rowCount("videos"))
            assertEquals(1, db.rowCount("practice_videos"))
        }
    }

    private companion object {
        const val DB_NAME = "migration-test"
    }
}
