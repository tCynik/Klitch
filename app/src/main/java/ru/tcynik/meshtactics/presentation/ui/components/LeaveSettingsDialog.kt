package ru.tcynik.meshtactics.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = onDiscard,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = stringResource(R.string.leave_settings_dialog_discard),
                        textAlign = TextAlign.Center,
                    )
                }
                Button(onClick = onConfirm) {
                    Text(stringResource(R.string.leave_settings_dialog_save))
                }
            }
        },
    )
}
