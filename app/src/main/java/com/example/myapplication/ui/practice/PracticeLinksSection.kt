package com.example.myapplication.ui.practice

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.myapplication.data.dao.DrilledInSession
import com.example.myapplication.data.dao.PracticeDao
import com.example.myapplication.data.model.DrillType
import com.example.myapplication.data.model.Practice
import com.example.myapplication.data.model.SessionDrill
import com.example.myapplication.ui.breakingApp
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PracticeLinksUiState(
    val practices: List<Practice> = emptyList(),
    val sessions: List<DrilledInSession> = emptyList(),
)

/** How one move or set connects to practice. Exactly one id is non-null. */
class PracticeLinksViewModel(
    private val dao: PracticeDao,
    private val moveId: Long?,
    private val setId: Long?,
) : ViewModel() {
    val uiState: StateFlow<PracticeLinksUiState> =
        combine(dao.observePracticesWith(moveId, setId), dao.observeSessionsWith(moveId, setId), ::PracticeLinksUiState)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PracticeLinksUiState())

    /** Starts a session drilling just this move or set. */
    fun start(title: String, onStarted: (sessionId: Long) -> Unit) {
        viewModelScope.launch {
            val drill = SessionDrill(
                sessionId = 0,
                position = 0,
                type = if (moveId != null) DrillType.MOVE else DrillType.SET,
                moveId = moveId,
                setId = setId,
                title = title,
            )
            onStarted(dao.startQuickSession(title, drill))
        }
    }

    companion object {
        fun factory(moveId: Long?, setId: Long?) = viewModelFactory {
            initializer { PracticeLinksViewModel(breakingApp().database.practiceDao(), moveId, setId) }
        }
    }
}

/**
 * The practice side of a move or set page: start drilling it, its totals, the practices that use
 * it and its latest log entries. [noun] is how the page's subject is named, e.g. "move".
 */
@Composable
fun PracticeLinksSection(
    moveId: Long?,
    setId: Long?,
    title: String,
    noun: String,
    onNavigate: (route: String) -> Unit,
    viewModel: PracticeLinksViewModel = viewModel(factory = PracticeLinksViewModel.factory(moveId, setId)),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Practice")
        OutlinedButton(
            onClick = { viewModel.start(title) { onNavigate(PracticeRoutes.live(it)) } },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Practice this $noun")
        }

        if (state.sessions.isNotEmpty()) {
            Text(
                "${plural(state.sessions.sumOf { it.reps }, "rep")} over ${plural(state.sessions.size, "session")}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        state.practices.forEach { practice ->
            Text(
                "In ${practice.name}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigate(PracticeRoutes.detail(practice.id)) }
                    .padding(vertical = 4.dp),
            )
        }

        state.sessions.take(RECENT_SHOWN).forEach { session ->
            Text(
                "${shortDate(context, session.startedAt)} · ${session.sessionName} · " +
                    "${plural(session.sets, "set")}, ${plural(session.reps, "rep")}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigate(PracticeRoutes.log(session.sessionId)) }
                    .padding(vertical = 4.dp),
            )
        }
    }
}

private const val RECENT_SHOWN = 3
