package com.example.myapplication.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.myapplication.BreakingApp
import com.example.myapplication.ui.backup.BackupRoutes
import com.example.myapplication.ui.backup.DataNoticeDialog
import com.example.myapplication.ui.backup.backupScreen
import com.example.myapplication.ui.library.LibraryRoutes
import com.example.myapplication.ui.library.libraryGraph
import com.example.myapplication.ui.practice.PracticeRoutes
import com.example.myapplication.ui.practice.practiceGraph
import com.example.myapplication.ui.sets.SetRoutes
import com.example.myapplication.ui.sets.setsGraph
import com.example.myapplication.ui.videos.VideoLibraryScreen
import com.example.myapplication.ui.videos.videoPlayer

private enum class TopLevelDestination(val route: String, val label: String, val icon: ImageVector) {
    Library(LibraryRoutes.GRAPH, "Library", Icons.Filled.Home),
    Videos("videos", "Videos", Icons.Filled.Star),
    Sets(SetRoutes.GRAPH, "Sets", Icons.AutoMirrored.Filled.List),
    Practice(PracticeRoutes.GRAPH, "Practice", Icons.Filled.DateRange),
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    // A running practice owns the screen, including a video opened from it, so its timer can't be left behind.
    val inLivePractice = listOf(backStackEntry, navController.previousBackStackEntry)
        .any { it?.destination?.route == PracticeRoutes.LIVE }

    // Each screen's own top bar handles the status bar inset; the bottom bar handles the rest.
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (!inLivePractice) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = { Text(destination.label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.Library.route,
            // Consumed so a screen's imePadding only adds what the keyboard covers beyond the bottom bar.
            modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding),
        ) {
            libraryGraph(navController)
            composable(TopLevelDestination.Videos.route) {
                VideoLibraryScreen(onNavigate = { navController.navigate(it) })
            }
            setsGraph(navController)
            videoPlayer(onBack = { navController.popBackStack() })
            practiceGraph(navController)
            backupScreen(onBack = { navController.popBackStack() })
        }
    }

    val backup = (LocalContext.current.applicationContext as BreakingApp).backup
    val backupStatus by backup.status.collectAsStateWithLifecycle()
    if (!backupStatus.noticeSeen && !inLivePractice) {
        DataNoticeDialog(
            onSetUp = {
                backup.markNoticeSeen()
                navController.navigate(BackupRoutes.HOME)
            },
            onNotNow = backup::markNoticeSeen,
        )
    }
}
