package com.example.myapplication.ui.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.myapplication.data.dao.LibraryDao
import com.example.myapplication.data.model.MoveWithScore
import com.example.myapplication.ui.breakingApp
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class MoveRow(val item: MoveWithScore, val depth: Int)

data class CategoryMovesUiState(
    val categoryName: String = "",
    val rows: List<MoveRow> = emptyList(),
    val averageScore: Double? = null,
)

class CategoryMovesViewModel(savedStateHandle: SavedStateHandle, dao: LibraryDao) : ViewModel() {
    val categoryId: Long = checkNotNull(savedStateHandle[LibraryRoutes.ARG_CATEGORY_ID])

    val uiState: StateFlow<CategoryMovesUiState> = combine(
        dao.observeCategory(categoryId),
        dao.observeMovesWithScore(categoryId),
    ) { category, moves ->
        CategoryMovesUiState(
            categoryName = category?.name.orEmpty(),
            rows = groupWithVariations(moves),
            averageScore = moves.mapNotNull { it.latestScore }.takeIf { it.isNotEmpty() }?.average(),
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
fun groupWithVariations(moves: List<MoveWithScore>): List<MoveRow> {
    val ids = moves.map { it.move.id }.toSet()
    val children = moves.filter { it.move.parentMoveId in ids }.groupBy { it.move.parentMoveId }
    val visited = mutableSetOf<Long>()
    val rows = mutableListOf<MoveRow>()

    fun add(item: MoveWithScore, depth: Int) {
        if (!visited.add(item.move.id)) return
        rows += MoveRow(item, depth)
        children[item.move.id].orEmpty().forEach { add(it, depth + 1) }
    }

    moves.filter { it.move.parentMoveId !in ids }.forEach { add(it, 0) }
    // Anything left is part of a parent cycle; show it rather than lose it.
    moves.forEach { add(it, 0) }
    return rows
}
