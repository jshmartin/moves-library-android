package com.example.myapplication.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication.data.model.MoveStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StarterMoveSeedTest {
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
        ).addCallback(AppDatabase.SEED_ON_CREATE).build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun newLibraryStartsWithEveryStarterMoveInItsCategory() = runBlocking {
        val summaries = db.libraryDao().observeCategorySummaries().first()

        assertEquals(AppDatabase.DEFAULT_CATEGORIES, summaries.map { it.categoryName })
        summaries.forEach { summary ->
            assertEquals(
                summary.categoryName,
                StarterMoves.ALL.count { it.category == summary.categoryName },
                summary.totalMoves,
            )
        }
        val moves = db.libraryDao().observeMoves().first()
        assertTrue(moves.all { it.status == MoveStatus.LEARNING && it.notes.isNotBlank() })
    }

    @Test
    fun variationsAreFiledUnderTheirBaseMove() = runBlocking {
        val moves = db.libraryDao().observeMoves().first().associateBy { it.name }
        val sixStep = moves.getValue("6-step")

        assertNull(sixStep.parentMoveId)
        assertEquals(sixStep.id, moves.getValue("3-step").parentMoveId)
        assertEquals(sixStep.id, moves.getValue("2-step").parentMoveId)
    }

    @Test
    fun seedingAgainAddsNothing() = runBlocking {
        val before = db.libraryDao().observeMoves().first().size

        StarterMoves.seed(db.openHelper.writableDatabase)

        assertEquals(before, db.libraryDao().observeMoves().first().size)
    }
}
