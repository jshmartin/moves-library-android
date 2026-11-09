package com.example.myapplication.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A drilled combo: an ordered chain of moves (see [SetMove]). */
@Entity(tableName = "sets")
data class DanceSet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val notes: String = "",
    val durationSec: Int? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

/** One slot in a set's chain. The same move may appear at several positions. */
@Entity(
    tableName = "set_moves",
    primaryKeys = ["setId", "position"],
    foreignKeys = [
        ForeignKey(
            entity = DanceSet::class,
            parentColumns = ["id"],
            childColumns = ["setId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Move::class,
            parentColumns = ["id"],
            childColumns = ["moveId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("moveId")],
)
data class SetMove(
    val setId: Long,
    val position: Int,
    val moveId: Long,
)

/** A battle round: an ordered chain of sets and loose moves (see [RoundItem]). */
@Entity(tableName = "rounds")
data class Round(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val notes: String = "",
    val targetDurationSec: Int? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * One slot in a round's chain: exactly one of [setId] or [moveId] is set.
 * [durationSec] lets a loose move contribute to the round's estimated length.
 */
@Entity(
    tableName = "round_items",
    primaryKeys = ["roundId", "position"],
    foreignKeys = [
        ForeignKey(
            entity = Round::class,
            parentColumns = ["id"],
            childColumns = ["roundId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = DanceSet::class,
            parentColumns = ["id"],
            childColumns = ["setId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Move::class,
            parentColumns = ["id"],
            childColumns = ["moveId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("setId"), Index("moveId")],
)
data class RoundItem(
    val roundId: Long,
    val position: Int,
    val setId: Long? = null,
    val moveId: Long? = null,
    val durationSec: Int? = null,
) {
    init {
        require((setId == null) != (moveId == null)) { "A round item is either a set or a move" }
    }

    companion object {
        fun ofSet(roundId: Long, position: Int, setId: Long) =
            RoundItem(roundId = roundId, position = position, setId = setId)

        fun ofMove(roundId: Long, position: Int, moveId: Long, durationSec: Int? = null) =
            RoundItem(roundId = roundId, position = position, moveId = moveId, durationSec = durationSec)
    }
}
