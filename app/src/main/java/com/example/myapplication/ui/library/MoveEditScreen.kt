package com.example.myapplication.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.data.model.MoveStatus

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MoveEditScreen(
    onBack: () -> Unit,
    onSaved: (moveId: Long, isNew: Boolean) -> Unit,
    viewModel: MoveEditViewModel = viewModel(factory = MoveEditViewModel.Factory),
) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val moves by viewModel.allMoves.collectAsStateWithLifecycle()
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
                        onClick = { viewModel.save { id -> onSaved(id, viewModel.isNew) } },
                        enabled = viewModel.canSave,
                    ) { Text("Save") }
                },
            )
        },
    ) { innerPadding ->
        if (!viewModel.loaded) return@Scaffold
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

            Picker(
                label = "Category",
                selectedText = categories.firstOrNull { it.id == viewModel.categoryId }?.name ?: "Choose",
                options = categories.map { it.id to it.name },
                onSelect = { viewModel.selectCategory(it) },
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
