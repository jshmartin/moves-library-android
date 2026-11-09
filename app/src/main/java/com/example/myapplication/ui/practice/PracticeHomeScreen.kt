package com.example.myapplication.ui.practice

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.myapplication.data.dao.PracticeDao
import com.example.myapplication.data.dao.PracticeSummary
import com.example.myapplication.data.dao.SessionSummary
import com.example.myapplication.data.model.PracticeSession
import com.example.myapplication.ui.breakingApp
import com.example.myapplication.ui.videos.formatDuration
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PracticeHomeViewModel(private val dao: PracticeDao) : ViewModel() {
    val practices: StateFlow<List<PracticeSummary>> = dao.observePracticeSummaries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val sessions: StateFlow<List<SessionSummary>> = dao.observeFinishedSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** A session the app was closed in the middle of. */
    val openSession: StateFlow<PracticeSession?> = dao.observeOpenSession()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun startQuick(onStarted: (sessionId: Long) -> Unit) {
        viewModelScope.launch { onStarted(dao.startQuickSession("Quick practice")) }
    }

    fun start(practiceId: Long, onStarted: (sessionId: Long) -> Unit) {
        viewModelScope.launch { onStarted(dao.startSession(practiceId)) }
    }

    fun closeOpenSession() {
        viewModelScope.launch { dao.closeOpenSessions() }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { PracticeHomeViewModel(breakingApp().database.practiceDao()) }
        }
    }
}

internal data class LogTotals(val weekCount: Int, val weekMs: Long, val allCount: Int, val allMs: Long)

internal fun logTotals(sessions: List<SessionSummary>, now: Long): LogTotals {
    val week = sessions.filter { now - it.session.startedAt < WEEK_MS }
    return LogTotals(
        weekCount = week.size,
        weekMs = week.sumOf { it.session.durationMs },
        allCount = sessions.size,
        allMs = sessions.sumOf { it.session.durationMs },
    )
}

private const val WEEK_MS = 7 * 24 * 60 * 60 * 1000L

/** Hours and minutes, for totals too long to read as a clock. */
internal fun hoursAndMinutes(ms: Long): String {
    val minutes = ms / 60_000
    return if (minutes < 60) "${minutes}m" else "${minutes / 60}h ${minutes % 60}m"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeHomeScreen(
    onNavigate: (route: String) -> Unit,
    viewModel: PracticeHomeViewModel = viewModel(factory = PracticeHomeViewModel.Factory),
) {
    val practices by viewModel.practices.collectAsStateWithLifecycle()
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val open by viewModel.openSession.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val openLive: (Long) -> Unit = { onNavigate(PracticeRoutes.live(it)) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = { TopAppBar(title = { Text("Practice") }) },
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(onClick = { onNavigate(PracticeRoutes.edit()) }) {
                    Icon(Icons.Filled.Add, contentDescription = "New practice")
                }
            }
        },
    ) { innerPadding ->
        Column(Modifier.padding(innerPadding).fillMaxSize()) {
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                listOf("Practices", "Log").forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) },
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            open?.let { session ->
                UnfinishedBanner(
                    session = session,
                    onResume = { openLive(session.id) },
                    onFinish = viewModel::closeOpenSession,
                )
            }
            when (selectedTab) {
                0 -> PracticesTab(
                    practices = practices,
                    onQuickStart = { viewModel.startQuick(openLive) },
                    onStart = { viewModel.start(it, openLive) },
                    onOpen = { onNavigate(PracticeRoutes.detail(it)) },
                )
                else -> LogTab(sessions = sessions, onOpen = { onNavigate(PracticeRoutes.log(it)) })
            }
        }
    }
}

@Composable
private fun UnfinishedBanner(session: PracticeSession, onResume: () -> Unit, onFinish: () -> Unit) {
    val context = LocalContext.current
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            Text("Unfinished practice", style = MaterialTheme.typography.titleSmall)
            Text(
                "${session.name} · ${dateAndTime(context, session.startedAt)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onFinish) { Text("Save to log") }
                TextButton(onClick = onResume) { Text("Resume") }
            }
        }
    }
}

@Composable
private fun PracticesTab(
    practices: List<PracticeSummary>,
    onQuickStart: () -> Unit,
    onStart: (Long) -> Unit,
    onOpen: (Long) -> Unit,
) {
    val context = LocalContext.current
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "quick") {
            Button(onClick = onQuickStart, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Quick practice")
            }
        }
        if (practices.isEmpty()) {
            item(key = "empty") {
                Text(
                    "Start a quick practice to run the timer now, or tap + to plan drills with sets, reps and rounds.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
        items(practices, key = { it.practice.id }) { summary ->
            Card(Modifier.fillMaxWidth().clickable { onOpen(summary.practice.id) }) {
                Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(summary.practice.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            listOfNotNull(
                                plural(summary.drillCount, "drill"),
                                summary.lastPracticedAt?.let { "Last on ${shortDate(context, it)}" },
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { onStart(summary.practice.id) }) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Start ${summary.practice.name}")
                    }
                }
            }
        }
    }
}

@Composable
private fun LogTab(sessions: List<SessionSummary>, onOpen: (Long) -> Unit) {
    if (sessions.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
                "Every practice you finish is logged here.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    val context = LocalContext.current
    val totals = logTotals(sessions, System.currentTimeMillis())
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "totals") { TotalsCard(totals) }
        sessions.groupBy { monthAndYear(context, it.session.startedAt) }.forEach { (month, rows) ->
            item(key = month) {
                Text(month, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            }
            items(rows, key = { it.session.id }) { row -> SessionRow(row, onOpen) }
        }
    }
}

@Composable
private fun TotalsCard(totals: LogTotals) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp)) {
            TotalColumn("Last 7 days", totals.weekCount, totals.weekMs, Modifier.weight(1f))
            TotalColumn("All time", totals.allCount, totals.allMs, Modifier.weight(1f))
        }
    }
}

@Composable
private fun TotalColumn(label: String, count: Int, ms: Long, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(plural(count, "practice"), style = MaterialTheme.typography.titleMedium)
        Text(hoursAndMinutes(ms), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun SessionRow(row: SessionSummary, onOpen: (Long) -> Unit) {
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth().clickable { onOpen(row.session.id) }) {
        Column(Modifier.padding(16.dp)) {
            Text(row.session.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${dateAndTime(context, row.session.startedAt)} · ${formatDuration(row.session.durationMs)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(row.statsText(), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
