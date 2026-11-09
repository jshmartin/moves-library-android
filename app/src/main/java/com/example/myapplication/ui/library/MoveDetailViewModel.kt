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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MoveDetailUiState(
    val loading: Boolean = true,
    val move: Move? = null,
    val categoryName: String = "",
    val parent: Move? = null,
    val variations: List<Move> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class MoveDetailViewModel(savedStateHandle: SavedStateHandle, private val dao: LibraryDao) : ViewModel() {
    private val moveId: Long = checkNotNull(savedStateHandle[LibraryRoutes.ARG_MOVE_ID])

    val uiState: StateFlow<MoveDetailUiState> = dao.observeMove(moveId)
        .flatMapLatest { move ->
            if (move == null) {
                flowOf(MoveDetailUiState(loading = false))
            } else {
                combine(
                    dao.observeCategory(move.categoryId),
                    move.parentMoveId?.let { dao.observeMove(it) } ?: flowOf(null),
                    dao.observeVariations(move.id),
                ) { category, parent, variations ->
                    MoveDetailUiState(
                        loading = false,
                        move = move,
                        categoryName = category?.name.orEmpty(),
                        parent = parent,
                        variations = variations,
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MoveDetailUiState())

    /** Deletes the move; [uiState] then reports no move, which the screen treats as "go back". */
    fun delete() {
        val move = uiState.value.move ?: return
        viewModelScope.launch { dao.deleteMove(move) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                MoveDetailViewModel(createSavedStateHandle(), breakingApp().database.libraryDao())
            }
        }
    }
}
