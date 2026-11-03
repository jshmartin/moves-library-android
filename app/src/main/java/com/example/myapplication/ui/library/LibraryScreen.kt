package com.example.myapplication.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.data.model.CategoryScore
import com.example.myapplication.ui.theme.MyApplicationTheme

@Composable
fun LibraryScreen(
    onCategoryClick: (Long) -> Unit,
    onAddMove: () -> Unit,
    onManageCategories: () -> Unit,
    viewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory),
) {
    val scores by viewModel.categoryScores.collectAsStateWithLifecycle()
    LibraryContent(scores, onCategoryClick, onAddMove, onManageCategories)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryContent(
    scores: List<CategoryScore>,
    onCategoryClick: (Long) -> Unit,
    onAddMove: () -> Unit,
    onManageCategories: () -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text("Library") },
                actions = {
                    IconButton(onClick = onManageCategories) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit categories")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddMove) {
                Icon(Icons.Filled.Add, contentDescription = "Add move")
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (scores.isNotEmpty() && scores.all { it.totalMoves == 0 }) {
                item {
                    Text(
                        "Your library is empty. Tap + to add your first move.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }
            items(scores, key = { it.categoryId }) { score ->
                Card(onClick = { onCategoryClick(score.categoryId) }, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(score.categoryName, style = MaterialTheme.typography.titleMedium)
                        Text(scoreSummary(score), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

private fun scoreSummary(score: CategoryScore): String = when {
    score.totalMoves == 0 -> "No moves yet"
    score.averageScore == null -> "${score.totalMoves} unrated"
    else -> "${formatAverage(score.averageScore)} · ${score.ratedMoves} of ${score.totalMoves} rated"
}

@Preview(showBackground = true)
@Composable
private fun LibraryContentPreview() {
    MyApplicationTheme {
        LibraryContent(
            listOf(
                CategoryScore(1, "Toprock", 3.4, 5, 6),
                CategoryScore(2, "Power", null, 0, 2),
                CategoryScore(3, "Freezes", null, 0, 0),
            ),
            onCategoryClick = {},
            onAddMove = {},
            onManageCategories = {},
        )
    }
}
