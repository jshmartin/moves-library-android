package com.example.myapplication.ui.sets

import kotlin.random.Random

/**
 * Where a move sits in a set. A set runs from standing down to the floor: toprock, a go-down, then
 * footwork and power, finishing on a freeze.
 */
enum class FlowStage {
    TOPROCK, GO_DOWN, FOOTWORK, POWER, FREEZE;

    companion object {
        /**
         * The stage of a category's moves, read from the category's name so renamed ones such as
         * "Top rock", "Uprock" or "Drops" still count. Null when the name doesn't say, as with "Tricks".
         */
        fun of(categoryName: String): FlowStage? {
            val key = categoryName.lowercase().filter { it.isLetterOrDigit() }
            return KEYWORDS.firstOrNull { (_, words) -> words.any { it in key } }?.first
        }

        // Checked in order, so "Downrock" is footwork rather than a go-down or toprock,
        // and "Spin downs" is a go-down rather than power.
        private val KEYWORDS = listOf(
            FREEZE to listOf("freeze", "stall"),
            FOOTWORK to listOf("footwork", "downrock", "floor", "thread", "legwork", "shuffle"),
            GO_DOWN to listOf("godown", "down", "drop", "entry", "entries"),
            POWER to listOf("power", "spin", "flare", "mill"),
            TOPROCK to listOf("toprock", "uprock", "rock", "standing"),
        )
    }
}

/**
 * How moves stack. Once on the floor a set never goes back to toprock or a go-down, because getting
 * up out of footwork, power or a freeze takes a move of its own. Toprock doesn't go straight into
 * power or a freeze either: you go down first.
 */
object SetFlow {
    private val NEXT: Map<FlowStage, Map<FlowStage, Double>> = mapOf(
        FlowStage.TOPROCK to mapOf(
            FlowStage.TOPROCK to 3.0,
            FlowStage.GO_DOWN to 6.0,
            FlowStage.FOOTWORK to 1.0,
        ),
        FlowStage.GO_DOWN to mapOf(
            FlowStage.FOOTWORK to 6.0,
            FlowStage.POWER to 2.0,
            FlowStage.FREEZE to 0.5,
        ),
        FlowStage.FOOTWORK to mapOf(
            FlowStage.FOOTWORK to 4.0,
            FlowStage.POWER to 2.0,
            FlowStage.FREEZE to 4.0,
        ),
        FlowStage.POWER to mapOf(
            FlowStage.POWER to 2.0,
            FlowStage.FOOTWORK to 2.0,
            FlowStage.FREEZE to 5.0,
        ),
        // A freeze usually ends a set, but you can come out of one into more footwork or power,
        // or go straight into another freeze.
        FlowStage.FREEZE to mapOf(
            FlowStage.FOOTWORK to 1.0,
            FlowStage.POWER to 0.5,
            FlowStage.FREEZE to 0.5,
        ),
    )

    /** Sets mostly open standing, sometimes straight from a go-down or footwork. */
    private val START = mapOf(
        FlowStage.TOPROCK to 6.0,
        FlowStage.GO_DOWN to 1.0,
        FlowStage.FOOTWORK to 1.0,
    )

    /** Sets mostly end on a freeze. They never stop halfway down. */
    private val END = mapOf(
        FlowStage.FREEZE to 6.0,
        FlowStage.POWER to 1.5,
        FlowStage.FOOTWORK to 1.0,
        FlowStage.TOPROCK to 0.3,
    )

    /** How often [to] follows [from]. Zero means never. */
    fun next(from: FlowStage, to: FlowStage): Double = NEXT.getValue(from)[to] ?: 0.0

    /** How often a set opens on [stage]. Zero means never. */
    fun start(stage: FlowStage): Double = START[stage] ?: 0.0

    /** How often a set ends on [stage]. Zero means never. */
    fun end(stage: FlowStage): Double = END[stage] ?: 0.0
}

/** A move the randomizer can use. */
data class FlowMove(val id: Long, val stage: FlowStage)

/**
 * Builds sets by stacking moves at random along [SetFlow]. Each stage is as likely as the flow says no
 * matter how many moves it has, and moves within a stage are equally likely. The same move never comes
 * twice in a row and rarely twice in one set. A step the dancer has already chained in one of their own
 * sets ([chainedBefore], as from and to move ids) is a little more likely, since they know it works.
 */
