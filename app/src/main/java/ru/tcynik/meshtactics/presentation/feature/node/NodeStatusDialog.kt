package ru.tcynik.meshtactics.presentation.feature.node

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun NodeStatusDialog(
    uiState: NodeStatusUiState,
    onDismiss: () -> Unit,
    onNavigateToSettings: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Node Status") },
        text = { Text("TODO") },
        confirmButton = {
            TextButton(onClick = onNavigateToSettings) { Text("Settings") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}
