package com.example.myapplication.ui.sets

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.myapplication.data.dao.ChainDao
import com.example.myapplication.data.dao.LibraryDao
import com.example.myapplication.data.model.Category
import com.example.myapplication.data.model.Move
import com.example.myapplication.data.model.MoveStatus
import com.example.myapplication.ui.breakingApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.random.Random

/** One move in the randomized set. [canSwap] is false when no other move fits between its neighbours. */
data class RandomStep(val move: Move, val categoryName: String, val canSwap: Boolean)

/** Stacks library moves into a random set (see [SetRandomizer]) that the dancer can reshuffle and save. */
class RandomizerViewModel(
    private val chainDao: ChainDao,
    private val libraryDao: LibraryDao,
    private val random: Random = Random.Default,
) : ViewModel() {
    var loaded by mutableStateOf(false)
        private set
    var length by mutableIntStateOf(DEFAULT_LENGTH)
        private set
    var skipLearning by mutableStateOf(false)
        private set
    var steps by mutableStateOf<List<RandomStep>>(emptyList())
        private set

    /** Categories whose moves are left out because their names don't say where in a set they go. */
    var unplacedCategories by mutableStateOf<List<String>>(emptyList())
        private set

    /** Why there's no set to show, or null when there is one. */
    var emptyReason by mutableStateOf<String?>(null)
        private set

    private var library: List<Move> = emptyList()
    private var categories: Map<Long, Category> = emptyMap()
    private var chainedBefore: Set<Pair<Long, Long>> = emptySet()
    private var randomizer = SetRandomizer(emptyList())
    private var chain: List<FlowMove> = emptyList()

    init {
        viewModelScope.launch {
            library = libraryDao.observeMoves().first()
            categories = libraryDao.observeCategories().first().associateBy { it.id }
            chainedBefore = chainDao.observeTransitions().first().map { it.fromMoveId to it.toMoveId }.toSet()
            rebuild()
            loaded = true
        }
    }

    fun shuffle() = show(randomizer.generate(length, random))

    fun swap(index: Int) {
        randomizer.swap(chain, index, random)?.let(::show)
    }

    fun changeLength(value: Int) {
        length = value.coerceIn(LENGTHS)
        shuffle()
    }

    fun toggleSkipLearning() {
        skipLearning = !skipLearning
        rebuild()
    }

    /** The editor route for saving this set, named after its first and last moves. */
    fun draftRoute(): String? {
        val first = steps.firstOrNull()?.move ?: return null
        val last = steps.last().move
        val name = if (steps.size == 1) first.name else "${first.name} to ${last.name}"
        return SetRoutes.draft(name, notes = "", moveIds = steps.map { it.move.id })
    }

    private fun rebuild() {
        val stages = categories.mapValues { (_, category) -> FlowStage.of(category.name) }
        val pool = library.filter { !skipLearning || it.status != MoveStatus.LEARNING }
        unplacedCategories = pool
            .filter { stages[it.categoryId] == null }
            .mapNotNull { categories[it.categoryId] }
            .distinct()
            .sortedBy { it.sortOrder }
            .map { it.name }
        randomizer = SetRandomizer(
            pool.mapNotNull { move -> stages[move.categoryId]?.let { FlowMove(move.id, it) } },
            chainedBefore,
        )
        emptyReason = when {
            library.isEmpty() -> "Add moves in the Library tab first."
            pool.isEmpty() -> "Every move is still Learning. Turn off \"Skip Learning moves\" to use them."
            randomizer.isEmpty -> "None of your moves are in a category the randomizer can place."
            else -> null
        }
        shuffle()
    }

    private fun show(newChain: List<FlowMove>) {
        chain = newChain
        val moves = library.associateBy { it.id }
        steps = newChain.mapIndexed { index, flowMove ->
            val move = moves.getValue(flowMove.id)
            RandomStep(move, categories[move.categoryId]?.name.orEmpty(), randomizer.canSwap(newChain, index))
        }
    }

    companion object {
        val LENGTHS = 2..10
        const val DEFAULT_LENGTH = 5

        val Factory = viewModelFactory {
            initializer {
                val database = breakingApp().database
                RandomizerViewModel(database.chainDao(), database.libraryDao())
            }
        }
    }
}
