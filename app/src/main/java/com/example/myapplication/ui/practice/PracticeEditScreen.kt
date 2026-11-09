package com.example.myapplication.ui.practice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.myapplication.data.dao.ChainDao
import com.example.myapplication.data.dao.LibraryDao
import com.example.myapplication.data.dao.PracticeDao
import com.example.myapplication.data.model.DanceSet
import com.example.myapplication.data.model.DrillType
import com.example.myapplication.data.model.Move
import com.example.myapplication.data.model.Practice
import com.example.myapplication.data.model.PracticeDrill
import com.example.myapplication.data.model.TimerSettings
import com.example.myapplication.ui.breakingApp
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A drill being edited. */
data class EditableDrill(
    val type: DrillType,
    val moveId: Long?,
    val setId: Long?,
    val title: String,
    val targetSets: Int,
    val targetReps: Int?,
)

/** Creates a new practice, or edits one when the route carries a practice id. */
class PracticeEditViewModel(
    savedStateHandle: SavedStateHandle,
    private val dao: PracticeDao,
    libraryDao: LibraryDao,
    chainDao: ChainDao,
) : ViewModel() {
    private val practiceId: Long = savedStateHandle[PracticeRoutes.ARG_PRACTICE_ID] ?: PracticeRoutes.NO_ID
    val isNew: Boolean get() = practiceId == PracticeRoutes.NO_ID
    private var original: Practice? = null

    var loaded by mutableStateOf(false)
        private set
    var name by mutableStateOf("")
    var notes by mutableStateOf("")
    var timer by mutableStateOf(TimerSettings())
    val drills = mutableStateListOf<EditableDrill>()

    val allMoves: StateFlow<List<Move>> = libraryDao.observeMoves()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val allSets: StateFlow<List<DanceSet>> = chainDao.observeSets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            if (!isNew) {
                dao.getPractice(practiceId)?.let { practice ->
                    original = practice
                    name = practice.name
                    notes = practice.notes
                    timer = practice.timer
                    drills.addAll(
                        dao.getDrills(practiceId).map {
                            EditableDrill(it.drill.type, it.drill.moveId, it.drill.setId, it.title, it.drill.targetSets, it.drill.targetReps)
                        }
                    )
                }
            }
            loaded = true
        }
    }

    fun addDrill(choice: DrillChoice) {
        drills.add(EditableDrill(choice.type, choice.moveId, choice.setId, choice.title, choice.defaultSets, choice.defaultReps))
    }

    fun update(index: Int, drill: EditableDrill) {
        drills[index] = drill
    }

    fun removeAt(index: Int) {
        drills.removeAt(index)
    }

    fun moveUp(index: Int) {
        if (index > 0) drills.add(index - 1, drills.removeAt(index))
    }

    fun moveDown(index: Int) {
        if (index < drills.lastIndex) drills.add(index + 1, drills.removeAt(index))
    }

    val canSave: Boolean get() = loaded && name.isNotBlank()

    /** Saves and reports the practice's id. */
    fun save(onSaved: (Long) -> Unit) {
        if (!canSave) return
        viewModelScope.launch {
            val practice = (original ?: Practice(name = "")).copy(name = name.trim(), notes = notes.trim(), timer = timer)
            val rows = drills.map {
                PracticeDrill(
                    practiceId = 0,
                    position = 0,
                    type = it.type,
                    moveId = it.moveId,
                    setId = it.setId,
                    label = if (it.type == DrillType.FREE) it.title else "",
                    targetSets = it.targetSets,
                    targetReps = it.targetReps,
                )
            }
            onSaved(dao.savePractice(practice, rows))
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val database = breakingApp().database
                PracticeEditViewModel(
                    createSavedStateHandle(),
                    database.practiceDao(),
                    database.libraryDao(),
                    database.chainDao(),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeEditScreen(
    onBack: () -> Unit,
    onSaved: (practiceId: Long, isNew: Boolean) -> Unit,
    viewModel: PracticeEditViewModel = viewModel(factory = PracticeEditViewModel.Factory),
) {
    val moves by viewModel.allMoves.collectAsStateWithLifecycle()
    val sets by viewModel.allSets.collectAsStateWithLifecycle()

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.isNew) "New practice" else "Edit practice") },
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
                Text("Round timer", style = MaterialTheme.typography.labelLarge)
                TimerSettingsEditor(viewModel.timer) { viewModel.timer = it }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Drills", style = MaterialTheme.typography.labelLarge)
                viewModel.drills.forEachIndexed { index, drill ->
                    DrillEditor(
                        drill = drill,
                        isFirst = index == 0,
                        isLast = index == viewModel.drills.lastIndex,
                        onChange = { viewModel.update(index, it) },
                        onMoveUp = { viewModel.moveUp(index) },
                        onMoveDown = { viewModel.moveDown(index) },
                        onRemove = { viewModel.removeAt(index) },
                    )
                }
                AddDrillButtons(moves, sets, viewModel::addDrill)
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
private fun DrillEditor(
    drill: EditableDrill,
    isFirst: Boolean,
    isLast: Boolean,
    onChange: (EditableDrill) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(drill.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        drillSource(drill.type),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onMoveUp, enabled = !isFirst) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move ${drill.title} earlier")
                }
                IconButton(onClick = onMoveDown, enabled = !isLast) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move ${drill.title} later")
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Filled.Close, contentDescription = "Remove ${drill.title}")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IntStepper(
                    label = "Sets",
                    value = drill.targetSets,
                    range = 1..50,
                    onValueChange = { onChange(drill.copy(targetSets = it)) },
                    modifier = Modifier.weight(1f),
                )
                // Zero stands for "no rep target", for drills that are only counted in sets.
                IntStepper(
                    label = "Reps",
                    value = drill.targetReps ?: 0,
                    range = 0..500,
                    onValueChange = { onChange(drill.copy(targetReps = it.takeIf { reps -> reps > 0 })) },
                    modifier = Modifier.weight(1f),
                    format = { if (it == 0) "Any" else it.toString() },
                )
            }
        }
    }
}
