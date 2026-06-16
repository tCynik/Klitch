package ru.tcynik.klitch.presentation.feature.marks

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import ru.tcynik.klitch.R
import ru.tcynik.klitch.presentation.feature.marks.models.GeoMarksDeleteConfirmUi

@Composable
fun GeoMarksDeleteConfirmDialog(
    confirm: GeoMarksDeleteConfirmUi,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(confirm.message.resolve()) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.geo_mark_delete_confirm_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.geo_mark_delete_cancel))
            }
        },
    )
}
