package ru.tcynik.meshtactics.presentation.feature.node

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun NodeStatusDialog(
    onDismiss: () -> Unit,
    viewModel: NodeStatusViewModel = koinViewModel(),
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Node Status") },
        text = { Text("TODO") },
        confirmButton = {
            TextButton(onClick = { /* navigate to NodeSettings */ }) { Text("Settings") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}
