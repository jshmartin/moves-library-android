package com.example.myapplication.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.myapplication.data.model.PracticeSession
import com.example.myapplication.data.model.Routine
import com.example.myapplication.data.model.RoutineDrill
import kotlinx.coroutines.flow.Flow

/** Routines (practice plans) and the practice session log. */
@Dao
interface PracticeDao {
    @Query("SELECT * FROM routines ORDER BY name")
    fun observeRoutines(): Flow<List<Routine>>

    @Insert
    suspend fun insertRoutine(routine: Routine): Long

    @Update
    suspend fun updateRoutine(routine: Routine)

    @Delete
    suspend fun deleteRoutine(routine: Routine)

    @Query("SELECT * FROM routine_drills WHERE routineId = :routineId ORDER BY position")
    fun observeDrills(routineId: Long): Flow<List<RoutineDrill>>

    @Query("DELETE FROM routine_drills WHERE routineId = :routineId")
    suspend fun clearDrills(routineId: Long)

    @Insert
    suspend fun insertDrills(drills: List<RoutineDrill>)

    /** Replaces a routine's drills; positions are reassigned from [drills]' order. */
    @Transaction
    suspend fun replaceDrills(routineId: Long, drills: List<RoutineDrill>) {
        clearDrills(routineId)
        insertDrills(drills.mapIndexed { index, drill -> drill.copy(routineId = routineId, position = index) })
    }

    @Query("SELECT * FROM practice_sessions ORDER BY startedAt DESC")
    fun observeSessions(): Flow<List<PracticeSession>>

    @Insert
    suspend fun insertSession(session: PracticeSession): Long

    @Update
    suspend fun updateSession(session: PracticeSession)
}
