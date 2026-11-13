package com.example.myapplication.ui.videos

import android.net.Uri
import androidx.camera.video.VideoRecordEvent.Finalize
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication.data.AppDatabase
import com.example.myapplication.data.VideoOwner
import com.example.myapplication.data.VideoRepository
import com.example.myapplication.data.model.Category
import com.example.myapplication.data.model.Move
import java.io.File
import java.util.Random
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** What happens to a recording once the camera is done with it. */
@RunWith(AndroidJUnit4::class)
class RecordViewModelTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: AppDatabase
    private lateinit var dir: File

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        dir = File(context.cacheDir, "record-vm-test").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    @After
    fun tearDown() {
        db.close()
        dir.deleteRecursively()
    }

    private fun viewModel(owner: VideoOwner?) =
        RecordViewModel(context, VideoRepository(context, db.videoDao()), owner)

    private fun clip(name: String = "clip.mp4", seed: Long = 1): Uri =
        Uri.fromFile(File(dir, name).apply { writeBytes(ByteArray(4096).also { Random(seed).nextBytes(it) }) })

    private suspend fun RecordViewModel.awaitSaved() =
        withTimeout(5_000) { uiState.first { it.saved != null } }.saved!!

    @Test
    fun aFinishedClipGoesIntoTheLibrary() = runBlocking {
        val vm = viewModel(owner = null)
        val uri = clip()

        vm.onRecorded(uri, Finalize.ERROR_NONE, durationMs = 4_000)

        val saved = vm.awaitSaved()
        assertEquals(uri.toString(), saved.uri)
        assertEquals(4_000L, saved.durationMs)
        assertFalse(vm.uiState.value.saving)
        assertEquals(listOf(uri.toString()), db.videoDao().observeAll().first().map { it.uri })
    }

    @Test
    fun aClipRecordedFromAMoveIsLinkedToIt() = runBlocking {
        val category = db.libraryDao().insertCategory(Category(name = "Power"))
        val move = db.libraryDao().insertMove(Move(name = "Windmill", categoryId = category))
        val vm = viewModel(VideoOwner.Move(move))

        vm.onRecorded(clip(), Finalize.ERROR_NONE, durationMs = 2_000)
        vm.awaitSaved()

        val onMove = db.videoDao().observeLinked(moveId = move, setId = null).first()
        assertEquals(1, onMove.size)
    }

    @Test
    fun aClipCutShortByLeavingTheAppIsStillKept() = runBlocking {
        val vm = viewModel(owner = null)

        vm.onRecorded(clip(), Finalize.ERROR_SOURCE_INACTIVE, durationMs = 1_500)

        assertNotNull(vm.awaitSaved())
        assertEquals(1, db.videoDao().observeAll().first().size)
    }

    @Test
    fun aClipWithNothingUsableInItIsLeftOutAndExplained() = runBlocking {
        val vm = viewModel(owner = null)

        vm.onRecorded(clip(), Finalize.ERROR_NO_VALID_DATA, durationMs = 0)

        assertNull(vm.uiState.value.saved)
        assertFalse(vm.uiState.value.saving)
        assertNotNull(vm.uiState.value.message)
        assertTrue(db.videoDao().observeAll().first().isEmpty())
    }

    @Test
    fun aClipThatStoppedBeforeAnythingWasRecordedIsLeftOut() = runBlocking {
        val vm = viewModel(owner = null)

        // Storage was already full when recording began.
        vm.onRecorded(clip(), Finalize.ERROR_INSUFFICIENT_STORAGE, durationMs = 0)

        assertNull(vm.uiState.value.saved)
        assertNotNull(vm.uiState.value.message)
        assertTrue(db.videoDao().observeAll().first().isEmpty())
    }

    @Test
    fun noFileAtAllIsLeftOutAndExplained() = runBlocking {
        val vm = viewModel(owner = null)

        vm.onRecorded(null, Finalize.ERROR_UNKNOWN, durationMs = 0)

        assertNull(vm.uiState.value.saved)
        assertNotNull(vm.uiState.value.message)
        assertTrue(db.videoDao().observeAll().first().isEmpty())
    }

    @Test
    fun recordingAnotherClearsTheSavedCard() = runBlocking {
        val vm = viewModel(owner = null)
        vm.onRecorded(clip(), Finalize.ERROR_NONE, durationMs = 1_000)
        vm.awaitSaved()

        vm.recordAnother()

        assertNull(vm.uiState.value.saved)
    }
}
