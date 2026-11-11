package com.example.myapplication.data

import com.example.myapplication.data.model.Move

/**
 * A common combo of foundation moves that the dancer can add to their sets. [moves] are names from
 * [StarterMoves], in order.
 */
data class BasicSet(
    val name: String,
    val notes: String,
    val moves: List<String>,
)

/** A basic set with the library moves it would use. */
data class BasicSetOption(val set: BasicSet, val moves: List<Move>)

/**
 * Basic sets built from the starter moves. They aren't stored in the database: the Sets tab offers each
 * one whose moves are all in the library, until the dancer adds it.
 */
object BasicSets {
    val ALL: List<BasicSet> = listOf(
        BasicSet(
            "Foundation set",
            "The classic first set: toprock, go down, footwork, freeze.",
            listOf("Indian step", "Knee drop", "6-step", "Baby freeze"),
        ),
        BasicSet(
            "Spin down to chair",
            "The spin down lands you on your hands, ready to go straight into the 6-step.",
            listOf("Crossover", "Spin down", "6-step", "3-step", "Chair freeze"),
        ),
        BasicSet(
            "Footwork flow",
            "All on the floor. Keep moving from one step to the next without pausing, then hit the freeze.",
            listOf("6-step", "CC's", "Kick-out", "Baby freeze"),
        ),
        BasicSet(
            "Sweep to turtle",
            "Short footwork off a sweep. Snap the kick-out back under you and tuck your elbows in for the turtle.",
            listOf("Side step", "Sweep drop", "2-step", "Kick-out", "Turtle freeze"),
        ),
        BasicSet(
            "Corkscrew spins",
            "Twist down, then stay low and spin. Plant a hand out of the coffee grinder and sit into the chair.",
            listOf("Salsa step", "Corkscrew", "Zulu spin", "Coffee grinder", "Chair freeze"),
        ),
        BasicSet(
            "Freeze to freeze",
            "Go from the baby freeze to the turtle without putting your feet down.",
            listOf("3-step", "Baby freeze", "Turtle freeze"),
        ),
        BasicSet(
            "Backspin to chair",
            "Where power starts: the windmill grew out of backspins that ran into chair freezes.",
            listOf("Indian step", "Coin drop", "Backspin", "Chair freeze"),
        ),
        BasicSet(
            "Backspin into mills",
            "The windmill starts from the backspin position, so a backspin is an easy way in.",
            listOf("6-step", "Backspin", "Windmill", "Baby freeze"),
        ),
        BasicSet(
            "Mills to baby",
            "Coin drop into windmills, then catch a baby freeze to stop the spin.",
            listOf("Crossover", "Coin drop", "Windmill", "Baby freeze"),
        ),
    )

    /** Each starter move's name and other names, so a library that had a move before the starter moves still matches. */
    private val starterKeys: Map<String, Set<String>> = StarterMoves.ALL.associate { it.name to it.keys }

    /**
     * The library moves for [set], in order, or null if any is missing. Names match the way
     * [StarterMoves.normalize] does, under the move's name or any of its other names. When several moves
     * match, the base move added first wins.
     */
    fun resolve(set: BasicSet, library: List<Move>): List<Move>? {
        val byKey = library
            .sortedWith(compareBy<Move> { it.parentMoveId != null }.thenBy { it.id })
            .groupBy { StarterMoves.normalize(it.name) }
        return set.moves.map { name ->
            val keys = starterKeys[name] ?: setOf(StarterMoves.normalize(name))
            keys.firstNotNullOfOrNull { byKey[it]?.first() } ?: return null
        }
    }

    /**
     * The basic sets to offer: those whose moves are all in [library], leaving out any the dancer already
     * has, either under the same name ([setNames]) or as the same chain of moves ([setChains], move ids in order).
     */
    fun available(library: List<Move>, setNames: List<String>, setChains: Collection<List<Long>>): List<BasicSetOption> {
        val names = setNames.map(StarterMoves::normalize).toSet()
        val chains = setChains.toSet()
        return ALL.mapNotNull { set ->
            val moves = resolve(set, library) ?: return@mapNotNull null
            if (StarterMoves.normalize(set.name) in names || moves.map { it.id } in chains) null
            else BasicSetOption(set, moves)
        }
    }
}
