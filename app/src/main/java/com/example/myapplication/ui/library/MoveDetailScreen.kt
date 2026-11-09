package com.example.myapplication.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.data.VideoOwner
import com.example.myapplication.ui.videos.LinkedVideosSection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoveDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onMoveClick: (Long) -> Unit,
    onPlayVideo: (Long) -> Unit,
    viewModel: MoveDetailViewModel = viewModel(factory = MoveDetailViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val move = state.move
    var confirmDelete by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // The move was deleted (here or elsewhere): leave the screen.
    LaunchedEffect(state.loading, move) {
        if (!state.loading && move == null) onBack()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(move?.name.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (move != null) {
                        IconButton(onClick = { onEdit(move.id) }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit move")
                        }
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete move")
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        if (move == null) return@Scaffold
        LazyColumn(
            modifier = Modifier.padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text("${state.categoryName} · ${move.status.label}", style = MaterialTheme.typography.titleMedium)
                state.parent?.let { parent ->
                    Text(
                        "Variation of ${parent.name}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .clickable { onMoveClick(parent.id) },
                    )
                }
                if (move.notes.isNotBlank()) {
                    Text(move.notes, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                }
            }

            item(key = "videos") {
                LinkedVideosSection(
                    owner = VideoOwner.ofMove(move.id),
                    ownerNoun = "move",
                    onPlay = onPlayVideo,
                    snackbarHostState = snackbarHostState,
                )
            }

            if (state.variations.isNotEmpty()) {
                item { SectionTitle("Variations") }
                items(state.variations, key = { "variation-${it.id}" }) { variation ->
                    Text(
                        variation.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onMoveClick(variation.id) }
                            .padding(vertical = 8.dp),
                    )
                }
            }
        }
    }

    if (confirmDelete && move != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${move.name}?") },
            text = {
                Text("It's removed from any sets, rounds and routines. Variations and videos are kept.")
            },
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

@Composable
private fun SectionTitle(text: String) {
    Column {
        HorizontalDivider(Modifier.padding(top = 8.dp))
        Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
    }
}
