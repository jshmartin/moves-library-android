package com.example.myapplication.ui.library

import com.example.myapplication.data.model.Move
import com.example.myapplication.data.model.MoveWithScore
import org.junit.Assert.assertEquals
import org.junit.Test

class GroupWithVariationsTest {
    private fun move(id: Long, name: String, parent: Long? = null) =
        MoveWithScore(Move(id = id, name = name, categoryId = 1, parentMoveId = parent), latestScore = null)

    private fun List<MoveRow>.summary() = map { it.item.move.name to it.depth }

    @Test
    fun variationsFollowTheirBaseMove() {
        val rows = groupWithVariations(
            listOf(move(1, "3-step", parent = 3), move(2, "CC's"), move(3, "6-step"), move(4, "2-step", parent = 3))
        )
        assertEquals(
            listOf("CC's" to 0, "6-step" to 0, "3-step" to 1, "2-step" to 1),
            rows.summary(),
        )
    }

    @Test
    fun variationOfMoveInAnotherCategoryIsTopLevel() {
        val rows = groupWithVariations(listOf(move(1, "Air baby", parent = 99)))
        assertEquals(listOf("Air baby" to 0), rows.summary())
    }

    @Test
    fun parentCycleStillShowsEveryMove() {
        val rows = groupWithVariations(listOf(move(1, "A", parent = 2), move(2, "B", parent = 1)))
        assertEquals(setOf("A", "B"), rows.map { it.item.move.name }.toSet())
        assertEquals(2, rows.size)
    }
}
