package com.example.myapplication.ui.practice

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.data.dao.SessionDrillView
import com.example.myapplication.data.model.TimerSettings
import com.example.myapplication.ui.theme.Blaze
import com.example.myapplication.ui.theme.Gold
import com.example.myapplication.ui.theme.Ink
import com.example.myapplication.ui.theme.InkHigh
import com.example.myapplication.ui.videos.formatDuration
import kotlinx.coroutines.delay
import kotlin.math.ceil

/** The screen you train on: the round timer up top, and a sets and reps counter below it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeLiveScreen(
    onFinished: (sessionId: Long) -> Unit,
    onDiscarded: () -> Unit,
    onNavigate: (route: String) -> Unit,
    viewModel: PracticeLiveViewModel = viewModel(factory = PracticeLiveViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val moves by viewModel.allMoves.collectAsStateWithLifecycle()
    val sets by viewModel.allSets.collectAsStateWithLifecycle()
    val session = state.session
    var confirmingEnd by rememberSaveable { mutableStateOf(false) }
    var adjustingTimer by rememberSaveable { mutableStateOf(false) }

    // The phone sits on the floor mid-round; it mustn't lock.
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    // The session was discarded: leave the screen.
    LaunchedEffect(state.loading, session) {
        if (!state.loading && session == null) onDiscarded()
    }

    BackHandler { confirmingEnd = true }

    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(1_000)
            value = System.currentTimeMillis()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.navigationBars,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(session?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        session?.let {
                            Text(
                                formatDuration((now - it.startedAt).coerceAtLeast(0)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { confirmingEnd = true }) {
                        Icon(Icons.Filled.Close, contentDescription = "End practice")
                    }
                },
                actions = { TextButton(onClick = { confirmingEnd = true }) { Text("Finish") } },
            )
        },
    ) { innerPadding ->
        if (session == null) return@Scaffold
        LazyColumn(
            modifier = Modifier.padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "timer") {
                TimerPanel(
                    timer = viewModel.timerUi,
                    onStart = viewModel::startTimer,
                    onPause = viewModel::pauseTimer,
                    onSkip = viewModel::skipPhase,
                    onReset = viewModel::resetTimer,
                    onAdjust = { adjustingTimer = true },
                )
            }
            state.current?.let { drill ->
                item(key = "counter") {
                    CounterCard(
                        drill = drill,
                        onAddRep = { viewModel.addRep(drill.drill.position) },
                        onUndoRep = { viewModel.undoRep(drill.drill.position) },
                        onFinishSet = { viewModel.finishSet(drill) },
                        onNavigate = onNavigate,
                    )
                }
            }
            if (state.drills.size > 1) {
                item(key = "drills-title") { Text("Drills", style = MaterialTheme.typography.titleSmall) }
                items(state.drills, key = { it.drill.position }) { drill ->
                    DrillListRow(
                        drill = drill,
                        selected = drill.drill.position == state.current?.drill?.position,
                        onClick = { viewModel.selectDrill(drill.drill.position) },
                    )
                }
            }
            if (state.drills.isEmpty()) {
                item(key = "empty") {
                    Text(
                        "Run the timer as it is, or add a drill to count sets and reps.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item(key = "add") { AddDrillButtons(moves, sets, viewModel::addDrill) }
        }
    }

    if (confirmingEnd && session != null) {
        EndPracticeDialog(
            initialNotes = session.notes,
            onSave = { notes ->
                confirmingEnd = false
                viewModel.finish(notes) { onFinished(viewModel.sessionId) }
            },
            onDiscard = {
                confirmingEnd = false
                viewModel.discard()
            },
            onKeepGoing = { confirmingEnd = false },
        )
    }

    if (adjustingTimer) {
        AdjustTimerDialog(
            initial = viewModel.timerUi.settings,
            onApply = {
                adjustingTimer = false
                viewModel.applyTimerSettings(it)
            },
            onDismiss = { adjustingTimer = false },
        )
    }
}

private data class PhaseLook(val background: Color, val content: Color, val title: String, val subtitle: String)

@Composable
private fun phaseLook(timer: TimerUi): PhaseLook {
    val rounds = timer.settings.rounds
    val position = timer.position
    return when {
        !timer.started -> PhaseLook(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            "READY",
            timerText(timer.settings),
        )
        position.phase == TimerPhase.GET_READY -> PhaseLook(Gold, Ink, "GET READY", "Round 1 of $rounds next")
        position.phase == TimerPhase.ACTIVE -> PhaseLook(Blaze, Ink, "ACTIVE", "Round ${position.round} of $rounds")
        position.phase == TimerPhase.REST -> PhaseLook(InkHigh, Gold, "REST", "Round ${position.round + 1} of $rounds next")
        else -> PhaseLook(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            "DONE",
            plural(rounds, "round"),
        )
    }
}

@Composable
private fun TimerPanel(
    timer: TimerUi,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onSkip: () -> Unit,
    onReset: () -> Unit,
    onAdjust: () -> Unit,
) {
    val look = phaseLook(timer)
    val background by animateColorAsState(look.background, label = "timer background")
    val position = timer.position
    val seconds = if (timer.started) ceil(position.remainingMs / 1000.0).toLong() else timer.settings.activeSec.toLong()
    val done = position.phase == TimerPhase.DONE

    Surface(
        color = background,
        contentColor = look.content,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(look.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                    Text(look.subtitle, style = MaterialTheme.typography.bodyMedium)
                }
                IconButton(onClick = onAdjust) {
                    Icon(Icons.Filled.Settings, contentDescription = "Adjust timer")
                }
            }
            Text(
                formatDuration(seconds * 1000),
                fontSize = 96.sp,
                lineHeight = 104.sp,
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.displayLarge.copy(fontFeatureSettings = "tnum"),
            )
            if (timer.started && !done && position.phaseMs > 0) {
                LinearProgressIndicator(
                    progress = { 1f - position.remainingMs.toFloat() / position.phaseMs },
                    color = look.content,
                    trackColor = look.content.copy(alpha = 0.25f),
                    drawStopIndicator = {},
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                val text = ButtonDefaults.textButtonColors(contentColor = look.content)
                if (timer.started) {
                    TextButton(onClick = onReset, colors = text) { Text("Reset") }
                }
                Button(
                    onClick = if (timer.running) onPause else onStart,
                    colors = ButtonDefaults.buttonColors(containerColor = look.content, contentColor = background),
                    modifier = Modifier.width(140.dp).height(56.dp),
                ) {
                    Text(
                        when {
                            timer.running -> "Pause"
                            done -> "Run again"
                            timer.started -> "Resume"
                            else -> "Start"
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                if (timer.started && !done) {
                    TextButton(onClick = onSkip, colors = text) { Text("Skip") }
                }
            }
        }
    }
}

@Composable
private fun CounterCard(
    drill: SessionDrillView,
    onAddRep: () -> Unit,
    onUndoRep: () -> Unit,
    onFinishSet: () -> Unit,
    onNavigate: (route: String) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val target = drill.drill.targetReps
    val current = drill.drill.currentReps
    val repTargetMet = target != null && current >= target
    val watch = watchRoute(drill.drill.setId, drill.hasCombo, drill.topVideoId)
    val canUndo = current > 0 || drill.setsDone > 0

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(drill.displayTitle, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${drillSource(drill.drill.type)} · ${targetText(drill.drill.targetSets, target)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (watch != null) {
                    TextButton(onClick = { onNavigate(watch) }) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Text("Watch")
                    }
                }
            }

            Text(
                if (drill.targetMet) {
                    "Set ${drill.setsDone + 1} · past your ${plural(drill.drill.targetSets, "set")}"
                } else {
                    "Set ${drill.setsDone + 1} of ${drill.drill.targetSets}"
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    if (target != null) "$current / $target" else "$current",
                    fontSize = 56.sp,
                    lineHeight = 64.sp,
                    fontWeight = FontWeight.Black,
                    color = if (repTargetMet) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.displayMedium.copy(fontFeatureSettings = "tnum"),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onUndoRep,
                    enabled = canUndo,
                    modifier = Modifier.height(88.dp).width(88.dp),
                    contentPadding = PaddingValues(0.dp),
                ) { Text("−1", style = MaterialTheme.typography.titleLarge) }
                Button(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onAddRep()
                    },
                    modifier = Modifier.weight(1f).height(88.dp),
                ) { Text("+1 rep", style = MaterialTheme.typography.headlineSmall) }
            }

            val finishEnabled = current > 0 || target == null
            if (repTargetMet) {
                Button(onClick = onFinishSet, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Finish set") }
            } else {
                FilledTonalButton(
                    onClick = onFinishSet,
                    enabled = finishEnabled,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) { Text("Finish set") }
            }
        }
    }
}

@Composable
private fun DrillListRow(drill: SessionDrillView, selected: Boolean, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
        ),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(drill.displayTitle, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${drill.setsDone} of ${plural(drill.drill.targetSets, "set")} · ${plural(drill.repsDone, "rep")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (drill.targetMet) {
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Filled.Check, contentDescription = "Done", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun EndPracticeDialog(
    initialNotes: String,
    onSave: (notes: String) -> Unit,
    onDiscard: () -> Unit,
    onKeepGoing: () -> Unit,
) {
    var notes by rememberSaveable { mutableStateOf(initialNotes) }
    var confirmingDiscard by rememberSaveable { mutableStateOf(false) }

    if (confirmingDiscard) {
        AlertDialog(
            onDismissRequest = { confirmingDiscard = false },
            title = { Text("Discard this practice?") },
            text = { Text("Nothing from it is saved to your log.") },
            confirmButton = { TextButton(onClick = onDiscard) { Text("Discard") } },
            dismissButton = { TextButton(onClick = { confirmingDiscard = false }) { Text("Cancel") } },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onKeepGoing,
        title = { Text("Finish practice?") },
        text = {
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("How did it go? (optional)") },
                minLines = 3,
            )
        },
        confirmButton = { TextButton(onClick = { onSave(notes) }) { Text("Save") } },
        dismissButton = {
            Row {
                TextButton(onClick = { confirmingDiscard = true }) { Text("Discard") }
                TextButton(onClick = onKeepGoing) { Text("Keep going") }
            }
        },
    )
}

@Composable
private fun AdjustTimerDialog(initial: TimerSettings, onApply: (TimerSettings) -> Unit, onDismiss: () -> Unit) {
    var rounds by rememberSaveable { mutableStateOf(initial.rounds) }
    var activeSec by rememberSaveable { mutableStateOf(initial.activeSec) }
    var restSec by rememberSaveable { mutableStateOf(initial.restSec) }
    val settings = TimerSettings(rounds, activeSec, restSec)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adjust timer") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Applies to this practice only and starts the timer over.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TimerSettingsEditor(settings) {
                    rounds = it.rounds
                    activeSec = it.activeSec
                    restSec = it.restSec
                }
            }
        },
        confirmButton = { TextButton(onClick = { onApply(settings) }) { Text("Apply") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
