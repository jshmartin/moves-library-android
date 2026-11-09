package com.example.myapplication.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication.data.model.Category
import com.example.myapplication.data.model.DanceSet
import com.example.myapplication.data.model.Move
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VideoLinkTest {
    private lateinit var db: AppDatabase
    private val videos get() = db.videoDao()
    private val library get() = db.libraryDao()
    private val chains get() = db.chainDao()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
        ).build()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun newMove(name: String): Long {
        val category = library.insertCategory(Category(name = "Cat $name"))
        return library.insertMove(Move(name = name, categoryId = category))
    }

    private suspend fun newVideo(name: String): Long = videos.insert(
        Video(
            uri = "content://$name",
            displayName = name,
            sizeBytes = 1,
            durationMs = null,
            mimeType = null,
            fingerprint = name,
            addedAt = 0,
        )
    )

    private suspend fun linkToMove(videoId: Long, moveId: Long, at: Long) =
        videos.setOwner(videoId, moveId = moveId, setId = null, linkedAt = at)

    private suspend fun linked(moveId: Long) = videos.observeLinked(moveId, null).first().map { it.displayName }

    @Test
    fun linkedVideosListNewestFirstAndMakeTopReorders() = runBlocking {
        val move = newMove("Windmill")
        val first = newVideo("first")
        linkToMove(first, move, at = 1)
        linkToMove(newVideo("second"), move, at = 2)

        assertEquals(listOf("second", "first"), linked(move))

        linkToMove(first, move, at = 3)

        assertEquals(listOf("first", "second"), linked(move))
    }

    @Test
    fun linkingToSetTakesVideoOffItsMove() = runBlocking {
        val move = newMove("Windmill")
        val set = chains.saveSet(DanceSet(name = "Combo"), emptyList())
        val video = newVideo("clip")
        linkToMove(video, move, at = 1)

        videos.setOwner(video, moveId = null, setId = set, linkedAt = 2)

        assertEquals(emptyList<String>(), linked(move))
        assertEquals(listOf("clip"), videos.observeLinked(null, set).first().map { it.displayName })
    }

    @Test
    fun deletingOwnerKeepsVideoUnlinked() = runBlocking {
        val move = newMove("Windmill")
        val video = newVideo("clip")
        linkToMove(video, move, at = 1)

        library.deleteMove(library.getMove(move)!!)

        assertNull(videos.getById(video)!!.moveId)
    }

    @Test
    fun videosOnASetsMovesListEachMovesTopVideoFirst() = runBlocking {
        val windmill = newMove("Windmill")
        val flare = newMove("Flare")
        val notInSet = newMove("Freeze")
        linkToMove(newVideo("old"), windmill, at = 1)
        linkToMove(newVideo("best"), windmill, at = 2)
        linkToMove(newVideo("flare"), flare, at = 3)
        linkToMove(newVideo("elsewhere"), notInSet, at = 4)
        val set = chains.saveSet(DanceSet(name = "Combo"), listOf(windmill, flare, windmill))

        val byMove = videos.observeForSetMoves(set).first().groupBy({ it.moveId }, { it.displayName })

        assertEquals(mapOf(flare to listOf("flare"), windmill to listOf("best", "old")), byMove)
    }

    @Test
    fun comboStepsUseEachMovesTopVideoInChainOrder() = runBlocking {
        val windmill = newMove("Windmill")
        val flare = newMove("Flare")
        val freeze = newMove("Freeze")
        linkToMove(newVideo("old"), windmill, at = 1)
        linkToMove(newVideo("best"), windmill, at = 2)
        linkToMove(newVideo("flare"), flare, at = 1)
        val set = chains.saveSet(DanceSet(name = "Combo"), listOf(flare, freeze, windmill, flare))

        val steps = chains.observeComboSteps(set).first()

        assertEquals(listOf("Flare", "Freeze", "Windmill", "Flare"), steps.map { it.moveName })
        assertEquals(
            listOf("content://flare", null, "content://best", "content://flare"),
            steps.map { it.videoUri },
        )
    }
}
