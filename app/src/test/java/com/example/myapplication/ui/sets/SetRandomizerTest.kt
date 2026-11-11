package com.example.myapplication.ui.sets

import com.example.myapplication.ui.sets.FlowStage.FOOTWORK
import com.example.myapplication.ui.sets.FlowStage.FREEZE
import com.example.myapplication.ui.sets.FlowStage.GO_DOWN
import com.example.myapplication.ui.sets.FlowStage.POWER
import com.example.myapplication.ui.sets.FlowStage.TOPROCK
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SetRandomizerTest {
    /** Shaped like the starter library: 4 toprock, 5 go-downs, 7 footwork, 4 power, 5 freezes. */
    private val library: List<FlowMove> = listOf(
        TOPROCK to 4, GO_DOWN to 5, FOOTWORK to 7, POWER to 4, FREEZE to 5,
    ).flatMap { (stage, count) -> List(count) { stage } }
        .mapIndexed { index, stage -> FlowMove(index + 1L, stage) }

    private fun assertFollowsFlow(chain: List<FlowMove>) {
        assertTrue("opens on ${chain.first().stage}", SetFlow.start(chain.first().stage) > 0)
        assertTrue("ends on ${chain.last().stage}", SetFlow.end(chain.last().stage) > 0)
        chain.zipWithNext { a, b ->
            assertTrue("${a.stage} into ${b.stage}", SetFlow.next(a.stage, b.stage) > 0)
            assertNotEquals("same move twice in a row", a.id, b.id)
        }
    }

    @Test
    fun readsStagesFromDefaultAndRenamedCategories() {
        assertEquals(TOPROCK, FlowStage.of("Toprock"))
        assertEquals(GO_DOWN, FlowStage.of("Go-downs"))
        assertEquals(FOOTWORK, FlowStage.of("Footwork"))
        assertEquals(POWER, FlowStage.of("Power"))
        assertEquals(FREEZE, FlowStage.of("Freezes"))

        assertEquals(TOPROCK, FlowStage.of("Top rock"))
        assertEquals(TOPROCK, FlowStage.of("Uprock"))
        assertEquals(GO_DOWN, FlowStage.of("Drops"))
        assertEquals(GO_DOWN, FlowStage.of("Spin downs"))
        assertEquals(FOOTWORK, FlowStage.of("Downrock"))
        assertEquals(FOOTWORK, FlowStage.of("Threads"))
        assertEquals(POWER, FlowStage.of("Air power"))
        assertEquals(POWER, FlowStage.of("Headspins"))
        assertEquals(FREEZE, FlowStage.of("Chair freezes"))
        assertEquals(FREEZE, FlowStage.of("Stalls"))
        assertNull(FlowStage.of("Tricks"))
    }

    @Test
    fun nothingOnTheFloorLeadsBackUp() {
        for (stage in listOf(GO_DOWN, FOOTWORK, POWER, FREEZE)) {
            assertEquals(0.0, SetFlow.next(stage, TOPROCK), 0.0)
            assertEquals(0.0, SetFlow.next(stage, GO_DOWN), 0.0)
        }
        assertEquals(0.0, SetFlow.next(TOPROCK, POWER), 0.0)
        assertEquals(0.0, SetFlow.next(TOPROCK, FREEZE), 0.0)
        assertEquals(0.0, SetFlow.start(POWER), 0.0)
        assertEquals(0.0, SetFlow.end(GO_DOWN), 0.0)
    }

    @Test
    fun everyGeneratedSetFollowsTheFlowAtEveryLength() {
        val randomizer = SetRandomizer(library)
        for (seed in 0 until 300) {
            for (length in 2..10) {
                val chain = randomizer.generate(length, Random(seed * 100 + length))
                assertEquals(length, chain.size)
                assertFollowsFlow(chain)
            }
        }
    }

    @Test
    fun setsMostlyOpenWithToprockAndEndOnAFreeze() {
        val randomizer = SetRandomizer(library)
        val chains = (0 until 1000).map { randomizer.generate(5, Random(it)) }
        assertTrue(chains.count { it.first().stage == TOPROCK } > 600)
        assertTrue(chains.count { it.last().stage == FREEZE } > 700)
    }

    @Test
    fun setsGoDownInsteadOfStackingToprockAndRarelyFreezeMidway() {
        val randomizer = SetRandomizer(library)
        val chains = (0 until 2000).map { randomizer.generate(5, Random(it)) }
        assertTrue(chains.count { chain -> chain.count { it.stage == TOPROCK } >= 3 } < 100)
        assertTrue(chains.count { chain -> chain.dropLast(1).any { it.stage == FREEZE } } < 400)
    }

    @Test
    fun movesRarelyRepeatInOneSet() {
        val randomizer = SetRandomizer(library)
        val chains = (0 until 1000).map { randomizer.generate(6, Random(it)) }
        assertTrue(chains.count { chain -> chain.distinctBy { it.id }.size < chain.size } < 150)
    }

    @Test
    fun shortensTheSetWhenTheLibraryCantMakeItThatLong() {
        val moves = listOf(FlowMove(1, TOPROCK), FlowMove(2, GO_DOWN), FlowMove(3, FOOTWORK))
        val chain = SetRandomizer(moves).generate(10, Random(1))
        assertEquals(listOf(1L, 2L, 3L), chain.map { it.id })
    }

    @Test
    fun endsAnywhereWhenNoMoveCanEndASet() {
        val moves = listOf(FlowMove(1, TOPROCK), FlowMove(2, GO_DOWN))
        assertEquals(listOf(1L, 2L), SetRandomizer(moves).generate(5, Random(1)).map { it.id })
    }

    @Test
    fun opensAnywhereWhenNoMoveCanOpenASet() {
        val moves = listOf(FlowMove(1, POWER), FlowMove(2, FREEZE), FlowMove(3, FREEZE))
        val chain = SetRandomizer(moves).generate(4, Random(1))
        assertEquals(4, chain.size)
        chain.zipWithNext { a, b ->
            assertTrue("${a.stage} into ${b.stage}", SetFlow.next(a.stage, b.stage) > 0)
            assertNotEquals(a.id, b.id)
        }
    }

    @Test
    fun anEmptyLibraryMakesNoSet() {
        val randomizer = SetRandomizer(emptyList())
        assertTrue(randomizer.isEmpty)
        assertTrue(randomizer.generate(5).isEmpty())
    }

    @Test
    fun swappingAMoveKeepsTheSetFlowing() {
        val randomizer = SetRandomizer(library)
        for (seed in 0 until 200) {
            val random = Random(seed)
            val chain = randomizer.generate(6, random)
            for (index in chain.indices) {
                val swapped = randomizer.swap(chain, index, random) ?: continue
                assertNotEquals(chain[index].id, swapped[index].id)
                assertEquals(chain.filterIndexed { i, _ -> i != index }, swapped.filterIndexed { i, _ -> i != index })
                assertFollowsFlow(swapped)
            }
        }
    }

    @Test
    fun aMoveCantBeSwappedWhenNothingElseFits() {
        val moves = listOf(FlowMove(1, TOPROCK), FlowMove(2, GO_DOWN), FlowMove(3, FOOTWORK), FlowMove(4, FREEZE))
        val randomizer = SetRandomizer(moves)
        val chain = listOf(moves[0], moves[1], moves[2], moves[3])
        // Only the freeze is left to put between the toprock and the footwork, and toprock can't go into a freeze.
        assertFalse(randomizer.canSwap(chain, 1))
        assertNull(randomizer.swap(chain, 1))

        // At the end of toprock, go-down, footwork, the footwork can become the freeze but never the toprock.
        val shorter = listOf(moves[0], moves[1], moves[2])
        assertTrue(randomizer.canSwap(shorter, 2))
        assertEquals(4L, randomizer.swap(shorter, 2, Random(1))?.last()?.id)
    }

    @Test
    fun stepsTheDancerHasChainedBeforeComeUpMoreOften() {
        val moves = listOf(FlowMove(1, TOPROCK), FlowMove(2, FOOTWORK), FlowMove(3, FOOTWORK))
        val randomizer = SetRandomizer(moves, chainedBefore = setOf(1L to 3L))
        val seconds = (0 until 3000)
            .map { randomizer.generate(2, Random(it)) }
            .filter { it.first().id == 1L }
            .map { it[1].id }
        assertTrue(seconds.count { it == 3L } > seconds.count { it == 2L } * 1.2)
    }
}
