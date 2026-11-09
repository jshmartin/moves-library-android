package com.example.myapplication.data

import android.content.Context
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
import com.example.myapplication.data.model.PracticeSession
import com.example.myapplication.data.model.Round
import com.example.myapplication.data.model.RoundItem
import com.example.myapplication.data.model.Routine
import com.example.myapplication.data.model.RoutineDrill
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
        Routine::class,
        RoutineDrill::class,
        PracticeSession::class,
        Video::class,
    ],
    version = 2,
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

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "breaking.db")
                .addMigrations(MIGRATION_1_2)
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
