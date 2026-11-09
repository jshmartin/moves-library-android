package com.example.myapplication.ui.practice

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.myapplication.data.VideoOwner
import com.example.myapplication.data.dao.PracticeDao
import com.example.myapplication.data.dao.SessionDrillView
import com.example.myapplication.data.model.PracticeSession
import com.example.myapplication.data.model.SessionSet
import com.example.myapplication.ui.breakingApp
import com.example.myapplication.ui.videos.LinkedVideosSection
import com.example.myapplication.ui.videos.VideoRoutes
import com.example.myapplication.ui.videos.formatDuration
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SessionLogUiState(
    val loading: Boolean = true,
    val session: PracticeSession? = null,
    val drills: List<SessionDrillView> = emptyList(),
    /** Finished sets by drill position. */
    val sets: Map<Int, List<SessionSet>> = emptyMap(),
)

class SessionLogViewModel(savedStateHandle: SavedStateHandle, private val dao: PracticeDao) : ViewModel() {
    private val sessionId: Long = checkNotNull(savedStateHandle[PracticeRoutes.ARG_SESSION_ID])

    val uiState: StateFlow<SessionLogUiState> = combine(
        dao.observeSession(sessionId),
        dao.observeSessionDrills(sessionId),
        dao.observeSessionSets(sessionId),
    ) { session, drills, sets ->
        SessionLogUiState(
            loading = false,
            session = session,
            drills = drills,
            sets = sets.groupBy { it.drillPosition },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionLogUiState())

    fun saveNotes(notes: String) {
        viewModelScope.launch { dao.setSessionNotes(sessionId, notes.trim()) }
    }

    fun delete() {
        viewModelScope.launch { dao.deleteSession(sessionId) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                SessionLogViewModel(createSavedStateHandle(), breakingApp().database.practiceDao())
            }
        }
    }
}

/** One entry in the log: what was done in a session, its notes and its footage. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionLogScreen(
    onBack: () -> Unit,
    onNavigate: (route: String) -> Unit,
    viewModel: SessionLogViewModel = viewModel(factory = SessionLogViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val session = state.session
    var confirmDelete by remember { mutableStateOf(false) }
    var editingNotes by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // The entry was deleted: leave the screen.
    LaunchedEffect(state.loading, session) {
        if (!state.loading && session == null) onBack()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(session?.name.orEmpty(), maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (session != null) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete from log")
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        if (session == null) return@Scaffold
        LazyColumn(
            modifier = Modifier.padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "summary") {
                Text(dateAndTime(context, session.startedAt), style = MaterialTheme.typography.titleMedium)
                Text("Practiced for ${formatDuration(session.durationMs)}", style = MaterialTheme.typography.bodyMedium)
                if (session.roundsCompleted > 0) {
                    Text(
                        "${plural(session.roundsCompleted, "round")} completed · " +
                            "${formatDuration(session.timer.activeSec * 1000L)} active / " +
                            "${formatDuration(session.timer.restSec * 1000L)} rest",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            if (state.drills.isNotEmpty()) {
                item(key = "drills-title") { SectionTitle("Sets and reps") }
                items(state.drills, key = { it.drill.position }) { drill ->
                    LoggedDrillRow(drill, state.sets[drill.drill.position].orEmpty(), onNavigate)
                }
            }

            item(key = "notes-title") {
                SectionTitle("Notes")
                Text(
                    session.notes.ifBlank { "No notes." },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (session.notes.isBlank()) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.padding(top = 4.dp),
                )
                TextButton(onClick = { editingNotes = true }, contentPadding = PaddingValues(horizontal = 0.dp)) {
                    Text(if (session.notes.isBlank()) "Add notes" else "Edit notes")
                }
            }

            item(key = "videos") {
                LinkedVideosSection(
                    owner = VideoOwner.Session(session.id),
                    ownerNoun = "session",
                    onPlay = { onNavigate(VideoRoutes.video(it)) },
                    onNavigate = onNavigate,
                    snackbarHostState = snackbarHostState,
                )
            }
        }
    }

    if (editingNotes && session != null) {
        var notes by rememberSaveable { mutableStateOf(session.notes) }
        AlertDialog(
            onDismissRequest = { editingNotes = false },
            title = { Text("Notes") },
            text = {
                OutlinedTextField(value = notes, onValueChange = { notes = it }, minLines = 4)
            },
            confirmButton = {
                TextButton(onClick = {
                    editingNotes = false
                    viewModel.saveNotes(notes)
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editingNotes = false }) { Text("Cancel") } },
        )
    }

    if (confirmDelete && session != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete from log?") },
            text = { Text("This session's sets and reps are gone for good. Its videos stay in your library.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun LoggedDrillRow(drill: SessionDrillView, sets: List<SessionSet>, onNavigate: (route: String) -> Unit) {
    val route = drillRoute(drill.drill.moveId, drill.drill.setId)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = route != null) { route?.let(onNavigate) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(drill.displayTitle, style = MaterialTheme.typography.bodyLarge)
            Text(
                "${drill.setsDone} of ${plural(drill.drill.targetSets, "set")} · ${plural(drill.repsDone, "rep")}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (sets.any { it.reps > 0 }) {
                Text(
                    "Reps by set: ${sets.joinToString(" · ") { it.reps.toString() }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
