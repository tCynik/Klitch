package ru.tcynik.meshtactics.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import org.koin.compose.viewmodel.koinViewModel
import ru.tcynik.meshtactics.BuildConfig
import ru.tcynik.meshtactics.presentation.feature.chat.ChatScreen
import ru.tcynik.meshtactics.presentation.feature.chat.ChatViewModel
import ru.tcynik.meshtactics.presentation.feature.groups.GroupManagementScreen
import ru.tcynik.meshtactics.presentation.feature.groups.GroupsViewModel
import ru.tcynik.meshtactics.presentation.feature.main.MainScreen
import ru.tcynik.meshtactics.presentation.feature.main.MainViewModel
import ru.tcynik.meshtactics.presentation.feature.markers.MarkerManagementScreen
import ru.tcynik.meshtactics.presentation.feature.markers.MarkersViewModel
import ru.tcynik.meshtactics.presentation.feature.meshtest.MeshTestScreen
import ru.tcynik.meshtactics.presentation.feature.node.NodeSettingsScreen
import ru.tcynik.meshtactics.presentation.feature.node.NodeSettingsViewModel
import ru.tcynik.meshtactics.presentation.feature.node.NodeStatusDialog
import ru.tcynik.meshtactics.presentation.feature.node.NodeStatusViewModel
import ru.tcynik.meshtactics.presentation.feature.nodes.NodesScreen
import ru.tcynik.meshtactics.presentation.feature.settings.SettingsScreen
import ru.tcynik.meshtactics.presentation.feature.settings.SettingsViewModel

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
                val viewModel: MainViewModel = koinViewModel()
                val uiState by viewModel.uiState.collectAsState()
                MainScreen(
                    uiState = uiState,
                    onChatClick = { navController.navigate(Route.Chat) },
                    onSettingsClick = { navController.navigate(Route.Settings) },
                    onNodeStatusClick = { navController.navigate(Route.NodeStatus) },
                    onMarkerManagementClick = { navController.navigate(Route.MarkerManagement) },
                )
            }

            // ── Feature screens (NavGraph modal destinations) ────────────────
            composable<Route.Chat> {
                val viewModel: ChatViewModel = koinViewModel()
                val uiState by viewModel.uiState.collectAsState()
                ChatScreen(
                    uiState = uiState,
                    onNavigateBack = { navController.popBackStack() },
                )
            }

            composable<Route.Settings> {
                val viewModel: SettingsViewModel = koinViewModel()
                val uiState by viewModel.uiState.collectAsState()
                SettingsScreen(
                    uiState = uiState,
                    onNavigateBack = { navController.popBackStack() },
                )
            }

            composable<Route.NodeSettings> {
                val viewModel: NodeSettingsViewModel = koinViewModel()
                val uiState by viewModel.uiState.collectAsState()
                NodeSettingsScreen(
                    uiState = uiState,
                    onNavigateBack = { navController.popBackStack() },
                )
            }

            dialog<Route.NodeStatus> {
                val viewModel: NodeStatusViewModel = koinViewModel()
                val uiState by viewModel.uiState.collectAsState()
                NodeStatusDialog(
                    uiState = uiState,
                    onDismiss = { navController.popBackStack() },
                    onNavigateToSettings = { navController.navigate(Route.NodeSettings) },
                )
            }

            composable<Route.MarkerManagement> {
                val viewModel: MarkersViewModel = koinViewModel()
                val uiState by viewModel.uiState.collectAsState()
                MarkerManagementScreen(
                    uiState = uiState,
                    onNavigateBack = { navController.popBackStack() },
                )
            }

            composable<Route.GroupManagement> {
                val viewModel: GroupsViewModel = koinViewModel()
                val uiState by viewModel.uiState.collectAsState()
                GroupManagementScreen(
                    uiState = uiState,
                    onNavigateBack = { navController.popBackStack() },
                )
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
