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
