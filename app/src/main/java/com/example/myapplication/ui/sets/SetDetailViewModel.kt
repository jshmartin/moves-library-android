package com.example.myapplication.ui.sets

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.myapplication.data.dao.ChainDao
import com.example.myapplication.data.dao.ComboStep
import com.example.myapplication.data.model.DanceSet
import com.example.myapplication.ui.breakingApp
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SetDetailUiState(
    val loading: Boolean = true,
    val set: DanceSet? = null,
    val steps: List<ComboStep> = emptyList(),
) {
    val canPlayCombo: Boolean get() = steps.any { it.videoUri != null }
}

class SetDetailViewModel(savedStateHandle: SavedStateHandle, private val dao: ChainDao) : ViewModel() {
    private val setId: Long = checkNotNull(savedStateHandle[SetRoutes.ARG_SET_ID])

    val uiState: StateFlow<SetDetailUiState> =
        combine(dao.observeSet(setId), dao.observeComboSteps(setId)) { set, steps ->
            SetDetailUiState(loading = false, set = set, steps = steps)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SetDetailUiState())

    fun delete() {
        val set = uiState.value.set ?: return
        viewModelScope.launch { dao.deleteSet(set) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                SetDetailViewModel(createSavedStateHandle(), breakingApp().database.chainDao())
            }
        }
    }
}
