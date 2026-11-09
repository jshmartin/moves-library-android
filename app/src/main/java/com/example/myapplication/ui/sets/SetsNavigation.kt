package com.example.myapplication.ui.sets

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
    const val NO_ID = -1L

    const val HOME = "sets/home"
    const val DETAIL = "sets/detail/{$ARG_SET_ID}"
    const val EDIT = "sets/edit?$ARG_SET_ID={$ARG_SET_ID}"

    fun detail(id: Long) = "sets/detail/$id"
    fun edit(id: Long = NO_ID) = "sets/edit?$ARG_SET_ID=$id"
}

fun NavGraphBuilder.setsGraph(navController: NavHostController) {
    navigation(startDestination = SetRoutes.HOME, route = SetRoutes.GRAPH) {
        composable(SetRoutes.HOME) {
            SetsScreen(
                onSetClick = { navController.navigate(SetRoutes.detail(it)) },
                onAddSet = { navController.navigate(SetRoutes.edit()) },
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
            ),
        ) {
            SetEditScreen(
                onBack = { navController.popBackStack() },
                onSaved = { setId, isNew ->
                    if (isNew) {
                        navController.navigate(SetRoutes.detail(setId)) {
                            popUpTo(SetRoutes.EDIT) { inclusive = true }
                        }
                    } else {
                        navController.popBackStack()
                    }
                },
            )
        }
    }
}
