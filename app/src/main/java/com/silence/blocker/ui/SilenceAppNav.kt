package com.silence.blocker.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Rule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.silence.blocker.ui.apps.AppsScreen
import com.silence.blocker.ui.home.HomeScreen
import com.silence.blocker.ui.lists.ListsScreen
import com.silence.blocker.ui.rules.RulesScreen
import com.silence.blocker.ui.settings.SettingsScreen
import com.silence.blocker.ui.stats.StatsScreen

private data class Dest(val route: String, val label: String, val icon: ImageVector)

private val destinations = listOf(
    Dest("home", "Home", Icons.Outlined.Home),
    Dest("stats", "Stats", Icons.Outlined.BarChart),
    Dest("lists", "Lists", Icons.Outlined.FilterList),
    Dest("rules", "Rules", Icons.Outlined.Rule),
    Dest("apps", "Apps", Icons.Outlined.Apps),
    Dest("settings", "Settings", Icons.Outlined.Settings),
)

@Composable
fun SilenceAppNav(
    onRequestVpnPermission: () -> Unit,
    onStopVpn: () -> Unit,
) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                destinations.forEach { dest ->
                    NavigationBarItem(
                        selected = current == dest.route,
                        onClick = {
                            navController.navigate(dest.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(dest.icon, contentDescription = dest.label) },
                        label = { Text(dest.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(padding),
        ) {
            composable("home") {
                HomeScreen(
                    onEnable = onRequestVpnPermission,
                    onDisable = onStopVpn,
                )
            }
            composable("stats") { StatsScreen() }
            composable("lists") { ListsScreen() }
            composable("rules") { RulesScreen() }
            composable("apps") { AppsScreen() }
            composable("settings") { SettingsScreen() }
        }
    }
}
