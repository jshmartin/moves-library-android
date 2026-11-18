package com.example.myapplication.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.camera.video.VideoRecordEvent.Finalize
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Where in-app recordings go, what they're called, and which permissions they need. */
object VideoRecording {
    /** Recordings land in the shared Movies folder under this name, so they also show up in the phone's gallery. */
    const val FOLDER = "Cypher"

    fun fileName(timeMs: Long, zone: TimeZone = TimeZone.getDefault()): String {
        val format = SimpleDateFormat("yyyy-MM-dd HH-mm-ss", Locale.US)
        format.timeZone = zone
        return "$FOLDER ${format.format(Date(timeMs))}"
    }

    /**
     * Whether the file left behind by a recording that ended with [error] (a [Finalize] error code)
     * after [durationMs] of footage is a playable video. Stopping early because the camera went away
     * or a limit was hit still leaves one, as long as something was recorded first. (Storage that
     * was already full ends the same way as storage that filled up mid-clip, but with nothing in
     * the file.) Having no data to write, or failing to encode, never leaves one.
     */
    fun keepsFile(error: Int, durationMs: Long): Boolean = durationMs > 0 && when (error) {
        Finalize.ERROR_NONE,
        Finalize.ERROR_SOURCE_INACTIVE,
        Finalize.ERROR_DURATION_LIMIT_REACHED,
        Finalize.ERROR_FILE_SIZE_LIMIT_REACHED,
        Finalize.ERROR_INSUFFICIENT_STORAGE -> true
        else -> false
    }

    /** Whether the device has any camera at all. Recording is hidden when it doesn't. */
    fun isSupported(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)

    /** Permissions without which a recording can't be made: the camera, and before Android 10 writing to Movies. */
    fun requiredPermissions(): List<String> = buildList {
        add(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

    /** Without the microphone a recording still works, but it's silent. */
    const val MICROPHONE = Manifest.permission.RECORD_AUDIO

    fun hasRequired(context: Context): Boolean = requiredPermissions().all { has(context, it) }

    fun hasMicrophone(context: Context): Boolean = has(context, MICROPHONE)

    /** Deletes a recording that can't be used, so it doesn't linger in the gallery as a broken file. */
    fun discard(context: Context, uri: Uri) {
        runCatching { context.contentResolver.delete(uri, null, null) }
    }

    private fun has(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
