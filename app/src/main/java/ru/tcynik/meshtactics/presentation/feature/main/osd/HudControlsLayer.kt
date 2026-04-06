package ru.tcynik.meshtactics.presentation.feature.main.osd

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.tcynik.meshtactics.BuildConfig

// HUD button columns overlay — rendered on top of MapLibreLayer.
// Left column: map tool buttons (follow, zoom, grab, record track, plan route).
// Right column: main menu buttons (chat, settings, map source, filter, add marker, tools).
// Both columns are transparent-background icon buttons (MeshIconButton — to be designed in Phase 2).
@Composable
fun HudControlsLayer(
    modifier: Modifier = Modifier,
    onChatClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onNodeStatusClick: () -> Unit = {},
    onMarkerManagementClick: () -> Unit = {},
    onMeshTestClick: () -> Unit = {},
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
                if (BuildConfig.DEBUG) {
                    IconButton(onClick = onMeshTestClick) {
                        Icon(Icons.Default.Build, contentDescription = "Mesh Test")
                    }
                }
                // TODO: MenuButton(icon = ChatIcon, onClick = onChatClick)
                // TODO: MenuButton(icon = SettingsIcon, onClick = onSettingsClick)
                // TODO: MenuButton(icon = NodeStatusIcon, onClick = onNodeStatusClick)
                // TODO: MenuButton(icon = MarkerIcon, onClick = onMarkerManagementClick)
            }
        }
    }
}
