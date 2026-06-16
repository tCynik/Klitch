package ru.tcynik.klitch.presentation.feature.main.osd

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.tcynik.klitch.R
import ru.tcynik.klitch.domain.marker.model.GeoMarkColor
import ru.tcynik.klitch.domain.track.model.TrackRecordingPreset
import ru.tcynik.klitch.domain.track.model.TrackRecordingState
import ru.tcynik.klitch.presentation.feature.main.osd.models.TrackRecordingSheetUiState

@Composable
fun TrackRecordingSheet(
    state: TrackRecordingSheetUiState,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = state.isVisible, onBack = state.onClose)

    AnimatedVisibility(
        visible = state.isVisible,
        modifier = modifier,
        enter = slideInVertically(animationSpec = tween(250)) { it } + fadeIn(tween(200)),
        exit  = slideOutVertically(animationSpec = tween(250)) { it } + fadeOut(tween(200)),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = {},
                ),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(bottom = 8.dp),
            ) {
                TrackSheetHeader(state = state)
                AnimatedVisibility(
                    visible = !state.isCollapsed,
                    enter = expandVertically(animationSpec = tween(200)),
                    exit  = shrinkVertically(animationSpec = tween(200)),
                ) {
                    val rs = state.recordingState
                    Column(modifier = Modifier.fillMaxWidth()) {
                        HorizontalDivider()
                        if (rs is TrackRecordingState.Recording) {
                            TrackStatsSection(
                                recordingState = rs,
                                durationSeconds = state.durationSeconds,
                                speedMps = state.speedMps,
                            )
                            HorizontalDivider()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                ColorPickerDropdown(
                                    colorIndex = state.settings.color,
                                    onColorSelected = state.onColorSelected,
                                    modifier = Modifier.width(80.dp),
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (rs.isPaused) {
                                        OutlinedButton(onClick = state.onResume) {
                                            Text(stringResource(R.string.track_action_resume))
                                        }
                                    } else {
                                        OutlinedButton(onClick = state.onPause) {
                                            Text(stringResource(R.string.track_action_pause))
                                        }
                                    }
                                    Button(
                                        onClick = state.onStop,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.error,
                                            contentColor = MaterialTheme.colorScheme.onError,
                                        ),
                                    ) { Text(stringResource(R.string.track_action_stop)) }
                                }
                            }
                        } else {
                            TrackSettingsSection(state = state)
                            HorizontalDivider()
                            TrackActionRow {
                                Button(onClick = state.onStart) {
                                    Text(stringResource(R.string.track_action_start))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackSheetHeader(state: TrackRecordingSheetUiState) {
    val rs = state.recordingState
    val isRecording = rs is TrackRecordingState.Recording

    var isEditingName by remember { mutableStateOf(false) }
    val recordingName = (rs as? TrackRecordingState.Recording)?.name.orEmpty()
    var editedName by remember(recordingName) { mutableStateOf(recordingName) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isEditingName) {
        if (isEditingName) focusRequester.requestFocus()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 4.dp, end = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_track_record),
            contentDescription = null,
            modifier = Modifier
                .padding(start = 12.dp, end = 8.dp)
                .size(24.dp),
        )
        if (rs is TrackRecordingState.Recording) {
            Text(
                text = if (rs.isPaused) stringResource(R.string.track_status_paused)
                       else stringResource(R.string.track_status_rec),
                style = MaterialTheme.typography.titleMedium,
                color = if (rs.isPaused) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.error,
                maxLines = 1,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
        if (!state.isCollapsed) {
            if (isRecording && isEditingName) {
                OutlinedTextField(
                    value = editedName,
                    onValueChange = { editedName = it },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    textStyle = MaterialTheme.typography.titleMedium,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        state.onTrackNameChanged(editedName)
                        isEditingName = false
                    }),
                )
            } else {
                Text(
                    text = buildHeaderTitle(state),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .weight(1f)
                        .then(
                            if (isRecording) Modifier.clickable {
                                editedName = recordingName
                                isEditingName = true
                            } else Modifier
                        ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            Spacer(Modifier.weight(1f))
        }
        if (state.isCollapsed && isRecording) {
            val dist = (rs as? TrackRecordingState.Recording)?.distanceMeters
            Text(
                text = formatDuration(state.durationSeconds),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.padding(end = 8.dp),
            )
            if (dist != null) {
                Text(
                    text = formatDistance(dist),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
            Text(
                text = formatSpeed(state.speedMps),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.padding(end = 4.dp),
            )
        }
        IconButton(onClick = state.onToggleCollapsed) {
            Icon(
                imageVector = if (state.isCollapsed) Icons.Default.KeyboardArrowUp
                              else Icons.Default.KeyboardArrowDown,
                contentDescription = if (state.isCollapsed) stringResource(R.string.track_cd_expand)
                                     else stringResource(R.string.track_cd_collapse),
            )
        }
        IconButton(onClick = state.onClose) {
            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.track_cd_close))
        }
    }
}

@Composable
private fun TrackStatsSection(
    recordingState: TrackRecordingState.Recording,
    durationSeconds: Long,
    speedMps: Float?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        StatItem(label = stringResource(R.string.track_stat_time),     value = formatDuration(durationSeconds))
        StatItem(label = stringResource(R.string.track_stat_distance), value = formatDistance(recordingState.distanceMeters))
        StatItem(label = stringResource(R.string.track_stat_speed),    value = formatSpeed(speedMps))
        StatItem(label = stringResource(R.string.track_stat_points),   value = recordingState.pointCount.toString())
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MaterialTheme.typography.titleMedium)
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TrackSettingsSection(state: TrackRecordingSheetUiState) {
    Column(modifier = Modifier.fillMaxWidth()) {
        PresetAndColorRow(state = state)
        TriggerRow(state = state)
        NameRow(state = state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PresetAndColorRow(state: TrackRecordingSheetUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        var expanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier.weight(1f),
        ) {
            OutlinedTextField(
                value = state.settings.preset.displayName(),
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.track_label_preset)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                TrackRecordingPreset.entries.forEach { preset ->
                    DropdownMenuItem(
                        text = { Text(preset.displayName()) },
                        onClick = { state.onPresetSelected(preset); expanded = false },
                    )
                }
            }
        }
        ColorPickerDropdown(
            colorIndex = state.settings.color,
            onColorSelected = state.onColorSelected,
            modifier = Modifier.width(80.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TriggerRow(state: TrackRecordingSheetUiState) {
    val intervalOptions = intervalOptions()
    val distanceOptions = minDistanceOptions()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        var intervalExpanded by remember { mutableStateOf(false) }
        val intervalLabel = intervalOptions
            .firstOrNull { it.first == state.settings.intervalSeconds }?.second
            ?: state.settings.intervalSeconds?.let { stringResource(R.string.track_interval_unknown_s, it) }
            ?: stringResource(R.string.track_option_none)

        ExposedDropdownMenuBox(
            expanded = intervalExpanded,
            onExpandedChange = { intervalExpanded = it },
            modifier = Modifier.weight(1f),
        ) {
            OutlinedTextField(
                value = intervalLabel,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.track_label_interval)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(intervalExpanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            )
            ExposedDropdownMenu(expanded = intervalExpanded, onDismissRequest = { intervalExpanded = false }) {
                intervalOptions.forEach { (value, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = { state.onIntervalSelected(value); intervalExpanded = false },
                    )
                }
            }
        }

        var distExpanded by remember { mutableStateOf(false) }
        val distLabel = distanceOptions
            .firstOrNull { it.first == state.settings.minDistanceMeters }?.second
            ?: stringResource(R.string.track_distance_unknown_m, state.settings.minDistanceMeters)

        ExposedDropdownMenuBox(
            expanded = distExpanded,
            onExpandedChange = { distExpanded = it },
            modifier = Modifier.weight(1f),
        ) {
            OutlinedTextField(
                value = distLabel,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.track_label_min_dist)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(distExpanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            )
            ExposedDropdownMenu(expanded = distExpanded, onDismissRequest = { distExpanded = false }) {
                distanceOptions.forEach { (value, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = { state.onMinDistanceSelected(value); distExpanded = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun NameRow(state: TrackRecordingSheetUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        OutlinedTextField(
            value = state.settings.name,
            onValueChange = state.onNameChanged,
            label = { Text(stringResource(R.string.track_label_name)) },
            modifier = Modifier.weight(1f),
            singleLine = true,
        )
        OutlinedTextField(
            value = state.settings.nameCounter?.toString().orEmpty(),
            onValueChange = { v ->
                when {
                    v.isEmpty() -> state.onNameCounterChanged(null)
                    else -> v.toIntOrNull()?.let { state.onNameCounterChanged(it) }
                }
            },
            label = { Text(stringResource(R.string.track_label_counter)) },
            modifier = Modifier.size(width = 72.dp, height = 56.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColorPickerDropdown(
    colorIndex: Int,
    onColorSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val currentColor = Color(GeoMarkColor.colorAt(colorIndex))

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = " ",
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.track_label_color)) },
            leadingIcon = {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(currentColor, RoundedCornerShape(4.dp))
                )
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            GeoMarkColor.palette.chunked(4).forEachIndexed { rowIndex, rowColors ->
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowColors.forEachIndexed { colIndex, argb ->
                        val index = rowIndex * 4 + colIndex
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(Color(argb), RoundedCornerShape(6.dp))
                                .clickable { onColorSelected(index); expanded = false },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackActionRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        content()
    }
}

@Composable
private fun buildHeaderTitle(state: TrackRecordingSheetUiState): String =
    when (val rs = state.recordingState) {
        is TrackRecordingState.Recording -> rs.name
        is TrackRecordingState.Idle -> {
            val name = state.settings.name.trim().ifEmpty { stringResource(R.string.track_recording_default_name) }
            val counter = state.settings.nameCounter?.let { " $it" }.orEmpty()
            "$name$counter"
        }
    }

private fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%02d:%02d:%02d".format(h, m, s)
    else "%02d:%02d".format(m, s)
}

@Composable
private fun formatDistance(meters: Double): String = when {
    meters >= 1000.0 -> stringResource(R.string.track_sheet_distance_km, meters / 1000.0)
    else             -> stringResource(R.string.track_sheet_distance_m, meters)
}

@Composable
private fun formatSpeed(mps: Float?): String = when {
    mps == null -> "—"
    mps >= 1000f / 3.6f -> stringResource(R.string.track_sheet_speed_fast, mps * 3.6f)
    else -> stringResource(R.string.track_sheet_speed, mps * 3.6f)
}

@Composable
private fun TrackRecordingPreset.displayName(): String = when (this) {
    TrackRecordingPreset.WALKING  -> stringResource(R.string.track_preset_walking)
    TrackRecordingPreset.BICYCLE  -> stringResource(R.string.track_preset_bicycle)
    TrackRecordingPreset.MOTO     -> stringResource(R.string.track_preset_moto)
    TrackRecordingPreset.CAR      -> stringResource(R.string.track_preset_car)
    TrackRecordingPreset.AIRPLANE -> stringResource(R.string.track_preset_airplane)
    TrackRecordingPreset.CUSTOM   -> stringResource(R.string.track_preset_custom)
}

@Composable
private fun intervalOptions(): List<Pair<Int?, String>> = listOf(
    null to stringResource(R.string.track_option_none),
    5    to stringResource(R.string.track_interval_5s),
    10   to stringResource(R.string.track_interval_10s),
    30   to stringResource(R.string.track_interval_30s),
    60   to stringResource(R.string.track_interval_1m),
    120  to stringResource(R.string.track_interval_2m),
    300  to stringResource(R.string.track_interval_5m),
)

@Composable
private fun minDistanceOptions(): List<Pair<Int, String>> = listOf(
    0   to stringResource(R.string.track_option_none),
    5   to stringResource(R.string.track_distance_5m),
    10  to stringResource(R.string.track_distance_10m),
    15  to stringResource(R.string.track_distance_15m),
    30  to stringResource(R.string.track_distance_30m),
    50  to stringResource(R.string.track_distance_50m),
    100 to stringResource(R.string.track_distance_100m),
    200 to stringResource(R.string.track_distance_200m),
)

@Composable
internal fun TrackStopConfirmDialog(
    initialName: String,
    trimToMovement: Boolean,
    onTrimToMovementChanged: (Boolean) -> Unit,
    onSave: (String) -> Unit,
    onDiscard: () -> Unit,
    onCancel: () -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.track_stop_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.track_label_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onTrimToMovementChanged(!trimToMovement) },
                ) {
                    Checkbox(
                        checked = trimToMovement,
                        onCheckedChange = onTrimToMovementChanged,
                    )
                    Text(
                        text = stringResource(R.string.track_stop_trim_label),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(name) }) { Text(stringResource(R.string.track_action_save)) }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = onDiscard,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text(stringResource(R.string.track_action_delete)) }
                TextButton(onClick = onCancel) { Text(stringResource(R.string.track_action_cancel)) }
            }
        },
    )
}
