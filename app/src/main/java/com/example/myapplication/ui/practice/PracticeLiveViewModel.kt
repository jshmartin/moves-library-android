package com.example.myapplication.ui.practice

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.myapplication.data.dao.ChainDao
import com.example.myapplication.data.dao.LibraryDao
import com.example.myapplication.data.dao.PracticeDao
import com.example.myapplication.data.dao.SessionDrillView
import com.example.myapplication.data.model.DanceSet
import com.example.myapplication.data.model.Move
import com.example.myapplication.data.model.PracticeSession
import com.example.myapplication.data.model.SessionDrill
import com.example.myapplication.data.model.TimerSettings
import com.example.myapplication.ui.breakingApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LiveUiState(
    val loading: Boolean = true,
    val session: PracticeSession? = null,
    val drills: List<SessionDrillView> = emptyList(),
    val selectedPosition: Int? = null,
) {
    /** The drill being counted: the one picked, else the next one with sets left to do. */
    val current: SessionDrillView?
        get() = drills.firstOrNull { it.drill.position == selectedPosition }
            ?: drills.firstOrNull { !it.targetMet }
            ?: drills.lastOrNull()
}

data class TimerUi(
    val settings: TimerSettings,
    val position: TimerPosition,
    val running: Boolean,
    val started: Boolean,
)

/** Runs one logged session: its round timer and its sets and reps. Every count is written at once. */
class PracticeLiveViewModel(
    savedStateHandle: SavedStateHandle,
    private val dao: PracticeDao,
    libraryDao: LibraryDao,
    chainDao: ChainDao,
) : ViewModel() {
    val sessionId: Long = checkNotNull(savedStateHandle[PracticeRoutes.ARG_SESSION_ID])
    private val selected = MutableStateFlow<Int?>(null)

    val uiState: StateFlow<LiveUiState> =
        combine(dao.observeSession(sessionId), dao.observeSessionDrills(sessionId), selected) { session, drills, selected ->
            LiveUiState(loading = false, session = session, drills = drills, selectedPosition = selected)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LiveUiState())

    val allMoves: StateFlow<List<Move>> = libraryDao.observeMoves()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val allSets: StateFlow<List<DanceSet>> = chainDao.observeSets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val timer = RoundTimer(TimerSettings(), clock = SystemClock::elapsedRealtime)
    private val sounds = TimerSounds()
    private var ticker: Job? = null

    var timerUi by mutableStateOf(snapshot(timer.tick().position))
        private set

    init {
        viewModelScope.launch {
            dao.getSession(sessionId)?.let {
                timer.applySettings(it.timer)
                refreshTimer()
            }
        }
        viewModelScope.launch(Dispatchers.Default) { sounds.prepare() }
    }

    fun selectDrill(position: Int) {
        selected.value = position
    }

    fun addRep(position: Int) {
        viewModelScope.launch { dao.addRep(sessionId, position, System.currentTimeMillis()) }
    }

    fun undoRep(position: Int) {
        viewModelScope.launch { dao.undoRep(sessionId, position, System.currentTimeMillis()) }
    }

    fun finishSet(drill: SessionDrillView) {
        viewModelScope.launch {
            dao.finishSet(sessionId, drill.drill.position, System.currentTimeMillis())
            if (drill.setsDone + 1 >= drill.drill.targetSets) selected.value = null
        }
    }

    fun addDrill(choice: DrillChoice) {
        viewModelScope.launch {
            selected.value = dao.addSessionDrill(
                SessionDrill(
                    sessionId = sessionId,
                    position = 0,
                    type = choice.type,
                    moveId = choice.moveId,
                    setId = choice.setId,
                    title = choice.title,
                    targetSets = choice.defaultSets,
                    targetReps = choice.defaultReps,
                ),
                System.currentTimeMillis(),
            )
        }
    }

    fun startTimer() {
        timer.start()
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (timer.isRunning) {
                refreshTimer()
                delay(TICK_MS)
            }
        }
    }

    fun pauseTimer() {
        timer.pause()
        refreshTimer()
    }

    fun skipPhase() {
        timer.skip()
        refreshTimer()
    }

    fun resetTimer() {
        timer.reset()
        refreshTimer()
    }

    /** Changes this session's timer, starting it over. The saved practice is left alone. */
    fun applyTimerSettings(settings: TimerSettings) {
        timer.applySettings(settings)
        refreshTimer()
        viewModelScope.launch {
            dao.setSessionTimer(sessionId, settings.rounds, settings.activeSec, settings.restSec)
        }
    }

    private fun refreshTimer() {
        val tick = timer.tick()
        tick.cue?.let(sounds::play)
        if (tick.roundsFinished > 0) {
            viewModelScope.launch {
                dao.addCompletedRounds(sessionId, tick.roundsFinished, System.currentTimeMillis())
            }
        }
        timerUi = snapshot(tick.position)
    }

    private fun snapshot(position: TimerPosition) =
        TimerUi(timer.settings, position, timer.isRunning, timer.hasStarted)

    fun finish(notes: String, onFinished: () -> Unit) {
        timer.pause()
        viewModelScope.launch {
            dao.finishSession(sessionId, System.currentTimeMillis(), notes.trim())
            onFinished()
        }
    }

    fun discard() {
        timer.pause()
        viewModelScope.launch { dao.deleteSession(sessionId) }
    }

    override fun onCleared() {
        sounds.release()
    }

    companion object {
        private const val TICK_MS = 100L

        val Factory = viewModelFactory {
            initializer {
                val database = breakingApp().database
                PracticeLiveViewModel(
                    createSavedStateHandle(),
                    database.practiceDao(),
                    database.libraryDao(),
                    database.chainDao(),
                )
            }
        }
    }
}
