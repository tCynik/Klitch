package ru.tcynik.klitch.presentation.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import ru.tcynik.klitch.R

@Composable
fun SyncRequiredDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sync_required_dialog_title)) },
        text = { Text(stringResource(R.string.sync_required_dialog_message)) },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(stringResource(R.string.sync_required_dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.sync_required_dialog_dismiss))
            }
        },
    )
}

@Preview
@Composable
private fun SyncRequiredDialogPreview() {
    SyncRequiredDialog(onConfirm = {}, onDismiss = {})
}
