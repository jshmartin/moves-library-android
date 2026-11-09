package com.example.myapplication.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication.data.dao.SessionDrillView
import com.example.myapplication.data.model.Category
import com.example.myapplication.data.model.DanceSet
import com.example.myapplication.data.model.DrillType
import com.example.myapplication.data.model.Move
import com.example.myapplication.data.model.Practice
import com.example.myapplication.data.model.PracticeDrill
import com.example.myapplication.data.model.PracticeVideo
import com.example.myapplication.data.model.SessionDrill
import com.example.myapplication.data.model.SessionVideo
import com.example.myapplication.data.model.TimerSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PracticeDaoTest {
    private lateinit var db: AppDatabase
    private val practice get() = db.practiceDao()
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

    private fun moveDrill(moveId: Long, sets: Int = 2, reps: Int? = 10) = PracticeDrill(
        practiceId = 0,
        position = 0,
        type = DrillType.MOVE,
        moveId = moveId,
        targetSets = sets,
        targetReps = reps,
    )

    private suspend fun drillAt(sessionId: Long, position: Int = 0): SessionDrillView =
        practice.observeSessionDrills(sessionId).first().single { it.drill.position == position }

    private suspend fun startedSession(moveId: Long): Long {
        val practiceId = practice.savePractice(Practice(name = "Power day"), listOf(moveDrill(moveId)))
        return practice.startSession(practiceId)
    }

    @Test
    fun savingAPracticeKeepsDrillOrderAndTimer() = runBlocking {
        val windmill = newMove("Windmill")
        val flare = newMove("Flare")
        val id = practice.savePractice(
            Practice(name = "Power", timer = TimerSettings(6, 45, 15)),
            listOf(moveDrill(flare), moveDrill(windmill)),
        )

        assertEquals(TimerSettings(6, 45, 15), practice.getPractice(id)!!.timer)
        assertEquals(listOf("Flare", "Windmill"), practice.observeDrills(id).first().map { it.title })
    }

    @Test
    fun startingASessionCopiesThePlanAndItsTimer() = runBlocking {
        val move = newMove("Windmill")
        val practiceId = practice.savePractice(
            Practice(name = "Power", timer = TimerSettings(3, 30, 20)),
            listOf(moveDrill(move, sets = 4, reps = 8)),
        )

        val sessionId = practice.startSession(practiceId)

        val session = practice.getSession(sessionId)!!
        assertEquals("Power", session.name)
        assertEquals(TimerSettings(3, 30, 20), session.timer)
        assertNull(session.endedAt)
        val drill = drillAt(sessionId)
        assertEquals("Windmill", drill.displayTitle)
        assertEquals(4, drill.drill.targetSets)
        assertEquals(8, drill.drill.targetReps)
    }

    @Test
    fun countingRepsAndFinishingSetsTracksEachSet() = runBlocking {
        val sessionId = startedSession(newMove("Windmill"))
        repeat(3) { practice.addRep(sessionId, 0, now = 5) }
        assertEquals(3, drillAt(sessionId).drill.currentReps)

        practice.finishSet(sessionId, 0, now = 6)
        repeat(2) { practice.addRep(sessionId, 0, now = 7) }
        practice.finishSet(sessionId, 0, now = 8)

        val drill = drillAt(sessionId)
        assertEquals(0, drill.drill.currentReps)
        assertEquals(2, drill.setsDone)
        assertEquals(5, drill.repsDone)
        assertTrue(drill.targetMet)
        assertEquals(listOf(3, 2), practice.observeSessionSets(sessionId).first().map { it.reps })
        assertEquals(8L, practice.getSession(sessionId)!!.lastActiveAt)
    }

    @Test
    fun undoTakesARepOffThenReopensTheLastSet() = runBlocking {
        val sessionId = startedSession(newMove("Windmill"))
        repeat(2) { practice.addRep(sessionId, 0, now = 1) }
        practice.finishSet(sessionId, 0, now = 2)
        practice.addRep(sessionId, 0, now = 3)

        practice.undoRep(sessionId, 0, now = 4)
        assertEquals(0, drillAt(sessionId).drill.currentReps)
        assertEquals(1, drillAt(sessionId).setsDone)

        practice.undoRep(sessionId, 0, now = 5)
        assertEquals(2, drillAt(sessionId).drill.currentReps)
        assertEquals(0, drillAt(sessionId).setsDone)

        repeat(5) { practice.undoRep(sessionId, 0, now = 6) }
        assertEquals(0, drillAt(sessionId).drill.currentReps)
    }

    @Test
    fun aSetFinishedWithoutCountingRepsStillCounts() = runBlocking {
        val sessionId = startedSession(newMove("Windmill"))

        practice.finishSet(sessionId, 0, now = 1)

        assertEquals(1, drillAt(sessionId).setsDone)
        assertEquals(0, drillAt(sessionId).repsDone)
    }

    @Test
    fun finishingBanksTheUnfinishedSetAndAppearsInTheLog() = runBlocking {
        val sessionId = startedSession(newMove("Windmill"))
        repeat(4) { practice.addRep(sessionId, 0, now = 1) }
        practice.addCompletedRounds(sessionId, 2, now = 2)

        assertTrue(practice.observeFinishedSessions().first().isEmpty())
        practice.finishSession(sessionId, endedAt = 99, notes = "Solid")

        val entry = practice.observeFinishedSessions().first().single()
        assertEquals(99L, entry.session.endedAt)
        assertEquals("Solid", entry.session.notes)
        assertEquals(2, entry.session.roundsCompleted)
        assertEquals(1, entry.setCount)
        assertEquals(4, entry.repCount)
        assertEquals(0, drillAt(sessionId).drill.currentReps)
    }

    @Test
    fun startingAgainClosesAnAbandonedSessionAtItsLastChange() = runBlocking {
        val move = newMove("Windmill")
        val abandoned = startedSession(move)
        practice.addRep(abandoned, 0, now = 12_345)

        startedSession(move)

        val closed = practice.getSession(abandoned)!!
        assertEquals(12_345L, closed.endedAt)
        assertEquals(1, drillAt(abandoned).setsDone)
    }

    @Test
    fun anAbandonedSessionWithNothingInItIsDropped() = runBlocking {
        val move = newMove("Windmill")
        val abandoned = startedSession(move)

        practice.closeOpenSessions()

        assertNull(practice.getSession(abandoned))
    }

    @Test
    fun onlyAnUnfinishedSessionShowsAsOpen() = runBlocking {
        val sessionId = startedSession(newMove("Windmill"))
        assertEquals(sessionId, practice.observeOpenSession().first()?.id)

        practice.finishSession(sessionId, endedAt = 5, notes = "")

        assertNull(practice.observeOpenSession().first())
    }

    @Test
    fun deletingAPracticeOrMoveKeepsTheLogReadable() = runBlocking {
        val move = newMove("Windmill")
        val practiceId = practice.savePractice(Practice(name = "Power day"), listOf(moveDrill(move)))
        val sessionId = practice.startSession(practiceId)
        practice.addRep(sessionId, 0, now = 1)
        practice.finishSession(sessionId, endedAt = 2, notes = "")

        practice.deletePractice(practice.getPractice(practiceId)!!)
        library.deleteMove(library.getMove(move)!!)

        val session = practice.getSession(sessionId)!!
        assertNull(session.practiceId)
        assertEquals("Power day", session.name)
        val drill = drillAt(sessionId)
        assertNull(drill.drill.moveId)
        assertEquals("Windmill", drill.displayTitle)
        assertEquals(1, drill.repsDone)
    }

    @Test
    fun drillsCanBeAddedToARunningSession() = runBlocking {
        val sessionId = practice.startQuickSession("Quick practice")

        val first = practice.addSessionDrill(
            SessionDrill(sessionId, 0, DrillType.FREE, title = "Warm-up", targetSets = 1, targetReps = null),
            now = 1,
        )
        val second = practice.addSessionDrill(
            SessionDrill(sessionId, 0, DrillType.MOVE, moveId = newMove("Flare"), title = "Flare"),
            now = 2,
        )

        assertEquals(listOf(0, 1), listOf(first, second))
        assertEquals(listOf("Warm-up", "Flare"), practice.observeSessionDrills(sessionId).first().map { it.displayTitle })
    }

    @Test
    fun drillsOfferTheirMovesTopVideoAndASetsCombo() = runBlocking {
        val move = newMove("Windmill")
        val set = chains.saveSet(DanceSet(name = "Combo"), listOf(move))
        videos.setOwner(newVideo("old"), moveId = move, setId = null, linkedAt = 1)
        val best = newVideo("best")
        videos.setOwner(best, moveId = move, setId = null, linkedAt = 2)
        val id = practice.savePractice(
            Practice(name = "Power"),
            listOf(
                moveDrill(move),
                PracticeDrill(0, 0, DrillType.SET, setId = set),
                PracticeDrill(0, 0, DrillType.FREE, label = "Warm-up"),
            ),
        )

        val (moveDrill, setDrill, block) = practice.observeDrills(id).first()

        assertEquals(best, moveDrill.topVideoId)
        assertFalse(moveDrill.hasCombo)
        assertNull(setDrill.topVideoId)
        assertTrue(setDrill.hasCombo)
        assertEquals("Combo", setDrill.title)
        assertNull(block.topVideoId)
        assertFalse(block.hasCombo)
    }

    @Test
    fun attachingAVideoToAPracticeLeavesItOnItsMove() = runBlocking {
        val move = newMove("Windmill")
        val practiceId = practice.savePractice(Practice(name = "Power"), emptyList())
        val video = newVideo("clip")
        videos.setOwner(video, moveId = move, setId = null, linkedAt = 1)

        videos.attachToPractice(PracticeVideo(practiceId, video, linkedAt = 2))

        assertEquals(listOf(video), videos.observeLinked(move, null).first().map { it.id })
        assertEquals(listOf(video), videos.observeForPractice(practiceId).first().map { it.id })
    }

    @Test
    fun aVideoCanBeInManyPracticesAndNewestIsFirst() = runBlocking {
        val first = practice.savePractice(Practice(name = "A"), emptyList())
        val second = practice.savePractice(Practice(name = "B"), emptyList())
        val older = newVideo("older")
        val newer = newVideo("newer")
        videos.attachToPractice(PracticeVideo(first, older, linkedAt = 1))
        videos.attachToPractice(PracticeVideo(second, older, linkedAt = 2))
        videos.attachToPractice(PracticeVideo(first, newer, linkedAt = 3))

        assertEquals(listOf("newer", "older"), videos.observeForPractice(first).first().map { it.displayName })
        assertEquals(listOf("older"), videos.observeForPractice(second).first().map { it.displayName })
    }

    @Test
    fun deletingAPracticeOrSessionKeepsItsVideos() = runBlocking {
        val practiceId = practice.savePractice(Practice(name = "Power"), emptyList())
        val sessionId = practice.startQuickSession("Quick practice")
        val video = newVideo("clip")
        videos.attachToPractice(PracticeVideo(practiceId, video, linkedAt = 1))
        videos.attachToSession(SessionVideo(sessionId, video, linkedAt = 2))

        practice.deletePractice(practice.getPractice(practiceId)!!)
        practice.deleteSession(sessionId)

        assertNotNull(videos.getById(video))
        assertTrue(videos.observeLinks().first().isEmpty())
    }

    @Test
    fun aVideoReportsEverywhereItIsLinked() = runBlocking {
        val move = newMove("Windmill")
        val practiceId = practice.savePractice(Practice(name = "Power"), emptyList())
        val sessionId = practice.startQuickSession("Quick practice")
        val video = newVideo("clip")
        videos.setOwner(video, moveId = move, setId = null, linkedAt = 1)
        videos.attachToPractice(PracticeVideo(practiceId, video, linkedAt = 2))
        videos.attachToSession(SessionVideo(sessionId, video, linkedAt = 3))

        val links = videos.observeLinks().first().filter { it.videoId == video }.map { it.target }

        assertEquals(
            setOf(
                VideoLinkTarget(VideoLinkKind.MOVE, move, "Windmill"),
                VideoLinkTarget(VideoLinkKind.PRACTICE, practiceId, "Power"),
                VideoLinkTarget(VideoLinkKind.SESSION, sessionId, "Quick practice"),
            ),
            links.toSet(),
        )
    }

    @Test
    fun aMoveKnowsThePracticesAndSessionsThatDrilledIt() = runBlocking {
        val move = newMove("Windmill")
        val other = newMove("Flare")
        val practiceId = practice.savePractice(Practice(name = "Power day"), listOf(moveDrill(move)))
        practice.savePractice(Practice(name = "Other"), listOf(moveDrill(other)))
        val sessionId = practice.startSession(practiceId)
        repeat(3) { practice.addRep(sessionId, 0, now = 1) }
        practice.finishSet(sessionId, 0, now = 2)

        assertTrue(practice.observeSessionsWith(move, null).first().isEmpty())
        practice.finishSession(sessionId, endedAt = 3, notes = "")

        assertEquals(listOf("Power day"), practice.observePracticesWith(move, null).first().map { it.name })
        val drilled = practice.observeSessionsWith(move, null).first().single()
        assertEquals(sessionId, drilled.sessionId)
        assertEquals(1, drilled.sets)
        assertEquals(3, drilled.reps)
    }
}
