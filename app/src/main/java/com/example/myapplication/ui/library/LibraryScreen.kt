package com.example.myapplication.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import java.util.Locale

@Composable
fun LibraryScreen(
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory),
) {
    val scores by viewModel.categoryScores.collectAsStateWithLifecycle()
    CategoryScoreList(scores, modifier)
}

@Composable
private fun CategoryScoreList(scores: List<CategoryScore>, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(scores, key = { it.categoryId }) { score ->
            Card(Modifier.fillMaxWidth()) {
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

private fun scoreSummary(score: CategoryScore): String = when {
    score.totalMoves == 0 -> "No moves yet"
    score.averageScore == null -> "${score.totalMoves} unrated"
    else -> String.format(Locale.getDefault(), "%.1f · %d of %d rated", score.averageScore, score.ratedMoves, score.totalMoves)
}

@Preview(showBackground = true)
@Composable
private fun CategoryScoreListPreview() {
    MyApplicationTheme {
        CategoryScoreList(
            listOf(
                CategoryScore(1, "Toprock", 3.4, 5, 6),
                CategoryScore(2, "Power", null, 0, 2),
                CategoryScore(3, "Freezes", null, 0, 0),
            )
        )
    }
}
