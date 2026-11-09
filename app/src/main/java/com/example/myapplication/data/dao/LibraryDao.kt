package com.example.myapplication.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.myapplication.data.model.Category
import com.example.myapplication.data.model.CategorySummary
import com.example.myapplication.data.model.Move
import com.example.myapplication.data.model.VideoClip
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryDao {
    @Query("SELECT * FROM categories ORDER BY sortOrder, name")
    fun observeCategories(): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE id = :id")
    fun observeCategory(id: Long): Flow<Category?>

    @Insert
    suspend fun insertCategory(category: Category): Long

    @Update
    suspend fun updateCategory(category: Category)

    @Update
    suspend fun updateCategories(categories: List<Category>)

    @Delete
    suspend fun deleteCategory(category: Category)

    @Query("SELECT * FROM moves ORDER BY name")
    fun observeMoves(): Flow<List<Move>>

    @Query("SELECT * FROM moves WHERE categoryId = :categoryId ORDER BY name COLLATE NOCASE")
    fun observeMovesInCategory(categoryId: Long): Flow<List<Move>>

    @Query("SELECT * FROM moves WHERE parentMoveId = :moveId ORDER BY name COLLATE NOCASE")
    fun observeVariations(moveId: Long): Flow<List<Move>>

    @Query("SELECT * FROM moves WHERE id = :id")
    fun observeMove(id: Long): Flow<Move?>

    @Query("SELECT * FROM moves WHERE id = :id")
    suspend fun getMove(id: Long): Move?

    @Insert
    suspend fun insertMove(move: Move): Long

    @Update
    suspend fun updateMove(move: Move)

    @Delete
    suspend fun deleteMove(move: Move)

    /** Each category with its number of moves; empty categories are included. */
    @Query(
        """
        SELECT c.id AS categoryId,
               c.name AS categoryName,
               COUNT(m.id) AS totalMoves
        FROM categories c
        LEFT JOIN moves m ON m.categoryId = c.id
        GROUP BY c.id
        ORDER BY c.sortOrder, c.name
        """
    )
    fun observeCategorySummaries(): Flow<List<CategorySummary>>

    @Insert
    suspend fun insertClip(clip: VideoClip): Long

    @Delete
    suspend fun deleteClip(clip: VideoClip)

    @Query("SELECT * FROM video_clips WHERE moveId = :moveId ORDER BY createdAt DESC")
    fun observeClipsForMove(moveId: Long): Flow<List<VideoClip>>
}
