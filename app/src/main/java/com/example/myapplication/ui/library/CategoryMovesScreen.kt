package com.example.myapplication.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryMovesScreen(
    onBack: () -> Unit,
    onMoveClick: (Long) -> Unit,
    onAddMove: (categoryId: Long) -> Unit,
    viewModel: CategoryMovesViewModel = viewModel(factory = CategoryMovesViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(state.categoryName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onAddMove(viewModel.categoryId) }) {
                Icon(Icons.Filled.Add, contentDescription = "Add move")
            }
        },
    ) { innerPadding ->
        if (state.rows.isEmpty()) {
            Box(
                Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("No moves in ${state.categoryName} yet. Tap + to add one.")
            }
        } else {
            LazyColumn(Modifier.padding(innerPadding)) {
                items(state.rows, key = { it.move.id }) { row ->
                    val move = row.move
                    ListItem(
                        modifier = Modifier
                            .clickable { onMoveClick(move.id) }
                            .padding(start = (row.depth * 24).dp),
                        headlineContent = { Text(move.name) },
                        supportingContent = { Text(move.status.label) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
