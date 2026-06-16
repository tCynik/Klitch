package ru.tcynik.klitch.presentation.feature.node

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.tcynik.klitch.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodeSettingsScreen(
    uiState: NodeSettingsUiState,
    onNavigateBack: () -> Unit,
    onRegenerateClick: () -> Unit = {},
    onRegenerateConfirm: () -> Unit = {},
    onRegenerateDismiss: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.node_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.node_settings_cd_back))
                    }
                },
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            item {
                Text(
                    text = stringResource(R.string.node_settings_pkc_section),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            item {
                val keyPreview = uiState.publicKeyHex?.let { hex ->
                    if (hex.length >= 16) "${hex.take(8)}…${hex.takeLast(8)}" else hex
                } ?: "—"

                ListItem(
                    headlineContent = { Text(stringResource(R.string.node_settings_public_key)) },
                    trailingContent = {
                        Text(
                            text = keyPreview,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
            }

            item {
                when {
                    uiState.isMismatch -> WarningCard(
                        text = stringResource(R.string.node_settings_key_corrupt),
                        isError = true,
                    )
                    !uiState.hasKey -> WarningCard(
                        text = stringResource(R.string.node_settings_key_missing),
                        isError = true,
                    )
                    else -> ListItem(
                        headlineContent = { Text(stringResource(R.string.node_settings_pkc_active)) },
                        leadingContent = {
                            Text(
                                text = "✓",
                                color = MaterialTheme.colorScheme.primary,
                            )
                        },
                    )
                }
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

            item {
                Button(
                    onClick = onRegenerateClick,
                    enabled = uiState.isNodeConnected,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Text(stringResource(R.string.node_settings_regen_button))
                }
            }
        }

        if (uiState.showRegenerateDialog) {
            AlertDialog(
                onDismissRequest = onRegenerateDismiss,
                title = { Text(stringResource(R.string.node_settings_regen_title)) },
                text = { Text(stringResource(R.string.node_settings_regen_message)) },
                confirmButton = {
                    TextButton(onClick = onRegenerateConfirm) { Text(stringResource(R.string.node_settings_regen_confirm)) }
                },
                dismissButton = {
                    TextButton(onClick = onRegenerateDismiss) { Text(stringResource(R.string.node_settings_regen_cancel)) }
                },
            )
        }
    }
}

@Composable
private fun WarningCard(text: String, isError: Boolean) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isError)
                MaterialTheme.colorScheme.errorContainer
            else
                MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isError)
                    MaterialTheme.colorScheme.onErrorContainer
                else
                    MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}
