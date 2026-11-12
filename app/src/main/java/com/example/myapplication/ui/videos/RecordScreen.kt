package com.example.myapplication.ui.videos

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.data.VideoRecording
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The self-timer choices, in seconds. 0 starts as soon as Record is tapped. */
private val TIMER_CHOICES = listOf(0, 3, 10)

/**
 * Records a video with the camera and files it in the library. The self-timer exists because the
 * usual way to film yourself dancing is to prop the phone up and walk away from it.
 */
@Composable
fun RecordScreen(
    onBack: () -> Unit,
    viewModel: RecordViewModel = viewModel(factory = RecordViewModel.Factory),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var canRecord by remember { mutableStateOf(VideoRecording.hasRequired(context)) }
    var hasMicrophone by remember { mutableStateOf(VideoRecording.hasMicrophone(context)) }
    var asked by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        canRecord = VideoRecording.hasRequired(context)
        hasMicrophone = VideoRecording.hasMicrophone(context)
    }
    fun askForPermissions() =
        permissionLauncher.launch((VideoRecording.requiredPermissions() + VideoRecording.MICROPHONE).toTypedArray())

    LaunchedEffect(Unit) {
        if (!asked && (!canRecord || !hasMicrophone)) {
            asked = true
            askForPermissions()
        }
    }
    // Permissions can be changed in the system settings while the app waits in the background.
    LifecycleResumeEffect(Unit) {
        canRecord = VideoRecording.hasRequired(context)
        hasMicrophone = VideoRecording.hasMicrophone(context)
        onPauseOrDispose { }
    }

    val recorder = remember { CameraRecorder(context) }
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FIT_CENTER
            keepScreenOn = true
        }
    }
    var front by rememberSaveable { mutableStateOf(false) }
    var canFlip by remember { mutableStateOf(false) }
    var cameraMissing by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    var elapsedMs by remember { mutableLongStateOf(0L) }
    var countdown by remember { mutableStateOf<Int?>(null) }
    var countdownJob by remember { mutableStateOf<Job?>(null) }
    var timerSeconds by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(canRecord, front) {
        if (!canRecord) return@LaunchedEffect
        val binding = try {
            recorder.bind(lifecycleOwner, previewView, front)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        cameraMissing = binding == null
        if (binding != null) {
            canFlip = binding.canFlip
            if (binding.front != front) front = binding.front
        }
    }
    DisposableEffect(recorder) { onDispose { recorder.release() } }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.messageShown()
        }
    }

    fun beginRecording() {
        try {
            recorder.start(withAudio = hasMicrophone) { event ->
                when (event) {
                    is RecorderEvent.Progress -> elapsedMs = event.elapsedMs
                    is RecorderEvent.Finished -> {
                        recording = false
                        viewModel.onRecorded(event.uri, event.error, event.durationMs)
                    }
                }
            }
            elapsedMs = 0L
            recording = true
        } catch (_: Exception) {
            viewModel.showMessage("Couldn't start recording. Try again.")
        }
    }

    fun cancelCountdown() {
        countdownJob?.cancel()
        countdownJob = null
        countdown = null
    }

    // The camera closes while the app is in the background, so a countdown left running there would end
    // by trying to record with no camera. Leaving the app calls it off instead.
    LifecycleStartEffect(Unit) {
        onStopOrDispose { cancelCountdown() }
    }

    fun startRecording() {
        if (timerSeconds == 0) {
            beginRecording()
            return
        }
        countdownJob = scope.launch {
            for (second in timerSeconds downTo 1) {
                countdown = second
                delay(1_000)
            }
            countdown = null
            countdownJob = null
            beginRecording()
        }
    }

    val busy = recording || countdown != null || state.saving
    // Turning the phone mid-clip would rebuild the screen and cut the recording, so the view stays as it was
    // when Record was tapped. Pick portrait or landscape first, then record.
    LockOrientation(locked = recording || countdown != null)
    DarkSystemBars()

    // Leaving mid-clip would throw it away, so back ends the clip and keeps it instead.
    BackHandler(enabled = busy) {
        when {
            recording -> recorder.stop()
            countdown != null -> cancelCountdown()
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (canRecord && !cameraMissing) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        }

        when {
            !canRecord -> PermissionRequest(
                onAllow = ::askForPermissions,
                onOpenSettings = { openAppSettings(context) },
                modifier = Modifier.align(Alignment.Center),
            )
            cameraMissing -> Message(
                "No camera is available right now. Close any app that's using it and try again.",
                Modifier.align(Alignment.Center),
            )
        }

        Column(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp)) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                IconButton(
                    onClick = {
                        when {
                            recording -> recorder.stop()
                            countdown != null -> cancelCountdown()
                            else -> onBack()
                        }
                    },
                    enabled = !state.saving,
                    modifier = Modifier.align(Alignment.CenterStart),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color.Black.copy(alpha = 0.4f),
                        contentColor = Color.White,
                    ),
                ) {
                    Icon(Icons.Filled.Close, contentDescription = if (recording) "Stop recording" else "Close")
                }
                if (recording) ElapsedPill(elapsedMs)
            }
            if (canRecord && !cameraMissing && !hasMicrophone && !recording) {
                MicrophoneNotice(onOpenSettings = { openAppSettings(context) })
            }

            Spacer(Modifier.weight(1f))

            val saved = state.saved
            when {
                saved != null -> SavedPanel(
                    saved = saved,
                    ownerNoun = viewModel.owner?.let(RecordRoutes::nounOf),
                    onRecordAnother = viewModel::recordAnother,
                    onDone = onBack,
                )
                state.saving -> SavingPanel()
                canRecord && !cameraMissing -> Controls(
                    recording = recording,
                    counting = countdown != null,
                    timerSeconds = timerSeconds,
                    canFlip = canFlip,
                    onTimer = { timerSeconds = TIMER_CHOICES[(TIMER_CHOICES.indexOf(timerSeconds) + 1) % TIMER_CHOICES.size] },
                    onFlip = { front = !front },
                    onRecordTap = {
                        when {
                            recording -> recorder.stop()
                            countdown != null -> cancelCountdown()
                            else -> startRecording()
                        }
                    },
                )
            }
        }

        countdown?.let {
            Text(
                it.toString(),
                color = Color.White,
                fontSize = 128.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        SnackbarHost(
            snackbarHostState,
            Modifier.align(Alignment.BottomCenter).systemBarsPadding().padding(bottom = 128.dp),
        )
    }
}

