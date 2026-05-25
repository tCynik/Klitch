package ru.tcynik.meshtactics.presentation.feature.marks

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import ru.tcynik.meshtactics.presentation.feature.marks.models.GeoMarksDeleteConfirmUi

@Composable
fun GeoMarksDeleteConfirmDialog(
    confirm: GeoMarksDeleteConfirmUi,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(confirm.message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Удалить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        },
    )
}
