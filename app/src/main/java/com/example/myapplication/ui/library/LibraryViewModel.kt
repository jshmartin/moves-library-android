package com.example.myapplication.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.myapplication.data.dao.LibraryDao
import com.example.myapplication.data.model.CategoryScore
import com.example.myapplication.ui.breakingApp
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class LibraryViewModel(dao: LibraryDao) : ViewModel() {
    val categoryScores: StateFlow<List<CategoryScore>> = dao.observeCategoryScores()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    companion object {
        val Factory = viewModelFactory {
            initializer { LibraryViewModel(breakingApp().database.libraryDao()) }
        }
    }
}
