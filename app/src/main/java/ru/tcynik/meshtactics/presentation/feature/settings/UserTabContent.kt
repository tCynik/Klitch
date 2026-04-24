package ru.tcynik.meshtactics.presentation.feature.settings

import android.util.Base64
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import ru.tcynik.meshtactics.R
import ru.tcynik.meshtactics.domain.channel.model.ChannelSyncStatus
import ru.tcynik.meshtactics.presentation.feature.settings.models.ChannelItem
import ru.tcynik.meshtactics.presentation.feature.settings.models.NodeWriteEvent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserTabContent(
    viewModel: UserSettingsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val event = state.nodeWriteEvent
    val snackbarSentTemplate = stringResource(R.string.channel_snackbar_sent)
    val snackbarNotConnected = stringResource(R.string.channel_snackbar_not_connected)
    val snackbarNoFreeSlot = stringResource(R.string.channel_snackbar_no_free_slot)
    LaunchedEffect(event) {
        if (event != null) {
            val message = when (event) {
                is NodeWriteEvent.Sent -> snackbarSentTemplate.format(event.channelName)
                NodeWriteEvent.NotConnected -> snackbarNotConnected
                NodeWriteEvent.NoFreeSlot -> snackbarNoFreeSlot
            }
            snackbarHostState.showSnackbar(message)
            viewModel.onNodeWriteEventConsumed()
        }
    }

    if (state.deleteConfirmId != null) {
        AlertDialog(
            onDismissRequest = viewModel::onDismissDelete,
            title = { Text(stringResource(R.string.user_channel_delete_title)) },
            text = { Text(stringResource(R.string.user_channel_delete_message)) },
            confirmButton = {
                TextButton(onClick = viewModel::onConfirmDelete) {
                    Text(stringResource(R.string.user_channel_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onDismissDelete) {
                    Text(stringResource(R.string.user_channel_delete_cancel))
                }
            },
        )
    }

    state.editorSheet?.let { editor ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = viewModel::onEditorDismiss,
            sheetState = sheetState,
        ) {
            ChannelEditorSheet(
                state = editor,
                onNameChange = viewModel::onEditorNameChange,
                onPskChange = viewModel::onEditorPskChange,
                onGeneratePsk = viewModel::onEditorGeneratePsk,
                onSave = viewModel::onEditorSave,
                onDismiss = viewModel::onEditorDismiss,
            )
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.user_section_profile),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
            item {
                OutlinedTextField(
                    value = state.displayName,
                    onValueChange = viewModel::onDisplayNameChange,
                    label = { Text(stringResource(R.string.user_display_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Button(
                    onClick = viewModel::onSaveUser,
                    enabled = state.hasUnsavedUserChanges,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.user_save_button))
                }
            }
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.user_section_channels),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
            items(state.channels, key = { it.id.value }) { channel ->
                ChannelCard(
                    item = channel,
                    onEdit = { viewModel.onEditChannelClick(channel.id) },
                    onDelete = { viewModel.onDeleteChannelRequest(channel.id) },
                    onPushToNode = { viewModel.onPushToNode(channel.id) },
                    onDeleteFromNode = { viewModel.onDeleteFromNode(channel.id) },
                    onToggleAutoSync = { enabled -> viewModel.onToggleAutoSync(channel.id, enabled) },
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.padding(horizontal = 16.dp),
        ) { data -> Snackbar(snackbarData = data) }

        Button(
            onClick = viewModel::onAddChannelClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(stringResource(R.string.user_add_channel_button))
        }
    }
}

@Composable
private fun ChannelCard(
    item: ChannelItem,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onPushToNode: () -> Unit,
    onDeleteFromNode: () -> Unit,
    onToggleAutoSync: (Boolean) -> Unit,
) {
    var showDropdown by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = item.isAutoSync,
                onCheckedChange = onToggleAutoSync,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(4.dp))
                SyncStatusBadge(item.syncStatus)
            }
            IconButton(onClick = { showDropdown = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.user_channel_more_actions))
            }
            DropdownMenu(
                expanded = showDropdown,
                onDismissRequest = { showDropdown = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.user_channel_push_to_node)) },
                    onClick = { showDropdown = false; onPushToNode() },
                )
                if (item.syncStatus is ChannelSyncStatus.OnNode && item.syncStatus.slot != 0) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.user_channel_delete_from_node)) },
                        onClick = { showDropdown = false; onDeleteFromNode() },
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.user_channel_edit)) },
                    onClick = { showDropdown = false; onEdit() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.user_channel_delete)) },
                    onClick = { showDropdown = false; onDelete() },
                )
            }
        }
    }
}

@Composable
private fun SyncStatusBadge(status: ChannelSyncStatus) {
    when (status) {
        is ChannelSyncStatus.OnNode -> Badge(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Text(
                text = stringResource(R.string.channel_sync_on_node, status.slot),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        ChannelSyncStatus.NotOnNode -> Badge(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ) {
            Text(
                text = stringResource(R.string.channel_sync_not_on_node),
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        ChannelSyncStatus.NoFreeSlot -> Badge(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ) {
            Text(
                text = stringResource(R.string.channel_sync_no_free_slot),
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        ChannelSyncStatus.NotConnected -> {}
    }
}

@Composable
private fun ChannelEditorSheet(
    state: ChannelEditorState,
    onNameChange: (String) -> Unit,
    onPskChange: (String) -> Unit,
    onGeneratePsk: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val pskValid = remember(state.pskBase64) {
        if (state.pskBase64.isBlank()) false
        else runCatching {
            val bytes = Base64.decode(state.pskBase64, Base64.DEFAULT)
            bytes.size == 16 || bytes.size == 32
        }.getOrDefault(false)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = if (state.id == null) stringResource(R.string.channel_editor_title_new)
                   else stringResource(R.string.channel_editor_title_edit),
            style = MaterialTheme.typography.titleMedium,
        )

        OutlinedTextField(
            value = state.name,
            onValueChange = onNameChange,
            label = { Text(stringResource(R.string.channel_editor_name_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            text = stringResource(R.string.channel_editor_meshtastic_section),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )

        OutlinedTextField(
            value = state.pskBase64,
            onValueChange = onPskChange,
            label = { Text(stringResource(R.string.channel_editor_psk_label)) },
            singleLine = true,
            isError = state.pskBase64.isNotBlank() && !pskValid,
            supportingText = if (state.pskBase64.isNotBlank() && !pskValid) {
                { Text(stringResource(R.string.channel_editor_psk_error)) }
            } else null,
            trailingIcon = {
                TextButton(onClick = onGeneratePsk) {
                    Text(stringResource(R.string.channel_editor_psk_generate))
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(R.string.channel_editor_cancel))
            }
            Button(
                onClick = onSave,
                enabled = state.name.isNotBlank() && pskValid,
            ) {
                Text(stringResource(R.string.channel_editor_save))
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}
