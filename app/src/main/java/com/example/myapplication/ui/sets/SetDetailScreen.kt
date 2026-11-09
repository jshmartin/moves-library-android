package com.example.myapplication.ui.sets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.data.VideoOwner
import com.example.myapplication.ui.practice.PracticeLinksSection
import com.example.myapplication.ui.videos.LinkedVideosSection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onMoveClick: (Long) -> Unit,
    onPlayCombo: (Long) -> Unit,
    onPlayVideo: (Long) -> Unit,
    onNavigate: (route: String) -> Unit,
    viewModel: SetDetailViewModel = viewModel(factory = SetDetailViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val set = state.set
    var confirmDelete by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // The set was deleted: leave the screen.
    LaunchedEffect(state.loading, set) {
        if (!state.loading && set == null) onBack()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(set?.name.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (set != null) {
                        IconButton(onClick = { onEdit(set.id) }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit set")
                        }
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete set")
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        if (set == null) return@Scaffold
        LazyColumn(
            modifier = Modifier.padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (set.notes.isNotBlank()) {
                item { Text(set.notes, style = MaterialTheme.typography.bodyMedium) }
            }

            item {
                Button(
                    onClick = { onPlayCombo(set.id) },
                    enabled = state.canPlayCombo,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Play combo")
                }
            }

            if (state.steps.isEmpty()) {
                item {
                    Text(
                        "No moves yet. Tap edit to chain moves into this set.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(state.steps, key = { it.position }) { step ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onMoveClick(step.moveId) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${step.position + 1}. ${step.moveName}",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    if (step.videoUri == null) {
                        Text(
                            "No video",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item(key = "videos") {
                LinkedVideosSection(
                    owner = VideoOwner.Set(set.id),
                    ownerNoun = "set",
                    onPlay = onPlayVideo,
                    onNavigate = onNavigate,
                    snackbarHostState = snackbarHostState,
                )
            }

            item(key = "practice") {
                PracticeLinksSection(
                    moveId = null,
                    setId = set.id,
                    title = set.name,
                    noun = "set",
                    onNavigate = onNavigate,
                )
            }
        }
    }

    if (confirmDelete && set != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${set.name}?") },
            text = { Text("It's removed from any rounds and practices. Its moves, videos and logs are kept.") },
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
