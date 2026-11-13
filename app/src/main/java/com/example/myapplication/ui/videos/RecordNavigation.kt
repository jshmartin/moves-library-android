package com.example.myapplication.ui.videos

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.myapplication.data.VideoOwner

object RecordRoutes {
    const val ARG_OWNER_KIND = "ownerKind"
    const val ARG_OWNER_ID = "ownerId"
    const val NO_ID = -1L
    const val ROUTE = "record?$ARG_OWNER_KIND={$ARG_OWNER_KIND}&$ARG_OWNER_ID={$ARG_OWNER_ID}"

    /** Records a video, and links it to [owner] if given. With no owner it only goes into the library. */
    fun record(owner: VideoOwner? = null): String = when (owner) {
        null -> "record"
        is VideoOwner.Move -> "record?$ARG_OWNER_KIND=move&$ARG_OWNER_ID=${owner.id}"
        is VideoOwner.Set -> "record?$ARG_OWNER_KIND=set&$ARG_OWNER_ID=${owner.id}"
        is VideoOwner.Practice -> "record?$ARG_OWNER_KIND=practice&$ARG_OWNER_ID=${owner.id}"
        is VideoOwner.Session -> "record?$ARG_OWNER_KIND=session&$ARG_OWNER_ID=${owner.id}"
    }

    /** The owner named by a [record] route's arguments, or null if it names none. */
    fun ownerOf(kind: String?, id: Long?): VideoOwner? {
        if (id == null || id == NO_ID) return null
        return when (kind) {
            "move" -> VideoOwner.Move(id)
            "set" -> VideoOwner.Set(id)
            "practice" -> VideoOwner.Practice(id)
            "session" -> VideoOwner.Session(id)
            else -> null
        }
    }

    /** How the owner is named in the UI, e.g. "this move". */
    fun nounOf(owner: VideoOwner): String = when (owner) {
        is VideoOwner.Move -> "move"
        is VideoOwner.Set -> "set"
        is VideoOwner.Practice -> "practice"
        is VideoOwner.Session -> "log"
    }
}

fun NavGraphBuilder.videoRecorder(onBack: () -> Unit) {
    composable(
        RecordRoutes.ROUTE,
        arguments = listOf(
            navArgument(RecordRoutes.ARG_OWNER_KIND) {
                type = NavType.StringType
                defaultValue = ""
            },
            navArgument(RecordRoutes.ARG_OWNER_ID) {
                type = NavType.LongType
                defaultValue = RecordRoutes.NO_ID
            },
        ),
    ) {
        RecordScreen(onBack = onBack)
    }
}
