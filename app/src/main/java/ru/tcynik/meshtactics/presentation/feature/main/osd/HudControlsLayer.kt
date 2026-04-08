package ru.tcynik.meshtactics.presentation.feature.main.osd

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ru.tcynik.meshtactics.domain.mesh.model.MeshConnectionStatus

// BLE RSSI threshold separating low signal (red) from medium/high signal (green).
// Adjust this value based on field experience; -90 dBm is standard Meshtastic convention.
private const val RSSI_LOW_THRESHOLD = -90

// HUD button columns overlay — rendered on top of MapLibreLayer.
// Left column: map tool buttons (follow, zoom, grab, record track, plan route).
// Right column: main menu buttons (chat, settings, node status indicator, filter, add marker, tools).
// Both columns are transparent-background icon buttons (MeshIconButton — to be designed in Phase 2).
@Composable
fun HudControlsLayer(
    modifier: Modifier = Modifier,
    connectionStatus: MeshConnectionStatus = MeshConnectionStatus.Disconnected,
    nodesWithPositionCount: Int = 0,
    onChatClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onNodeStatusClick: () -> Unit = {},
    onMarkerManagementClick: () -> Unit = {},
) {
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Left column — map tools
            Column(
                modifier = Modifier.fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            ) {
                // TODO: MapToolButton(icon = FollowIcon, onClick = { ... })
                // TODO: MapToolButton(icon = ZoomInIcon, onClick = { ... })
                // TODO: MapToolButton(icon = ZoomOutIcon, onClick = { ... })
                // TODO: MapToolButton(icon = GrabIcon, onClick = { ... })
                // TODO: MapToolButton(icon = RecordTrackIcon, onClick = { ... })
                // TODO: MapToolButton(icon = PlanRouteIcon, onClick = { ... })
            }

            // Right column — main menu
            Column(
                modifier = Modifier.fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Top),
            ) {
                NodeStatusIndicator(
                    connectionStatus = connectionStatus,
                    nodesWithPositionCount = nodesWithPositionCount,
                    onClick = onNodeStatusClick,
                )
                // TODO: MenuButton(icon = ChatIcon, onClick = onChatClick)
                // TODO: MenuButton(icon = SettingsIcon, onClick = onSettingsClick)
                // TODO: MenuButton(icon = NodeStatusIcon, onClick = onNodeStatusClick)
                // TODO: MenuButton(icon = MarkerIcon, onClick = onMarkerManagementClick)
            }
        }
    }
}

@Composable
private fun NodeStatusIndicator(
    connectionStatus: MeshConnectionStatus,
    nodesWithPositionCount: Int,
    onClick: () -> Unit,
) {
    val (text, color) = when (connectionStatus) {
        is MeshConnectionStatus.Connected -> {
            val signalColor = if (connectionStatus.rssi < RSSI_LOW_THRESHOLD) Color.Red else Color.Green
            nodesWithPositionCount.toString() to signalColor
        }
        else -> "--" to Color.Gray
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clickable(onClick = onClick),
    ) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.titleLarge,
        )
    }
}
