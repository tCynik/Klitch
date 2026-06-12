package ru.tcynik.klitch.presentation.feature.nodes.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import ru.tcynik.klitch.presentation.feature.nodes.state.GeoNodesListState
import ru.tcynik.klitch.presentation.feature.nodes.state.models.GeoNodeUi

@Composable
fun GeoNodesList(
    state: GeoNodesListState,
    modifier: Modifier = Modifier,
) {
    var nowSeconds by remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            nowSeconds = System.currentTimeMillis() / 1000
        }
    }

    if (state.nodes.isEmpty()) {
        Text(
            text = "No nodes with geo data",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(16.dp),
        )
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            items(state.nodes, key = { it.nodeId }) { node ->
                GeoNodeRow(node = node, nowSeconds = nowSeconds)
            }
        }
    }
}

@Composable
private fun GeoNodeRow(
    node: GeoNodeUi,
    nowSeconds: Long,
    modifier: Modifier = Modifier,
) {
    val ageSeconds = (nowSeconds - node.positionTime).coerceAtLeast(0)
    val ageFormatted = if (ageSeconds < 60) "${ageSeconds}s" else "${ageSeconds / 60} min"

    val speedKmh = node.groundSpeed * 3.6
    val speedText = "%.0f km/h".format(speedKmh)
    val hasTrack = node.groundTrack > 0

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = node.shortName,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = speedText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1.2f),
            )
            Box(
                modifier = Modifier.weight(0.6f),
                contentAlignment = Alignment.Center,
            ) {
                if (hasTrack) {
                    Icon(
                        imageVector = Icons.Filled.Explore,
                        contentDescription = "Course ${node.groundTrack}°",
                        modifier = Modifier
                            .size(16.dp)
                            .rotate(node.groundTrack.toFloat()),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = node.distanceFormatted,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End,
            )
        }
        Text(
            text = ageFormatted,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
