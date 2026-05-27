package ru.tcynik.meshtactics.presentation.feature.network.components

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
import ru.tcynik.meshtactics.R
import ru.tcynik.meshtactics.domain.user.model.DISPLAY_NAME_MAX_LENGTH

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
                    onValueChange = { if (it.length <= DISPLAY_NAME_MAX_LENGTH) onCallsignChange(it) },
                    label = { Text(stringResource(R.string.user_display_name_label)) },
                    supportingText = { Text("${callsignInput.length}/$DISPLAY_NAME_MAX_LENGTH") },
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
