package com.example.myapplication.data

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class VideoRepository(context: Context, private val dao: VideoDao) {
    private val context = context.applicationContext
    private val resolver = context.contentResolver

    val videos: Flow<List<Video>> = dao.observeAll()

    data class AddResult(val added: Int, val relinked: Int, val duplicates: Int, val failed: Int)

    enum class RelinkResult { Relinked, Mismatch, AlreadyInLibrary, Unreadable }

    /**
     * Adds picked videos. A file whose content matches a library entry that has gone missing
     * relinks that entry (keeping its history) instead of creating a duplicate.
     */
    suspend fun add(uris: List<Uri>): AddResult = withContext(Dispatchers.IO) {
        var added = 0
        var relinked = 0
        var duplicates = 0
        var failed = 0
        for (uri in uris) {
            val probe = VideoFiles.probe(context, uri)
            if (probe == null) {
                failed++
                continue
            }
            val existing = dao.findByFingerprint(probe.fingerprint)
            when {
                existing == null -> {
                    takePermission(uri)
                    val id = dao.insert(
                        Video(
                            uri = uri.toString(),
                            displayName = probe.displayName,
                            sizeBytes = probe.sizeBytes,
                            durationMs = probe.durationMs,
                            mimeType = probe.mimeType,
                            fingerprint = probe.fingerprint,
                            addedAt = System.currentTimeMillis(),
                        )
                    )
                    if (id == -1L) duplicates++ else added++
                }
                existing.uri == uri.toString() || isAvailable(existing) -> duplicates++
                else -> {
                    relinkTo(existing, uri, probe)
                    relinked++
                }
            }
        }
        AddResult(added, relinked, duplicates, failed)
    }

    /** Ids of videos whose file can no longer be opened (moved, deleted, or access revoked). */
    suspend fun findMissing(videos: List<Video>): Set<Long> = withContext(Dispatchers.IO) {
        videos.filterNot(::isAvailable).mapTo(mutableSetOf()) { it.id }
    }

    /** Points [video] at a file the user located by hand. Unless [force], refuses files whose content differs. */
    suspend fun relink(video: Video, uri: Uri, force: Boolean = false): RelinkResult =
        withContext(Dispatchers.IO) {
            if (dao.findByUri(uri.toString()) != null) return@withContext RelinkResult.AlreadyInLibrary
            val probe = VideoFiles.probe(context, uri) ?: return@withContext RelinkResult.Unreadable
            if (probe.fingerprint != video.fingerprint && !force) return@withContext RelinkResult.Mismatch
            relinkTo(video, uri, probe)
            RelinkResult.Relinked
        }

    /**
     * Looks through the device's media index for files matching [missing] videos: candidates are
     * narrowed by exact size, then confirmed by fingerprint. Needs media read permission.
     * Returns how many videos were relinked.
     */
    suspend fun searchDevice(missing: List<Video>): Int = withContext(Dispatchers.IO) {
        if (missing.isEmpty()) return@withContext 0
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
        val remaining = missing.associateByTo(mutableMapOf()) { it.fingerprint }
        var relinked = 0
        missing.map { it.sizeBytes }.distinct().chunked(500).forEach { sizes ->
            if (remaining.isEmpty()) return@forEach
            resolver.query(
                collection,
                arrayOf(MediaStore.Video.Media._ID),
                "${MediaStore.Video.Media.SIZE} IN (${sizes.joinToString(",") { "?" }})",
                sizes.map(Long::toString).toTypedArray(),
                null,
            )?.use { cursor ->
                while (cursor.moveToNext() && remaining.isNotEmpty()) {
                    val uri = ContentUris.withAppendedId(collection, cursor.getLong(0))
                    val fingerprint = VideoFiles.sizeAndFingerprint(resolver, uri)?.second ?: continue
                    val video = remaining.remove(fingerprint) ?: continue
                    if (dao.findByUri(uri.toString()) == null) {
                        relinkTo(video, uri)
                        relinked++
                    }
                }
            }
        }
        relinked
    }

    suspend fun remove(video: Video) = withContext(Dispatchers.IO) {
        releasePermission(video.uri.toUri())
        dao.delete(video)
    }

    private fun isAvailable(video: Video) = VideoFiles.isAvailable(resolver, video.uri.toUri())

    private suspend fun relinkTo(video: Video, uri: Uri, probe: VideoFiles.Probe? = null) {
        takePermission(uri)
        releasePermission(video.uri.toUri())
        val current = probe ?: VideoFiles.probe(context, uri)
        dao.update(
            video.copy(
                uri = uri.toString(),
                displayName = current?.displayName ?: video.displayName,
                sizeBytes = current?.sizeBytes ?: video.sizeBytes,
                durationMs = current?.durationMs ?: video.durationMs,
                mimeType = current?.mimeType ?: video.mimeType,
                fingerprint = current?.fingerprint ?: video.fingerprint,
            )
        )
    }

    /** Document-picker grants are lost on reboot unless persisted; MediaStore URIs rely on the media permission instead. */
    private fun takePermission(uri: Uri) {
        if (!DocumentsContract.isDocumentUri(context, uri)) return
        runCatching { resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }

    private fun releasePermission(uri: Uri) {
        if (!DocumentsContract.isDocumentUri(context, uri)) return
        runCatching { resolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }
}
