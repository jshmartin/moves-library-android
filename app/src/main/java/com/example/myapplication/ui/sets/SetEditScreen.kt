package com.example.myapplication.ui.sets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.data.model.Move

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetEditScreen(
    onBack: () -> Unit,
    onSaved: (setId: Long, isNew: Boolean) -> Unit,
    viewModel: SetEditViewModel = viewModel(factory = SetEditViewModel.Factory),
) {
    val allMoves by viewModel.allMoves.collectAsStateWithLifecycle()
    var pickingMove by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.isNew) "New set" else "Edit set") },
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

            Column {
                Text("Moves", style = MaterialTheme.typography.labelLarge)
                viewModel.chain.forEachIndexed { index, move ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${index + 1}. ${move.name}",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { viewModel.moveUp(index) }, enabled = index > 0) {
                            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move ${move.name} earlier")
                        }
                        IconButton(onClick = { viewModel.moveDown(index) }, enabled = index < viewModel.chain.lastIndex) {
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move ${move.name} later")
                        }
                        IconButton(onClick = { viewModel.removeAt(index) }) {
                            Icon(Icons.Filled.Close, contentDescription = "Remove ${move.name}")
                        }
                    }
                }
                OutlinedButton(onClick = { pickingMove = true }, enabled = allMoves.isNotEmpty()) {
                    Text("Add move")
                }
                if (allMoves.isEmpty()) {
                    Text(
                        "Add moves in the Library tab first.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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

    if (pickingMove) {
        MovePickerDialog(
            moves = allMoves,
            onPick = {
                pickingMove = false
                viewModel.addMove(it)
            },
            onDismiss = { pickingMove = false },
        )
    }
}

@Composable
private fun MovePickerDialog(moves: List<Move>, onPick: (Move) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add move") },
        text = {
            LazyColumn(Modifier.heightIn(max = 400.dp)) {
                items(moves, key = { it.id }) { move ->
                    Text(
                        move.name,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(move) }
                            .padding(vertical = 12.dp),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
