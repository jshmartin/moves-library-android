package com.example.myapplication.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.myapplication.data.model.Practice
import com.example.myapplication.data.model.PracticeDrill
import com.example.myapplication.data.model.PracticeSession
import com.example.myapplication.data.model.SessionDrill
import com.example.myapplication.data.model.SessionSet
import kotlinx.coroutines.flow.Flow

/** What can be played for a drill. Needs its drill table aliased as `d`. */
private const val DRILL_VIDEO_COLUMNS = """
    (SELECT id FROM videos WHERE moveId = d.moveId OR setId = d.setId
     ORDER BY linkedAt DESC, id DESC LIMIT 1) AS topVideoId,
    EXISTS (SELECT 1 FROM set_moves sm JOIN videos v ON v.moveId = sm.moveId
            WHERE sm.setId = d.setId) AS hasCombo
"""

private const val PRACTICE_DRILLS = """
    SELECT d.*, COALESCE(m.name, s.name, d.label) AS title, $DRILL_VIDEO_COLUMNS
    FROM practice_drills d
    LEFT JOIN moves m ON m.id = d.moveId
    LEFT JOIN sets s ON s.id = d.setId
    WHERE d.practiceId = :practiceId
    ORDER BY d.position
"""

/** A session's totals. Needs its session table aliased as `s`. */
private const val SESSION_TOTAL_COLUMNS = """
    (SELECT COUNT(*) FROM session_sets x WHERE x.sessionId = s.id) AS setCount,
    (SELECT COALESCE(SUM(x.reps), 0) FROM session_sets x WHERE x.sessionId = s.id) AS repCount
"""

/** Saved practices, the sessions run from them, and the sets and reps counted in those sessions. */
@Dao
interface PracticeDao {
    @Query(
        """
        SELECT p.*,
               (SELECT COUNT(*) FROM practice_drills d WHERE d.practiceId = p.id) AS drillCount,
               (SELECT MAX(s.startedAt) FROM practice_sessions s
                WHERE s.practiceId = p.id AND s.endedAt IS NOT NULL) AS lastPracticedAt
        FROM practices p
        ORDER BY p.name COLLATE NOCASE
        """
    )
    fun observePracticeSummaries(): Flow<List<PracticeSummary>>

    @Query("SELECT * FROM practices WHERE id = :id")
    fun observePractice(id: Long): Flow<Practice?>

    @Query("SELECT * FROM practices WHERE id = :id")
    suspend fun getPractice(id: Long): Practice?

    @Insert
    suspend fun insertPractice(practice: Practice): Long

    @Update
    suspend fun updatePractice(practice: Practice)

    @Delete
    suspend fun deletePractice(practice: Practice)

    @Query(PRACTICE_DRILLS)
    fun observeDrills(practiceId: Long): Flow<List<PracticeDrillView>>

    @Query(PRACTICE_DRILLS)
    suspend fun getDrills(practiceId: Long): List<PracticeDrillView>

    @Query("DELETE FROM practice_drills WHERE practiceId = :practiceId")
    suspend fun clearDrills(practiceId: Long)

    @Insert
    suspend fun insertDrills(drills: List<PracticeDrill>)

    /** Creates or updates [practice] together with its drills, and returns its id. */
    @Transaction
    suspend fun savePractice(practice: Practice, drills: List<PracticeDrill>): Long {
        val id = if (practice.id == 0L) insertPractice(practice) else practice.id.also { updatePractice(practice) }
        clearDrills(id)
        insertDrills(drills.mapIndexed { index, drill -> drill.copy(practiceId = id, position = index) })
        return id
    }

    /** Starts a logged session from a saved practice and returns its id. */
    @Transaction
    suspend fun startSession(practiceId: Long): Long {
        closeOpenSessions()
        val practice = checkNotNull(getPractice(practiceId)) { "No practice $practiceId" }
        val sessionId = insertSession(
            PracticeSession(practiceId = practice.id, name = practice.name, timer = practice.timer)
        )
        insertSessionDrills(
            getDrills(practiceId).mapIndexed { index, view ->
                SessionDrill(
                    sessionId = sessionId,
                    position = index,
                    type = view.drill.type,
                    moveId = view.drill.moveId,
                    setId = view.drill.setId,
                    title = view.title,
                    targetSets = view.drill.targetSets,
                    targetReps = view.drill.targetReps,
                )
            }
        )
        return sessionId
    }

    /** Starts a logged session that isn't from a saved practice, with at most one drill so far. */
    @Transaction
    suspend fun startQuickSession(name: String, drill: SessionDrill? = null): Long {
        closeOpenSessions()
        val sessionId = insertSession(PracticeSession(name = name))
        if (drill != null) insertSessionDrills(listOf(drill.copy(sessionId = sessionId, position = 0)))
        return sessionId
    }

