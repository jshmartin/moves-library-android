package com.example.myapplication.data

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.net.toUri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication.data.VideoRepository.RelinkResult
import com.example.myapplication.data.model.Category
import com.example.myapplication.data.model.Move
import java.io.File
import java.util.Random
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Moves and deletes real files, so these exercise the same checks the app does. */
@RunWith(AndroidJUnit4::class)
class VideoRecoveryTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val resolver = context.contentResolver

    private lateinit var db: AppDatabase
    private lateinit var repository: VideoRepository
    private lateinit var dir: File
    private val deviceVideos = mutableListOf<Uri>()

    private val videos get() = db.videoDao()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repository = VideoRepository(context, db.videoDao())
        dir = File(context.cacheDir, "video-recovery-test").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    @After
    fun tearDown() {
        db.close()
        dir.deleteRecursively()
        // Tests delete some of these themselves, and deleting a gone entry throws.
        deviceVideos.forEach { runCatching { resolver.delete(it, null, null) } }
    }

    private fun content(seed: Long) = ByteArray(4096).also { Random(seed).nextBytes(it) }

    private fun write(name: String, bytes: ByteArray): File = File(dir, name).apply { writeBytes(bytes) }

    private suspend fun newMove(): Long {
        val category = db.libraryDao().insertCategory(Category(name = "Power"))
        return db.libraryDao().insertMove(Move(name = "Windmill", categoryId = category))
    }

    /** Adds [file] to the library, filed under a new move. */
    private suspend fun addOnMove(file: File): Video {
        repository.add(listOf(Uri.fromFile(file)), VideoOwner.Move(newMove()))
        return videos.findByUri(Uri.fromFile(file).toString())!!
    }

    @Test
    fun aDeletedFileIsReportedMissingAndItsEntryIsKept() = runBlocking {
        val file = write("clip.mp4", content(1))
        val video = addOnMove(file)

        file.delete()
        val result = repository.checkAvailability(listOf(video))

        assertEquals(setOf(video.id), result.missing)
        assertEquals(0, result.relinked)
        assertEquals(video, videos.getById(video.id))
    }

    @Test
    fun aFileThatsStillThereIsNotMissing() = runBlocking {
        val video = addOnMove(write("clip.mp4", content(1)))

        assertEquals(emptySet<Long>(), repository.checkAvailability(listOf(video)).missing)
    }

    @Test
    fun addingAMovedFileAgainRelinksItsEntryAndKeepsItsLinks() = runBlocking {
        val file = write("clip.mp4", content(1))
        val video = addOnMove(file)
        val moved = File(dir, "somewhere-else.mp4")
        file.renameTo(moved)

        val result = repository.add(listOf(Uri.fromFile(moved)))

        assertEquals(1, result.relinked)
        assertEquals(0, result.added)
        val relinked = videos.getById(video.id)!!
        assertEquals(Uri.fromFile(moved).toString(), relinked.uri)
        assertEquals(video.moveId, relinked.moveId)
    }

    @Test
    fun locatingAMovedFileRelinksItAndKeepsItsLinks() = runBlocking {
        val file = write("clip.mp4", content(1))
        val video = addOnMove(file)
        val moved = File(dir, "somewhere-else.mp4")
        file.renameTo(moved)

        assertEquals(RelinkResult.Relinked, repository.relink(video, Uri.fromFile(moved)))

        val relinked = videos.getById(video.id)!!
        assertEquals(Uri.fromFile(moved).toString(), relinked.uri)
        assertEquals(video.moveId, relinked.moveId)
        assertEquals(emptySet<Long>(), repository.checkAvailability(listOf(relinked)).missing)
    }

    @Test
    fun locatingADifferentFileNeedsConfirmation() = runBlocking {
        val file = write("clip.mp4", content(1))
        val video = addOnMove(file)
        file.delete()
        val other = Uri.fromFile(write("other.mp4", content(2)))

        assertEquals(RelinkResult.Mismatch, repository.relink(video, other))
        assertEquals(video, videos.getById(video.id))

        assertEquals(RelinkResult.Relinked, repository.relink(video, other, force = true))
        assertEquals(other.toString(), videos.getById(video.id)!!.uri)
    }

    @Test
    fun locatingAFileThatCameBackToTheSamePlaceWorks() = runBlocking {
        val bytes = content(1)
        val file = write("clip.mp4", bytes)
        val video = addOnMove(file)
        file.delete()
        write("clip.mp4", bytes)

        assertEquals(RelinkResult.Relinked, repository.relink(video, Uri.fromFile(file)))
        assertEquals(video.moveId, videos.getById(video.id)!!.moveId)
    }

    @Test
    fun locatingAFileThatsAnotherEntryIsRefused() = runBlocking {
        val first = write("first.mp4", content(1))
        val video = addOnMove(first)
        first.delete()
        val second = Uri.fromFile(write("second.mp4", content(2)))
        repository.add(listOf(second))

        assertEquals(RelinkResult.AlreadyInLibrary, repository.relink(video, second, force = true))
    }

    @Test
    fun addingAFileAnotherEntryAlreadyPointsAtDoesNotCrash() = runBlocking {
        val first = write("first.mp4", content(1))
        val video = addOnMove(first)
        first.delete()
        val secondFile = write("second.mp4", content(2))
        repository.add(listOf(Uri.fromFile(secondFile)))
        val second = videos.findByUri(Uri.fromFile(secondFile).toString())!!

        // The second entry's file is overwritten with what used to be the first video.
        secondFile.writeBytes(content(1))
        val result = repository.add(listOf(Uri.fromFile(secondFile)))

        assertEquals(1, result.duplicates)
        assertEquals(Uri.fromFile(secondFile).toString(), videos.getById(second.id)!!.uri)
        assertEquals(video.uri, videos.getById(video.id)!!.uri)
    }

    private fun insertDeviceVideo(name: String, bytes: ByteArray): Uri {
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val pending = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/CypherTest")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, pending)!!
        deviceVideos += uri
        resolver.openOutputStream(uri)!!.use { it.write(bytes) }
        resolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
        return uri
    }

    private fun grantMediaPermission() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.READ_MEDIA_VIDEO)
    }

    @Test
    fun aMovedFileRelinksByItselfWhenTheAppCanReadDeviceMedia() = runBlocking {
        grantMediaPermission()
        val bytes = content(System.nanoTime())
        val original = insertDeviceVideo("clip-${System.nanoTime()}.mp4", bytes)
        val move = newMove()
        repository.add(listOf(original), VideoOwner.Move(move))
        val video = videos.findByUri(original.toString())!!

        // A file manager's move shows up as the old media entry going away and a new one appearing.
        resolver.delete(original, null, null)
        val moved = insertDeviceVideo("moved-${System.nanoTime()}.mp4", bytes)
        val result = repository.checkAvailability(listOf(video))

        assertEquals(emptySet<Long>(), result.missing)
        assertEquals(1, result.relinked)
        val relinked = videos.getById(video.id)!!
        assertEquals(ContentUris.parseId(moved), ContentUris.parseId(relinked.uri.toUri()))
        assertEquals(move, relinked.moveId)
    }

    @Test
    fun aDeletedFileStaysMissingEvenWhenTheAppCanReadDeviceMedia() = runBlocking {
        grantMediaPermission()
        val original = insertDeviceVideo("clip-${System.nanoTime()}.mp4", content(System.nanoTime()))
        repository.add(listOf(original))
        val video = videos.findByUri(original.toString())!!

        resolver.delete(original, null, null)
        val result = repository.checkAvailability(listOf(video))

        assertEquals(setOf(video.id), result.missing)
        assertEquals(0, result.relinked)
        assertEquals(video, videos.getById(video.id))
    }
}
