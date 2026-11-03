package com.example.myapplication.data.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A user-editable grouping of moves, e.g. Toprock, Footwork, Power, Freezes. */
@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sortOrder: Int = 0,
)

enum class MoveStatus { LEARNING, IN_ARSENAL, BATTLE_READY }

/**
 * A move in the dancer's library. The library starts empty; every move is created by the user.
 * [parentMoveId] links a variation to its base move (e.g. 3-step under 6-step).
 */
@Entity(
    tableName = "moves",
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = Move::class,
            parentColumns = ["id"],
            childColumns = ["parentMoveId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("categoryId"), Index("parentMoveId")],
)
data class Move(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val categoryId: Long,
    val parentMoveId: Long? = null,
    val status: MoveStatus = MoveStatus.LEARNING,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * One self-rating of a move's execution (1–5). Ratings are never overwritten, so the full
 * history is kept for trend charts; a move's current score is its latest rating.
 */
@Entity(
    tableName = "move_ratings",
    foreignKeys = [
        ForeignKey(
            entity = Move::class,
            parentColumns = ["id"],
            childColumns = ["moveId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = VideoClip::class,
            parentColumns = ["id"],
            childColumns = ["clipId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("moveId"), Index("clipId")],
)
data class MoveRating(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val moveId: Long,
    val score: Int,
    val ratedAt: Long = System.currentTimeMillis(),
    val clipId: Long? = null,
)

/**
 * A section of a video that stays where it is on the device; only its URI is stored.
 * A clip belongs to a move or a set. A null [endMs] means "until the end of the video".
 */
@Entity(
    tableName = "video_clips",
    foreignKeys = [
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
data class VideoClip(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val videoUri: String,
    val startMs: Long = 0,
    val endMs: Long? = null,
    val label: String = "",
    val moveId: Long? = null,
    val setId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

/** A move with its latest rating (null when unrated). */
data class MoveWithScore(
    @Embedded val move: Move,
    val latestScore: Int?,
)

/** A category with the average of its moves' latest ratings (null when nothing is rated). */
data class CategoryScore(
    val categoryId: Long,
    val categoryName: String,
    val averageScore: Double?,
    val ratedMoves: Int,
    val totalMoves: Int,
)
