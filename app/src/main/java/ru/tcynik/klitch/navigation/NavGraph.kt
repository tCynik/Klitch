package ru.tcynik.klitch.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.compose.rememberNavController
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.maplibre.compose.location.LocationProvider
import ru.tcynik.klitch.data.gps.NodeGpsWatchdog
import ru.tcynik.klitch.di.orientation.DeviceOrientationProvider
import ru.tcynik.klitch.presentation.feature.chat.ChatScreen
import ru.tcynik.klitch.presentation.feature.chat.ChatViewModel
import ru.tcynik.klitch.presentation.feature.groups.GroupManagementScreen
import ru.tcynik.klitch.presentation.feature.groups.GroupsViewModel
import ru.tcynik.klitch.presentation.feature.main.ConnectionViewModel
import ru.tcynik.klitch.presentation.feature.main.EmergencyViewModel
import ru.tcynik.klitch.presentation.feature.main.GeoMarkViewModel
import ru.tcynik.klitch.presentation.feature.main.HudNavCallbacks
import ru.tcynik.klitch.presentation.feature.main.MainScreen
import ru.tcynik.klitch.presentation.feature.main.MainViewModel
import ru.tcynik.klitch.presentation.feature.main.TrackRecordingViewModel
import ru.tcynik.klitch.domain.track.model.TrackRecordingState
import ru.tcynik.klitch.presentation.feature.main.osd.HudStateMapper
import ru.tcynik.klitch.presentation.feature.main.osd.TrackStopConfirmDialog
import ru.tcynik.klitch.presentation.feature.marks.GeoMarksListScreen
import ru.tcynik.klitch.presentation.feature.marks.GeoMarksListViewModel
import ru.tcynik.klitch.presentation.feature.markers.MarkerManagementScreen
import ru.tcynik.klitch.presentation.feature.markers.MarkersViewModel
import ru.tcynik.klitch.presentation.feature.network.NetworkScreen
import ru.tcynik.klitch.presentation.feature.network.NetworkSettingsScreen
import ru.tcynik.klitch.presentation.feature.node.NodeSettingsScreen
import ru.tcynik.klitch.presentation.feature.node.NodeSettingsViewModel
import ru.tcynik.klitch.presentation.feature.node.NodeStatusDialog
import ru.tcynik.klitch.presentation.feature.node.NodeStatusViewModel
import ru.tcynik.klitch.presentation.feature.nodes.NodesScreen
import android.app.Activity
import androidx.compose.ui.platform.LocalContext
import ru.tcynik.klitch.presentation.feature.settings.display.DisplaySettingsScreen
import ru.tcynik.klitch.presentation.feature.settings.main.MainSettingsScreen
import ru.tcynik.klitch.presentation.feature.settings.map.MapSettingsScreen
import ru.tcynik.klitch.presentation.feature.settings.user.UserSettingsScreen
import ru.tcynik.klitch.service.GpsService

