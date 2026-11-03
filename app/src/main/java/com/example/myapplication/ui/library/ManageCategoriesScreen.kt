package com.example.myapplication.ui.library

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.data.model.Category

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageCategoriesScreen(
    onBack: () -> Unit,
    viewModel: ManageCategoriesViewModel = viewModel(factory = ManageCategoriesViewModel.Factory),
) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Category?>(null) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text("Categories") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { adding = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add category")
            }
        },
    ) { innerPadding ->
        LazyColumn(Modifier.padding(innerPadding)) {
            itemsIndexed(categories, key = { _, item -> item.category.id }) { index, item ->
                ListItem(
                    headlineContent = { Text(item.category.name) },
                    supportingContent = {
                        Text(
                            when (item.moveCount) {
                                0 -> "No moves"
                                1 -> "1 move"
                                else -> "${item.moveCount} moves"
                            }
                        )
                    },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { viewModel.move(index, -1) }, enabled = index > 0) {
                                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up")
                            }
                            IconButton(onClick = { viewModel.move(index, 1) }, enabled = index < categories.lastIndex) {
                                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down")
                            }
                            IconButton(onClick = { renaming = item.category }) {
                                Icon(Icons.Filled.Edit, contentDescription = "Rename")
                            }
                            // Categories that still hold moves can't be deleted.
                            IconButton(onClick = { viewModel.delete(item) }, enabled = item.moveCount == 0) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete")
                            }
                        }
                    },
                )
                HorizontalDivider()
            }
        }
    }

    if (adding) {
        NameDialog(
            title = "New category",
            initial = "",
            onDismiss = { adding = false },
            onConfirm = {
                viewModel.add(it)
                adding = false
            },
        )
    }
    renaming?.let { category ->
        NameDialog(
            title = "Rename category",
            initial = category.name,
            onDismiss = { renaming = null },
            onConfirm = {
                viewModel.rename(category, it)
                renaming = null
            },
        )
    }
}

@Composable
private fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true, label = { Text("Name") })
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
