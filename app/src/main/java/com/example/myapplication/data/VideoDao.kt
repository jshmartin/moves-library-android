package com.example.myapplication.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.myapplication.data.model.PracticeVideo
import com.example.myapplication.data.model.SessionVideo
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoDao {
    @Query("SELECT * FROM videos ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<Video>>

    @Query(
        """
        SELECT v.*, COALESCE(m.name, s.name) AS ownerName
        FROM videos v
        LEFT JOIN moves m ON m.id = v.moveId
        LEFT JOIN sets s ON s.id = v.setId
        ORDER BY v.addedAt DESC
        """
    )
    fun observeAllWithOwner(): Flow<List<VideoWithOwner>>

    /** A move's or set's videos, top video first. Exactly one argument is non-null. */
    @Query(
        """
        SELECT * FROM videos
        WHERE moveId = :moveId OR setId = :setId
        ORDER BY linkedAt DESC, id DESC
        """
    )
    fun observeLinked(moveId: Long?, setId: Long?): Flow<List<Video>>

    /** Every video filed under a move in a set's chain, with each move's top video before its others. */
    @Query(
        """
        SELECT * FROM videos
        WHERE moveId IN (SELECT moveId FROM set_moves WHERE setId = :setId)
        ORDER BY linkedAt DESC, id DESC
        """
    )
    fun observeForSetMoves(setId: Long): Flow<List<Video>>

    /** Replaces the video's move or set in one write, so it can never hold two. Nulls unlink it. */
    @Query("UPDATE videos SET moveId = :moveId, setId = :setId, linkedAt = :linkedAt WHERE id = :videoId")
    suspend fun setOwner(videoId: Long, moveId: Long?, setId: Long?, linkedAt: Long?)

    @Query(
        """
        SELECT v.* FROM videos v
        JOIN practice_videos l ON l.videoId = v.id
        WHERE l.practiceId = :practiceId
        ORDER BY l.linkedAt DESC, v.id DESC
        """
    )
    fun observeForPractice(practiceId: Long): Flow<List<Video>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun attachToPractice(link: PracticeVideo)

    @Query("DELETE FROM practice_videos WHERE practiceId = :practiceId AND videoId = :videoId")
    suspend fun detachFromPractice(practiceId: Long, videoId: Long)

    @Query(
        """
        SELECT v.* FROM videos v
        JOIN session_videos l ON l.videoId = v.id
        WHERE l.sessionId = :sessionId
        ORDER BY l.linkedAt DESC, v.id DESC
        """
    )
    fun observeForSession(sessionId: Long): Flow<List<Video>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun attachToSession(link: SessionVideo)

    @Query("DELETE FROM session_videos WHERE sessionId = :sessionId AND videoId = :videoId")
    suspend fun detachFromSession(sessionId: Long, videoId: Long)

    /** Everywhere every video is linked. */
    @Query(
        """
        SELECT v.id AS videoId, 'MOVE' AS kind, m.id AS targetId, m.name AS label
        FROM videos v JOIN moves m ON m.id = v.moveId
        UNION ALL
        SELECT v.id, 'SET', s.id, s.name
        FROM videos v JOIN sets s ON s.id = v.setId
        UNION ALL
        SELECT l.videoId, 'PRACTICE', p.id, p.name
        FROM practice_videos l JOIN practices p ON p.id = l.practiceId
        UNION ALL
        SELECT l.videoId, 'SESSION', s.id, s.name
        FROM session_videos l JOIN practice_sessions s ON s.id = l.sessionId
        """
    )
    fun observeLinks(): Flow<List<VideoLink>>

    /** The moves, sets and practices a video can be linked to from a picker. */
    @Query(
        """
        SELECT 'MOVE' AS kind, id AS targetId, name AS label FROM moves
        UNION ALL
        SELECT 'SET', id, name FROM sets
        UNION ALL
        SELECT 'PRACTICE', id, name FROM practices
        ORDER BY label COLLATE NOCASE
        """
    )
    fun observeLinkTargets(): Flow<List<VideoLinkTarget>>

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
