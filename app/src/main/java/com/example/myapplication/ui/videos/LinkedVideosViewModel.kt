package com.example.myapplication.ui.videos

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.myapplication.data.Video
import com.example.myapplication.data.VideoLinkTarget
import com.example.myapplication.data.VideoOwner
import com.example.myapplication.data.VideoRepository
import com.example.myapplication.data.VideoWithOwner
import com.example.myapplication.ui.breakingApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LinkedVideosUiState(
    /** This owner's videos, top video first. */
    val items: List<VideoItem> = emptyList(),
    /** Library videos that could be linked here, including ones linked elsewhere. */
    val candidates: List<VideoWithOwner> = emptyList(),
    /** Everywhere each video is linked, by video id. */
    val links: Map<Long, List<VideoLinkTarget>> = emptyMap(),
    /** Everything a video here could also be linked to. */
    val linkTargets: List<VideoLinkTarget> = emptyList(),
    val message: String? = null,
)

/** The videos linked to one move, set, practice or logged session. */
class LinkedVideosViewModel(
    private val repository: VideoRepository,
    private val owner: VideoOwner,
) : ViewModel() {
    private val linked = repository.linkedTo(owner)
    private val missingIds = MutableStateFlow<Set<Long>>(emptySet())
    private val message = MutableStateFlow<String?>(null)
    private val status = combine(missingIds, message, ::Pair)

    val uiState: StateFlow<LinkedVideosUiState> =
        combine(
            linked,
            repository.videosWithOwner,
            repository.linksByVideo,
            repository.linkTargets,
            status,
        ) { linked, all, links, targets, (missing, message) ->
            val linkedIds = linked.mapTo(mutableSetOf()) { it.id }
            LinkedVideosUiState(
                items = linked.map { VideoItem(it, it.id in missing) },
                candidates = all.filter { it.video.id !in linkedIds },
                links = links,
                linkTargets = targets,
                message = message,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LinkedVideosUiState())

    init {
        linked.onEach { missingIds.value = repository.findMissing(it) }.launchIn(viewModelScope)
    }

    fun refreshAvailability() {
        viewModelScope.launch { missingIds.value = repository.findMissing(linked.first()) }
    }

    fun addFromDevice(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val result = repository.add(uris, owner)
            val linkedCount = result.added + result.relinked + result.duplicates
            message.value = listOfNotNull(
                linkedCount.takeIf { it > 0 }?.let { "Added $it" },
                result.failed.takeIf { it > 0 }?.let { "$it couldn't be read" },
            ).joinToString(" · ")
        }
    }

    fun link(video: Video) {
        viewModelScope.launch { repository.link(video.id, owner) }
    }

    /** Links [video] somewhere other than this owner. */
    fun linkTo(video: Video, target: VideoLinkTarget) {
        viewModelScope.launch {
            repository.link(video.id, target.owner)
            message.value = "Linked to ${target.label}"
        }
    }

    fun unlink(video: Video) {
        viewModelScope.launch { repository.unlink(video.id, owner) }
    }

    fun makeTop(video: Video) {
        viewModelScope.launch { repository.makeTop(video.id, owner) }
    }

    fun messageShown() {
        message.value = null
    }

    companion object {
        fun factory(owner: VideoOwner) = viewModelFactory {
            initializer {
                val app = breakingApp()
                LinkedVideosViewModel(VideoRepository(app, app.database.videoDao()), owner)
            }
        }
    }
}
