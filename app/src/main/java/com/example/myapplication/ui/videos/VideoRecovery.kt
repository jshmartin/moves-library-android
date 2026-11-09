package com.example.myapplication.ui.videos

import android.net.Uri
import com.example.myapplication.data.Video
import com.example.myapplication.data.VideoRepository
import com.example.myapplication.data.VideoRepository.RelinkResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A manual relink whose file didn't match the original, waiting for the user to confirm. */
data class PendingRelink(val video: Video, val uri: Uri)

data class VideoRecoveryState(
    /** The videos whose file can't be opened right now. */
    val missingIds: Set<Long> = emptySet(),
    val pendingRelink: PendingRelink? = null,
)

/**
 * Watches [videos] for files that have gone missing and repairs what it can, for any screen that
 * lists videos. Moved files relink by themselves when the app already has media permission, and
 * [relink] handles files the user finds by hand. Files can change while the app is in the
 * background, so screens call [refresh] when they resume.
 */
class VideoRecovery(
    private val scope: CoroutineScope,
    private val repository: VideoRepository,
    private val videos: Flow<List<Video>>,
    private val showMessage: (String) -> Unit = {},
) {
    private val _state = MutableStateFlow(VideoRecoveryState())
    val state: StateFlow<VideoRecoveryState> = _state.asStateFlow()

    /** One check at a time, so a slow one can't overwrite a newer answer. */
    private val checking = Mutex()

    init {
        videos.onEach { check() }.launchIn(scope)
    }

    fun refresh() {
        scope.launch { check() }
    }

    private suspend fun check() = checking.withLock {
        val result = repository.checkAvailability(videos.first())
        _state.update { it.copy(missingIds = result.missing) }
        when {
            result.relinked == 1 -> showMessage("Found a moved video and relinked it")
            result.relinked > 1 -> showMessage("Found and relinked ${result.relinked} moved videos")
        }
    }

    fun relink(video: Video, uri: Uri, force: Boolean = false) {
        dismissPendingRelink()
        scope.launch {
            when (repository.relink(video, uri, force)) {
                RelinkResult.Relinked -> {
                    check()
                    showMessage("Relinked ${video.displayName}")
                }
                RelinkResult.Mismatch -> _state.update { it.copy(pendingRelink = PendingRelink(video, uri)) }
                RelinkResult.AlreadyInLibrary -> showMessage("That file is already in your library")
                RelinkResult.Unreadable -> showMessage("Couldn't read that file")
            }
        }
    }

    fun dismissPendingRelink() = _state.update { it.copy(pendingRelink = null) }
}
