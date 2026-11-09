package com.example.myapplication.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.myapplication.data.model.DanceSet
import com.example.myapplication.data.model.Move

/**
 * A video the user added to the library. The file itself stays where it is on the device;
 * we only keep a reference ([uri]) plus enough metadata to find it again if it moves.
 *
 * A video is filed under at most one move or set. It can also be attached to any number of
 * practices and logged sessions (see [VideoOwner]). Deleting any of those leaves the video in
 * the library.
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
    ],
    indices = [
        Index(value = ["uri"], unique = true),
        Index(value = ["fingerprint"]),
        Index(value = ["moveId"]),
        Index(value = ["setId"]),
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
    /** Orders an owner's videos, newest first. The first one is the owner's "top" video. */
    val linkedAt: Long? = null,
) {
    init {
        require(listOfNotNull(moveId, setId).size <= 1) {
            "A video belongs to at most one move or set"
        }
    }
}

/** Something a video can be shown on. */
sealed interface VideoOwner {
    /** Filed under this owner alone: linking it here takes it off any other move or set. */
    sealed interface Exclusive : VideoOwner

    data class Move(val id: Long) : Exclusive
    data class Set(val id: Long) : Exclusive
    data class Practice(val id: Long) : VideoOwner
    data class Session(val id: Long) : VideoOwner
}

/** A library video with the name of the move or set it's filed under, if any. */
data class VideoWithOwner(
    @Embedded val video: Video,
    val ownerName: String?,
)

enum class VideoLinkKind(val noun: String) {
    MOVE("Move"),
    SET("Set"),
    PRACTICE("Practice"),
    SESSION("Log"),
}

/** A place a video is, or could be, linked to. */
data class VideoLinkTarget(
    val kind: VideoLinkKind,
    val targetId: Long,
    val label: String,
) {
    val owner: VideoOwner
        get() = when (kind) {
            VideoLinkKind.MOVE -> VideoOwner.Move(targetId)
            VideoLinkKind.SET -> VideoOwner.Set(targetId)
            VideoLinkKind.PRACTICE -> VideoOwner.Practice(targetId)
            VideoLinkKind.SESSION -> VideoOwner.Session(targetId)
        }
}

/** One place one library video is linked to. */
data class VideoLink(
    val videoId: Long,
    @Embedded val target: VideoLinkTarget,
)
