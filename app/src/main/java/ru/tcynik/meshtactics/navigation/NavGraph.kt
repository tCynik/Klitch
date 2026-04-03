package ru.tcynik.meshtactics.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import ru.tcynik.meshtactics.presentation.feature.meshtest.MeshTestScreen
import ru.tcynik.meshtactics.presentation.feature.nodes.NodesScreen

@Composable
fun NavGraph() {
    val navController = rememberNavController()

    BlePermissionGuard {
    NavHost(
        navController = navController,
        startDestination = Route.MeshTest(),
    ) {
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
