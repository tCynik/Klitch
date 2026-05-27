package ru.tcynik.meshtactics.presentation.feature.meshtest.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import ru.tcynik.meshtactics.R

@Composable
fun CallsignGateDialog(
    callsignInput: String,
    onCallsignChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.callsign_gate_dialog_title)) },
        text = {
            Column {
                Text(stringResource(R.string.callsign_gate_dialog_message))
                OutlinedTextField(
                    value = callsignInput,
                    onValueChange = onCallsignChange,
                    label = { Text(stringResource(R.string.user_display_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = callsignInput.isNotBlank(),
            ) {
                Text(stringResource(R.string.callsign_gate_dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.callsign_gate_dialog_dismiss))
            }
        },
    )
}

@Preview
@Composable
private fun CallsignGateDialogPreview() {
    CallsignGateDialog(
        callsignInput = "",
        onCallsignChange = {},
        onConfirm = {},
        onDismiss = {},
    )
}
