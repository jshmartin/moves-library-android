package com.example.myapplication.ui.sets

import android.net.Uri
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import com.example.myapplication.ui.library.LibraryRoutes
import com.example.myapplication.ui.videos.VideoRoutes

object SetRoutes {
    const val GRAPH = "sets"
    const val ARG_SET_ID = "setId"
    const val ARG_NAME = "name"
    const val ARG_NOTES = "notes"
    const val ARG_MOVE_IDS = "moveIds"
    const val NO_ID = -1L

    const val HOME = "sets/home"
    const val RANDOMIZER = "sets/randomizer"
    const val DETAIL = "sets/detail/{$ARG_SET_ID}"
    const val EDIT = "sets/edit?$ARG_SET_ID={$ARG_SET_ID}&$ARG_NAME={$ARG_NAME}&$ARG_NOTES={$ARG_NOTES}" +
        "&$ARG_MOVE_IDS={$ARG_MOVE_IDS}"

    fun detail(id: Long) = "sets/detail/$id"
    fun edit(id: Long = NO_ID) = "sets/edit?$ARG_SET_ID=$id"

    /** A new set already filled in, for the dancer to check and save. */
    fun draft(name: String, notes: String, moveIds: List<Long>) =
        "sets/edit?$ARG_SET_ID=$NO_ID&$ARG_NAME=${Uri.encode(name)}&$ARG_NOTES=${Uri.encode(notes)}" +
            "&$ARG_MOVE_IDS=${moveIds.joinToString(",")}"
}

fun NavGraphBuilder.setsGraph(navController: NavHostController) {
    navigation(startDestination = SetRoutes.HOME, route = SetRoutes.GRAPH) {
        composable(SetRoutes.HOME) {
            SetsScreen(
                onSetClick = { navController.navigate(SetRoutes.detail(it)) },
                onAddSet = { navController.navigate(SetRoutes.edit()) },
                onRandomize = { navController.navigate(SetRoutes.RANDOMIZER) },
                onAddBasicSet = { option ->
                    navController.navigate(SetRoutes.draft(option.set.name, option.set.notes, option.moves.map { it.id }))
                },
            )
        }
        composable(SetRoutes.RANDOMIZER) {
            RandomizerScreen(
                onBack = { navController.popBackStack() },
                onSave = { navController.navigate(it) },
            )
        }
        composable(
            SetRoutes.DETAIL,
            arguments = listOf(navArgument(SetRoutes.ARG_SET_ID) { type = NavType.LongType }),
        ) {
            SetDetailScreen(
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate(SetRoutes.edit(it)) },
                onMoveClick = { navController.navigate(LibraryRoutes.move(it)) },
                onPlayCombo = { navController.navigate(VideoRoutes.combo(it)) },
                onPlayVideo = { navController.navigate(VideoRoutes.video(it)) },
                onNavigate = { navController.navigate(it) },
            )
        }
        composable(
            SetRoutes.EDIT,
            arguments = listOf(
                navArgument(SetRoutes.ARG_SET_ID) {
                    type = NavType.LongType
                    defaultValue = SetRoutes.NO_ID
                },
                navArgument(SetRoutes.ARG_NAME) { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument(SetRoutes.ARG_NOTES) { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument(SetRoutes.ARG_MOVE_IDS) { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) {
            SetEditScreen(
                onBack = { navController.popBackStack() },
                onSaved = { setId, isNew ->
                    if (isNew) {
                        // Back from the new set goes to the list, skipping the editor and the randomizer.
                        navController.navigate(SetRoutes.detail(setId)) {
                            popUpTo(SetRoutes.HOME)
                        }
                    } else {
                        navController.popBackStack()
                    }
                },
            )
        }
    }
}
