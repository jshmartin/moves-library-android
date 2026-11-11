package com.example.myapplication.ui.library

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.data.VideoWithOwner
import com.example.myapplication.data.model.Category
import com.example.myapplication.data.model.MoveStatus
import com.example.myapplication.ui.videos.LibraryPickerDialog
import com.example.myapplication.ui.videos.Thumbnail
import com.example.myapplication.ui.videos.VIDEO_MIME_TYPES

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MoveEditScreen(
    onBack: () -> Unit,
    onSaved: (moveId: Long, isNew: Boolean) -> Unit,
    viewModel: MoveEditViewModel = viewModel(factory = MoveEditViewModel.Factory),
) {
    val context = LocalContext.current
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val moves by viewModel.allMoves.collectAsStateWithLifecycle()
    val libraryVideos by viewModel.libraryVideos.collectAsStateWithLifecycle()
    val parentOptions = viewModel.parentOptions(moves)

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.isNew) "New move" else "Edit move") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            viewModel.save { id, unreadable ->
                                if (unreadable > 0) {
                                    val what = if (unreadable == 1) "1 video" else "$unreadable videos"
                                    Toast.makeText(context, "$what couldn't be read", Toast.LENGTH_LONG).show()
                                }
                                onSaved(id, viewModel.isNew)
                            }
                        },
                        enabled = viewModel.canSave,
                    ) { Text("Save") }
                },
            )
        },
    ) { innerPadding ->
        val loadedCategories = categories
        if (!viewModel.loaded || loadedCategories == null) return@Scaffold
        Column(
            Modifier
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = viewModel.name,
                onValueChange = { viewModel.name = it },
                label = { Text("Name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )

            CategoryChips(
                categories = loadedCategories,
                selectedId = viewModel.categoryId,
                showHint = viewModel.name.isNotBlank(),
                onSelect = viewModel::selectCategory,
            )

            Column {
                Text("Status", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MoveStatus.entries.forEach { status ->
                        FilterChip(
                            selected = viewModel.status == status,
                            onClick = { viewModel.status = status },
                            label = { Text(status.label) },
                        )
                    }
                }
            }

            if (viewModel.hasVariations(moves)) {
                Text(
                    "This move has variations, so it can't be a variation itself.",
                    style = MaterialTheme.typography.bodySmall,
                )
            } else if (parentOptions.isNotEmpty() || viewModel.parentMoveId != null) {
                Picker(
                    label = "Variation of",
                    selectedText = parentOptions.firstOrNull { it.id == viewModel.parentMoveId }?.name ?: "None",
                    options = listOf<Pair<Long?, String>>(null to "None") + parentOptions.map { it.id to it.name },
                    onSelect = { viewModel.parentMoveId = it },
                )
            }

            if (viewModel.isNew) {
                PendingVideos(
                    videos = viewModel.pendingVideos,
                    libraryCandidates = viewModel.libraryCandidates(libraryVideos),
                    onAddFromDevice = viewModel::addFromDevice,
                    onAddFromLibrary = { viewModel.addFromLibrary(it.video) },
                    onRemove = viewModel::removePending,
                )
            } else {
                OutlinedTextField(
                    value = viewModel.notes,
                    onValueChange = { viewModel.notes = it },
                    label = { Text("Notes") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** One chip per category. A new move starts with none selected unless it was added from a category. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryChips(
    categories: List<Category>,
    selectedId: Long,
    showHint: Boolean,
    onSelect: (Long) -> Unit,
) {
    Column {
        Text("Category", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            categories.forEach { category ->
                FilterChip(
                    selected = category.id == selectedId,
                    onClick = { onSelect(category.id) },
                    label = { Text(category.name) },
                )
            }
        }
        val hint = when {
            categories.isEmpty() -> "There are no categories yet. Add one from the Library screen's edit button."
            showHint && categories.none { it.id == selectedId } -> "Choose a category to save the move."
            else -> null
        }
        hint?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Videos picked for a new move. They're linked to it when it's saved. */
@Composable
private fun PendingVideos(
    videos: List<PendingVideo>,
    libraryCandidates: List<VideoWithOwner>,
    onAddFromDevice: (List<Uri>) -> Unit,
    onAddFromLibrary: (VideoWithOwner) -> Unit,
    onRemove: (PendingVideo) -> Unit,
) {
    var pickingFromLibrary by rememberSaveable { mutableStateOf(false) }
    val deviceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> onAddFromDevice(uris) }
    // Otherwise the name field keeps focus and the keyboard reopens over the picked videos.
    val focusManager = LocalFocusManager.current

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column {
            Text("Videos", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    focusManager.clearFocus()
                    deviceLauncher.launch(VIDEO_MIME_TYPES)
                }) { Text("From device") }
                if (libraryCandidates.isNotEmpty()) {
                    OutlinedButton(onClick = {
                        focusManager.clearFocus()
                        pickingFromLibrary = true
                    }) { Text("From library") }
                }
            }
        }
        videos.forEach { video ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Thumbnail(video.uri)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        video.name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { onRemove(video) }) {
                        Icon(Icons.Filled.Close, contentDescription = "Remove ${video.name}")
                    }
                }
            }
        }
    }

    if (pickingFromLibrary) {
        LibraryPickerDialog(
            candidates = libraryCandidates,
            movesVideos = true,
            onPick = {
                pickingFromLibrary = false
                onAddFromLibrary(it)
            },
            onDismiss = { pickingFromLibrary = false },
        )
    }
}

@Composable
private fun <T> Picker(
    label: String,
    selectedText: String,
    options: List<Pair<T, String>>,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Box {
            OutlinedButton(onClick = { expanded = true }) { Text(selectedText) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (value, text) ->
                    DropdownMenuItem(
                        text = { Text(text) },
                        onClick = {
                            onSelect(value)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}
