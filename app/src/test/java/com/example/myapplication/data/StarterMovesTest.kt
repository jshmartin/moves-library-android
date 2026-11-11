package com.example.myapplication.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StarterMovesTest {
    private val starters = StarterMoves.ALL

    @Test
    fun everyCategoryHasStarterMovesAndNoOtherCategoryDoes() {
        assertEquals(AppDatabase.DEFAULT_CATEGORIES.toSet(), starters.map { it.category }.toSet())
    }

    @Test
    fun noTwoStarterMovesShareANameOrAlias() {
        val keys = starters.flatMap { it.keys }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun variationsComeAfterABaseMoveInTheirCategory() {
        starters.forEachIndexed { index, starter ->
            val baseName = starter.variationOf ?: return@forEachIndexed
            val base = starters.take(index).single { it.name == baseName }
            assertEquals(starter.category, base.category)
            assertNull(base.variationOf)
        }
    }

    @Test
    fun everyStarterMoveSaysHowItGoes() {
        assertTrue(starters.all { it.notes.isNotBlank() })
    }

    @Test
    fun namesMatchIgnoringCaseSpacingPunctuationAndPlurals() {
        val normalize = StarterMoves::normalize
        assertEquals(normalize("CC's"), normalize("ccs"))
        assertEquals(normalize("CC's"), normalize("CC"))
        assertEquals(normalize("6-step"), normalize("6 Step"))
        assertEquals(normalize("Kick-out"), normalize("Kickouts"))
        assertEquals(normalize("Go-downs"), normalize("go downs"))
        assertNotEquals(normalize("3-step"), normalize("6-step"))
    }
}
