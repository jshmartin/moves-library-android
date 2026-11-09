package com.example.myapplication.ui.practice

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument

object PracticeRoutes {
    const val GRAPH = "practice"
    const val ARG_PRACTICE_ID = "practiceId"
    const val ARG_SESSION_ID = "sessionId"
    const val NO_ID = -1L

    const val HOME = "practice/home"
    const val DETAIL = "practice/detail/{$ARG_PRACTICE_ID}"
    const val EDIT = "practice/edit?$ARG_PRACTICE_ID={$ARG_PRACTICE_ID}"
    const val LIVE = "practice/live/{$ARG_SESSION_ID}"
    const val LOG = "practice/log/{$ARG_SESSION_ID}"

    fun detail(id: Long) = "practice/detail/$id"
    fun edit(id: Long = NO_ID) = "practice/edit?$ARG_PRACTICE_ID=$id"
    fun live(sessionId: Long) = "practice/live/$sessionId"
    fun log(sessionId: Long) = "practice/log/$sessionId"
}

fun NavGraphBuilder.practiceGraph(navController: NavHostController) {
    val navigate: (String) -> Unit = { navController.navigate(it) }
    navigation(startDestination = PracticeRoutes.HOME, route = PracticeRoutes.GRAPH) {
        composable(PracticeRoutes.HOME) {
            PracticeHomeScreen(onNavigate = navigate)
        }
        composable(
            PracticeRoutes.DETAIL,
            arguments = listOf(navArgument(PracticeRoutes.ARG_PRACTICE_ID) { type = NavType.LongType }),
        ) {
            PracticeDetailScreen(
                onBack = { navController.popBackStack() },
                onNavigate = navigate,
            )
        }
        composable(
            PracticeRoutes.EDIT,
            arguments = listOf(
                navArgument(PracticeRoutes.ARG_PRACTICE_ID) {
                    type = NavType.LongType
                    defaultValue = PracticeRoutes.NO_ID
                },
            ),
        ) {
            PracticeEditScreen(
                onBack = { navController.popBackStack() },
                onSaved = { practiceId, isNew ->
                    if (isNew) {
                        navController.navigate(PracticeRoutes.detail(practiceId)) {
                            popUpTo(PracticeRoutes.EDIT) { inclusive = true }
                        }
                    } else {
                        navController.popBackStack()
                    }
                },
            )
        }
        composable(
            PracticeRoutes.LIVE,
            arguments = listOf(navArgument(PracticeRoutes.ARG_SESSION_ID) { type = NavType.LongType }),
        ) {
            PracticeLiveScreen(
                onFinished = { sessionId ->
                    navController.navigate(PracticeRoutes.log(sessionId)) {
                        popUpTo(PracticeRoutes.LIVE) { inclusive = true }
                    }
                },
                onDiscarded = { navController.popBackStack() },
                onNavigate = navigate,
            )
        }
        composable(
            PracticeRoutes.LOG,
            arguments = listOf(navArgument(PracticeRoutes.ARG_SESSION_ID) { type = NavType.LongType }),
        ) {
            SessionLogScreen(
                onBack = { navController.popBackStack() },
                onNavigate = navigate,
            )
        }
    }
}
