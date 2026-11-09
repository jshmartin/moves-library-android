package com.example.myapplication.data.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.myapplication.data.Video

/** A boxing-style round timer: [rounds] of [activeSec] work, with [restSec] between them. */
data class TimerSettings(
    val rounds: Int = 5,
    val activeSec: Int = 60,
    val restSec: Int = 30,
)

/** A saved practice: an ordered list of drills (see [PracticeDrill]) and its round timer. */
@Entity(tableName = "practices")
data class Practice(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    @Embedded val timer: TimerSettings = TimerSettings(),
)

enum class DrillType { MOVE, SET, FREE }

/**
 * One drill in a practice. The target id matching [type] is set ([label] names FREE blocks
 * like warm-ups). [targetSets] and [targetReps] are training sets and reps, unrelated to the
 * combos in the Sets tab; a null [targetReps] means sets are counted but reps aren't targeted.
 */
@Entity(
    tableName = "practice_drills",
    primaryKeys = ["practiceId", "position"],
    foreignKeys = [
        ForeignKey(
            entity = Practice::class,
            parentColumns = ["id"],
            childColumns = ["practiceId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Move::class,
            parentColumns = ["id"],
            childColumns = ["moveId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = DanceSet::class,
            parentColumns = ["id"],
            childColumns = ["setId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("moveId"), Index("setId")],
)
data class PracticeDrill(
    val practiceId: Long,
    val position: Int,
    val type: DrillType,
    val moveId: Long? = null,
    val setId: Long? = null,
    val label: String = "",
    val targetSets: Int = DEFAULT_TARGET_SETS,
    val targetReps: Int? = DEFAULT_TARGET_REPS,
) {
    companion object {
        const val DEFAULT_TARGET_SETS = 3
        const val DEFAULT_TARGET_REPS = 10
    }
}

/**
 * One logged practice, written from the moment it starts so a killed app can't lose it.
 * [endedAt] is null while it's running. [name] and [timer] are copied from the practice so the
 * log reads the same after the practice is edited or deleted.
 */
@Entity(
    tableName = "practice_sessions",
    foreignKeys = [
        ForeignKey(
            entity = Practice::class,
            parentColumns = ["id"],
            childColumns = ["practiceId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("practiceId")],
)
data class PracticeSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val practiceId: Long? = null,
    val name: String,
    val startedAt: Long = System.currentTimeMillis(),
    /** When the session last changed; ends a session the app never got to finish. */
    val lastActiveAt: Long = startedAt,
    val endedAt: Long? = null,
    val notes: String = "",
    @Embedded val timer: TimerSettings = TimerSettings(),
    val roundsCompleted: Int = 0,
)

/**
 * A drill as it was run in one session. Deleting its move or set keeps this row, with [title]
 * still naming it. [currentReps] is the unfinished set being counted.
 */
@Entity(
    tableName = "session_drills",
    primaryKeys = ["sessionId", "position"],
    foreignKeys = [
        ForeignKey(
            entity = PracticeSession::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Move::class,
            parentColumns = ["id"],
            childColumns = ["moveId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = DanceSet::class,
            parentColumns = ["id"],
            childColumns = ["setId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("moveId"), Index("setId")],
)
data class SessionDrill(
    val sessionId: Long,
    val position: Int,
    val type: DrillType,
    val moveId: Long? = null,
    val setId: Long? = null,
    val title: String,
    val targetSets: Int = PracticeDrill.DEFAULT_TARGET_SETS,
    val targetReps: Int? = PracticeDrill.DEFAULT_TARGET_REPS,
    val currentReps: Int = 0,
)

/** One finished training set of a session drill and how many reps it took. */
@Entity(
    tableName = "session_sets",
    primaryKeys = ["sessionId", "drillPosition", "setNumber"],
    foreignKeys = [
        ForeignKey(
            entity = SessionDrill::class,
            parentColumns = ["sessionId", "position"],
            childColumns = ["sessionId", "drillPosition"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class SessionSet(
    val sessionId: Long,
    val drillPosition: Int,
    val setNumber: Int,
    val reps: Int,
)

/** A video attached to a practice. Unlike a move or set, a practice shares its videos. */
@Entity(
    tableName = "practice_videos",
    primaryKeys = ["practiceId", "videoId"],
    foreignKeys = [
        ForeignKey(
            entity = Practice::class,
            parentColumns = ["id"],
            childColumns = ["practiceId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Video::class,
            parentColumns = ["id"],
            childColumns = ["videoId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("videoId")],
)
data class PracticeVideo(
    val practiceId: Long,
    val videoId: Long,
    val linkedAt: Long,
)

/** A video attached to one logged session, such as footage filmed that day. */
@Entity(
    tableName = "session_videos",
    primaryKeys = ["sessionId", "videoId"],
    foreignKeys = [
        ForeignKey(
            entity = PracticeSession::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Video::class,
            parentColumns = ["id"],
            childColumns = ["videoId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("videoId")],
)
data class SessionVideo(
    val sessionId: Long,
    val videoId: Long,
    val linkedAt: Long,
)
