package com.example.myapplication.data

import androidx.camera.video.VideoRecordEvent.Finalize
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoRecordingTest {
    @Test
    fun namesAClipAfterWhenItWasRecorded() {
        // 2026-11-12 14:03:22 UTC
        val time = 1_794_492_202_000L
        assertEquals("Cypher 2026-11-12 14-03-22", VideoRecording.fileName(time, TimeZone.getTimeZone("UTC")))
    }

    @Test
    fun namesFollowTheTimeZone() {
        val time = 1_794_492_202_000L
        assertEquals("Cypher 2026-11-12 23-03-22", VideoRecording.fileName(time, TimeZone.getTimeZone("Asia/Tokyo")))
    }

    @Test
    fun keepsClipsThatEndedNormallyOrWereCutShort() {
        assertTrue(VideoRecording.keepsFile(Finalize.ERROR_NONE, 4_000))
        // The camera went away, e.g. the app was sent to the background mid-clip.
        assertTrue(VideoRecording.keepsFile(Finalize.ERROR_SOURCE_INACTIVE, 4_000))
        assertTrue(VideoRecording.keepsFile(Finalize.ERROR_DURATION_LIMIT_REACHED, 4_000))
        assertTrue(VideoRecording.keepsFile(Finalize.ERROR_FILE_SIZE_LIMIT_REACHED, 4_000))
        // Storage filled up partway through.
        assertTrue(VideoRecording.keepsFile(Finalize.ERROR_INSUFFICIENT_STORAGE, 4_000))
    }

    @Test
    fun discardsClipsWithNothingUsableInThem() {
        assertFalse(VideoRecording.keepsFile(Finalize.ERROR_NO_VALID_DATA, 4_000))
        assertFalse(VideoRecording.keepsFile(Finalize.ERROR_ENCODING_FAILED, 4_000))
        assertFalse(VideoRecording.keepsFile(Finalize.ERROR_RECORDER_ERROR, 4_000))
        assertFalse(VideoRecording.keepsFile(Finalize.ERROR_INVALID_OUTPUT_OPTIONS, 4_000))
        assertFalse(VideoRecording.keepsFile(Finalize.ERROR_UNKNOWN, 4_000))
    }

    @Test
    fun discardsClipsThatEndedBeforeAnythingWasRecorded() {
        // Storage was already full when recording began.
        assertFalse(VideoRecording.keepsFile(Finalize.ERROR_INSUFFICIENT_STORAGE, 0))
        assertFalse(VideoRecording.keepsFile(Finalize.ERROR_SOURCE_INACTIVE, 0))
        assertFalse(VideoRecording.keepsFile(Finalize.ERROR_NONE, 0))
    }
}
