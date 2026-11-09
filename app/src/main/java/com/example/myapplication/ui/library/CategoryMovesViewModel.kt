package com.example.myapplication.ui.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.myapplication.data.dao.LibraryDao
import com.example.myapplication.data.model.Move
import com.example.myapplication.ui.breakingApp
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class MoveRow(val move: Move, val depth: Int)

data class CategoryMovesUiState(
    val categoryName: String = "",
    val rows: List<MoveRow> = emptyList(),
)

class CategoryMovesViewModel(savedStateHandle: SavedStateHandle, dao: LibraryDao) : ViewModel() {
    val categoryId: Long = checkNotNull(savedStateHandle[LibraryRoutes.ARG_CATEGORY_ID])

    val uiState: StateFlow<CategoryMovesUiState> = combine(
        dao.observeCategory(categoryId),
        dao.observeMovesInCategory(categoryId),
    ) { category, moves ->
        CategoryMovesUiState(
            categoryName = category?.name.orEmpty(),
            rows = groupWithVariations(moves),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoryMovesUiState())

    companion object {
        val Factory = viewModelFactory {
            initializer {
                CategoryMovesViewModel(createSavedStateHandle(), breakingApp().database.libraryDao())
            }
        }
    }
}

/**
 * Orders [moves] so each variation follows its base move, indented by [MoveRow.depth].
 * Moves whose base is in another category are shown at the top level. Input order is kept
 * among siblings.
 */
fun groupWithVariations(moves: List<Move>): List<MoveRow> {
    val ids = moves.map { it.id }.toSet()
    val children = moves.filter { it.parentMoveId in ids }.groupBy { it.parentMoveId }
    val visited = mutableSetOf<Long>()
    val rows = mutableListOf<MoveRow>()

    fun add(move: Move, depth: Int) {
        if (!visited.add(move.id)) return
        rows += MoveRow(move, depth)
        children[move.id].orEmpty().forEach { add(it, depth + 1) }
    }

    moves.filter { it.parentMoveId !in ids }.forEach { add(it, 0) }
    // Anything left is part of a parent cycle; show it rather than lose it.
    moves.forEach { add(it, 0) }
    return rows
}
