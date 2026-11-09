package com.example.myapplication.data

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoTest {
    private fun video(moveId: Long? = null, setId: Long? = null, routineId: Long? = null) = Video(
        uri = "u",
        displayName = "d",
        sizeBytes = 1,
        durationMs = null,
        mimeType = null,
        fingerprint = "f",
        addedAt = 0,
        moveId = moveId,
        setId = setId,
        routineId = routineId,
    )

    @Test
    fun allowsNoOwnerOrOneOwner() {
        assertEquals(null, video().moveId)
        assertEquals(1L, video(moveId = 1).moveId)
        assertEquals(1L, video(setId = 1).setId)
        assertEquals(1L, video(routineId = 1).routineId)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTwoOwners() {
        video(moveId = 1, setId = 2)
    }
}
