package com.example.myapplication.ui.videos

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.myapplication.data.VideoDao
import com.example.myapplication.data.VideoFiles
import com.example.myapplication.data.dao.ChainDao
import com.example.myapplication.ui.breakingApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PlayerClip(val uri: String, val title: String)

/** Plays one video, or a set as a combo: each move's top video in chain order. */
class VideoPlayerViewModel(
    savedStateHandle: SavedStateHandle,
    context: Context,
    private val videoDao: VideoDao,
    private val chainDao: ChainDao,
) : ViewModel() {
    private val resolver = context.applicationContext.contentResolver
    private val videoId: Long = savedStateHandle[VideoRoutes.ARG_VIDEO_ID] ?: VideoRoutes.NO_ID
    private val setId: Long = savedStateHandle[VideoRoutes.ARG_SET_ID] ?: VideoRoutes.NO_ID

    var loaded by mutableStateOf(false)
        private set
    var clips by mutableStateOf<List<PlayerClip>>(emptyList())
        private set

    /** Names of the moves left out of the combo because they have no playable video. */
    var skipped by mutableStateOf<List<String>>(emptyList())
        private set

    init {
        viewModelScope.launch {
            val slots = if (setId != VideoRoutes.NO_ID) {
                chainDao.observeComboSteps(setId).first().map { it.moveName to it.videoUri }
            } else {
                listOfNotNull(videoDao.getById(videoId)).map { it.displayName to it.uri }
            }
            val playable = withContext(Dispatchers.IO) {
                slots.map { (title, uri) -> title to uri?.takeIf { VideoFiles.isAvailable(resolver, it.toUri()) } }
            }
            clips = playable.mapNotNull { (title, uri) -> uri?.let { PlayerClip(it, title) } }
            skipped = playable.filter { it.second == null }.map { it.first }.distinct()
            loaded = true
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = breakingApp()
                VideoPlayerViewModel(
                    createSavedStateHandle(),
                    app,
                    app.database.videoDao(),
                    app.database.chainDao(),
                )
            }
        }
    }
}
