package com.example.myapplication.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.myapplication.data.dao.LibraryDao
import com.example.myapplication.data.model.Category
import com.example.myapplication.ui.breakingApp
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CategoryWithCount(val category: Category, val moveCount: Int)

class ManageCategoriesViewModel(private val dao: LibraryDao) : ViewModel() {
    val categories: StateFlow<List<CategoryWithCount>> = combine(
        dao.observeCategories(),
        dao.observeCategoryScores(),
    ) { categories, scores ->
        val counts = scores.associate { it.categoryId to it.totalMoves }
        categories.map { CategoryWithCount(it, counts[it.id] ?: 0) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun add(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val nextOrder = (categories.value.maxOfOrNull { it.category.sortOrder } ?: -1) + 1
        viewModelScope.launch { dao.insertCategory(Category(name = trimmed, sortOrder = nextOrder)) }
    }

    fun rename(category: Category, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { dao.updateCategory(category.copy(name = trimmed)) }
    }

    /** Moves the category at [index] up (-1) or down (+1) and renumbers every sort order. */
    fun move(index: Int, offset: Int) {
        val list = categories.value.map { it.category }.toMutableList()
        val target = index + offset
        if (index !in list.indices || target !in list.indices) return
        list.add(target, list.removeAt(index))
        viewModelScope.launch {
            dao.updateCategories(list.mapIndexed { order, category -> category.copy(sortOrder = order) })
        }
    }

    /** Only empty categories can be deleted, so no move is left without one. */
    fun delete(item: CategoryWithCount) {
        if (item.moveCount > 0) return
        viewModelScope.launch { dao.deleteCategory(item.category) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { ManageCategoriesViewModel(breakingApp().database.libraryDao()) }
        }
    }
}
