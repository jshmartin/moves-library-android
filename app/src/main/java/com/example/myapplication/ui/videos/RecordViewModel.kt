package com.example.myapplication.ui.videos

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.myapplication.data.VideoOwner
import com.example.myapplication.data.VideoRecording
import com.example.myapplication.data.VideoRepository
import com.example.myapplication.ui.breakingApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A clip that was recorded and put in the library. */
data class SavedRecording(val uri: String, val durationMs: Long)

data class RecordUiState(
    /** The clip is being added to the library. Leaving now would lose that step. */
    val saving: Boolean = false,
    val saved: SavedRecording? = null,
    val message: String? = null,
)

/**
 * Takes a finished recording into the library, linked to [owner] if there is one. This outlives
 * the camera screen's own state, so a recording cut short by rotating or leaving the app still lands.
 */
class RecordViewModel(
    private val context: Context,
    private val repository: VideoRepository,
    val owner: VideoOwner?,
) : ViewModel() {
    private val state = MutableStateFlow(RecordUiState())
    val uiState: StateFlow<RecordUiState> = state

    fun onRecorded(uri: Uri?, error: Int, durationMs: Long) {
        if (uri == null || !VideoRecording.keepsFile(error, durationMs)) {
            uri?.let { VideoRecording.discard(context, it) }
            state.update { it.copy(message = "That recording was too short or couldn't be saved. Try again.") }
            return
        }
        state.update { it.copy(saving = true, saved = null) }
        viewModelScope.launch {
            val result = repository.add(listOf(uri), owner)
            val inLibrary = result.added + result.relinked + result.duplicates > 0
            state.update {
                if (inLibrary) {
                    it.copy(saving = false, saved = SavedRecording(uri.toString(), durationMs))
                } else {
                    it.copy(
                        saving = false,
                        message = "Recorded, but couldn't add it to your videos. It's in Movies/${VideoRecording.FOLDER}.",
                    )
                }
            }
        }
    }

    fun showMessage(message: String) = state.update { it.copy(message = message) }

    fun messageShown() = state.update { it.copy(message = null) }

    /** Clears the saved card so the camera is ready for another clip. */
    fun recordAnother() = state.update { it.copy(saved = null) }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = breakingApp()
                val handle: SavedStateHandle = createSavedStateHandle()
                RecordViewModel(
                    context = app,
                    repository = VideoRepository(app, app.database.videoDao()),
                    owner = RecordRoutes.ownerOf(
                        handle[RecordRoutes.ARG_OWNER_KIND],
                        handle[RecordRoutes.ARG_OWNER_ID],
                    ),
                )
            }
        }
    }
}
