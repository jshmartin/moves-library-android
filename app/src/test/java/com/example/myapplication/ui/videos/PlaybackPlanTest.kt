package com.example.myapplication.ui.videos

import com.example.myapplication.data.Video
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackPlanTest {
    private fun video(id: Long) = Video(
        id = id,
        uri = "content://$id",
        displayName = "clip$id",
        sizeBytes = 1,
        durationMs = null,
        mimeType = null,
        fingerprint = "f$id",
        addedAt = 0,
    )

    private val top = video(1)
    private val older = video(2)

    @Test
    fun playsTheTopVideoWhenItsThere() {
        val plan = planPlayback(listOf(PlayerSlot("Windmill", listOf(top, older))), missing = emptySet())

        assertEquals(listOf(PlayerClip("content://1", "Windmill")), plan.clips)
        assertTrue(plan.notices.isEmpty())
    }

    @Test
    fun fallsBackToTheNextVideoWhenTheTopOneIsGone() {
        val slot = PlayerSlot("Windmill", listOf(top, older))

        val plan = planPlayback(listOf(slot), missing = setOf(top.id))

        assertEquals(SlotStatus.OLDER_VIDEO, slot.status(setOf(top.id)))
        assertEquals(listOf(PlayerClip("content://2", "Windmill")), plan.clips)
        assertEquals(listOf("Playing another video for: Windmill. The top one can't be found."), plan.notices)
    }

    @Test
    fun leavesOutAMoveWhoseVideosAreAllGone() {
        val slot = PlayerSlot("Windmill", listOf(top, older))

        val plan = planPlayback(listOf(slot), missing = setOf(top.id, older.id))

        assertEquals(SlotStatus.ALL_MISSING, slot.status(setOf(top.id, older.id)))
        assertTrue(plan.clips.isEmpty())
        assertEquals(listOf("Can't find the video for: Windmill. It may have been moved or deleted."), plan.notices)
    }

    @Test
    fun tellsANeverFilmedMoveApartFromALostVideo() {
        val slot = PlayerSlot("Freeze", emptyList())

        val plan = planPlayback(listOf(slot), missing = emptySet())

        assertEquals(SlotStatus.NO_VIDEO, slot.status(emptySet()))
        assertEquals(listOf("No video for: Freeze"), plan.notices)
    }

    @Test
    fun keepsChainOrderAndNamesARepeatedMoveOnce() {
        val flare = video(3)
        val slots = listOf(
            PlayerSlot("Windmill", listOf(top)),
            PlayerSlot("Flare", listOf(flare)),
            PlayerSlot("Windmill", listOf(top)),
            PlayerSlot("Freeze", emptyList()),
            PlayerSlot("Freeze", emptyList()),
        )

        val plan = planPlayback(slots, missing = emptySet())

        assertEquals(listOf("Windmill", "Flare", "Windmill"), plan.clips.map { it.title })
        assertEquals(listOf("No video for: Freeze"), plan.notices)
    }
}
