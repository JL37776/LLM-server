package com.nzshores.llmserver.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nzshores.llmserver.ui.library.LibraryScreen
import com.nzshores.llmserver.ui.monitor.MonitorScreen
import com.nzshores.llmserver.ui.search.SearchScreen
import com.nzshores.llmserver.ui.server.ServerScreen

@Composable
fun LlmManagerNavHost() {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry?.destination

            NavigationBar {
                Destination.entries.forEach { destination ->
                    val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Search.route,
            modifier = androidx.compose.ui.Modifier.padding(innerPadding),
        ) {
            composable(Destination.Search.route) { SearchScreen() }
            composable(Destination.Library.route) { LibraryScreen() }
            composable(Destination.Server.route) { ServerScreen() }
            composable(Destination.Monitor.route) { MonitorScreen() }
        }
    }
}
