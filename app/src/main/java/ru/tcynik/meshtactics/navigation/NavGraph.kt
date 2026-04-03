package ru.tcynik.meshtactics.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import ru.tcynik.meshtactics.BuildConfig
import ru.tcynik.meshtactics.presentation.feature.chat.ChatScreen
import ru.tcynik.meshtactics.presentation.feature.groups.GroupManagementScreen
import ru.tcynik.meshtactics.presentation.feature.main.MainScreen
import ru.tcynik.meshtactics.presentation.feature.markers.MarkerManagementScreen
import ru.tcynik.meshtactics.presentation.feature.meshtest.MeshTestScreen
import ru.tcynik.meshtactics.presentation.feature.node.NodeSettingsScreen
import ru.tcynik.meshtactics.presentation.feature.node.NodeStatusDialog
import ru.tcynik.meshtactics.presentation.feature.nodes.NodesScreen
import ru.tcynik.meshtactics.presentation.feature.settings.SettingsScreen

@Composable
fun NavGraph() {
    val navController = rememberNavController()

    BlePermissionGuard {
        NavHost(
            navController = navController,
            startDestination = Route.Main,
        ) {

            // ── Primary destination ──────────────────────────────────────────
            composable<Route.Main> {
                MainScreen(navController = navController)
            }

            // ── Feature screens (NavGraph modal destinations) ────────────────
            composable<Route.Chat> {
                ChatScreen(onNavigateBack = { navController.popBackStack() })
            }

            composable<Route.Settings> {
                SettingsScreen(onNavigateBack = { navController.popBackStack() })
            }

            composable<Route.NodeSettings> {
                NodeSettingsScreen(onNavigateBack = { navController.popBackStack() })
            }

            dialog<Route.NodeStatus> {
                NodeStatusDialog(onDismiss = { navController.popBackStack() })
            }

            composable<Route.MarkerManagement> {
                MarkerManagementScreen(onNavigateBack = { navController.popBackStack() })
            }

            composable<Route.GroupManagement> {
                GroupManagementScreen(onNavigateBack = { navController.popBackStack() })
            }

            // ── Legacy / prototype screens ───────────────────────────────────
            composable<Route.Nodes> {
                NodesScreen(
                    onNodeClick = { nodeId ->
                        navController.navigate(Route.MeshTest(nodeId))
                    },
                )
            }

            composable<Route.NodeDetail> {
                // NodeDetailScreen — реализовать при добавлении фичи
            }

            if (BuildConfig.DEBUG) {
                composable<Route.MeshTest> { backStackEntry ->
                    val route = backStackEntry.toRoute<Route.MeshTest>()
                    MeshTestScreen(
                        nodeId = route.nodeId,
                        onNavigateBack = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}
