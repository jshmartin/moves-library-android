package com.example.myapplication.ui.practice

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.example.myapplication.data.dao.PracticeDrillView
import com.example.myapplication.data.dao.SessionSummary
import com.example.myapplication.data.model.Practice
import com.example.myapplication.ui.breakingApp
import com.example.myapplication.ui.videos.LinkedVideosSection
import com.example.myapplication.ui.videos.VideoRoutes
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PracticeDetailUiState(
    val loading: Boolean = true,
    val practice: Practice? = null,
    val drills: List<PracticeDrillView> = emptyList(),
    val history: List<SessionSummary> = emptyList(),
)

class PracticeDetailViewModel(savedStateHandle: SavedStateHandle, private val dao: PracticeDao) : ViewModel() {
    private val practiceId: Long = checkNotNull(savedStateHandle[PracticeRoutes.ARG_PRACTICE_ID])

    val uiState: StateFlow<PracticeDetailUiState> = combine(
        dao.observePractice(practiceId),
        dao.observeDrills(practiceId),
        dao.observeFinishedSessionsOf(practiceId),
    ) { practice, drills, history ->
        PracticeDetailUiState(loading = false, practice = practice, drills = drills, history = history)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PracticeDetailUiState())

    fun start(onStarted: (sessionId: Long) -> Unit) {
        viewModelScope.launch { onStarted(dao.startSession(practiceId)) }
    }

    fun delete() {
        val practice = uiState.value.practice ?: return
        viewModelScope.launch { dao.deletePractice(practice) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                PracticeDetailViewModel(createSavedStateHandle(), breakingApp().database.practiceDao())
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeDetailScreen(
    onBack: () -> Unit,
    onNavigate: (route: String) -> Unit,
    viewModel: PracticeDetailViewModel = viewModel(factory = PracticeDetailViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val practice = state.practice
    var confirmDelete by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // The practice was deleted: leave the screen.
    LaunchedEffect(state.loading, practice) {
        if (!state.loading && practice == null) onBack()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(practice?.name.orEmpty(), maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (practice != null) {
                        IconButton(onClick = { onNavigate(PracticeRoutes.edit(practice.id)) }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit practice")
                        }
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete practice")
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        if (practice == null) return@Scaffold
        LazyColumn(
            modifier = Modifier.padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (practice.notes.isNotBlank()) {
                item(key = "notes") { Text(practice.notes, style = MaterialTheme.typography.bodyMedium) }
            }

            item(key = "start") {
                Button(
                    onClick = { viewModel.start { onNavigate(PracticeRoutes.live(it)) } },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Start practice")
                }
                Text(
                    timerText(practice.timer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            item(key = "drills-title") { SectionTitle("Drills") }
            if (state.drills.isEmpty()) {
                item(key = "drills-empty") {
                    Text(
                        "No drills yet. Tap edit to add moves, sets and blocks, or just use this practice for its timer.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(state.drills, key = { it.drill.position }) { drill ->
                DrillRow(drill, onNavigate)
            }

            item(key = "videos") {
                LinkedVideosSection(
                    owner = VideoOwner.Practice(practice.id),
                    ownerNoun = "practice",
                    onPlay = { onNavigate(VideoRoutes.video(it)) },
                    onNavigate = onNavigate,
                    snackbarHostState = snackbarHostState,
                )
            }

            if (state.history.isNotEmpty()) {
                item(key = "history-title") { SectionTitle("History") }
                items(state.history.take(HISTORY_SHOWN), key = { it.session.id }) { row ->
                    SessionRow(row, onOpen = { onNavigate(PracticeRoutes.log(it)) })
                }
                if (state.history.size > HISTORY_SHOWN) {
                    item(key = "history-more") {
                        Text(
                            "See the Log tab for all ${state.history.size} sessions.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    if (confirmDelete && practice != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${practice.name}?") },
            text = { Text("Your logs of it and its videos are kept.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
}

private const val HISTORY_SHOWN = 5

@Composable
private fun DrillRow(view: PracticeDrillView, onNavigate: (route: String) -> Unit) {
    val drill = view.drill
    val route = drillRoute(drill.moveId, drill.setId)
    val watch = watchRoute(drill.setId, view.hasCombo, view.topVideoId)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = route != null) { route?.let(onNavigate) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("${drill.position + 1}. ${view.title}", style = MaterialTheme.typography.bodyLarge)
            Text(
                "${drillSource(drill.type)} · ${targetText(drill.targetSets, drill.targetReps)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (watch != null) {
            IconButton(onClick = { onNavigate(watch) }) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "Watch ${view.title}")
            }
        }
    }
}

@Composable
internal fun SectionTitle(text: String) {
    Column {
        HorizontalDivider(Modifier.padding(top = 8.dp))
        Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
    }
}
