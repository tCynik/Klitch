package ru.tcynik.klitch.presentation.feature.network.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import ru.tcynik.klitch.R

@Composable
fun DisconnectConfirmDialog(
    deviceName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val message = if (deviceName.isNotBlank()) {
        stringResource(R.string.network_disconnect_dialog_message, deviceName)
    } else {
        stringResource(R.string.network_disconnect_dialog_message_generic)
    }

    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.network_disconnect_dialog_title)) },
        text = { Text(message) },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(stringResource(R.string.network_disconnect_dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.network_disconnect_dialog_dismiss))
            }
        },
    )
}
