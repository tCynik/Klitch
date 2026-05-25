package ru.tcynik.meshtactics.presentation.feature.marks

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ru.tcynik.meshtactics.presentation.feature.marks.models.GeoMarksSendContourPickerUi

@Composable
fun GeoMarksSendContourDialog(
    picker: GeoMarksSendContourPickerUi,
    onContourSelected: (contourId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Отправить в контур") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("Метка: ${picker.markName}")
                picker.contours.forEach { option ->
                    TextButton(onClick = { onContourSelected(option.contourId) }) {
                        Text(option.displayName)
                    }
                }
            }
        },
        confirmButton = { /* выбор контура — кнопки в text */ },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        },
    )
}
