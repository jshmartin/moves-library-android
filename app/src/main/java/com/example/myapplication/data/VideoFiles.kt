package com.example.myapplication.data

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.LruCache
import androidx.core.content.ContextCompat
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.security.MessageDigest

/** Low-level helpers for reading video files the user picked. All calls block; run them off the main thread. */
object VideoFiles {
    private const val SAMPLE_BYTES = 1L shl 20 // 1 MiB from the start and end of the file

    data class Probe(
        val displayName: String,
        val sizeBytes: Long,
        val durationMs: Long?,
        val mimeType: String?,
        val fingerprint: String,
    )

    /** Reads everything we store about a video, or null if the file can't be opened. */
    fun probe(context: Context, uri: Uri): Probe? {
        val resolver = context.contentResolver
        val (size, fingerprint) = sizeAndFingerprint(resolver, uri) ?: return null
        return Probe(
            displayName = displayName(resolver, uri) ?: uri.lastPathSegment ?: "Video",
            sizeBytes = size,
            durationMs = duration(context, uri),
            mimeType = resolver.getType(uri),
            fingerprint = fingerprint,
        )
    }

    /** Hashes the size plus the first and last [SAMPLE_BYTES] — fast even for multi-GB files, and unique in practice. */
    fun sizeAndFingerprint(resolver: ContentResolver, uri: Uri): Pair<Long, String>? = runCatching {
        val pfd = resolver.openFileDescriptor(uri, "r") ?: return null
        ParcelFileDescriptor.AutoCloseInputStream(pfd).channel.use { channel ->
            val size = channel.size()
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(ByteBuffer.allocate(Long.SIZE_BYTES).putLong(size).array())
            channel.digestRange(0, minOf(SAMPLE_BYTES, size), digest)
            if (size > SAMPLE_BYTES) {
                channel.digestRange(maxOf(SAMPLE_BYTES, size - SAMPLE_BYTES), size, digest)
            }
            size to digest.digest().joinToString("") { "%02x".format(it) }
        }
    }.getOrNull()

    fun isAvailable(resolver: ContentResolver, uri: Uri): Boolean = runCatching {
        resolver.openFileDescriptor(uri, "r")?.use { true } ?: false
    }.getOrDefault(false)

    /** The runtime permissions that let us look through the device's videos. Any one of them is enough. */
    fun mediaPermissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
            arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(Manifest.permission.READ_MEDIA_VIDEO)
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    fun canReadDeviceVideos(context: Context): Boolean = mediaPermissions().any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    private val thumbnails = LruCache<String, Bitmap>(64)

    fun thumbnail(context: Context, uri: Uri): Bitmap? {
        thumbnails.get(uri.toString())?.let { return it }
        return withRetriever(context, uri) { retriever ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                retriever.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 320, 320)
            } else {
                retriever.frameAtTime
            }
        }?.also { thumbnails.put(uri.toString(), it) }
    }

    private fun duration(context: Context, uri: Uri): Long? = withRetriever(context, uri) {
        it.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
    }

    private fun displayName(resolver: ContentResolver, uri: Uri): String? = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()

    /** Concurrent retrievers compete for a small pool of hardware decoders and fail at random, so run them one at a time. */
    private val retrieverLock = Any()

    private fun <T> withRetriever(context: Context, uri: Uri, block: (MediaMetadataRetriever) -> T?): T? = synchronized(retrieverLock) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            block(retriever)
        } catch (_: RuntimeException) {
            null
        } finally {
            retriever.release()
        }
    }

    private fun FileChannel.digestRange(start: Long, end: Long, digest: MessageDigest) {
        position(start)
        val buffer = ByteBuffer.allocate(64 * 1024)
        var remaining = end - start
        while (remaining > 0) {
            buffer.clear()
            if (remaining < buffer.capacity()) buffer.limit(remaining.toInt())
            val read = read(buffer)
            if (read <= 0) break
            buffer.flip()
            digest.update(buffer)
            remaining -= read
        }
    }
}
