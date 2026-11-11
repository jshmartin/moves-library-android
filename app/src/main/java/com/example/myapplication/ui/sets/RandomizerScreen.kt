package com.example.myapplication.ui.sets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.ui.practice.IntStepper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RandomizerScreen(
    onBack: () -> Unit,
    onSave: (editorRoute: String) -> Unit,
    viewModel: RandomizerViewModel = viewModel(factory = RandomizerViewModel.Factory),
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text("Randomizer") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        if (!viewModel.loaded) return@Scaffold
        val steps = viewModel.steps
        Column(
            Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column {
                IntStepper(
                    label = "Moves",
                    value = viewModel.length,
                    range = RandomizerViewModel.LENGTHS,
                    onValueChange = viewModel::changeLength,
                )
                FilterChip(
                    selected = viewModel.skipLearning,
                    onClick = viewModel::toggleSkipLearning,
                    label = { Text("Skip Learning moves") },
                )
            }

            val emptyReason = viewModel.emptyReason
            if (emptyReason != null) {
                Text(
                    emptyReason,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Card(Modifier.fillMaxWidth()) {
                    steps.forEachIndexed { index, step ->
                        if (index > 0) HorizontalDivider()
                        Row(
                            Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${index + 1}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.widthIn(min = 28.dp),
                            )
                            Column(Modifier.weight(1f)) {
                                Text(step.move.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    step.categoryName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { viewModel.swap(index) }, enabled = step.canSwap) {
                                Icon(Icons.Filled.Refresh, contentDescription = "Swap ${step.move.name}")
                            }
                        }
                    }
                }
                if (steps.size < viewModel.length) {
                    Text(
                        "That's the longest set your moves make without repeating one back to back. " +
                            "Add more moves to the library for longer sets.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = viewModel::shuffle,
                    enabled = steps.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Shuffle")
                }
                Button(
                    onClick = { viewModel.draftRoute()?.let(onSave) },
                    enabled = steps.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                ) { Text("Save as set") }
            }

            if (viewModel.unplacedCategories.isNotEmpty()) {
                Text(
                    "Left out: ${viewModel.unplacedCategories.joinToString()}. The randomizer places moves by " +
                        "category and can't tell where these go in a set.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "Sets run from standing down to the floor: toprock, a go-down, footwork and power, ending on a " +
                    "freeze when you have one. Once you're down, nothing leads back up to toprock. " +
                    "Tap the arrow next to a move to swap just that one.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
