package com.example.myapplication.ui.library

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument

object LibraryRoutes {
    const val GRAPH = "library"
    const val ARG_CATEGORY_ID = "categoryId"
    const val ARG_MOVE_ID = "moveId"

    const val HOME = "library/home"
    const val CATEGORY = "library/category/{$ARG_CATEGORY_ID}"
    const val MOVE = "library/move/{$ARG_MOVE_ID}"
    const val MOVE_EDIT = "library/move-edit?$ARG_MOVE_ID={$ARG_MOVE_ID}&$ARG_CATEGORY_ID={$ARG_CATEGORY_ID}"
    const val CATEGORIES = "library/categories"

    fun category(id: Long) = "library/category/$id"
    fun move(id: Long) = "library/move/$id"
    fun newMove(categoryId: Long? = null) =
        "library/move-edit?$ARG_MOVE_ID=${MoveEditViewModel.NO_ID}&$ARG_CATEGORY_ID=${categoryId ?: MoveEditViewModel.NO_ID}"
    fun editMove(id: Long) = "library/move-edit?$ARG_MOVE_ID=$id&$ARG_CATEGORY_ID=${MoveEditViewModel.NO_ID}"
}

fun NavGraphBuilder.libraryGraph(navController: NavHostController) {
    navigation(startDestination = LibraryRoutes.HOME, route = LibraryRoutes.GRAPH) {
        composable(LibraryRoutes.HOME) {
            LibraryScreen(
                onCategoryClick = { navController.navigate(LibraryRoutes.category(it)) },
                onAddMove = { navController.navigate(LibraryRoutes.newMove()) },
                onManageCategories = { navController.navigate(LibraryRoutes.CATEGORIES) },
            )
        }
        composable(
            LibraryRoutes.CATEGORY,
            arguments = listOf(navArgument(LibraryRoutes.ARG_CATEGORY_ID) { type = NavType.LongType }),
        ) {
            CategoryMovesScreen(
                onBack = { navController.popBackStack() },
                onMoveClick = { navController.navigate(LibraryRoutes.move(it)) },
                onAddMove = { navController.navigate(LibraryRoutes.newMove(it)) },
            )
        }
        composable(
            LibraryRoutes.MOVE,
            arguments = listOf(navArgument(LibraryRoutes.ARG_MOVE_ID) { type = NavType.LongType }),
        ) {
            MoveDetailScreen(
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate(LibraryRoutes.editMove(it)) },
                onMoveClick = { navController.navigate(LibraryRoutes.move(it)) },
            )
        }
        composable(
            LibraryRoutes.MOVE_EDIT,
            arguments = listOf(
                navArgument(LibraryRoutes.ARG_MOVE_ID) {
                    type = NavType.LongType
                    defaultValue = MoveEditViewModel.NO_ID
                },
                navArgument(LibraryRoutes.ARG_CATEGORY_ID) {
                    type = NavType.LongType
                    defaultValue = MoveEditViewModel.NO_ID
                },
            ),
        ) {
            MoveEditScreen(
                onBack = { navController.popBackStack() },
                onSaved = { moveId, isNew ->
                    if (isNew) {
                        // Show the new move, with the editor removed from the back stack.
                        navController.navigate(LibraryRoutes.move(moveId)) {
                            popUpTo(LibraryRoutes.MOVE_EDIT) { inclusive = true }
                        }
                    } else {
                        navController.popBackStack()
                    }
                },
            )
        }
        composable(LibraryRoutes.CATEGORIES) {
            ManageCategoriesScreen(onBack = { navController.popBackStack() })
        }
    }
}
