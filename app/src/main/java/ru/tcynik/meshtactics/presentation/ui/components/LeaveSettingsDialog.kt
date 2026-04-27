package ru.tcynik.meshtactics.presentation.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import ru.tcynik.meshtactics.R

@Composable
fun LeaveSettingsDialog(
    onConfirm: () -> Unit,
    onDiscard: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.leave_settings_dialog_title)) },
        text = { Text(stringResource(R.string.leave_settings_dialog_message)) },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(stringResource(R.string.leave_settings_dialog_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDiscard) {
                Text(stringResource(R.string.leave_settings_dialog_discard))
            }
        },
    )
}
