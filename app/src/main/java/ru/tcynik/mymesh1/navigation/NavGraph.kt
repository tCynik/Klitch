package ru.tcynik.mymesh1.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ru.tcynik.mymesh1.presentation.feature.nodes.NodesScreen

@Composable
fun NavGraph() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Route.Nodes,
    ) {
        composable<Route.Nodes> {
            NodesScreen(
                onNodeClick = { nodeId ->
                    navController.navigate(Route.NodeDetail(nodeId))
                },
            )
        }

        composable<Route.NodeDetail> {
            // NodeDetailScreen — реализовать при добавлении фичи
        }
    }
}
