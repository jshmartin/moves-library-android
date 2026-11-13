package com.example.myapplication.ui.videos

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.data.Video
import com.example.myapplication.data.VideoFiles
import com.example.myapplication.data.VideoLinkTarget
import com.example.myapplication.data.VideoRecording
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal val VIDEO_MIME_TYPES = arrayOf("video/*")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoLibraryScreen(
    onNavigate: (route: String) -> Unit,
    viewModel: VideoLibraryViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val canRecord = remember { VideoRecording.isSupported(context) }
    val snackbarHostState = remember { SnackbarHostState() }
    var linkingVideoId by rememberSaveable { mutableStateOf<Long?>(null) }
    var removingVideoId by rememberSaveable { mutableStateOf<Long?>(null) }

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
    ) { uris -> viewModel.add(uris) }

    var locatingId by rememberSaveable { mutableStateOf<Long?>(null) }
    val locateLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val video = state.items.firstOrNull { it.video.id == locatingId }?.video
        locatingId = null
        if (uri != null && video != null) viewModel.relink(video, uri)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) {
            viewModel.searchDevice()
        } else {
            viewModel.showMessage("Searching needs access to your videos. You can still Locate files yourself.")
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text("Videos") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (canRecord) {
                    ExtendedFloatingActionButton(
                        onClick = { onNavigate(RecordRoutes.record()) },
                        icon = { Box(Modifier.size(14.dp).clip(CircleShape).background(Color(0xFFE53935))) },
                        text = { Text("Record") },
                    )
                }
                FloatingActionButton(onClick = { addLauncher.launch(VIDEO_MIME_TYPES) }) {
                    Icon(Icons.Filled.Add, contentDescription = "Add videos")
                }
            }
        },
    ) { innerPadding ->
        if (state.items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(innerPadding).padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "No videos yet. Tap + to add videos from your device. They stay where they are; " +
                        "the app only keeps a link to them." +
                        if (canRecord) " Or tap Record to film a new one." else "",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 88.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.missingCount > 0) {
                item(key = "missing-banner") {
                    MissingBanner(
                        count = state.missingCount,
                        searching = state.searching,
                        onSearch = { permissionLauncher.launch(VideoFiles.mediaPermissions()) },
                    )
                }
            }
            items(state.items, key = { it.video.id }) { item ->
                VideoRow(
                    item = item,
                    links = state.links[item.video.id].orEmpty(),
                    onOpen = { viewModel.open(item.video) { openVideo(context, item.video) } },
                    onLocate = {
                        locatingId = item.video.id
                        locateLauncher.launch(VIDEO_MIME_TYPES)
                    },
                    onRemove = { removingVideoId = item.video.id },
                    onLink = { linkingVideoId = item.video.id },
                    onNavigate = onNavigate,
                )
            }
        }
    }

    state.items.firstOrNull { it.video.id == linkingVideoId }?.let { item ->
        VideoLinkDialog(
            videoName = item.video.displayName,
            targets = state.linkTargets,
            current = state.links[item.video.id].orEmpty(),
            onPick = {
                linkingVideoId = null
                viewModel.link(item.video, it)
            },
            onDismiss = { linkingVideoId = null },
        )
    }

    state.items.firstOrNull { it.video.id == removingVideoId }?.let { item ->
        RemoveVideoDialog(
            item = item,
            links = state.links[item.video.id].orEmpty(),
            onConfirm = {
                removingVideoId = null
                viewModel.remove(item.video)
            },
            onDismiss = { removingVideoId = null },
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

/** Removing is the one step that can't be undone: the links go, and so does the means to relink it later. */
@Composable
private fun RemoveVideoDialog(
    item: VideoItem,
    links: List<VideoLinkTarget>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remove ${item.video.displayName}?", maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Text(
                listOfNotNull(
                    links.takeIf { it.isNotEmpty() }?.let { "It comes off ${it.joinToString(", ") { link -> link.label }}." },
                    if (item.missing) {
                        "If the file turns up later, you'll have to add and link it again."
                    } else {
                        "The file stays on your device."
                    },
                ).joinToString(" ")
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Remove") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun MissingBanner(count: Int, searching: Boolean, onSearch: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                if (count == 1) "1 video can't be found" else "$count videos can't be found",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                "They may have been moved, renamed or deleted. Search your device to relink them automatically.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (searching) {
                    CircularProgressIndicator(Modifier.padding(8.dp).size(24.dp))
                } else {
                    TextButton(onClick = onSearch) { Text("Search device") }
                }
            }
        }
    }
}

@Composable
private fun VideoRow(
    item: VideoItem,
    links: List<VideoLinkTarget>,
    onOpen: () -> Unit,
    onLocate: () -> Unit,
    onRemove: () -> Unit,
    onLink: () -> Unit,
    onNavigate: (route: String) -> Unit,
) {
    val video = item.video
    Card(Modifier.fillMaxWidth().clickable(enabled = !item.missing, onClick = onOpen)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Thumbnail(video, item.missing)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    video.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val context = LocalContext.current
                Text(
                    listOfNotNull(video.durationMs?.let(::formatDuration), Formatter.formatShortFileSize(context, video.sizeBytes))
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (item.missing) {
                    Text(
                        "File not found",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Row {
                        TextButton(onClick = onLocate, contentPadding = PaddingValues(horizontal = 0.dp)) {
                            Text("Locate")
                        }
                        Spacer(Modifier.width(16.dp))
                        TextButton(onClick = onRemove, contentPadding = PaddingValues(horizontal = 0.dp)) {
                            Text("Remove")
                        }
                    }
                }
                VideoLinkChips(links, onNavigate)
                TextButton(onClick = onLink, contentPadding = PaddingValues(horizontal = 0.dp)) {
                    Text("Link")
                }
            }
            if (!item.missing) {
                IconButton(onClick = onRemove) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remove from library")
                }
            }
        }
    }
}

@Composable
internal fun Thumbnail(video: Video, missing: Boolean) = Thumbnail(video.uri, missing)

@Composable
internal fun Thumbnail(uri: String, missing: Boolean = false) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(null, uri, missing) {
        value = if (missing) null else withContext(Dispatchers.IO) { VideoFiles.thumbnail(context, uri.toUri()) }
    }
    Box(
        Modifier
            .size(width = 96.dp, height = 64.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap
        when {
            image != null -> Image(
                image.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            missing -> Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
        }
    }
}

/** Hands the video to the device's player. Returns false if that couldn't be done. */
private fun openVideo(context: Context, video: Video): Boolean {
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(video.uri.toUri(), video.mimeType ?: "video/*")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    return try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}

internal fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}
