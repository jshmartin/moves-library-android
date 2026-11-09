package com.example.myapplication.ui.videos

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.BreakingApp
import com.example.myapplication.data.Video
import com.example.myapplication.data.VideoLinkTarget
import com.example.myapplication.data.VideoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VideoItem(val video: Video, val missing: Boolean)

data class VideoLibraryUiState(
    val items: List<VideoItem> = emptyList(),
    /** Everywhere each video is linked, by video id. */
    val links: Map<Long, List<VideoLinkTarget>> = emptyMap(),
    /** Everything a video could be linked to. */
    val linkTargets: List<VideoLinkTarget> = emptyList(),
    val searching: Boolean = false,
    val pendingRelink: PendingRelink? = null,
    val message: String? = null,
) {
    val missingCount get() = items.count { it.missing }
}

class VideoLibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = VideoRepository(application, (application as BreakingApp).database.videoDao())

    private val extra = MutableStateFlow(VideoLibraryUiState())
    private val recovery = VideoRecovery(viewModelScope, repository, repository.videos, ::showMessage)

    val uiState: StateFlow<VideoLibraryUiState> =
        combine(
            repository.videos,
            repository.linksByVideo,
            repository.linkTargets,
            recovery.state,
            extra,
        ) { videos, links, targets, recovered, extra ->
            extra.copy(
                items = videos.map { VideoItem(it, it.id in recovered.missingIds) },
                links = links,
                linkTargets = targets,
                pendingRelink = recovered.pendingRelink,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VideoLibraryUiState())

    /** Files can be moved while the app is in the background, so re-check whenever the screen resumes. */
    fun refreshAvailability() = recovery.refresh()

    /** The file can vanish after the last check, so look again right before handing it to a player. */
    fun open(video: Video, startPlayer: () -> Boolean) {
        viewModelScope.launch {
            if (repository.isAvailable(video)) {
                if (!startPlayer()) showMessage("Couldn't open that video")
            } else {
                refreshAvailability()
                showMessage("Can't find that file. It may have been moved or deleted.")
            }
        }
    }

    fun add(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val result = repository.add(uris)
            showMessage(
                listOfNotNull(
                    result.added.takeIf { it > 0 }?.let { "Added $it" },
                    result.relinked.takeIf { it > 0 }?.let { "Relinked $it moved" },
                    result.duplicates.takeIf { it > 0 }?.let { "$it already in library" },
                    result.failed.takeIf { it > 0 }?.let { "$it couldn't be read" },
                ).joinToString(" · ")
            )
        }
    }

    fun relink(video: Video, uri: Uri, force: Boolean = false) = recovery.relink(video, uri, force)

    fun dismissPendingRelink() = recovery.dismissPendingRelink()

    fun searchDevice() {
        val missing = uiState.value.items.filter { it.missing }.map { it.video }
        if (missing.isEmpty()) return
        extra.update { it.copy(searching = true) }
        viewModelScope.launch {
            val found = runCatching { repository.searchDevice(missing).size }.getOrDefault(0)
            extra.update { it.copy(searching = false) }
            refreshAvailability()
            showMessage(
                if (found == 0) "No matching files found. Try Locate to pick the file yourself."
                else "Found and relinked $found of ${missing.size}"
            )
        }
    }

    fun remove(video: Video) {
        viewModelScope.launch { repository.remove(video) }
    }

    fun link(video: Video, target: VideoLinkTarget) {
        viewModelScope.launch {
            repository.link(video.id, target.owner)
            showMessage("Linked to ${target.label}")
        }
    }

    fun showMessage(message: String) = extra.update { it.copy(message = message) }

    fun messageShown() = extra.update { it.copy(message = null) }
}
