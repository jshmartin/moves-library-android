package com.example.myapplication.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A video the user added to the library. The file itself stays where it is on the device;
 * we only keep a reference ([uri]) plus enough metadata to find it again if it moves.
 */
@Entity(
    tableName = "videos",
    indices = [Index(value = ["uri"], unique = true), Index(value = ["fingerprint"])],
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
)
