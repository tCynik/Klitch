package ru.tcynik.meshtactics.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.maplibre.compose.location.LocationProvider
import ru.tcynik.meshtactics.BuildConfig
import ru.tcynik.meshtactics.di.orientation.DeviceOrientationProvider
import ru.tcynik.meshtactics.presentation.feature.chat.ChatScreen
import ru.tcynik.meshtactics.presentation.feature.chat.ChatViewModel
import ru.tcynik.meshtactics.presentation.feature.groups.GroupManagementScreen
import ru.tcynik.meshtactics.presentation.feature.groups.GroupsViewModel
import ru.tcynik.meshtactics.presentation.feature.main.HudNavCallbacks
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
import android.app.Activity
import androidx.compose.ui.platform.LocalContext
import ru.tcynik.meshtactics.presentation.feature.settings.display.DisplaySettingsScreen
import ru.tcynik.meshtactics.presentation.feature.settings.main.MainSettingsScreen
import ru.tcynik.meshtactics.presentation.feature.settings.map.MapSettingsScreen
import ru.tcynik.meshtactics.presentation.feature.settings.user.UserSettingsScreen
import ru.tcynik.meshtactics.service.GpsService

@Composable
fun NavGraph() {
    val navController = rememberNavController()
    val context = LocalContext.current

    BlePermissionGuard {
        NavHost(
            navController = navController,
            startDestination = Route.Main,
        ) {

            // ── Primary destination ──────────────────────────────────────────
            composable<Route.Main> {
                val viewModel: MainViewModel = koinViewModel()
                val uiState by viewModel.uiState.collectAsState()
                val hudConfig by viewModel.hudConfig.collectAsState()
                val hudUiState by viewModel.hudUiState.collectAsState()
                val menuDrawerUiState by viewModel.menuDrawerUiState.collectAsState()
                val locationProvider: LocationProvider = koinInject()
                val orientationProvider: DeviceOrientationProvider = koinInject()

                // Provide navigation callbacks to ViewModel once navController is available.
                // Unit key — callbacks are stable for the lifetime of this destination.
                LaunchedEffect(Unit) {
                    viewModel.provideNavCallbacks(
                        HudNavCallbacks(
                            onRadioClick           = { navController.navigate(Route.MeshTest()) },
                            onMeshClick            = { navController.navigate(Route.Nodes) },
                            onChatClick            = { navController.navigate(Route.Chat) },
                            onMainSettingsClick    = { navController.navigate(Route.MainSettings) },
                            onMapSettingsClick     = { navController.navigate(Route.MapSettings) },
                            onDisplaySettingsClick = { navController.navigate(Route.DisplaySettings) },
                            onUserSettingsClick    = { navController.navigate(Route.UserSettings) },
                            onExitApp              = {
                                context.stopService(GpsService.createIntent(context))
                                (context as? Activity)?.finishAndRemoveTask()
                            },
                        )
                    )
                }

                MainScreen(
                    uiState = uiState,
                    hudConfig = hudConfig,
                    hudUiState = hudUiState,
                    onCameraPositionChanged = viewModel::onCameraPositionChanged,
                    locationProvider = locationProvider,
                    orientationProvider = orientationProvider,
                    onMapClick = viewModel::onMapClick,
                    onMapLongClick = viewModel::onMapLongClick,
                    onSendPendingMark = viewModel::sendPendingMark,
                    contextMenuEvents = viewModel.contextMenuEvent,
                    onDeletePendingPoint = viewModel::deletePendingPoint,
                    menuDrawerUiState = menuDrawerUiState,
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

            composable<Route.MainSettings> {
                MainSettingsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onExitApp = {
                        context.stopService(GpsService.createIntent(context))
                        (context as? Activity)?.finishAndRemoveTask()
                    },
                )
            }

            composable<Route.MapSettings> {
                MapSettingsScreen(
                    onNavigateBack = { navController.popBackStack() },
                )
            }

            composable<Route.DisplaySettings> {
                DisplaySettingsScreen(
                    onNavigateBack = { navController.popBackStack() },
                )
            }

            composable<Route.UserSettings> {
                UserSettingsScreen(
                    onNavigateBack = { navController.popBackStack() },
                )
            }

            composable<Route.NodeSettings> {
                val viewModel: NodeSettingsViewModel = koinViewModel()
                val uiState by viewModel.uiState.collectAsState()
                NodeSettingsScreen(
                    uiState = uiState,
                    onNavigateBack = { navController.popBackStack() },
                    onRegenerateClick = viewModel::onRegenerateClick,
                    onRegenerateConfirm = viewModel::onRegenerateConfirm,
                    onRegenerateDismiss = viewModel::onRegenerateDismiss,
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