@Composable
fun NavGraph() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val mainVm: MainViewModel = koinViewModel()
    val emergencyVm: EmergencyViewModel = koinViewModel()
    val connectionVm: ConnectionViewModel = koinViewModel()
    val geoMarkVm: GeoMarkViewModel = koinViewModel()
    val trackVm: TrackRecordingViewModel = koinViewModel()

    val trackSheetState by trackVm.trackRecordingSheetUiState.collectAsState()

    LaunchedEffect(Unit) {
        trackVm.exitAppEvent.collect {
            context.stopService(GpsService.createIntent(context))
            (context as? Activity)?.finishAndRemoveTask()
        }
    }

    LaunchedEffect(Unit) {
        trackVm.trackNoMovementDiscardedEvent.collect {
            android.widget.Toast.makeText(
                context,
                context.getString(ru.tcynik.klitch.R.string.track_no_movement_discarded),
                android.widget.Toast.LENGTH_SHORT,
            ).show()
        }
    }

    val nodeGpsWatchdog: NodeGpsWatchdog = koinInject()
    LaunchedEffect(Unit) {
        nodeGpsWatchdog.staleEvent.collect {
            android.widget.Toast.makeText(
                context,
                context.getString(ru.tcynik.klitch.R.string.node_gps_watchdog_toast),
                android.widget.Toast.LENGTH_LONG,
            ).show()
        }
    }

    val stopDialogRs = trackSheetState.recordingState
    if (trackSheetState.showStopDialog && stopDialogRs is TrackRecordingState.Recording) {
        TrackStopConfirmDialog(
            initialName            = stopDialogRs.name,
            trimToMovement         = trackSheetState.trimToMovement,
            onTrimToMovementChanged = trackSheetState.onTrimToMovementChanged,
            onSave                 = trackSheetState.onStopDialogSave,
            onDiscard              = trackSheetState.onStopDialogDiscard,
            onCancel               = trackSheetState.onStopDialogCancel,
        )
    }

    BlePermissionGuard {
        NavHost(
            navController = navController,
            startDestination = Route.Main,
        ) {

            // ── Primary destination ──────────────────────────────────────────
            composable<Route.Main> {
                val mainState by mainVm.uiState.collectAsState()
                val emergencyState by emergencyVm.uiState.collectAsState()
                val connState by connectionVm.uiState.collectAsState()
                val geoMarkState by geoMarkVm.uiState.collectAsState()
                val geoMarksSheetUiState by geoMarkVm.geoMarksSheetUiState.collectAsState()
                val trackRecordingSheetUiState = trackSheetState
                val locationProvider: LocationProvider = koinInject()
                val orientationProvider: DeviceOrientationProvider = koinInject()

                LaunchedEffect(Unit) {
                    geoMarkVm.onMainDestinationVisible()
                }

                val geoMarkSheetVisible = rememberUpdatedState(geoMarkState.isMarksSheetVisible)
                val trackSheetVisible = rememberUpdatedState(trackRecordingSheetUiState.isVisible)

                val navCallbacks = remember(navController) {
                    HudNavCallbacks(
                        onRadioClick           = { navController.navigate(Route.Network) },
                        onMeshClick            = { navController.navigate(Route.Nodes) },
                        onChatClick            = { navController.navigate(Route.Chat) },
                        onMainSettingsClick    = { navController.navigate(Route.MainSettings) },
                        onMapSettingsClick     = { navController.navigate(Route.MapSettings) },
                        onDisplaySettingsClick = { navController.navigate(Route.DisplaySettings) },
                        onUserSettingsClick    = { navController.navigate(Route.UserSettings) },
                        onGeoMarksList         = { navController.navigate(Route.GeoMarksList) },
                        onExitApp              = {
                            mainVm.closeMenuDrawer()
                            trackVm.requestExitIfSafe()
                        },
                        onToggleMenuDrawer     = mainVm::toggleMenuDrawer,
                        onFollowMeToggle       = mainVm::onFollowMeToggle,
                        onCompassTap           = mainVm::onCompassTap,
                        onToggleMarkTool       = geoMarkVm::toggleMarkTool,
                        onToggleGeoMarksSheet  = {
                            if (!geoMarkSheetVisible.value) trackVm.closeTrackRecordingSheetVisibility()
                            geoMarkVm.toggleGeoMarksSheet()
                        },
                        onToggleTrackRecordingSheet = {
                            if (!trackSheetVisible.value) geoMarkVm.closeGeoMarksSheet()
                            trackVm.toggleTrackRecordingSheet()
                        },
                        onSosClick = emergencyVm::onSosButtonClick,
                    )
                }

                val hudConfig = remember(mainState, connState, geoMarkState, navCallbacks) {
                    HudStateMapper.buildHudConfig(mainState, connState, geoMarkState, navCallbacks)
                }
                val hudUiState = remember(mainState, connState, geoMarkState, trackSheetState.recordingState, navCallbacks) {
                    HudStateMapper.buildHudUiState(mainState, connState, geoMarkState, trackSheetState.recordingState, navCallbacks)
                }
                val menuDrawerUiState = remember(mainState, emergencyState, navCallbacks) {
                    HudStateMapper.buildMenuDrawerUiState(mainState, emergencyState.isSosActive, navCallbacks)
                }

                MainScreen(
                    mainState = mainState,
                    emergencyState = emergencyState,
                    geoMarkUiState = geoMarkState,
                    hudConfig = hudConfig,
                    hudUiState = hudUiState,
                    onCameraPositionChanged = mainVm::onCameraPositionChanged,
                    locationProvider = locationProvider,
                    orientationProvider = orientationProvider,
                    onMapClick = { lat, lon, sx, sy ->
                        val nodeNames = mainState.nodeMarkers.associate { it.nodeId to it.longName }
                        geoMarkVm.onMapClick(lat, lon, sx, sy, nodeNames)
                    },
                    onMapDoubleClick = geoMarkVm::onMapDoubleClick,
                    onMapLongClick = geoMarkVm::onMapLongClick,
                    contextMenuEvents = geoMarkVm.contextMenuEvent,
                    onHideGeoMark = geoMarkVm::hideGeoMark,
                    onDeleteGeoMark = geoMarkVm::requestDeleteGeoMark,
                    onConfirmDeleteGeoMark = geoMarkVm::confirmDeleteGeoMark,
                    onDismissDeleteGeoMarkConfirm = geoMarkVm::dismissDeleteGeoMarkConfirm,
                    onSosRestoredKeep = emergencyVm::onSosRestoredKeep,
                    onSosRestoredDisable = emergencyVm::onSosRestoredDisable,
                    onSosTriggerConfirm = emergencyVm::onTriggerSosConfirm,
                    onSosCancelConfirm = emergencyVm::onCancelSosConfirm,
                    onSosDismiss = emergencyVm::onDismissSosDialog,
                    onSendGeoMark = geoMarkVm::prepareGeoMarkForResend,
                    menuDrawerUiState = menuDrawerUiState,
                    geoMarksSheetUiState = geoMarksSheetUiState,
                    trackRecordingSheetUiState = trackRecordingSheetUiState,
                    onFollowMeDeactivated = mainVm::onFollowMeDeactivated,
                    resetBearingEvents = mainVm.resetBearingEvent,
                    restoreZoomEvents = mainVm.restoreZoomEvent,
                    onMapBearingChanged = mainVm::onMapBearingChanged,
                    onMapRotatedByUser = mainVm::onMapRotatedByUser,
                    onCourseUpToggle = mainVm::onCourseUpToggle,
                    onFollowMeRestoreZoom = mainVm::onFollowMeRestoreZoom,
                    onClearGeoMarkSelection = geoMarkVm::clearSelectedGeoMark,
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
                        mainVm.closeMenuDrawer()
                        trackVm.requestExitIfSafe()
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
                    onExportTrackResult = viewModel::onExportTrackResult,
                    onImportTrackResult = viewModel::onImportTrackResult,
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
