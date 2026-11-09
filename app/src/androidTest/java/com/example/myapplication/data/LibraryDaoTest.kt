package com.example.myapplication.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication.data.model.Category
import com.example.myapplication.data.model.Move
import com.example.myapplication.data.model.MoveStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
    fun categorySummariesCountMovesOfEveryStatus() = runBlocking {
        val dao = db.libraryDao()
        val footwork = dao.insertCategory(Category(name = "Footwork"))
        val power = dao.insertCategory(Category(name = "Power", sortOrder = 1))

        dao.insertMove(Move(name = "6-step", categoryId = footwork, status = MoveStatus.BATTLE_READY))
        dao.insertMove(Move(name = "CC's", categoryId = footwork, status = MoveStatus.LEARNING))
        dao.insertMove(Move(name = "Pretzel", categoryId = footwork))

        val summaries = dao.observeCategorySummaries().first()

        assertEquals(3, summaries.single { it.categoryId == footwork }.totalMoves)
        assertEquals(0, summaries.single { it.categoryId == power }.totalMoves)
    }

    @Test
    fun movesInCategorySortIgnoringCase() = runBlocking {
        val dao = db.libraryDao()
        val freezes = dao.insertCategory(Category(name = "Freezes"))
        dao.insertMove(Move(name = "Chair freeze", categoryId = freezes))
        dao.insertMove(Move(name = "baby freeze", categoryId = freezes))

        val moves = dao.observeMovesInCategory(freezes).first()

        assertEquals(listOf("baby freeze", "Chair freeze"), moves.map { it.name })
    }
}