@Composable
private fun Controls(
    recording: Boolean,
    counting: Boolean,
    timerSeconds: Int,
    canFlip: Boolean,
    onTimer: () -> Unit,
    onFlip: () -> Unit,
    onRecordTap: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            if (!recording && !counting) {
                ControlPill(if (timerSeconds == 0) "Timer off" else "Timer ${timerSeconds}s", onTimer)
            }
        }
        RecordButton(recording = recording, counting = counting, onClick = onRecordTap)
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            if (canFlip && !recording && !counting) {
                ControlPill("Flip camera", onFlip)
            }
        }
    }
}

@Composable
private fun RecordButton(recording: Boolean, counting: Boolean, onClick: () -> Unit) {
    val label = when {
        recording -> "Stop recording"
        counting -> "Cancel timer"
        else -> "Start recording"
    }
    Box(
        Modifier
            .size(76.dp)
            .clip(CircleShape)
            .border(4.dp, Color.White, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(if (recording || counting) 30.dp else 58.dp)
                .clip(if (recording || counting) RoundedCornerShape(6.dp) else CircleShape)
                .background(Color(0xFFE53935))
        )
    }
}

@Composable
private fun ControlPill(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = Color.White,
        style = MaterialTheme.typography.labelLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

@Composable
private fun ElapsedPill(elapsedMs: Long) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFE53935)))
        Spacer(Modifier.width(8.dp))
        Text(formatDuration(elapsedMs), color = Color.White, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun MicrophoneNotice(onOpenSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "No microphone access, so clips will have no sound.",
            color = Color.White,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
        TextButton(onClick = onOpenSettings) { Text("Allow") }
    }
}

@Composable
private fun PermissionRequest(onAllow: () -> Unit, onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Allow camera access to record videos here. Clips are saved on your phone, in Movies/${VideoRecording.FOLDER}.",
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.size(16.dp))
        Button(onClick = onAllow) { Text("Allow camera") }
        TextButton(onClick = onOpenSettings) { Text("Open app settings") }
    }
}

@Composable
private fun Message(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = Color.White,
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        modifier = modifier.padding(32.dp),
    )
}

@Composable
private fun SavingPanel() {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
        Spacer(Modifier.width(12.dp))
        Text("Saving…", color = Color.White, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SavedPanel(
    saved: SavedRecording,
    ownerNoun: String?,
    onRecordAnother: () -> Unit,
    onDone: () -> Unit,
) {
    Card(
        // Capped so it doesn't stretch across a landscape screen.
        Modifier.fillMaxWidth().widthIn(max = 480.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Thumbnail(saved.uri)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Saved to your videos", style = MaterialTheme.typography.titleSmall)
                    Text(
                        listOfNotNull(
                            formatDuration(saved.durationMs),
                            ownerNoun?.let { "Added to this $it" },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onRecordAnother) { Text("Record another") }
                Button(onClick = onDone) { Text("Done") }
            }
        }
    }
}

/** Holds the screen's current orientation while [locked]. */
@Composable
private fun LockOrientation(locked: Boolean) {
    val activity = LocalContext.current.findActivity() ?: return
    DisposableEffect(locked) {
        if (!locked) return@DisposableEffect onDispose { }
        val previous = activity.requestedOrientation
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
        onDispose { activity.requestedOrientation = previous }
    }
}

/** Light status and navigation bar icons, since this screen is black whatever the app's theme is. */
@Composable
private fun DarkSystemBars() {
    val window = LocalContext.current.findActivity()?.window ?: return
    val view = LocalView.current
    DisposableEffect(window) {
        val controller = WindowCompat.getInsetsController(window, view)
        val lightStatus = controller.isAppearanceLightStatusBars
        val lightNavigation = controller.isAppearanceLightNavigationBars
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        onDispose {
            controller.isAppearanceLightStatusBars = lightStatus
            controller.isAppearanceLightNavigationBars = lightNavigation
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}
