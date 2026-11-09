package com.example.myapplication.ui.videos

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.data.VideoLinkTarget
import com.example.myapplication.data.VideoOwner
import com.example.myapplication.data.VideoWithOwner

/**
 * The videos on a move, set, practice or logged session, top video first, with controls to add,
 * reorder, remove and link them. [ownerNoun] is how the owner is named in the UI, e.g. "move".
 */
@Composable
fun LinkedVideosSection(
    owner: VideoOwner,
    ownerNoun: String,
    onPlay: (videoId: Long) -> Unit,
    onNavigate: (route: String) -> Unit,
    snackbarHostState: SnackbarHostState,
    viewModel: LinkedVideosViewModel = viewModel(factory = LinkedVideosViewModel.factory(owner)),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var pickingFromLibrary by rememberSaveable { mutableStateOf(false) }
    var linkingVideoId by rememberSaveable { mutableStateOf<Long?>(null) }

    LifecycleResumeEffect(Unit) {
        viewModel.refreshAvailability()
        onPauseOrDispose { }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.messageShown()
        }
    }

    val addLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> viewModel.addFromDevice(uris) }

    var locatingId by rememberSaveable { mutableStateOf<Long?>(null) }
    val locateLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val video = state.items.firstOrNull { it.video.id == locatingId }?.video
        locatingId = null
        if (uri != null && video != null) viewModel.relink(video, uri)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider(Modifier.padding(top = 8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Videos", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = { addLauncher.launch(VIDEO_MIME_TYPES) }) { Text("From device") }
            if (state.candidates.isNotEmpty()) {
                TextButton(onClick = { pickingFromLibrary = true }) { Text("From library") }
            }
        }
        if (state.items.isEmpty()) {
            Text(
                "Add videos of this $ownerNoun to track your progress. The newest one goes on top.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        state.items.forEachIndexed { index, item ->
            LinkedVideoRow(
                item = item,
                isTop = index == 0,
                ownerNoun = ownerNoun,
                otherLinks = state.links[item.video.id].orEmpty().filter { it.owner != owner },
                onPlay = { onPlay(item.video.id) },
                onLocate = {
                    locatingId = item.video.id
                    locateLauncher.launch(VIDEO_MIME_TYPES)
                },
                onMakeTop = { viewModel.makeTop(item.video) },
                onRemove = { viewModel.unlink(item.video) },
                onLink = { linkingVideoId = item.video.id },
                onNavigate = onNavigate,
            )
        }
    }

    if (pickingFromLibrary) {
        LibraryPickerDialog(
            candidates = state.candidates,
            movesVideos = owner is VideoOwner.Exclusive,
            onPick = {
                pickingFromLibrary = false
                viewModel.link(it.video)
            },
            onDismiss = { pickingFromLibrary = false },
        )
    }

    state.items.firstOrNull { it.video.id == linkingVideoId }?.let { item ->
        VideoLinkDialog(
            videoName = item.video.displayName,
            targets = state.linkTargets,
            current = state.links[item.video.id].orEmpty(),
            onPick = {
                linkingVideoId = null
                viewModel.linkTo(item.video, it)
            },
            onDismiss = { linkingVideoId = null },
        )
    }

    state.pendingRelink?.let { pending ->
        RelinkMismatchDialog(
            pending = pending,
            onLinkAnyway = { viewModel.relink(pending.video, pending.uri, force = true) },
            onDismiss = viewModel::dismissPendingRelink,
        )
    }
}

@Composable
private fun LinkedVideoRow(
    item: VideoItem,
    isTop: Boolean,
    ownerNoun: String,
    otherLinks: List<VideoLinkTarget>,
    onPlay: () -> Unit,
    onLocate: () -> Unit,
    onMakeTop: () -> Unit,
    onRemove: () -> Unit,
    onLink: () -> Unit,
    onNavigate: (route: String) -> Unit,
) {
    val video = item.video
    Card(Modifier.fillMaxWidth().clickable(enabled = !item.missing, onClick = onPlay)) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Thumbnail(video, item.missing)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                if (isTop) {
                    Text(
                        "Top video",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    video.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.missing) {
                    Text(
                        "File not found. It may have been moved or deleted.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = onLocate, contentPadding = PaddingValues(horizontal = 0.dp)) {
                        Text("Locate")
                    }
                } else {
                    video.durationMs?.let {
                        Text(
                            formatDuration(it),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                VideoLinkChips(otherLinks, onNavigate)
                TextButton(onClick = onLink, contentPadding = PaddingValues(horizontal = 0.dp)) {
                    Text("Link elsewhere")
                }
            }
            if (!isTop) {
                IconButton(onClick = onMakeTop) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Make top video")
                }
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Close, contentDescription = "Remove from this $ownerNoun")
            }
        }
    }
}

@Composable
private fun LibraryPickerDialog(
    candidates: List<VideoWithOwner>,
    movesVideos: Boolean,
    onPick: (VideoWithOwner) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add from library") },
        text = {
            LazyColumn(Modifier.heightIn(max = 400.dp)) {
                items(candidates, key = { it.video.id }) { candidate ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(candidate) }
                            .padding(vertical = 8.dp),
                    ) {
                        Text(
                            candidate.video.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        candidate.ownerName?.let {
                            Text(
                                if (movesVideos) "On $it. Adding it here moves it." else "On $it. It stays there too.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