class SetRandomizer(
    moves: List<FlowMove>,
    private val chainedBefore: Set<Pair<Long, Long>> = emptySet(),
) {
    private val moves = moves.distinctBy { it.id }
    private val stageSizes = this.moves.groupingBy { it.stage }.eachCount()

    val isEmpty: Boolean get() = moves.isEmpty()

    /**
     * A set of [length] moves that opens and ends on stages sets open and end on. If the moves can't make
     * one that long, it's the longest they can make. If they can't open or end one properly, as when the
     * library only has power moves and freezes, it may open and stop anywhere. Empty when there are no moves.
     */
    fun generate(length: Int, random: Random = Random.Default): List<FlowMove> {
        for (size in length downTo 1) {
            (sample(size, SetFlow::start, SetFlow::end, random) ?: sample(size, { 1.0 }, { 1.0 }, random))
                ?.let { return it }
        }
        return emptyList()
    }

    /** [chain] with the move at [index] swapped for another that fits between its neighbours, or null if none fits. */
    fun swap(chain: List<FlowMove>, index: Int, random: Random = Random.Default): List<FlowMove>? {
        val pick = pickWeighted(swapWeights(chain, index), random) ?: return null
        return chain.toMutableList().also { it[index] = moves[pick] }
    }

    fun canSwap(chain: List<FlowMove>, index: Int): Boolean = swapWeights(chain, index).any { it > 0 }

    private fun swapWeights(chain: List<FlowMove>, index: Int): List<Double> {
        val current = chain[index]
        val previous = chain.getOrNull(index - 1)
        val next = chain.getOrNull(index + 1)
        val rest = chain.filterIndexed { i, _ -> i != index }
        return moves.map { move ->
            if (move.id == current.id || move.id == previous?.id || move.id == next?.id) return@map 0.0
            val into = if (next == null) SetFlow.end(move.stage) else SetFlow.next(move.stage, next.stage)
            val known = if (next != null && (move.id to next.id) in chainedBefore) CHAINED_BEFORE else 1.0
            weight(previous, move, rest) * into * known
        }
    }

    /**
     * A set of exactly [size] moves opening by [startWeight] and ending by [endWeight], or null if the
     * moves can't make one.
     *
     * Each pick looks ahead: a move is as likely as the weight of every way the set can go on from it
     * and end. Looking only one step ahead lets a set wander, say through four toprocks, into a spot
     * where it can't reach a freeze any more.
     */
    private fun sample(
        size: Int,
        startWeight: (FlowStage) -> Double,
        endWeight: (FlowStage) -> Double,
        random: Random,
    ): List<FlowMove>? {
        // ahead[r][i]: the total weight of the ways r more moves can follow moves[i] and end the set.
        val ahead = Array(size) { DoubleArray(moves.size) }
        moves.forEachIndexed { i, move -> ahead[0][i] = endWeight(move.stage) }
        for (r in 1 until size) {
            for (i in moves.indices) {
                ahead[r][i] = moves.indices.sumOf { j ->
                    if (j == i) 0.0 else weight(moves[i], moves[j], emptyList()) * ahead[r - 1][j]
                }
            }
        }

        val chain = mutableListOf<FlowMove>()
        for (position in 0 until size) {
            val previous = chain.lastOrNull()
            val weights = moves.mapIndexed { i, move ->
                if (move.id == previous?.id) 0.0
                else weight(previous, move, chain, startWeight) * ahead[size - 1 - position][i]
            }
            chain += moves[pickWeighted(weights, random) ?: return null]
        }
        return chain
    }

    /**
     * How likely [move] is to come after [previous] in a set that already has [used], or to open the set
     * by [startWeight] when there's no [previous].
     */
    private fun weight(
        previous: FlowMove?,
        move: FlowMove,
        used: List<FlowMove>,
        startWeight: (FlowStage) -> Double = SetFlow::start,
    ): Double {
        val flow = if (previous == null) startWeight(move.stage) else SetFlow.next(previous.stage, move.stage)
        if (flow == 0.0) return 0.0
        var weight = flow / stageSizes.getValue(move.stage)
        if (used.any { it.id == move.id }) weight *= REPEAT
        if (previous != null && (previous.id to move.id) in chainedBefore) weight *= CHAINED_BEFORE
        return weight
    }

    private fun pickWeighted(weights: List<Double>, random: Random): Int? {
        val total = weights.sum()
        if (total <= 0.0) return null
        var roll = random.nextDouble() * total
        weights.forEachIndexed { i, weight ->
            if (weight <= 0.0) return@forEachIndexed
            roll -= weight
            if (roll < 0) return i
        }
        return weights.indexOfLast { it > 0.0 }
    }

    private companion object {
        const val REPEAT = 0.15
        const val CHAINED_BEFORE = 1.5
    }
}
