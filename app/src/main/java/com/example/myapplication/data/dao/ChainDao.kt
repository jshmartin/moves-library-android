package com.example.myapplication.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.myapplication.data.model.DanceSet
import com.example.myapplication.data.model.Round
import com.example.myapplication.data.model.RoundItem
import com.example.myapplication.data.model.SetMove
import kotlinx.coroutines.flow.Flow

/** Sets (chains of moves) and rounds (chains of sets and loose moves). */
@Dao
interface ChainDao {
    @Query("SELECT * FROM sets ORDER BY name")
    fun observeSets(): Flow<List<DanceSet>>

    @Insert
    suspend fun insertSet(set: DanceSet): Long

    @Update
    suspend fun updateSet(set: DanceSet)

    @Delete
    suspend fun deleteSet(set: DanceSet)

    @Query("SELECT * FROM set_moves WHERE setId = :setId ORDER BY position")
    fun observeSetChain(setId: Long): Flow<List<SetMove>>

    @Query("DELETE FROM set_moves WHERE setId = :setId")
    suspend fun clearSetChain(setId: Long)

    @Insert
    suspend fun insertSetMoves(moves: List<SetMove>)

    /** Replaces a set's whole chain with [moveIds], in order. */
    @Transaction
    suspend fun replaceSetChain(setId: Long, moveIds: List<Long>) {
        clearSetChain(setId)
        insertSetMoves(moveIds.mapIndexed { index, moveId -> SetMove(setId, index, moveId) })
    }

    /** Every move-to-move transition the dancer has chained in a set, with how often. */
    @Query(
        """
        SELECT a.moveId AS fromMoveId, b.moveId AS toMoveId, COUNT(*) AS timesUsed
        FROM set_moves a
        JOIN set_moves b ON b.setId = a.setId AND b.position = a.position + 1
        GROUP BY a.moveId, b.moveId
        """
    )
    fun observeTransitions(): Flow<List<Transition>>

    @Query("SELECT * FROM rounds ORDER BY name")
    fun observeRounds(): Flow<List<Round>>

    @Insert
    suspend fun insertRound(round: Round): Long

    @Update
    suspend fun updateRound(round: Round)

    @Delete
    suspend fun deleteRound(round: Round)

    @Query("SELECT * FROM round_items WHERE roundId = :roundId ORDER BY position")
    fun observeRoundChain(roundId: Long): Flow<List<RoundItem>>

    @Query("DELETE FROM round_items WHERE roundId = :roundId")
    suspend fun clearRoundChain(roundId: Long)

    @Insert
    suspend fun insertRoundItems(items: List<RoundItem>)

    /** Replaces a round's whole chain; positions are reassigned from [items]' order. */
    @Transaction
    suspend fun replaceRoundChain(roundId: Long, items: List<RoundItem>) {
        clearRoundChain(roundId)
        insertRoundItems(items.mapIndexed { index, item -> item.copy(roundId = roundId, position = index) })
    }
}

data class Transition(
    val fromMoveId: Long,
    val toMoveId: Long,
    val timesUsed: Int,
)
