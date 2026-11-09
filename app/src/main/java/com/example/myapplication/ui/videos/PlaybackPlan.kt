package com.example.myapplication.ui.videos

import com.example.myapplication.data.Video

data class PlayerClip(val uri: String, val title: String)

/** How well a place in the player's queue can be filled. */
enum class SlotStatus { TOP_VIDEO, OLDER_VIDEO, ALL_MISSING, NO_VIDEO }

/** One place in the player's queue: a title and the videos that could fill it, best first. */
data class PlayerSlot(val title: String, val videos: List<Video>) {
    /** The best video whose file can still be opened. */
    fun pick(missing: Set<Long>): Video? = videos.firstOrNull { it.id !in missing }

    fun status(missing: Set<Long>): SlotStatus = when (pick(missing)) {
        null -> if (videos.isEmpty()) SlotStatus.NO_VIDEO else SlotStatus.ALL_MISSING
        videos.first() -> SlotStatus.TOP_VIDEO
        else -> SlotStatus.OLDER_VIDEO
    }
}

/** What the player will play, and what the viewer should know about what it had to change. */
data class PlaybackPlan(val clips: List<PlayerClip>, val notices: List<String>)

/** Fills each slot with its best video that still exists. Slots with nothing to play are left out. */
fun planPlayback(slots: List<PlayerSlot>, missing: Set<Long>): PlaybackPlan {
    val titles = slots
        .groupBy({ it.status(missing) }, { it.title })
        .mapValues { (_, titles) -> titles.distinct().joinToString(", ") }
    return PlaybackPlan(
        clips = slots.mapNotNull { slot -> slot.pick(missing)?.let { PlayerClip(it.uri, slot.title) } },
        notices = listOfNotNull(
            titles[SlotStatus.NO_VIDEO]?.let { "No video for: $it" },
            titles[SlotStatus.ALL_MISSING]?.let { "Can't find the video for: $it. It may have been moved or deleted." },
            titles[SlotStatus.OLDER_VIDEO]?.let { "Playing another video for: $it. The top one can't be found." },
        ),
    )
}
