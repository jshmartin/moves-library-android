package com.example.myapplication.data

import com.example.myapplication.data.model.Move
import com.example.myapplication.ui.sets.FlowStage
import com.example.myapplication.ui.sets.FlowStage.FOOTWORK
import com.example.myapplication.ui.sets.FlowStage.FREEZE
import com.example.myapplication.ui.sets.FlowStage.GO_DOWN
import com.example.myapplication.ui.sets.FlowStage.POWER
import com.example.myapplication.ui.sets.FlowStage.TOPROCK
import com.example.myapplication.ui.sets.SetFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BasicSetsTest {
    /** Each starter move's name and the stage of the category it starts in. */
    private val starterStages: Map<String, FlowStage> = StarterMoves.ALL.associate { starter ->
        starter.name to (FlowStage.of(starter.category) ?: error("${starter.category} has no stage"))
    }

    private fun move(id: Long, name: String, parentMoveId: Long? = null) =
        Move(id = id, name = name, categoryId = 1, parentMoveId = parentMoveId)

    private val starterLibrary = StarterMoves.ALL.mapIndexed { index, starter -> move(index + 1L, starter.name) }

    @Test
    fun everyDefaultCategoryHasAStage() {
        assertEquals(
            listOf(TOPROCK, GO_DOWN, FOOTWORK, POWER, FREEZE),
            AppDatabase.DEFAULT_CATEGORIES.map { FlowStage.of(it) },
        )
    }

    @Test
    fun everyBasicSetIsMadeOfStarterMovesAndFollowsTheRandomizersFlow() {
        for (set in BasicSets.ALL) {
            val stages = set.moves.map { name -> starterStages[name] ?: error("${set.name}: $name isn't a starter move") }
            assertTrue(set.name, SetFlow.start(stages.first()) > 0)
            assertTrue(set.name, SetFlow.end(stages.last()) > 0)
            stages.zipWithNext { a, b -> assertTrue("${set.name}: $a into $b", SetFlow.next(a, b) > 0) }
        }
    }

    @Test
    fun basicSetsHaveUniqueNamesNotesAndNoBackToBackRepeats() {
        val names = BasicSets.ALL.map { StarterMoves.normalize(it.name) }
        assertEquals(names.size, names.toSet().size)
        for (set in BasicSets.ALL) {
            assertTrue(set.name, set.notes.isNotBlank())
            assertTrue(set.name, set.moves.size >= 3)
            set.moves.zipWithNext { a, b -> assertTrue(set.name, a != b) }
        }
    }

    @Test
    fun theWholeStarterLibraryOffersEveryBasicSet() {
        val offered = BasicSets.available(starterLibrary, setNames = emptyList(), setChains = emptyList())
        assertEquals(BasicSets.ALL, offered.map { it.set })
        offered.forEach { option -> assertEquals(option.set.moves, option.moves.map { it.name }) }
    }

    @Test
    fun movesMatchIgnoringCaseSpacingPunctuationAndOtherNames() {
        val library = listOf(move(1, "indian STEP"), move(2, "Knee-drop"), move(3, "Six Step"), move(4, "Baby"))
        val moves = BasicSets.resolve(BasicSets.ALL.single { it.name == "Foundation set" }, library)
        assertEquals(listOf(1L, 2L, 3L, 4L), moves?.map { it.id })
    }

    @Test
    fun aSetWithAMoveMissingFromTheLibraryIsNotOffered() {
        val library = starterLibrary.filter { it.name != "Knee drop" }
        assertNull(BasicSets.resolve(BasicSets.ALL.single { it.name == "Foundation set" }, library))
        val offered = BasicSets.available(library, emptyList(), emptyList()).map { it.set.name }
        assertTrue("Foundation set" !in offered)
    }

    @Test
    fun aBaseMoveWinsOverAVariationWithTheSameName() {
        val library = listOf(move(1, "6-step", parentMoveId = 9), move(2, "6-step"))
        val moves = BasicSets.resolve(BasicSet("Test", "Notes", listOf("6-step")), library)
        assertEquals(listOf(2L), moves?.map { it.id })
    }

    @Test
    fun aBasicSetTheDancerAlreadyHasIsNotOfferedAgain() {
        val foundation = BasicSets.ALL.single { it.name == "Foundation set" }
        val chain = BasicSets.resolve(foundation, starterLibrary)!!.map { it.id }

        val byName = BasicSets.available(starterLibrary, setNames = listOf("foundation SET"), setChains = emptyList())
        assertTrue(byName.none { it.set == foundation })

        val byChain = BasicSets.available(starterLibrary, setNames = listOf("My first set"), setChains = listOf(chain))
        assertTrue(byChain.none { it.set == foundation })
        assertEquals(BasicSets.ALL.size - 1, byChain.size)
    }
}
