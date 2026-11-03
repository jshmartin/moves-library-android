package com.example.myapplication.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A practice plan: an ordered list of drills (see [RoutineDrill]). */
@Entity(tableName = "routines")
data class Routine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

enum class DrillType { MOVE, SET, ROUND, FREE }

/**
 * One drill in a routine. The target id matching [type] is set ([label] describes FREE blocks
 * like warm-ups). A drill is measured by [reps], [durationSec], or both.
 */
@Entity(
    tableName = "routine_drills",
    primaryKeys = ["routineId", "position"],
    foreignKeys = [
        ForeignKey(
            entity = Routine::class,
            parentColumns = ["id"],
            childColumns = ["routineId"],
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
        ForeignKey(
            entity = Round::class,
            parentColumns = ["id"],
            childColumns = ["roundId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("moveId"), Index("setId"), Index("roundId")],
)
data class RoutineDrill(
    val routineId: Long,
    val position: Int,
    val type: DrillType,
    val moveId: Long? = null,
    val setId: Long? = null,
    val roundId: Long? = null,
    val label: String = "",
    val reps: Int? = null,
    val durationSec: Int? = null,
)

/** A logged practice session, optionally run from a routine. */
@Entity(
    tableName = "practice_sessions",
    foreignKeys = [
        ForeignKey(
            entity = Routine::class,
            parentColumns = ["id"],
            childColumns = ["routineId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("routineId")],
)
data class PracticeSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routineId: Long? = null,
    val startedAt: Long = System.currentTimeMillis(),
    val endedAt: Long? = null,
    val notes: String = "",
)
