package com.example.myapplication.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.myapplication.BreakingApp
import com.example.myapplication.data.dao.LibraryDao
import com.example.myapplication.data.model.CategoryScore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class LibraryViewModel(dao: LibraryDao) : ViewModel() {
    val categoryScores: StateFlow<List<CategoryScore>> = dao.observeCategoryScores()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as BreakingApp
                LibraryViewModel(app.database.libraryDao())
            }
        }
    }
}
