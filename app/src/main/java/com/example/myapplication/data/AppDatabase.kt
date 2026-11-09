package com.example.myapplication.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.myapplication.data.dao.ChainDao
import com.example.myapplication.data.dao.LibraryDao
import com.example.myapplication.data.dao.PracticeDao
import com.example.myapplication.data.model.Category
import com.example.myapplication.data.model.DanceSet
import com.example.myapplication.data.model.Move
import com.example.myapplication.data.model.Practice
import com.example.myapplication.data.model.PracticeDrill
import com.example.myapplication.data.model.PracticeSession
import com.example.myapplication.data.model.PracticeVideo
import com.example.myapplication.data.model.Round
import com.example.myapplication.data.model.RoundItem
import com.example.myapplication.data.model.SessionDrill
import com.example.myapplication.data.model.SessionSet
import com.example.myapplication.data.model.SessionVideo
import com.example.myapplication.data.model.SetMove
import com.example.myapplication.data.model.VideoClip

@Database(
    entities = [
        Category::class,
        Move::class,
        VideoClip::class,
        DanceSet::class,
        SetMove::class,
        Round::class,
        RoundItem::class,
        Practice::class,
        PracticeDrill::class,
        PracticeSession::class,
        SessionDrill::class,
        SessionSet::class,
        Video::class,
        PracticeVideo::class,
        SessionVideo::class,
    ],
    version = 4,
    autoMigrations = [AutoMigration(from = 2, to = 3)],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao
    abstract fun chainDao(): ChainDao
    abstract fun practiceDao(): PracticeDao
    abstract fun videoDao(): VideoDao

    companion object {
        /** Categories created on first launch. The user can rename, reorder or delete them. */
        val DEFAULT_CATEGORIES = listOf("Toprock", "Go-downs", "Footwork", "Power", "Freezes")

        /** The rating system was removed; its tables go with it. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS move_ratings")
                db.execSQL("DROP TABLE IF EXISTS set_ratings")
            }
        }

        /**
         * Routines became practices, and sessions now hold sets, reps and rounds. A video can now
         * be in many practices, so its one routine link moves into a join table.
         *
         * Tables are rebuilt rather than renamed because how SQLite rewrites foreign keys on a
         * rename depends on the Android version. Every table that points at another is dropped
         * before it, so this behaves the same whether foreign keys are being enforced or not.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE `practices` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL,
                    `notes` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `rounds` INTEGER NOT NULL,
                    `activeSec` INTEGER NOT NULL, `restSec` INTEGER NOT NULL)
                    """
                )
                db.execSQL(
                    """
                    INSERT INTO `practices` (id, name, notes, createdAt, rounds, activeSec, restSec)
                    SELECT id, name, notes, createdAt, 5, 60, 30 FROM routines
                    """
                )

                db.execSQL(
                    """
                    CREATE TABLE `practice_drills` (`practiceId` INTEGER NOT NULL, `position` INTEGER NOT NULL,
                    `type` TEXT NOT NULL, `moveId` INTEGER, `setId` INTEGER, `label` TEXT NOT NULL,
                    `targetSets` INTEGER NOT NULL, `targetReps` INTEGER, PRIMARY KEY(`practiceId`, `position`),
                    FOREIGN KEY(`practiceId`) REFERENCES `practices`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE ,
                    FOREIGN KEY(`moveId`) REFERENCES `moves`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE ,
                    FOREIGN KEY(`setId`) REFERENCES `sets`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )
                    """
                )
                db.execSQL(
                    """
                    INSERT INTO `practice_drills` (practiceId, position, type, moveId, setId, label, targetSets, targetReps)
                    SELECT routineId, position, type, moveId, setId, label, 1, reps FROM routine_drills
                    WHERE type != 'ROUND'
                    """
                )
                db.execSQL("DROP TABLE routine_drills")
                db.execSQL("CREATE INDEX `index_practice_drills_moveId` ON `practice_drills` (`moveId`)")
                db.execSQL("CREATE INDEX `index_practice_drills_setId` ON `practice_drills` (`setId`)")

                db.execSQL(
                    """
                    CREATE TABLE `practice_sessions_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `practiceId` INTEGER, `name` TEXT NOT NULL, `startedAt` INTEGER NOT NULL,
                    `lastActiveAt` INTEGER NOT NULL, `endedAt` INTEGER, `notes` TEXT NOT NULL,
                    `roundsCompleted` INTEGER NOT NULL, `rounds` INTEGER NOT NULL, `activeSec` INTEGER NOT NULL,
                    `restSec` INTEGER NOT NULL,
                    FOREIGN KEY(`practiceId`) REFERENCES `practices`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )
                    """
                )
                db.execSQL(
                    """
                    INSERT INTO `practice_sessions_new` (id, practiceId, name, startedAt, lastActiveAt, endedAt, notes,
                        roundsCompleted, rounds, activeSec, restSec)
                    SELECT s.id, s.routineId, COALESCE(r.name, 'Practice'), s.startedAt, COALESCE(s.endedAt, s.startedAt),
                        s.endedAt, s.notes, 0, 5, 60, 30
                    FROM practice_sessions s LEFT JOIN routines r ON r.id = s.routineId
                    """
                )
                db.execSQL("DROP TABLE practice_sessions")
                db.execSQL("ALTER TABLE `practice_sessions_new` RENAME TO `practice_sessions`")
                db.execSQL("CREATE INDEX `index_practice_sessions_practiceId` ON `practice_sessions` (`practiceId`)")

                db.execSQL(
                    """
                    CREATE TEMP TABLE routine_video_links AS
                    SELECT routineId AS practiceId, id AS videoId, COALESCE(linkedAt, addedAt) AS linkedAt
                    FROM videos WHERE routineId IS NOT NULL
                    """
                )
                db.execSQL(
                    """
                    CREATE TABLE `videos_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uri` TEXT NOT NULL,
                    `displayName` TEXT NOT NULL, `sizeBytes` INTEGER NOT NULL, `durationMs` INTEGER, `mimeType` TEXT,
                    `fingerprint` TEXT NOT NULL, `addedAt` INTEGER NOT NULL, `moveId` INTEGER, `setId` INTEGER,
                    `linkedAt` INTEGER,
                    FOREIGN KEY(`moveId`) REFERENCES `moves`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL ,
                    FOREIGN KEY(`setId`) REFERENCES `sets`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )
                    """
                )
                // A video that only had a routine no longer has a move-or-set order to keep.
                db.execSQL(
                    """
                    INSERT INTO `videos_new` (id, uri, displayName, sizeBytes, durationMs, mimeType, fingerprint,
                        addedAt, moveId, setId, linkedAt)
                    SELECT id, uri, displayName, sizeBytes, durationMs, mimeType, fingerprint, addedAt, moveId, setId,
                        CASE WHEN moveId IS NULL AND setId IS NULL THEN NULL ELSE linkedAt END
                    FROM videos
                    """
                )
                db.execSQL("DROP TABLE videos")
                db.execSQL("ALTER TABLE `videos_new` RENAME TO `videos`")
                db.execSQL("CREATE UNIQUE INDEX `index_videos_uri` ON `videos` (`uri`)")
                db.execSQL("CREATE INDEX `index_videos_fingerprint` ON `videos` (`fingerprint`)")
                db.execSQL("CREATE INDEX `index_videos_moveId` ON `videos` (`moveId`)")
                db.execSQL("CREATE INDEX `index_videos_setId` ON `videos` (`setId`)")

                db.execSQL("DROP TABLE routines")

                db.execSQL(
                    """
                    CREATE TABLE `practice_videos` (`practiceId` INTEGER NOT NULL, `videoId` INTEGER NOT NULL,
                    `linkedAt` INTEGER NOT NULL, PRIMARY KEY(`practiceId`, `videoId`),
                    FOREIGN KEY(`practiceId`) REFERENCES `practices`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE ,
                    FOREIGN KEY(`videoId`) REFERENCES `videos`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )
                    """
                )
                db.execSQL("CREATE INDEX `index_practice_videos_videoId` ON `practice_videos` (`videoId`)")
                db.execSQL("INSERT INTO `practice_videos` SELECT practiceId, videoId, linkedAt FROM routine_video_links")
                db.execSQL("DROP TABLE routine_video_links")

                db.execSQL(
                    """
                    CREATE TABLE `session_videos` (`sessionId` INTEGER NOT NULL, `videoId` INTEGER NOT NULL,
                    `linkedAt` INTEGER NOT NULL, PRIMARY KEY(`sessionId`, `videoId`),
                    FOREIGN KEY(`sessionId`) REFERENCES `practice_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE ,
                    FOREIGN KEY(`videoId`) REFERENCES `videos`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )
                    """
                )
                db.execSQL("CREATE INDEX `index_session_videos_videoId` ON `session_videos` (`videoId`)")

                db.execSQL(
                    """
                    CREATE TABLE `session_drills` (`sessionId` INTEGER NOT NULL, `position` INTEGER NOT NULL,
                    `type` TEXT NOT NULL, `moveId` INTEGER, `setId` INTEGER, `title` TEXT NOT NULL,
                    `targetSets` INTEGER NOT NULL, `targetReps` INTEGER, `currentReps` INTEGER NOT NULL,
                    PRIMARY KEY(`sessionId`, `position`),
                    FOREIGN KEY(`sessionId`) REFERENCES `practice_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE ,
                    FOREIGN KEY(`moveId`) REFERENCES `moves`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL ,
                    FOREIGN KEY(`setId`) REFERENCES `sets`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )
                    """
                )
                db.execSQL("CREATE INDEX `index_session_drills_moveId` ON `session_drills` (`moveId`)")
                db.execSQL("CREATE INDEX `index_session_drills_setId` ON `session_drills` (`setId`)")

                db.execSQL(
                    """
                    CREATE TABLE `session_sets` (`sessionId` INTEGER NOT NULL, `drillPosition` INTEGER NOT NULL,
                    `setNumber` INTEGER NOT NULL, `reps` INTEGER NOT NULL,
                    PRIMARY KEY(`sessionId`, `drillPosition`, `setNumber`),
                    FOREIGN KEY(`sessionId`, `drillPosition`) REFERENCES `session_drills`(`sessionId`, `position`)
                    ON UPDATE NO ACTION ON DELETE CASCADE )
                    """
                )
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "breaking.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_3_4)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        DEFAULT_CATEGORIES.forEachIndexed { index, name ->
                            db.execSQL(
                                "INSERT INTO categories (name, sortOrder) VALUES (?, ?)",
                                arrayOf<Any>(name, index),
                            )
                        }
                    }
                })
                .build()
    }
}
