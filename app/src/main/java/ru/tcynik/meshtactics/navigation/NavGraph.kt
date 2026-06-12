package ru.tcynik.meshtactics.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.compose.rememberNavController
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.maplibre.compose.location.LocationProvider
import ru.tcynik.meshtactics.di.orientation.DeviceOrientationProvider
import ru.tcynik.meshtactics.presentation.feature.chat.ChatScreen
import ru.tcynik.meshtactics.presentation.feature.chat.ChatViewModel
import ru.tcynik.meshtactics.presentation.feature.groups.GroupManagementScreen
import ru.tcynik.meshtactics.presentation.feature.groups.GroupsViewModel
import ru.tcynik.meshtactics.presentation.feature.main.HudNavCallbacks
import ru.tcynik.meshtactics.presentation.feature.main.MainScreen
import ru.tcynik.meshtactics.presentation.feature.main.MainViewModel
import ru.tcynik.meshtactics.domain.track.model.TrackRecordingState
import ru.tcynik.meshtactics.presentation.feature.main.osd.TrackStopConfirmDialog
import ru.tcynik.meshtactics.presentation.feature.marks.GeoMarksListScreen
import ru.tcynik.meshtactics.presentation.feature.marks.GeoMarksListViewModel
import ru.tcynik.meshtactics.presentation.feature.markers.MarkerManagementScreen
import ru.tcynik.meshtactics.presentation.feature.markers.MarkersViewModel
import ru.tcynik.meshtactics.presentation.feature.network.NetworkScreen
import ru.tcynik.meshtactics.presentation.feature.network.NetworkSettingsScreen
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
    val mainViewModel: MainViewModel = koinViewModel()
    val trackState by mainViewModel.trackRecordingSheetUiState.collectAsState()

    LaunchedEffect(Unit) {
        mainViewModel.exitAppEvent.collect {
            context.stopService(GpsService.createIntent(context))
            (context as? Activity)?.finishAndRemoveTask()
        }
    }

    LaunchedEffect(Unit) {
        mainViewModel.trackNoMovementDiscardedEvent.collect {
            android.widget.Toast.makeText(
                context,
                context.getString(ru.tcynik.meshtactics.R.string.track_no_movement_discarded),
                android.widget.Toast.LENGTH_SHORT,
            ).show()
        }
    }

    val stopDialogRs = trackState.recordingState
    if (trackState.showStopDialog && stopDialogRs is TrackRecordingState.Recording) {
        TrackStopConfirmDialog(
            initialName            = stopDialogRs.name,
            trimToMovement         = trackState.trimToMovement,
            onTrimToMovementChanged = trackState.onTrimToMovementChanged,
            onSave                 = trackState.onStopDialogSave,
            onDiscard              = trackState.onStopDialogDiscard,
            onCancel               = trackState.onStopDialogCancel,
        )
    }

    BlePermissionGuard {
        NavHost(
            navController = navController,
            startDestination = Route.Main,
        ) {

            // ── Primary destination ──────────────────────────────────────────
            composable<Route.Main> {
                val uiState by mainViewModel.uiState.collectAsState()
                val hudConfig by mainViewModel.hudConfig.collectAsState()
                val hudUiState by mainViewModel.hudUiState.collectAsState()
                val menuDrawerUiState by mainViewModel.menuDrawerUiState.collectAsState()
                val geoMarksSheetUiState by mainViewModel.geoMarksSheetUiState.collectAsState()
                val trackRecordingSheetUiState by mainViewModel.trackRecordingSheetUiState.collectAsState()
                val locationProvider: LocationProvider = koinInject()
                val orientationProvider: DeviceOrientationProvider = koinInject()

                // Provide navigation callbacks to ViewModel once navController is available.
                // Unit key — callbacks are stable for the lifetime of this destination.
                LaunchedEffect(Unit) {
                    mainViewModel.onMainDestinationVisible()
                    mainViewModel.provideNavCallbacks(
                        HudNavCallbacks(
                            onRadioClick           = { navController.navigate(Route.Network) },
                            onMeshClick            = { navController.navigate(Route.Nodes) },
                            onChatClick            = { navController.navigate(Route.Chat) },
                            onMainSettingsClick    = { navController.navigate(Route.MainSettings) },
                            onMapSettingsClick     = { navController.navigate(Route.MapSettings) },
                            onDisplaySettingsClick = { navController.navigate(Route.DisplaySettings) },
                            onUserSettingsClick    = { navController.navigate(Route.UserSettings) },
                            onGeoMarksList         = { navController.navigate(Route.GeoMarksList) },
                            onExitApp              = mainViewModel::requestExitApp,
                        )
                    )
                }

                MainScreen(
                    uiState = uiState,
                    hudConfig = hudConfig,
                    hudUiState = hudUiState,
                    onCameraPositionChanged = mainViewModel::onCameraPositionChanged,
                    locationProvider = locationProvider,
                    orientationProvider = orientationProvider,
                    onMapClick = mainViewModel::onMapClick,
                    onMapDoubleClick = mainViewModel::onMapDoubleClick,
                    onMapLongClick = mainViewModel::onMapLongClick,
                    contextMenuEvents = mainViewModel.contextMenuEvent,
                    onHideGeoMark = mainViewModel::hideGeoMark,
                    onDeleteGeoMark = mainViewModel::requestDeleteGeoMark,
                    onConfirmDeleteGeoMark = mainViewModel::confirmDeleteGeoMark,
                    onDismissDeleteGeoMarkConfirm = mainViewModel::dismissDeleteGeoMarkConfirm,
                    onSosRestoredKeep = mainViewModel::onSosRestoredKeep,
                    onSosRestoredDisable = mainViewModel::onSosRestoredDisable,
                    onSosTriggerConfirm = mainViewModel::onTriggerSosConfirm,
                    onSosCancelConfirm = mainViewModel::onCancelSosConfirm,
                    onSosDismiss = mainViewModel::onDismissSosDialog,
                    onSendGeoMark = mainViewModel::prepareGeoMarkForResend,
                    menuDrawerUiState = menuDrawerUiState,
                    geoMarksSheetUiState = geoMarksSheetUiState,
                    trackRecordingSheetUiState = trackRecordingSheetUiState,
                    onFollowMeDeactivated = mainViewModel::onFollowMeDeactivated,
                    resetBearingEvents = mainViewModel.resetBearingEvent,
                    restoreZoomEvents = mainViewModel.restoreZoomEvent,
                    onMapBearingChanged = mainViewModel::onMapBearingChanged,
                    onMapRotatedByUser = mainViewModel::onMapRotatedByUser,
                    onCourseUpToggle = mainViewModel::onCourseUpToggle,
                    onFollowMeRestoreZoom = mainViewModel::onFollowMeRestoreZoom,
                    onClearGeoMarkSelection = mainViewModel::clearSelectedGeoMark,
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
                    onExitApp = mainViewModel::requestExitApp,
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

            composable<Route.GeoMarksList> {
                val viewModel: GeoMarksListViewModel = koinViewModel()
                val uiState by viewModel.uiState.collectAsState()
                GeoMarksListScreen(
                    uiState = uiState,
                    onVisibilityToggle = viewModel::onVisibilityToggle,
                    onDeliveryFilterToggle = viewModel::onDeliveryFilterToggle,
                    onToggleAllFilteredVisibility = viewModel::onToggleAllFilteredVisibility,
                    onDeleteClick = viewModel::onDeleteClick,
                    onConfirmDelete = viewModel::onConfirmDelete,
                    onDismissDeleteDialog = viewModel::onDismissDeleteDialog,
                    onItemDeleteClick = viewModel::onItemDeleteClick,
                    onItemExtendClick = viewModel::onItemExtendClick,
                    onItemSendClick = viewModel::onItemSendClick,
                    onSendContourSelected = viewModel::onSendContourSelected,
                    onDismissSendContourPicker = viewModel::onDismissSendContourPicker,
                    onTrackVisibilityToggle = viewModel::onTrackVisibilityToggle,
                    onTrackDeleteClick = viewModel::onTrackDeleteClick,
                    onTracksFilterToggle = viewModel::onTracksFilterToggle,
                    onBack = { navController.popBackStack() },
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
                    onNodeClick = { navController.navigate(Route.Network) },
                    onNavigateBack = { navController.popBackStack() },
                )
            }

            composable<Route.NodeDetail> {
                // NodeDetailScreen — реализовать при добавлении фичи
            }

            composable<Route.Network> {
                NetworkScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToSettings = { navController.navigate(Route.NetworkSettings) },
                )
            }

            composable<Route.NetworkSettings> {
                NetworkSettingsScreen(
                    onNavigateBack = { navController.popBackStack() },
                )
            }
        }
    }
}
