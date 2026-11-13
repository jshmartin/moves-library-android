package com.example.myapplication.ui.videos

import com.example.myapplication.data.VideoOwner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecordRoutesTest {
    /** What the route carries is read back the way the screen's SavedStateHandle hands it over. */
    private fun roundTrip(owner: VideoOwner?): VideoOwner? {
        val query = RecordRoutes.record(owner).substringAfter('?', "")
            .split('&')
            .filter { it.isNotEmpty() }
            .associate { it.substringBefore('=') to it.substringAfter('=') }
        return RecordRoutes.ownerOf(
            query[RecordRoutes.ARG_OWNER_KIND],
            query[RecordRoutes.ARG_OWNER_ID]?.toLong(),
        )
    }

    @Test
    fun everyKindOfOwnerSurvivesTheRoute() {
        listOf(
            VideoOwner.Move(7),
            VideoOwner.Set(8),
            VideoOwner.Practice(9),
            VideoOwner.Session(10),
        ).forEach { assertEquals(it, roundTrip(it)) }
    }

    @Test
    fun recordingFromTheVideosTabHasNoOwner() {
        assertNull(roundTrip(null))
    }

    @Test
    fun missingOrUnknownArgumentsMeanNoOwner() {
        assertNull(RecordRoutes.ownerOf("", RecordRoutes.NO_ID))
        assertNull(RecordRoutes.ownerOf("move", RecordRoutes.NO_ID))
        assertNull(RecordRoutes.ownerOf("move", null))
        assertNull(RecordRoutes.ownerOf("round", 3))
        assertNull(RecordRoutes.ownerOf(null, 3))
    }

    @Test
    fun namesTheOwnerForTheSavedMessage() {
        assertEquals("move", RecordRoutes.nounOf(VideoOwner.Move(1)))
        assertEquals("set", RecordRoutes.nounOf(VideoOwner.Set(1)))
        assertEquals("practice", RecordRoutes.nounOf(VideoOwner.Practice(1)))
        assertEquals("log", RecordRoutes.nounOf(VideoOwner.Session(1)))
    }
}
