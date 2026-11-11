package com.example.myapplication.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.data.model.CategorySummary
import com.example.myapplication.ui.theme.CypherTheme

@Composable
fun LibraryScreen(
    onCategoryClick: (Long) -> Unit,
    onAddMove: () -> Unit,
    onManageCategories: () -> Unit,
    onOpenBackup: () -> Unit,
    viewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory),
) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    LibraryContent(categories, onCategoryClick, onAddMove, onManageCategories, onOpenBackup)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryContent(
    categories: List<CategorySummary>,
    onCategoryClick: (Long) -> Unit,
    onAddMove: () -> Unit,
    onManageCategories: () -> Unit,
    onOpenBackup: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text("Library") },
                actions = {
                    IconButton(onClick = onManageCategories) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit categories")
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Backup & transfer") },
                                onClick = {
                                    menuOpen = false
                                    onOpenBackup()
                                },
                            )
                        }
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
            if (categories.isNotEmpty() && categories.all { it.totalMoves == 0 }) {
                item {
                    Text(
                        "Your library is empty. Tap + to add your first move.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }
            items(categories, key = { it.categoryId }) { category ->
                Card(onClick = { onCategoryClick(category.categoryId) }, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(category.categoryName, style = MaterialTheme.typography.titleMedium)
                        Text(moveCountLabel(category.totalMoves), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

private fun moveCountLabel(count: Int): String = when (count) {
    0 -> "No moves yet"
    1 -> "1 move"
    else -> "$count moves"
}

@Preview(showBackground = true)
@Composable
private fun LibraryContentPreview() {
    CypherTheme {
        LibraryContent(
            listOf(
                CategorySummary(1, "Toprock", 6),
                CategorySummary(2, "Power", 2),
                CategorySummary(3, "Freezes", 0),
            ),
            onCategoryClick = {},
            onAddMove = {},
            onManageCategories = {},
            onOpenBackup = {},
        )
    }
}
