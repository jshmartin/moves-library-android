package com.example.myapplication.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.myapplication.data.model.DanceSet
import com.example.myapplication.data.model.Move
import com.example.myapplication.data.model.Routine

/**
 * A video the user added to the library. The file itself stays where it is on the device;
 * we only keep a reference ([uri]) plus enough metadata to find it again if it moves.
 *
 * A video belongs to at most one move, set or routine (see [VideoOwner]). Deleting the owner
 * leaves the video in the library, unlinked.
 */
@Entity(
    tableName = "videos",
    foreignKeys = [
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
        ForeignKey(
            entity = Routine::class,
            parentColumns = ["id"],
            childColumns = ["routineId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["uri"], unique = true),
        Index(value = ["fingerprint"]),
        Index(value = ["moveId"]),
        Index(value = ["setId"]),
        Index(value = ["routineId"]),
    ],
)
data class Video(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uri: String,
    val displayName: String,
    val sizeBytes: Long,
    val durationMs: Long?,
    val mimeType: String?,
    /** Content hash (size + head + tail of the file) used to recognise the file after a move. */
    val fingerprint: String,
    val addedAt: Long,
    val moveId: Long? = null,
    val setId: Long? = null,
    val routineId: Long? = null,
    /** Orders an owner's videos, newest first. The first one is the owner's "top" video. */
    val linkedAt: Long? = null,
) {
    init {
        require(listOfNotNull(moveId, setId, routineId).size <= 1) {
            "A video belongs to at most one move, set or routine"
        }
    }
}

/** The one move, set or routine that a video is linked to. */
@ConsistentCopyVisibility
data class VideoOwner private constructor(
    val moveId: Long? = null,
    val setId: Long? = null,
    val routineId: Long? = null,
) {
    companion object {
        fun ofMove(id: Long) = VideoOwner(moveId = id)
        fun ofSet(id: Long) = VideoOwner(setId = id)
        fun ofRoutine(id: Long) = VideoOwner(routineId = id)
    }
}

/** A library video with the name of whatever it's currently linked to, if anything. */
data class VideoWithOwner(
    @Embedded val video: Video,
    val ownerName: String?,
)
