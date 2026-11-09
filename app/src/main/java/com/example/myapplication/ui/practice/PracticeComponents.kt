package com.example.myapplication.ui.practice

import android.content.Context
import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.myapplication.data.dao.SessionSummary
import com.example.myapplication.data.model.DanceSet
import com.example.myapplication.data.model.DrillType
import com.example.myapplication.data.model.Move
import com.example.myapplication.data.model.PracticeDrill
import com.example.myapplication.data.model.PracticeSession
import com.example.myapplication.data.model.TimerSettings
import com.example.myapplication.ui.library.LibraryRoutes
import com.example.myapplication.ui.sets.SetRoutes
import com.example.myapplication.ui.videos.VideoRoutes
import com.example.myapplication.ui.videos.formatDuration

internal fun plural(count: Int, noun: String) = if (count == 1) "1 $noun" else "$count ${noun}s"

/** Training sets and reps, e.g. "3 sets × 10 reps". */
internal fun targetText(sets: Int, reps: Int?): String =
    if (reps == null) plural(sets, "set") else "${plural(sets, "set")} × ${plural(reps, "rep")}"

internal fun timerText(timer: TimerSettings): String =
    "${plural(timer.rounds, "round")} · ${formatDuration(timer.activeSec * 1000L)} active · " +
        "${formatDuration(timer.restSec * 1000L)} rest"

/** Which tab a drill's target lives in. */
internal fun drillSource(type: DrillType): String = when (type) {
    DrillType.MOVE -> "From Library"
    DrillType.SET -> "From Sets"
    DrillType.FREE -> "Block"
}

/** The screen for a drill's move or set, if it has one. */
internal fun drillRoute(moveId: Long?, setId: Long?): String? = when {
    moveId != null -> LibraryRoutes.move(moveId)
    setId != null -> SetRoutes.detail(setId)
    else -> null
}

/** What to play for a drill: its set's combo when there is one, otherwise the top video. */
internal fun watchRoute(setId: Long?, hasCombo: Boolean, topVideoId: Long?): String? = when {
    hasCombo && setId != null -> VideoRoutes.combo(setId)
    topVideoId != null -> VideoRoutes.video(topVideoId)
    else -> null
}

internal fun dateAndTime(context: Context, millis: Long): String = DateUtils.formatDateTime(
    context,
    millis,
    DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_ALL or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME,
)

internal fun shortDate(context: Context, millis: Long): String =
    DateUtils.formatDateTime(context, millis, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH)

internal fun monthAndYear(context: Context, millis: Long): String = DateUtils.formatDateTime(
    context,
    millis,
    DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR or DateUtils.FORMAT_NO_MONTH_DAY,
)

/** How long a session ran. One that was never finished counts up to its last change. */
internal val PracticeSession.durationMs: Long get() = (endedAt ?: lastActiveAt) - startedAt

/** A session's totals, e.g. "3 rounds · 4 sets · 40 reps". */
internal fun SessionSummary.statsText(): String = listOfNotNull(
    session.roundsCompleted.takeIf { it > 0 }?.let { plural(it, "round") },
    setCount.takeIf { it > 0 }?.let { plural(it, "set") },
    repCount.takeIf { it > 0 }?.let { plural(it, "rep") },
).joinToString(" · ").ifEmpty { "Nothing counted" }

/** A label with − and + buttons. A null handler disables that button. */
@Composable
internal fun Stepper(
    label: String,
    value: String,
    onDecrease: (() -> Unit)?,
    onIncrease: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = { onDecrease?.invoke() },
            enabled = onDecrease != null,
            modifier = Modifier.size(40.dp).semantics { contentDescription = "Decrease $label" },
        ) { Text("−", style = MaterialTheme.typography.titleLarge) }
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 44.dp),
        )
        IconButton(
            onClick = { onIncrease?.invoke() },
            enabled = onIncrease != null,
            modifier = Modifier.size(40.dp).semantics { contentDescription = "Increase $label" },
        ) { Text("+", style = MaterialTheme.typography.titleLarge) }
    }
}