    /** Ends sessions the app never got to finish, at the last moment they changed. */
    @Transaction
    suspend fun closeOpenSessions() {
        getOpenSessions().forEach { session ->
            finishSession(session.id, session.lastActiveAt, session.notes)
            if (isEmptySession(session.id)) deleteSession(session.id)
        }
    }

    @Query("SELECT * FROM practice_sessions WHERE endedAt IS NULL")
    suspend fun getOpenSessions(): List<PracticeSession>

    @Query("SELECT * FROM practice_sessions WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    fun observeOpenSession(): Flow<PracticeSession?>

    @Query(
        """
        SELECT NOT EXISTS (SELECT 1 FROM session_sets WHERE sessionId = :id)
           AND NOT EXISTS (SELECT 1 FROM session_videos WHERE sessionId = :id)
           AND EXISTS (SELECT 1 FROM practice_sessions WHERE id = :id AND roundsCompleted = 0 AND notes = '')
        """
    )
    suspend fun isEmptySession(id: Long): Boolean

    @Insert
    suspend fun insertSession(session: PracticeSession): Long

    @Insert
    suspend fun insertSessionDrills(drills: List<SessionDrill>)

    @Query("SELECT * FROM practice_sessions WHERE id = :id")
    fun observeSession(id: Long): Flow<PracticeSession?>

    @Query("SELECT * FROM practice_sessions WHERE id = :id")
    suspend fun getSession(id: Long): PracticeSession?

    @Query("DELETE FROM practice_sessions WHERE id = :id")
    suspend fun deleteSession(id: Long)

    @Query("UPDATE practice_sessions SET notes = :notes WHERE id = :id")
    suspend fun setSessionNotes(id: Long, notes: String)

    @Query("UPDATE practice_sessions SET endedAt = :endedAt, notes = :notes WHERE id = :id")
    suspend fun markSessionEnded(id: Long, endedAt: Long, notes: String)

    @Query("UPDATE practice_sessions SET lastActiveAt = :now WHERE id = :id")
    suspend fun touchSession(id: Long, now: Long)

    @Query(
        """
        UPDATE practice_sessions
        SET roundsCompleted = roundsCompleted + :rounds, lastActiveAt = :now
        WHERE id = :id
        """
    )
    suspend fun addCompletedRounds(id: Long, rounds: Int, now: Long)

    @Query("UPDATE practice_sessions SET rounds = :rounds, activeSec = :activeSec, restSec = :restSec WHERE id = :id")
    suspend fun setSessionTimer(id: Long, rounds: Int, activeSec: Int, restSec: Int)

    /** Banks any unfinished set, then closes the session. */
    @Transaction
    suspend fun finishSession(id: Long, endedAt: Long, notes: String) {
        getSessionDrills(id).filter { it.currentReps > 0 }.forEach { bankSet(it) }
        markSessionEnded(id, endedAt, notes)
    }

    @Query("SELECT * FROM session_drills WHERE sessionId = :sessionId ORDER BY position")
    suspend fun getSessionDrills(sessionId: Long): List<SessionDrill>

    @Query("SELECT * FROM session_drills WHERE sessionId = :sessionId AND position = :position")
    suspend fun getSessionDrill(sessionId: Long, position: Int): SessionDrill?

    @Query(
        """
        SELECT d.*, COALESCE(m.name, s.name, d.title) AS displayTitle,
               (SELECT COUNT(*) FROM session_sets x
                WHERE x.sessionId = d.sessionId AND x.drillPosition = d.position) AS setsDone,
               (SELECT COALESCE(SUM(x.reps), 0) FROM session_sets x
                WHERE x.sessionId = d.sessionId AND x.drillPosition = d.position) AS repsDone,
               $DRILL_VIDEO_COLUMNS
        FROM session_drills d
        LEFT JOIN moves m ON m.id = d.moveId
        LEFT JOIN sets s ON s.id = d.setId
        WHERE d.sessionId = :sessionId
        ORDER BY d.position
        """
    )
    fun observeSessionDrills(sessionId: Long): Flow<List<SessionDrillView>>

    @Query("SELECT * FROM session_sets WHERE sessionId = :sessionId ORDER BY drillPosition, setNumber")
    fun observeSessionSets(sessionId: Long): Flow<List<SessionSet>>

    @Query(
        """
        SELECT * FROM session_sets
        WHERE sessionId = :sessionId AND drillPosition = :position
        ORDER BY setNumber DESC LIMIT 1
        """
    )
    suspend fun getLastSet(sessionId: Long, position: Int): SessionSet?

    @Insert
    suspend fun insertSessionSet(set: SessionSet)

    @Delete
    suspend fun deleteSessionSet(set: SessionSet)

    // Rep taps can land faster than the UI redraws, so counting happens in the database.
    @Query("UPDATE session_drills SET currentReps = currentReps + 1 WHERE sessionId = :sessionId AND position = :position")
    suspend fun incrementReps(sessionId: Long, position: Int)

    @Query("UPDATE session_drills SET currentReps = :reps WHERE sessionId = :sessionId AND position = :position")
    suspend fun setCurrentReps(sessionId: Long, position: Int, reps: Int)

    @Query("SELECT COALESCE(MAX(position) + 1, 0) FROM session_drills WHERE sessionId = :sessionId")
    suspend fun nextDrillPosition(sessionId: Long): Int

    @Transaction
    suspend fun addRep(sessionId: Long, position: Int, now: Long) {
        incrementReps(sessionId, position)
        touchSession(sessionId, now)
    }

    /** Takes one rep off. With none counted yet, reopens the previous set instead. */
    @Transaction
    suspend fun undoRep(sessionId: Long, position: Int, now: Long) {
        val drill = getSessionDrill(sessionId, position) ?: return
        if (drill.currentReps > 0) {
            setCurrentReps(sessionId, position, drill.currentReps - 1)
        } else {
            val last = getLastSet(sessionId, position) ?: return
            deleteSessionSet(last)
            setCurrentReps(sessionId, position, last.reps)
        }
        touchSession(sessionId, now)
    }

    @Transaction
    suspend fun finishSet(sessionId: Long, position: Int, now: Long) {
        getSessionDrill(sessionId, position)?.let { bankSet(it) }
        touchSession(sessionId, now)
    }

    /** Files the drill's counted reps as its next finished set. */
    @Transaction
    suspend fun bankSet(drill: SessionDrill) {
        val setNumber = (getLastSet(drill.sessionId, drill.position)?.setNumber ?: 0) + 1
        insertSessionSet(SessionSet(drill.sessionId, drill.position, setNumber, drill.currentReps))
        setCurrentReps(drill.sessionId, drill.position, 0)
    }

    /** Adds a drill to a running session, after its existing ones, and returns its position. */
    @Transaction
    suspend fun addSessionDrill(drill: SessionDrill, now: Long): Int {
        val position = nextDrillPosition(drill.sessionId)
        insertSessionDrills(listOf(drill.copy(position = position)))
        touchSession(drill.sessionId, now)
        return position
    }

    /** Every finished session, newest first. */
    @Query(
        """
        SELECT s.*, $SESSION_TOTAL_COLUMNS
        FROM practice_sessions s
        WHERE s.endedAt IS NOT NULL
        ORDER BY s.startedAt DESC
        """
    )
    fun observeFinishedSessions(): Flow<List<SessionSummary>>

    @Query(
        """
        SELECT s.*, $SESSION_TOTAL_COLUMNS
        FROM practice_sessions s
        WHERE s.endedAt IS NOT NULL AND s.practiceId = :practiceId
        ORDER BY s.startedAt DESC
        """
    )
    fun observeFinishedSessionsOf(practiceId: Long): Flow<List<SessionSummary>>

    /** The saved practices that drill a move or set. Exactly one argument is non-null. */
    @Query(
        """
        SELECT DISTINCT p.* FROM practices p
        JOIN practice_drills d ON d.practiceId = p.id
        WHERE d.moveId = :moveId OR d.setId = :setId
        ORDER BY p.name COLLATE NOCASE
        """
    )
    fun observePracticesWith(moveId: Long?, setId: Long?): Flow<List<Practice>>

    /** The finished sessions that drilled a move or set, newest first. Exactly one argument is non-null. */
    @Query(
        """
        SELECT s.id AS sessionId, s.name AS sessionName, s.startedAt AS startedAt,
               COUNT(x.reps) AS sets, COALESCE(SUM(x.reps), 0) AS reps
        FROM session_drills d
        JOIN practice_sessions s ON s.id = d.sessionId AND s.endedAt IS NOT NULL
        LEFT JOIN session_sets x ON x.sessionId = d.sessionId AND x.drillPosition = d.position
        WHERE d.moveId = :moveId OR d.setId = :setId
        GROUP BY s.id
        ORDER BY s.startedAt DESC
        """
    )
    fun observeSessionsWith(moveId: Long?, setId: Long?): Flow<List<DrilledInSession>>
}

data class PracticeSummary(
    @Embedded val practice: Practice,
    val drillCount: Int,
    val lastPracticedAt: Long?,
)

/** A drill with its current name and what can be played for it. */
data class PracticeDrillView(
    @Embedded val drill: PracticeDrill,
    val title: String,
    val topVideoId: Long?,
    val hasCombo: Boolean,
)

data class SessionSummary(
    @Embedded val session: PracticeSession,
    val setCount: Int,
    val repCount: Int,
)

/** A session's drill with its progress so far and what can be played for it. */
data class SessionDrillView(
    @Embedded val drill: SessionDrill,
    val displayTitle: String,
    val setsDone: Int,
    val repsDone: Int,
    val topVideoId: Long?,
    val hasCombo: Boolean,
) {
    val targetMet: Boolean get() = setsDone >= drill.targetSets
}

/** How much a move or set was drilled in one session. */
data class DrilledInSession(
    val sessionId: Long,
    val sessionName: String,
    val startedAt: Long,
    val sets: Int,
    val reps: Int,
)
