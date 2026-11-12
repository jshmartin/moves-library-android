package com.example.myapplication.ui.videos

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.Display
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.myapplication.data.VideoRecording
import java.io.File

sealed interface RecorderEvent {
    /** How long the clip has run so far. */
    data class Progress(val elapsedMs: Long) : RecorderEvent

    /** The recording has ended and its file is closed. [uri] is null when nothing was written. */
    data class Finished(val uri: Uri?, val error: Int, val durationMs: Long) : RecorderEvent
}

/** Which camera ended up in use, and whether the other one could be switched to. */
data class CameraBinding(val front: Boolean, val canFlip: Boolean)

/**
 * Shows the camera in a [PreviewView] and records it into the shared Movies folder. Not thread
 * safe: call everything from the main thread.
 */
class CameraRecorder(context: Context) {
    private val context = context.applicationContext
    private var provider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null

    /**
     * Points the camera at [view] for as long as [owner] is started. Prefers the front or back
     * camera as asked and falls back to the other one; returns null if the device has neither.
     */
    suspend fun bind(owner: LifecycleOwner, view: PreviewView, front: Boolean): CameraBinding? {
        val provider = ProcessCameraProvider.awaitInstance(context).also { this.provider = it }
        val hasFront = provider.has(CameraSelector.DEFAULT_FRONT_CAMERA)
        val hasBack = provider.has(CameraSelector.DEFAULT_BACK_CAMERA)
        val useFront = when {
            front && hasFront -> true
            !front && hasBack -> false
            hasBack -> false
            hasFront -> true
            else -> return null
        }

        // Both use cases share one 16:9 frame, so the preview shows exactly what gets recorded.
        val frame = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
            .build()
        val rotation = view.display?.rotation ?: defaultDisplayRotation()
        val newPreview = Preview.Builder()
            .setResolutionSelector(frame)
            .setTargetRotation(rotation)
            .build()
            .also { it.surfaceProvider = view.surfaceProvider }
        val recorder = Recorder.Builder()
            .setQualitySelector(
                QualitySelector.from(Quality.FHD, FallbackStrategy.lowerQualityOrHigherThan(Quality.FHD))
            )
            .build()
        val newCapture = VideoCapture.Builder(recorder)
            .setTargetRotation(rotation)
            .build()

        provider.unbindAll()
        provider.bindToLifecycle(
            owner,
            if (useFront) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA,
            newPreview,
            newCapture,
        )
        preview = newPreview
        videoCapture = newCapture
        return CameraBinding(front = useFront, canFlip = hasFront && hasBack)
    }

    /** Starts a clip, with sound if [withAudio]. Throws if there's no camera bound or the recording can't begin. */
    @SuppressLint("MissingPermission") // The caller checks the microphone permission before asking for sound.
    fun start(withAudio: Boolean, onEvent: (RecorderEvent) -> Unit) {
        val capture = checkNotNull(videoCapture) { "The camera isn't ready" }
        check(recording == null) { "Already recording" }

        val name = VideoRecording.fileName(System.currentTimeMillis())
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/${VideoRecording.FOLDER}")
            } else {
                // Before Android 10 the media index has no folder column: the file's path picks the folder,
                // and CameraX needs that path to write to on Android 7.
                @Suppress("DEPRECATION")
                val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), VideoRecording.FOLDER)
                folder.mkdirs()
                @Suppress("DEPRECATION")
                put(MediaStore.MediaColumns.DATA, File(folder, "$name.mp4").absolutePath)
            }
        }
        val output = MediaStoreOutputOptions.Builder(context.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(values)
            .build()
        var pending = capture.output.prepareRecording(context, output)
        if (withAudio) pending = pending.withAudioEnabled()

        recording = pending.start(ContextCompat.getMainExecutor(context)) { event ->
            when (event) {
                is VideoRecordEvent.Status ->
                    onEvent(RecorderEvent.Progress(event.recordingStats.recordedDurationNanos / NANOS_PER_MS))
                is VideoRecordEvent.Finalize -> {
                    recording = null
                    val uri = event.outputResults.outputUri.takeUnless { it == Uri.EMPTY }
                    onEvent(
                        RecorderEvent.Finished(
                            uri = uri,
                            error = event.error,
                            durationMs = event.recordingStats.recordedDurationNanos / NANOS_PER_MS,
                        )
                    )
                }
                else -> Unit
            }
        }
    }

    /** Ends the clip. The result arrives as [RecorderEvent.Finished] once the file is closed. */
    fun stop() {
        recording?.stop()
    }

    /** Lets go of the camera. A clip in progress is closed first and still reports [RecorderEvent.Finished]. */
    fun release() {
        recording?.stop()
        val provider = provider ?: return
        listOfNotNull(preview, videoCapture).takeIf { it.isNotEmpty() }?.let { provider.unbind(*it.toTypedArray()) }
        preview = null
        videoCapture = null
    }

    private fun ProcessCameraProvider.has(selector: CameraSelector) = runCatching { hasCamera(selector) }.getOrDefault(false)

    /** Used until the preview is attached to a window and knows its own display. */
    private fun defaultDisplayRotation(): Int =
        (context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager)
            .getDisplay(Display.DEFAULT_DISPLAY)?.rotation ?: Surface.ROTATION_0

    private companion object {
        const val NANOS_PER_MS = 1_000_000L
    }
}