@Composable
internal fun IntStepper(
    label: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    format: (Int) -> String = Int::toString,
) {
    Stepper(
        label = label,
        value = format(value),
        onDecrease = (value - 1).takeIf { it >= range.first }?.let { { onValueChange(it) } },
        onIncrease = (value + 1).takeIf { it <= range.last }?.let { { onValueChange(it) } },
        modifier = modifier,
    )
}

/** Fine steps under a minute, where round lengths are most often tuned. */
internal fun stepDuration(seconds: Int, up: Boolean): Int = when {
    up -> seconds + if (seconds < 60) 5 else 15
    else -> seconds - if (seconds <= 60) 5 else 15
}

@Composable
private fun DurationStepper(label: String, seconds: Int, range: IntRange, onValueChange: (Int) -> Unit) {
    Stepper(
        label = label,
        value = formatDuration(seconds * 1000L),
        onDecrease = stepDuration(seconds, up = false).takeIf { it >= range.first }?.let { { onValueChange(it) } },
        onIncrease = stepDuration(seconds, up = true).takeIf { it <= range.last }?.let { { onValueChange(it) } },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
internal fun TimerSettingsEditor(settings: TimerSettings, onChange: (TimerSettings) -> Unit) {
    Column {
        IntStepper(
            label = "Rounds",
            value = settings.rounds,
            range = 1..50,
            onValueChange = { onChange(settings.copy(rounds = it)) },
            modifier = Modifier.fillMaxWidth(),
        )
        DurationStepper("Active", settings.activeSec, 5..1800) { onChange(settings.copy(activeSec = it)) }
        DurationStepper("Rest", settings.restSec, 0..600) { onChange(settings.copy(restSec = it)) }
    }
}

/** Something a drill can be built around, before it has sets and reps. */
data class DrillChoice(
    val type: DrillType,
    val moveId: Long? = null,
    val setId: Long? = null,
    val title: String,
) {
    /** Blocks like warm-ups are done once; moves and sets are drilled. */
    val defaultSets: Int get() = if (type == DrillType.FREE) 1 else PracticeDrill.DEFAULT_TARGET_SETS
    val defaultReps: Int? get() = if (type == DrillType.FREE) null else PracticeDrill.DEFAULT_TARGET_REPS
}

/** Buttons that add a move from the Library, a set from Sets, or a free-form block. */
@Composable
internal fun AddDrillButtons(moves: List<Move>, sets: List<DanceSet>, onPick: (DrillChoice) -> Unit) {
    var picking by rememberSaveable { mutableStateOf<DrillType?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Add a drill", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                Triple("Move", DrillType.MOVE, moves.isNotEmpty()),
                Triple("Set", DrillType.SET, sets.isNotEmpty()),
                Triple("Block", DrillType.FREE, true),
            ).forEach { (label, type, enabled) ->
                OutlinedButton(
                    onClick = { picking = type },
                    enabled = enabled,
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier.weight(1f),
                ) { Text(label) }
            }
        }
        if (moves.isEmpty() && sets.isEmpty()) {
            Text(
                "Moves and sets you build in their tabs can be drilled here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    val close = { picking = null }
    when (picking) {
        DrillType.MOVE -> ListPickerDialog("Add move", moves, { it.id }, { it.name }, close) {
            close()
            onPick(DrillChoice(DrillType.MOVE, moveId = it.id, title = it.name))
        }
        DrillType.SET -> ListPickerDialog("Add set", sets, { it.id }, { it.name }, close) {
            close()
            onPick(DrillChoice(DrillType.SET, setId = it.id, title = it.name))
        }
        DrillType.FREE -> BlockNameDialog(close) {
            close()
            onPick(DrillChoice(DrillType.FREE, title = it))
        }
        null -> Unit
    }
}

@Composable
private fun <T> ListPickerDialog(
    title: String,
    options: List<T>,
    key: (T) -> Long,
    label: (T) -> String,
    onDismiss: () -> Unit,
    onPick: (T) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(Modifier.heightIn(max = 400.dp)) {
                items(options, key = key) { option ->
                    Text(
                        label(option),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(option) }
                            .padding(vertical = 12.dp),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun BlockNameDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add block") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name, like Warm-up") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
        },
        confirmButton = {
            TextButton(onClick = { onAdd(name.trim()) }, enabled = name.isNotBlank()) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
