package ru.tcynik.klitch.presentation.feature.marks

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import ru.tcynik.klitch.R
import ru.tcynik.klitch.presentation.feature.marks.models.GeoMarksSendContourPickerUi

@Composable
fun GeoMarksSendContourDialog(
    picker: GeoMarksSendContourPickerUi,
    onContourSelected: (contourId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.geo_mark_send_contour_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.geo_mark_send_contour_label, picker.markName))
                picker.contours.forEach { option ->
                    TextButton(onClick = { onContourSelected(option.contourId) }) {
                        Text(option.displayName.resolve())
                    }
                }
            }
        },
        confirmButton = { /* выбор контура — кнопки в text */ },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.geo_mark_send_contour_cancel))
            }
        },
    )
}
