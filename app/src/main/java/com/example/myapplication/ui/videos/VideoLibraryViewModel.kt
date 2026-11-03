package com.example.myapplication.ui.videos

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.AppDatabase
import com.example.myapplication.data.Video
import com.example.myapplication.data.VideoRepository
import com.example.myapplication.data.VideoRepository.RelinkResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VideoItem(val video: Video, val missing: Boolean)

/** A manual relink whose file didn't match the original, waiting for the user to confirm. */
data class PendingRelink(val video: Video, val uri: Uri)

data class VideoLibraryUiState(
    val items: List<VideoItem> = emptyList(),
    val searching: Boolean = false,
    val pendingRelink: PendingRelink? = null,
    val message: String? = null,
) {
    val missingCount get() = items.count { it.missing }
}

class VideoLibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = VideoRepository(application, AppDatabase.get(application).videoDao())

    private val missingIds = MutableStateFlow<Set<Long>>(emptySet())
    private val extra = MutableStateFlow(VideoLibraryUiState())

    val uiState: StateFlow<VideoLibraryUiState> =
        combine(repository.videos, missingIds, extra) { videos, missing, extra ->
            extra.copy(items = videos.map { VideoItem(it, it.id in missing) })
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VideoLibraryUiState())

    init {
        repository.videos.onEach { missingIds.value = repository.findMissing(it) }.launchIn(viewModelScope)
    }

    /** Files can be moved while the app is in the background, so re-check whenever the screen resumes. */
    fun refreshAvailability() {
        viewModelScope.launch { missingIds.value = repository.findMissing(repository.videos.first()) }
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

    fun relink(video: Video, uri: Uri, force: Boolean = false) {
        extra.update { it.copy(pendingRelink = null) }
        viewModelScope.launch {
            when (repository.relink(video, uri, force)) {
                RelinkResult.Relinked -> showMessage("Relinked ${video.displayName}")
                RelinkResult.Mismatch -> extra.update { it.copy(pendingRelink = PendingRelink(video, uri)) }
                RelinkResult.AlreadyInLibrary -> showMessage("That file is already in your library")
                RelinkResult.Unreadable -> showMessage("Couldn't read that file")
            }
        }
    }

    fun dismissPendingRelink() = extra.update { it.copy(pendingRelink = null) }

    fun searchDevice() {
        val missing = uiState.value.items.filter { it.missing }.map { it.video }
        if (missing.isEmpty()) return
        extra.update { it.copy(searching = true) }
        viewModelScope.launch {
            val found = runCatching { repository.searchDevice(missing) }.getOrDefault(0)
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

    fun showMessage(message: String) = extra.update { it.copy(message = message) }

    fun messageShown() = extra.update { it.copy(message = null) }
}
