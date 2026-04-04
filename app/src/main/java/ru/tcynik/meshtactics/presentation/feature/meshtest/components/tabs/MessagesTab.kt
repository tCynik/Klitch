package ru.tcynik.meshtactics.presentation.feature.meshtest.components.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import ru.tcynik.meshtactics.presentation.feature.meshtest.state.ChannelConfigUi
import ru.tcynik.meshtactics.presentation.feature.meshtest.state.MeshConnectionStatusUi
import ru.tcynik.meshtactics.presentation.feature.meshtest.state.MeshMessageUi
import ru.tcynik.meshtactics.presentation.feature.meshtest.state.MessageDirection
import ru.tcynik.meshtactics.presentation.feature.meshtest.state.MessageStatus
import ru.tcynik.meshtactics.presentation.feature.meshtest.state.MessagesTabState

@Composable
fun MessagesTab(
    state: MessagesTabState,
    channels: List<ChannelConfigUi>,
    connectionStatus: MeshConnectionStatusUi,
    onInputChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onChannelSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isConnected = connectionStatus is MeshConnectionStatusUi.Connected
    val listState = rememberLazyListState()

    Column(modifier = modifier.fillMaxSize()) {
        // Channel selector
        if (channels.isNotEmpty()) {
            ChannelSelector(
                channels = channels,
                selectedChannel = state.selectedChannel,
                onChannelSelected = onChannelSelected,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        LazyColumn(
            state = listState,
            reverseLayout = true,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
        ) {
            items(state.messages, key = { it.id }) { message ->
                MessageBubble(message = message)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = state.inputText,
                onValueChange = onInputChange,
                placeholder = { Text("Send to broadcast ch${state.selectedChannel}...") },
                modifier = Modifier.weight(1f),
                enabled = isConnected && !state.isSending,
                singleLine = true,
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = onSendClick,
                enabled = isConnected && !state.isSending && state.inputText.isNotBlank(),
            ) {
                if (state.isSending) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelSelector(
    channels: List<ChannelConfigUi>,
    selectedChannel: Int,
    onChannelSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = channels.find { it.index == selectedChannel } ?: channels.first()

    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "Ch${selected.index}: ${selected.channelName.ifBlank { "Channel ${selected.index}" }}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            channels.forEach { ch ->
                DropdownMenuItem(
                    text = {
                        Text("Ch${ch.index}: ${ch.channelName.ifBlank { "Channel ${ch.index}" }}")
                    },
                    onClick = {
                        onChannelSelected(ch.index)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: MeshMessageUi,
    modifier: Modifier = Modifier,
) {
    val isOutgoing = message.direction == MessageDirection.Outgoing
    val bubbleColor = if (isOutgoing) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isOutgoing) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(bubbleColor)
                .padding(10.dp),
        ) {
            if (!isOutgoing) {
                Text(
                    text = message.fromNodeName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(
                modifier = Modifier.align(Alignment.End),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = message.formattedTime,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (isOutgoing) {
                    Text(
                        text = statusIcon(message.status),
                        style = MaterialTheme.typography.labelSmall,
                        color = when (message.status) {
                            MessageStatus.Acked -> MaterialTheme.colorScheme.primary
                            MessageStatus.Failed -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

private fun statusIcon(status: MessageStatus): String = when (status) {
    MessageStatus.Pending -> "○"
    MessageStatus.Sent -> "✓"
    MessageStatus.Acked -> "✓✓"
    MessageStatus.Failed -> "✗"
}
