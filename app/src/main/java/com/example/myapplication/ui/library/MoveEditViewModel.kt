package com.example.myapplication.ui.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.myapplication.data.dao.LibraryDao
import com.example.myapplication.data.model.Category
import com.example.myapplication.data.model.Move
import com.example.myapplication.data.model.MoveStatus
import com.example.myapplication.ui.breakingApp
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Adds a new move, or edits one when the route carries a move id. */
class MoveEditViewModel(savedStateHandle: SavedStateHandle, private val dao: LibraryDao) : ViewModel() {
    private val moveId: Long = savedStateHandle[LibraryRoutes.ARG_MOVE_ID] ?: NO_ID
    val isNew: Boolean get() = moveId == NO_ID
    private var original: Move? = null

    var loaded by mutableStateOf(false)
        private set
    var name by mutableStateOf("")
    var categoryId by mutableStateOf(savedStateHandle[LibraryRoutes.ARG_CATEGORY_ID] ?: NO_ID)
        private set
    var status by mutableStateOf(MoveStatus.LEARNING)
    var parentMoveId by mutableStateOf<Long?>(null)
    var notes by mutableStateOf("")

    val categories: StateFlow<List<Category>> = dao.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val allMoves: StateFlow<List<Move>> = dao.observeMoves()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            if (!isNew) {
                dao.getMove(moveId)?.let { move ->
                    original = move
                    name = move.name
                    categoryId = move.categoryId
                    status = move.status
                    parentMoveId = move.parentMoveId
                    notes = move.notes
                }
            } else if (categoryId == NO_ID) {
                categoryId = dao.observeCategories().first().firstOrNull()?.id ?: NO_ID
            }
            loaded = true
        }
    }

    fun selectCategory(id: Long) {
        if (id != categoryId) parentMoveId = null
        categoryId = id
    }

    /** Moves this one can be a variation of: base moves in the same category, other than itself. */
    fun parentOptions(moves: List<Move>): List<Move> =
        moves.filter { it.categoryId == categoryId && it.parentMoveId == null && it.id != moveId }

    /** A move that has variations stays a base move, so variations are only one level deep. */
    fun hasVariations(moves: List<Move>): Boolean = !isNew && moves.any { it.parentMoveId == moveId }

    val canSave: Boolean get() = loaded && name.isNotBlank() && categoryId != NO_ID

    /** Saves and reports the move's id. */
    fun save(onSaved: (Long) -> Unit) {
        if (!canSave) return
        viewModelScope.launch {
            val existing = original
            val id = if (existing == null) {
                dao.insertMove(
                    Move(
                        name = name.trim(),
                        categoryId = categoryId,
                        parentMoveId = parentMoveId,
                        status = status,
                        notes = notes.trim(),
                    )
                )
            } else {
                dao.updateMove(
                    existing.copy(
                        name = name.trim(),
                        categoryId = categoryId,
                        parentMoveId = parentMoveId,
                        status = status,
                        notes = notes.trim(),
                    )
                )
                existing.id
            }
            onSaved(id)
        }
    }

    companion object {
        const val NO_ID = -1L

        val Factory = viewModelFactory {
            initializer {
                MoveEditViewModel(createSavedStateHandle(), breakingApp().database.libraryDao())
            }
        }
    }
}
