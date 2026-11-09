package com.example.myapplication.ui.videos

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.myapplication.data.VideoDao
import com.example.myapplication.data.VideoRepository
import com.example.myapplication.data.dao.ChainDao
import com.example.myapplication.ui.breakingApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Plays one video, or a set as a combo: each move's top video in chain order. */
class VideoPlayerViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: VideoRepository,
    private val videoDao: VideoDao,
    private val chainDao: ChainDao,
) : ViewModel() {
    private val videoId: Long = savedStateHandle[VideoRoutes.ARG_VIDEO_ID] ?: VideoRoutes.NO_ID
    private val setId: Long = savedStateHandle[VideoRoutes.ARG_SET_ID] ?: VideoRoutes.NO_ID

    var loaded by mutableStateOf(false)
        private set
    var clips by mutableStateOf<List<PlayerClip>>(emptyList())
        private set

    /** What the viewer should know about videos that were left out or swapped. */
    var notices by mutableStateOf<List<String>>(emptyList())
        private set

    init {
        viewModelScope.launch {
            val missing = repository.checkAvailability(slots().flatMap { it.videos }.distinct()).missing
            // Read again: the check may have relinked moved files, which changes what to play.
            val plan = planPlayback(slots(), missing)
            clips = plan.clips
            notices = plan.notices
            loaded = true
        }
    }

    private suspend fun slots(): List<PlayerSlot> =
        if (setId != VideoRoutes.NO_ID) comboSlots() else listOfNotNull(singleSlot())

    private suspend fun comboSlots(): List<PlayerSlot> {
        val videos = videoDao.observeForSetMoves(setId).first().groupBy { it.moveId }
        return chainDao.observeComboSteps(setId).first().map { PlayerSlot(it.moveName, videos[it.moveId].orEmpty()) }
    }

    /** The video asked for, backed by whatever else is filed with it in case its file has gone. */
    private suspend fun singleSlot(): PlayerSlot? {
        val video = videoDao.getById(videoId) ?: return null
        val filedWith = videoDao.observeLinked(video.moveId, video.setId).first()
        return PlayerSlot(video.displayName, listOf(video) + (filedWith - video))
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = breakingApp()
                VideoPlayerViewModel(
                    createSavedStateHandle(),
                    VideoRepository(app, app.database.videoDao()),
                    app.database.videoDao(),
                    app.database.chainDao(),
                )
            }
        }
    }
}
