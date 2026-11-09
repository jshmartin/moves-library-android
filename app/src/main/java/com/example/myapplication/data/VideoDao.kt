package com.example.myapplication.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoDao {
    @Query("SELECT * FROM videos ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<Video>>

    @Query(
        """
        SELECT v.*, COALESCE(m.name, s.name, r.name) AS ownerName
        FROM videos v
        LEFT JOIN moves m ON m.id = v.moveId
        LEFT JOIN sets s ON s.id = v.setId
        LEFT JOIN routines r ON r.id = v.routineId
        ORDER BY v.addedAt DESC
        """
    )
    fun observeAllWithOwner(): Flow<List<VideoWithOwner>>

    /** An owner's videos, top video first. Exactly one argument is non-null. */
    @Query(
        """
        SELECT * FROM videos
        WHERE moveId = :moveId OR setId = :setId OR routineId = :routineId
        ORDER BY linkedAt DESC, id DESC
        """
    )
    fun observeLinked(moveId: Long?, setId: Long?, routineId: Long?): Flow<List<Video>>

    /** Replaces the video's owner in one write, so it can never hold two. All nulls unlinks it. */
    @Query(
        """
        UPDATE videos
        SET moveId = :moveId, setId = :setId, routineId = :routineId, linkedAt = :linkedAt
        WHERE id = :videoId
        """
    )
    suspend fun setOwner(videoId: Long, moveId: Long?, setId: Long?, routineId: Long?, linkedAt: Long?)

    @Query("UPDATE videos SET linkedAt = :linkedAt WHERE id = :videoId")
    suspend fun setLinkedAt(videoId: Long, linkedAt: Long)

    @Query("SELECT * FROM videos WHERE id = :id")
    suspend fun getById(id: Long): Video?

    @Query("SELECT * FROM videos WHERE fingerprint = :fingerprint LIMIT 1")
    suspend fun findByFingerprint(fingerprint: String): Video?

    @Query("SELECT * FROM videos WHERE uri = :uri LIMIT 1")
    suspend fun findByUri(uri: String): Video?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(video: Video): Long

    @Update
    suspend fun update(video: Video)

    @Delete
    suspend fun delete(video: Video)
}
