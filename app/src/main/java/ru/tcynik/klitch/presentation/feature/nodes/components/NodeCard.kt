package ru.tcynik.klitch.presentation.feature.nodes.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.tcynik.klitch.domain.model.NodeModel

@Composable
fun NodeCard(
    node: NodeModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = node.name,
                style = MaterialTheme.typography.titleMedium,
            )
            Row(Modifier.padding(top = 4.dp)) {
                Text(
                    text = node.address,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "RSSI: ${node.rssi} dBm",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                text = if (node.isConnected) "Connected" else "Disconnected",
                style = MaterialTheme.typography.bodySmall,
                color = if (node.isConnected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
