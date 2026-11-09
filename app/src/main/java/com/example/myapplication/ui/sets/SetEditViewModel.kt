package com.example.myapplication.ui.sets

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.myapplication.data.dao.ChainDao
import com.example.myapplication.data.dao.LibraryDao
import com.example.myapplication.data.model.DanceSet
import com.example.myapplication.data.model.Move
import com.example.myapplication.ui.breakingApp
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Creates a new set, or edits one when the route carries a set id. */
class SetEditViewModel(
    savedStateHandle: SavedStateHandle,
    private val chainDao: ChainDao,
    private val libraryDao: LibraryDao,
) : ViewModel() {
    private val setId: Long = savedStateHandle[SetRoutes.ARG_SET_ID] ?: SetRoutes.NO_ID
    val isNew: Boolean get() = setId == SetRoutes.NO_ID
    private var original: DanceSet? = null

    var loaded by mutableStateOf(false)
        private set
    var name by mutableStateOf("")
    var notes by mutableStateOf("")

    /** The chain being edited, in order. The same move may appear more than once. */
    val chain = mutableStateListOf<Move>()

    val allMoves: StateFlow<List<Move>> = libraryDao.observeMoves()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            if (!isNew) {
                chainDao.getSet(setId)?.let { set ->
                    original = set
                    name = set.name
                    notes = set.notes
                    val moves = libraryDao.observeMoves().first().associateBy { it.id }
                    chain.addAll(chainDao.observeSetChain(setId).first().mapNotNull { moves[it.moveId] })
                }
            }
            loaded = true
        }
    }

    fun addMove(move: Move) {
        chain.add(move)
    }

    fun removeAt(index: Int) {
        chain.removeAt(index)
    }

    fun moveUp(index: Int) {
        if (index > 0) chain.add(index - 1, chain.removeAt(index))
    }

    fun moveDown(index: Int) {
        if (index < chain.lastIndex) chain.add(index + 1, chain.removeAt(index))
    }

    val canSave: Boolean get() = loaded && name.isNotBlank()

    /** Saves and reports the set's id. */
    fun save(onSaved: (Long) -> Unit) {
        if (!canSave) return
        viewModelScope.launch {
            val set = (original ?: DanceSet(name = "")).copy(name = name.trim(), notes = notes.trim())
            onSaved(chainDao.saveSet(set, chain.map { it.id }))
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val database = breakingApp().database
                SetEditViewModel(createSavedStateHandle(), database.chainDao(), database.libraryDao())
            }
        }
    }
}
