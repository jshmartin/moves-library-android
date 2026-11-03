package com.example.myapplication.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class RoundItemTest {
    @Test
    fun setAndMoveFactoriesFillExactlyOneTarget() {
        assertEquals(7L, RoundItem.ofSet(roundId = 1, position = 0, setId = 7).setId)
        assertEquals(9L, RoundItem.ofMove(roundId = 1, position = 1, moveId = 9).moveId)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsItemWithBothSetAndMove() {
        RoundItem(roundId = 1, position = 0, setId = 1, moveId = 2)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsItemWithNeitherSetNorMove() {
        RoundItem(roundId = 1, position = 0)
    }
}
