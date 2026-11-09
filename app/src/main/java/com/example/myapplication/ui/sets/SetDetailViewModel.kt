package com.example.myapplication.ui.sets

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.myapplication.data.VideoDao
import com.example.myapplication.data.VideoRepository
import com.example.myapplication.data.dao.ChainDao
import com.example.myapplication.data.dao.ComboStep
import com.example.myapplication.data.model.DanceSet
import com.example.myapplication.ui.breakingApp
import com.example.myapplication.ui.videos.PlayerSlot
import com.example.myapplication.ui.videos.SlotStatus
import com.example.myapplication.ui.videos.VideoRecovery
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A move in the set's chain, and whether the combo has a video it can play for it. */
data class SetStep(val step: ComboStep, val status: SlotStatus)

data class SetDetailUiState(
    val loading: Boolean = true,
    val set: DanceSet? = null,
    val steps: List<SetStep> = emptyList(),
) {
    val canPlayCombo: Boolean
        get() = steps.any { it.status == SlotStatus.TOP_VIDEO || it.status == SlotStatus.OLDER_VIDEO }
}

class SetDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val dao: ChainDao,
    videoDao: VideoDao,
    repository: VideoRepository,
) : ViewModel() {
    private val setId: Long = checkNotNull(savedStateHandle[SetRoutes.ARG_SET_ID])
    private val moveVideos = videoDao.observeForSetMoves(setId)
    private val recovery = VideoRecovery(viewModelScope, repository, moveVideos)

    val uiState: StateFlow<SetDetailUiState> =
        combine(
            dao.observeSet(setId),
            dao.observeComboSteps(setId),
            moveVideos,
            recovery.state,
        ) { set, steps, videos, recovered ->
            val videosByMove = videos.groupBy { it.moveId }
            SetDetailUiState(
                loading = false,
                set = set,
                steps = steps.map { step ->
                    val slot = PlayerSlot(step.moveName, videosByMove[step.moveId].orEmpty())
                    SetStep(step, slot.status(recovered.missingIds))
                },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SetDetailUiState())

    fun refreshAvailability() = recovery.refresh()

    fun delete() {
        val set = uiState.value.set ?: return
        viewModelScope.launch { dao.deleteSet(set) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = breakingApp()
                SetDetailViewModel(
                    createSavedStateHandle(),
                    app.database.chainDao(),
                    app.database.videoDao(),
                    VideoRepository(app, app.database.videoDao()),
                )
            }
        }
    }
}
