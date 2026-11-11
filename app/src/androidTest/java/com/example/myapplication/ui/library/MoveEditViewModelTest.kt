package com.example.myapplication.ui.library

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication.data.AppDatabase
import com.example.myapplication.data.Video
import com.example.myapplication.data.VideoRepository
import com.example.myapplication.data.model.Category
import com.example.myapplication.data.model.Move
import com.example.myapplication.ui.library.MoveEditViewModel.Companion.NO_ID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MoveEditViewModelTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: AppDatabase
    private val library get() = db.libraryDao()
    private val videoDao get() = db.videoDao()
    private var footwork = 0L
    private var power = 0L

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        footwork = library.insertCategory(Category(name = "Footwork", sortOrder = 0))
        power = library.insertCategory(Category(name = "Power", sortOrder = 1))
    }

    @After
    fun tearDown() = db.close()

    private suspend fun viewModel(moveId: Long = NO_ID, categoryId: Long = NO_ID): MoveEditViewModel {
        val handle = SavedStateHandle(
            mapOf(LibraryRoutes.ARG_MOVE_ID to moveId, LibraryRoutes.ARG_CATEGORY_ID to categoryId)
        )
        return MoveEditViewModel(handle, library, VideoRepository(context, videoDao)).also { vm ->
            waitFor { vm.loaded }
        }
    }

    private suspend fun waitFor(condition: () -> Boolean) = withTimeout(5_000) {
        while (!condition()) delay(10)
    }

    private suspend fun MoveEditViewModel.saveAndWait(): Pair<Long, Int> {
        val saved = CompletableDeferred<Pair<Long, Int>>()
        save { id, unreadable -> saved.complete(id to unreadable) }
        return withTimeout(5_000) { saved.await() }
    }

    private suspend fun newVideo(name: String): Video {
        val id = videoDao.insert(
            Video(
                uri = "content://test/$name",
                displayName = name,
                sizeBytes = 1,
                durationMs = null,
                mimeType = null,
                fingerprint = name,
                addedAt = 0,
            )
        )
        return videoDao.observeAll().first().single { it.id == id }
    }

    @Test
    fun newMoveFromLibraryWaitsForACategory() = runBlocking {
        val vm = viewModel()
        vm.name = "Flare"

        assertEquals(NO_ID, vm.categoryId)
        assertFalse(vm.canSave)

        vm.selectCategory(power)

        assertTrue(vm.canSave)
    }

    @Test
    fun newMoveFromACategoryStartsInIt() = runBlocking {
        val vm = viewModel(categoryId = footwork)

        assertEquals(footwork, vm.categoryId)
    }

    @Test
    fun savingNewMoveLinksPickedLibraryVideos() = runBlocking {
        val first = newVideo("first")
        val second = newVideo("second")
        newVideo("other")
        val vm = viewModel()
        vm.name = "Flare"
        vm.selectCategory(power)
        vm.addFromLibrary(first)
        vm.addFromLibrary(second)
        vm.addFromLibrary(first)

        assertEquals(listOf("first", "second"), vm.pendingVideos.map { it.name })
        assertEquals(
            listOf("other"),
            vm.libraryCandidates(videoDao.observeAllWithOwner().first()).map { it.video.displayName },
        )

        val (id, unreadable) = vm.saveAndWait()

        assertEquals(0, unreadable)
        val move = library.getMove(id)!!
        assertEquals("Flare", move.name)
        assertEquals(power, move.categoryId)
        assertEquals("", move.notes)
        assertEquals(
            setOf("first", "second"),
            videoDao.observeLinked(moveId = id, setId = null).first().map { it.displayName }.toSet(),
        )
    }

    @Test
    fun unreadableDevicePickStillSavesTheMove() = runBlocking {
        val vm = viewModel(categoryId = footwork)
        vm.name = "CC"
        vm.addFromDevice(listOf(Uri.parse("content://com.example.missing/clip.mp4")))
        waitFor { vm.pendingVideos.isNotEmpty() }

        assertEquals("clip.mp4", vm.pendingVideos.single().name)

        val (id, unreadable) = vm.saveAndWait()

        assertEquals(1, unreadable)
        assertEquals("CC", library.getMove(id)?.name)
        assertTrue(videoDao.observeLinked(moveId = id, setId = null).first().isEmpty())
    }

    @Test
    fun editingAMoveKeepsItsNotes() = runBlocking {
        val id = library.insertMove(Move(name = "6-step", categoryId = footwork, notes = "Six steps around."))
        val vm = viewModel(moveId = id)

        assertEquals(footwork, vm.categoryId)
        assertEquals("Six steps around.", vm.notes)

        vm.name = "Six step"
        vm.saveAndWait()

        val saved = library.getMove(id)!!
        assertEquals("Six step", saved.name)
        assertEquals("Six steps around.", saved.notes)
    }
}
