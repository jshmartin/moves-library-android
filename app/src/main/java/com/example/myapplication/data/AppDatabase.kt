package com.example.myapplication.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.myapplication.data.dao.ChainDao
import com.example.myapplication.data.dao.LibraryDao
import com.example.myapplication.data.dao.PracticeDao
import com.example.myapplication.data.model.Category
import com.example.myapplication.data.model.DanceSet
import com.example.myapplication.data.model.Move
import com.example.myapplication.data.model.MoveRating
import com.example.myapplication.data.model.PracticeSession
import com.example.myapplication.data.model.Round
import com.example.myapplication.data.model.RoundItem
import com.example.myapplication.data.model.Routine
import com.example.myapplication.data.model.RoutineDrill
import com.example.myapplication.data.model.SetMove
import com.example.myapplication.data.model.SetRating
import com.example.myapplication.data.model.VideoClip

@Database(
    entities = [
        Category::class,
        Move::class,
        MoveRating::class,
        VideoClip::class,
        DanceSet::class,
        SetMove::class,
        SetRating::class,
        Round::class,
        RoundItem::class,
        Routine::class,
        RoutineDrill::class,
        PracticeSession::class,
    ],
    version = 1,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao
    abstract fun chainDao(): ChainDao
    abstract fun practiceDao(): PracticeDao

    companion object {
        /** Categories created on first launch. The user can rename, reorder or delete them. */
        val DEFAULT_CATEGORIES = listOf("Toprock", "Go-downs", "Footwork", "Power", "Freezes")

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "breaking.db")
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
