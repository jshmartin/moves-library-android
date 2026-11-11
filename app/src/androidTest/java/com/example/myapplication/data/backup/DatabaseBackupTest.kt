package com.example.myapplication.data.backup

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication.data.AppDatabase
import com.example.myapplication.data.Video
import com.example.myapplication.data.VideoOwner
import com.example.myapplication.data.VideoRepository
import com.example.myapplication.data.model.Category
import com.example.myapplication.data.model.DanceSet
import com.example.myapplication.data.model.Move
import com.example.myapplication.data.model.Practice
import java.io.File
import java.util.Random
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Backs up and restores real database files, the way the app does. */
@RunWith(AndroidJUnit4::class)
class DatabaseBackupTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dir = File(context.cacheDir, "database-backup-test")
    private val opened = mutableListOf<AppDatabase>()
    private val names = mutableListOf<String>()

    @Before
    fun setUp() {
        dir.deleteRecursively()
        dir.mkdirs()
    }

    @After
    fun tearDown() {
        opened.forEach { it.close() }
        names.forEach { context.deleteDatabase(it) }
        dir.deleteRecursively()
    }

    /** A database file like the app's own, with the starter moves a new install gets. */
    private fun newAppDatabase(): AppDatabase {
        val name = "backup-test-${System.nanoTime()}.db"
        names += name
        return AppDatabase.build(context, name).also { opened += it }
    }

    private fun appVersion(db: AppDatabase) = db.openHelper.readableDatabase.version

    private fun videoFile(name: String, seed: Long): File =
        File(dir, name).apply { writeBytes(ByteArray(4096).also { Random(seed).nextBytes(it) }) }

    private class Seeded(val moveId: Long, val practiceId: Long, val kept: Video, val gone: Video, val changed: Video)

    /** Moves, a set, a practice, a session and videos linked to them, some of whose files then go away. */
    private suspend fun seed(db: AppDatabase): Seeded {
        val library = db.libraryDao()
        val category = library.insertCategory(Category(name = "Test category"))
        val windmill = library.insertMove(Move(name = "Test windmill", categoryId = category))
        val flare = library.insertMove(Move(name = "Test flare", categoryId = category))
        val set = db.chainDao().saveSet(DanceSet(name = "Test combo"), listOf(windmill, flare, windmill))
        val practice = db.practiceDao().insertPractice(Practice(name = "Test practice"))
        val session = db.practiceDao().startQuickSession("Test session")

        val repository = VideoRepository(context, db.videoDao())
        val files = listOf(videoFile("kept.mp4", 1), videoFile("gone.mp4", 2), videoFile("changed.mp4", 3))
        files.forEach { repository.add(listOf(Uri.fromFile(it))) }
        val (kept, gone, changed) = files.map { db.videoDao().findByUri(Uri.fromFile(it).toString())!!.id }
        repository.link(kept, VideoOwner.Move(windmill))
        repository.link(kept, VideoOwner.Practice(practice))
        repository.link(gone, VideoOwner.Set(set))
        repository.link(gone, VideoOwner.Session(session))
        repository.link(changed, VideoOwner.Move(flare))

        files[1].delete()
        files[2].writeBytes(ByteArray(4096).also { Random(99).nextBytes(it) })
        val videos = db.videoDao()
        return Seeded(windmill, practice, videos.getById(kept)!!, videos.getById(gone)!!, videos.getById(changed)!!)
    }

    /** Every row of every table, sorted, for comparing two databases. */
    private fun contents(db: AppDatabase): Map<String, List<String>> {
        val sql = db.openHelper.readableDatabase
        return DatabaseBackup.tableNames(db).associateWith { table ->
            sql.query("SELECT * FROM `$table`").use { row ->
                buildList {
                    while (row.moveToNext()) {
                        add((0 until row.columnCount).joinToString("|") { "${row.getColumnName(it)}=${value(row, it)}" })
                    }
                }.sorted()
            }
        }
    }

    private fun value(row: Cursor, i: Int): String = when (row.getType(i)) {
        Cursor.FIELD_TYPE_NULL -> "null"
        Cursor.FIELD_TYPE_BLOB -> row.getBlob(i).contentToString()
        else -> row.getString(i)
    }

    private fun backUp(db: AppDatabase): File = File(dir, "backup-${System.nanoTime()}.db").also {
        DatabaseBackup.write(context, db, it)
    }

    @Test
    fun restoringABackupReplacesEverythingWithWhatWasBackedUp() = runBlocking {
        val source = newAppDatabase()
        val seeded = seed(source)
        val file = backUp(source)
        val target = newAppDatabase()
        target.libraryDao().insertCategory(Category(name = "Only on the new phone"))

        val summary = DatabaseBackup.inspect(context, file, appVersion(target))
        DatabaseBackup.restore(context, file, target)

        val expected = contents(source).toMutableMap()
        // Only the video whose file is still there, unchanged, keeps its address.
        expected["videos"] = contents(source).getValue("videos").map { row ->
            listOf(seeded.gone, seeded.changed).fold(row) { r, video ->
                r.replace("uri=${video.uri}|", "uri=${Video.missingUri(video.id)}|")
            }
        }.sorted()
        assertEquals(expected, contents(target))
        val moves = source.openHelper.readableDatabase.query("SELECT COUNT(*) FROM moves").use { it.moveToFirst(); it.getInt(0) }
        assertEquals(BackupSummary(moves = moves, sets = 1, practices = 1, sessions = 1, videos = 3), summary)
    }

    @Test
    fun videosThatCantBeConfirmedComeBackMissingWithTheirLinks() = runBlocking {
        val source = newAppDatabase()
        val seeded = seed(source)
        val file = backUp(source)
        val target = newAppDatabase()

        DatabaseBackup.restore(context, file, target)

        val videos = target.videoDao()
        assertEquals(seeded.kept.uri, videos.getById(seeded.kept.id)!!.uri)
        val gone = videos.getById(seeded.gone.id)!!
        assertEquals(Video.missingUri(gone.id), gone.uri)
        assertEquals(seeded.gone.setId, gone.setId)
        assertEquals(seeded.gone.fingerprint, gone.fingerprint)
        assertEquals(Video.missingUri(seeded.changed.id), videos.getById(seeded.changed.id)!!.uri)
        assertEquals(listOf(seeded.kept.id), videos.observeForPractice(seeded.practiceId).first().map { it.id })

        val result = VideoRepository(context, videos).checkAvailability(videos.observeAll().first())
        assertEquals(setOf(seeded.gone.id, seeded.changed.id), result.missing)
    }

    @Test
    fun aRestoredVideoRelinksWhenItsFileIsAddedAgain() = runBlocking {
        val source = newAppDatabase()
        val seeded = seed(source)
        val file = backUp(source)
        val target = newAppDatabase()
        DatabaseBackup.restore(context, file, target)

        // On the new phone the same video turns up somewhere else.
        val copy = videoFile("arrived-on-new-phone.mp4", 2)
        val added = VideoRepository(context, target.videoDao()).add(listOf(Uri.fromFile(copy)))

        assertEquals(1, added.relinked)
        val relinked = target.videoDao().getById(seeded.gone.id)!!
        assertEquals(Uri.fromFile(copy).toString(), relinked.uri)
        assertEquals(seeded.gone.setId, relinked.setId)
    }

    @Test
    fun aFileThatIsntABackupIsRefused() {
        val version = appVersion(newAppDatabase())
        val random = File(dir, "random.db").apply { writeBytes(ByteArray(8192).also { Random(5).nextBytes(it) }) }
        val empty = File(dir, "empty.db").apply { writeBytes(ByteArray(0)) }
        val otherApp = File(dir, "other.db")
        SQLiteDatabase.openOrCreateDatabase(otherApp, null).use { it.execSQL("CREATE TABLE notes (text TEXT)") }

        listOf(random, empty, otherApp).forEach { assertNull(it.name, DatabaseBackup.inspect(context, it, version)) }
    }

    @Test
    fun aBackupFromANewerVersionOfTheAppIsRefused() {
        val db = newAppDatabase()
        val file = backUp(db)
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { it.version = appVersion(db) + 1 }

        assertNull(DatabaseBackup.inspect(context, file, appVersion(db)))
    }

    @Test
    fun aBackupFromAnOlderVersionIsUpgradedAndRestored() = runBlocking {
        val name = "backup-test-v4-${System.nanoTime()}.db"
        names += name
        helper.createDatabase(name, 4).use { db ->
            db.execSQL("INSERT INTO categories (id, name, sortOrder) VALUES (1, 'Old category', 0)")
            db.execSQL(
                """
                INSERT INTO moves (id, name, categoryId, parentMoveId, status, notes, createdAt)
                VALUES (2, 'Old windmill', 1, NULL, 'LEARNING', '', 5)
                """
            )
        }
        val file = File(dir, "old.db")
        context.getDatabasePath(name).copyTo(file)
        val target = newAppDatabase()

        val summary = DatabaseBackup.inspect(context, file, appVersion(target))
        assertNotNull(summary)
        DatabaseBackup.restore(context, file, target)

        assertEquals("Old windmill", target.libraryDao().getMove(2)!!.name)
        assertEquals(
            listOf("Old category"),
            target.openHelper.readableDatabase.query("SELECT name FROM categories").use { row ->
                buildList { while (row.moveToNext()) add(row.getString(0)) }
            },
        )
    }

    @Test
    fun automaticBackupsWatchEveryTable() {
        assertEquals(DatabaseBackup.tableNames(newAppDatabase()), BackupManager.WATCHED_TABLES.toSet())
    }

    @Test
    fun aWrittenBackupIsOneCompleteFile() {
        val file = backUp(newAppDatabase())

        assertTrue(file.length() > 0)
        assertTrue(!File(file.path + "-wal").exists())
        assertNotNull(DatabaseBackup.inspect(context, file, appVersion(newAppDatabase())))
    }
}
