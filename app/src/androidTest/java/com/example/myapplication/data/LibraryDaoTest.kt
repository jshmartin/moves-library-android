package com.example.myapplication.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication.data.model.Category
import com.example.myapplication.data.model.Move
import com.example.myapplication.data.model.MoveRating
import com.example.myapplication.data.model.MoveStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryDaoTest {
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
        ).build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun categoryScoreAveragesLatestRatingsIncludingLearningMoves() = runBlocking {
        val dao = db.libraryDao()
        val footwork = dao.insertCategory(Category(name = "Footwork"))
        val power = dao.insertCategory(Category(name = "Power", sortOrder = 1))

        val sixStep = dao.insertMove(Move(name = "6-step", categoryId = footwork, status = MoveStatus.BATTLE_READY))
        val ccs = dao.insertMove(Move(name = "CC's", categoryId = footwork, status = MoveStatus.LEARNING))
        dao.insertMove(Move(name = "Pretzel", categoryId = footwork))

        dao.insertRating(MoveRating(moveId = sixStep, score = 2, ratedAt = 1))
        dao.insertRating(MoveRating(moveId = sixStep, score = 5, ratedAt = 2))
        dao.insertRating(MoveRating(moveId = ccs, score = 2, ratedAt = 1))

        val scores = dao.observeCategoryScores().first()

        val footworkScore = scores.single { it.categoryId == footwork }
        assertEquals(3.5, footworkScore.averageScore!!, 0.001)
        assertEquals(2, footworkScore.ratedMoves)
        assertEquals(3, footworkScore.totalMoves)

        val powerScore = scores.single { it.categoryId == power }
        assertNull(powerScore.averageScore)
        assertEquals(0, powerScore.totalMoves)
    }
}
