package com.example.myapplication.ui.sets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.myapplication.data.BasicSetOption
import com.example.myapplication.data.BasicSets
import com.example.myapplication.data.dao.ChainDao
import com.example.myapplication.data.dao.LibraryDao
import com.example.myapplication.data.dao.SetSummary
import com.example.myapplication.ui.breakingApp
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class SetsUiState(
    val sets: List<SetSummary> = emptyList(),
    /** Basic sets the dancer can add: all their moves are in the library and the dancer doesn't have them yet. */
    val basicSets: List<BasicSetOption> = emptyList(),
)

class SetsViewModel(chainDao: ChainDao, libraryDao: LibraryDao) : ViewModel() {
    val uiState: StateFlow<SetsUiState> = combine(
        chainDao.observeSetSummaries(),
        chainDao.observeAllSetChains(),
        libraryDao.observeMoves(),
    ) { sets, chains, moves ->
        SetsUiState(
            sets = sets,
            basicSets = BasicSets.available(
                library = moves,
                setNames = sets.map { it.name },
                setChains = chains.groupBy({ it.setId }, { it.moveId }).values,
            ),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SetsUiState())

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val database = breakingApp().database
                SetsViewModel(database.chainDao(), database.libraryDao())
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetsScreen(
    onSetClick: (Long) -> Unit,
    onAddSet: () -> Unit,
    onRandomize: () -> Unit,
    onAddBasicSet: (BasicSetOption) -> Unit,
    viewModel: SetsViewModel = viewModel(factory = SetsViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = { TopAppBar(title = { Text("Sets") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddSet) {
                Icon(Icons.Filled.Add, contentDescription = "New set")
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "randomize") {
                Button(onClick = onRandomize, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Randomize a set")
                }
            }
            if (state.sets.isEmpty()) {
                item(key = "empty") {
                    Text(
                        buildString {
                            append("No sets yet. Tap + to chain moves from your library into a combo, or randomize one.")
                            if (state.basicSets.isNotEmpty()) append(" You can also start from a basic set below.")
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
            }
            items(state.sets, key = { it.setId }) { set ->
                Card(Modifier.fillMaxWidth().clickable { onSetClick(set.setId) }) {
                    Column(Modifier.padding(16.dp)) {
                        Text(set.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (set.moveCount == 1) "1 move" else "${set.moveCount} moves",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (state.basicSets.isNotEmpty()) {
                item(key = "basic-header") {
                    Column(Modifier.padding(top = 16.dp)) {
                        Text("Basic sets", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Common combos of foundation moves. Tap one to add it to your sets.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(state.basicSets, key = { "basic:${it.set.name}" }) { option ->
                    OutlinedCard(onClick = { onAddBasicSet(option) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(option.set.name, style = MaterialTheme.typography.titleMedium)
                            Text(option.moves.joinToString(" → ") { it.name }, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                option.set.notes,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
