package com.example.myapplication.ui.library

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.myapplication.data.Video
import com.example.myapplication.data.VideoOwner
import com.example.myapplication.data.VideoRepository
import com.example.myapplication.data.VideoWithOwner
import com.example.myapplication.data.dao.LibraryDao
import com.example.myapplication.data.model.Category
import com.example.myapplication.data.model.Move
import com.example.myapplication.data.model.MoveStatus
import com.example.myapplication.ui.breakingApp
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A video picked while adding a move. It's added and linked when the move is saved. */
sealed interface PendingVideo {
    val uri: String
    val name: String

    data class FromDevice(val file: Uri, override val name: String) : PendingVideo {
        override val uri: String get() = file.toString()
    }

    data class FromLibrary(val video: Video) : PendingVideo {
        override val uri: String get() = video.uri
        override val name: String get() = video.displayName
    }
}

/** Adds a new move, or edits one when the route carries a move id. */
class MoveEditViewModel(
    savedStateHandle: SavedStateHandle,
    private val dao: LibraryDao,
    private val videos: VideoRepository,
) : ViewModel() {
    private val moveId: Long = savedStateHandle[LibraryRoutes.ARG_MOVE_ID] ?: NO_ID
    val isNew: Boolean get() = moveId == NO_ID
    private var original: Move? = null

    var loaded by mutableStateOf(false)
        private set
    var saving by mutableStateOf(false)
        private set
    var name by mutableStateOf("")

    /** Preset when adding from a category's screen; otherwise the user has to choose one. */
    var categoryId by mutableStateOf(savedStateHandle[LibraryRoutes.ARG_CATEGORY_ID] ?: NO_ID)
        private set
    var status by mutableStateOf(MoveStatus.LEARNING)
    var parentMoveId by mutableStateOf<Long?>(null)

    /** Only edited on an existing move; a new move starts without notes. */
    var notes by mutableStateOf("")

    /** Videos to link to a new move once it's saved, in the order they were picked. */
    var pendingVideos by mutableStateOf<List<PendingVideo>>(emptyList())
        private set

    /** Null until the categories have loaded. */
    val categories: StateFlow<List<Category>?> = dao.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val allMoves: StateFlow<List<Move>> = dao.observeMoves()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val libraryVideos: StateFlow<List<VideoWithOwner>> = videos.videosWithOwner
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            if (!isNew) {
                dao.getMove(moveId)?.let { move ->
                    original = move
                    name = move.name
                    categoryId = move.categoryId
                    status = move.status
                    parentMoveId = move.parentMoveId
                    notes = move.notes
                }
            }
            loaded = true
        }
    }

    fun selectCategory(id: Long) {
        if (id != categoryId) parentMoveId = null
        categoryId = id
    }

    /** Moves this one can be a variation of: base moves in the same category, other than itself. */
    fun parentOptions(moves: List<Move>): List<Move> =
        moves.filter { it.categoryId == categoryId && it.parentMoveId == null && it.id != moveId }

    /** A move that has variations stays a base move, so variations are only one level deep. */
    fun hasVariations(moves: List<Move>): Boolean = !isNew && moves.any { it.parentMoveId == moveId }

    /** Library videos that aren't already picked. */
    fun libraryCandidates(all: List<VideoWithOwner>): List<VideoWithOwner> {
        val picked = pendingVideos.mapTo(mutableSetOf()) { it.uri }
        return all.filter { it.video.uri !in picked }
    }

    fun addFromDevice(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val picked = uris.map { PendingVideo.FromDevice(it, videos.displayName(it)) }
            addPending(picked)
        }
    }

    fun addFromLibrary(video: Video) = addPending(listOf(PendingVideo.FromLibrary(video)))

    fun removePending(video: PendingVideo) {
        pendingVideos = pendingVideos - video
    }

    private fun addPending(picked: List<PendingVideo>) {
        pendingVideos = (pendingVideos + picked).distinctBy { it.uri }
    }

    val canSave: Boolean get() = loaded && !saving && name.isNotBlank() && categoryId != NO_ID

    /** Saves, links any picked videos, and reports the move's id and how many picked files couldn't be read. */
    fun save(onSaved: (moveId: Long, unreadableVideos: Int) -> Unit) {
        if (!canSave) return
        saving = true
        viewModelScope.launch {
            val existing = original
            if (existing == null) {
                val id = dao.insertMove(
                    Move(
                        name = name.trim(),
                        categoryId = categoryId,
                        parentMoveId = parentMoveId,
                        status = status,
                    )
                )
                onSaved(id, linkPendingVideos(id))
            } else {
                dao.updateMove(
                    existing.copy(
                        name = name.trim(),
                        categoryId = categoryId,
                        parentMoveId = parentMoveId,
                        status = status,
                        notes = notes.trim(),
                    )
                )
                onSaved(existing.id, 0)
            }
        }
    }

    /** Links the picked videos in the order they were picked, so the last one ends up on top. */
    private suspend fun linkPendingVideos(moveId: Long): Int {
        val owner = VideoOwner.Move(moveId)
        var unreadable = 0
        for (video in pendingVideos) {
            when (video) {
                is PendingVideo.FromDevice -> unreadable += videos.add(listOf(video.file), owner).failed
                is PendingVideo.FromLibrary -> videos.link(video.video.id, owner)
            }
        }
        return unreadable
    }

    companion object {
        const val NO_ID = -1L

        val Factory = viewModelFactory {
            initializer {
                val app = breakingApp()
                MoveEditViewModel(
                    createSavedStateHandle(),
                    app.database.libraryDao(),
                    VideoRepository(app, app.database.videoDao()),
                )
            }
        }
    }
}
