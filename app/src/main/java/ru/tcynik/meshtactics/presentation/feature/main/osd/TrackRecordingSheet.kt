package ru.tcynik.meshtactics.presentation.feature.main.osd

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.tcynik.meshtactics.R
import ru.tcynik.meshtactics.domain.marker.model.GeoMarkColor
import ru.tcynik.meshtactics.domain.track.model.TrackRecordingPreset
import ru.tcynik.meshtactics.domain.track.model.TrackRecordingState
import ru.tcynik.meshtactics.presentation.feature.main.osd.models.TrackRecordingSheetUiState

private val INTERVAL_OPTIONS: List<Pair<Int?, String>> = listOf(
    null to "нет",
    5    to "5 сек",
    10   to "10 сек",
    30   to "30 сек",
    60   to "1 мин",
    120  to "2 мин",
    300  to "5 мин",
)

private val MIN_DISTANCE_OPTIONS: List<Pair<Int, String>> = listOf(
    0   to "нет",
    5   to "5 м",
    10  to "10 м",
    15  to "15 м",
    30  to "30 м",
    50  to "50 м",
    100 to "100 м",
    200 to "200 м",
)

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
                            )
                            HorizontalDivider()
                            TrackActionRow {
                                if (rs.isPaused) {
                                    OutlinedButton(
                                        onClick = state.onResume,
                                        modifier = Modifier.padding(end = 8.dp),
                                    ) { Text("Продолжить") }
                                } else {
                                    OutlinedButton(
                                        onClick = state.onPause,
                                        modifier = Modifier.padding(end = 8.dp),
                                    ) { Text("Пауза") }
                                }
                                Button(
                                    onClick = state.onStop,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError,
                                    ),
                                ) { Text("Остановить") }
                            }
                        } else {
                            TrackSettingsSection(state = state)
                            HorizontalDivider()
                            TrackActionRow {
                                Button(onClick = state.onStart) { Text("Начать запись") }
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
                text = if (rs.isPaused) "⏸ ПАУЗА" else "● REC",
                style = MaterialTheme.typography.titleMedium,
                color = if (rs.isPaused) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.error,
                maxLines = 1,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
        Text(
            text = buildHeaderTitle(state),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (state.isCollapsed && isRecording) {
            Text(
                text = formatDuration(state.durationSeconds),
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
                contentDescription = if (state.isCollapsed) "Развернуть" else "Свернуть",
            )
        }
        IconButton(onClick = state.onClose) {
            Icon(Icons.Default.Close, contentDescription = "Закрыть")
        }
    }
}

@Composable
private fun TrackStatsSection(
    recordingState: TrackRecordingState.Recording,
    durationSeconds: Long,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        StatItem(label = "Время",     value = formatDuration(durationSeconds))
        StatItem(label = "Дистанция", value = formatDistance(recordingState.distanceMeters))
        StatItem(label = "Точки",     value = recordingState.pointCount.toString())
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
                value = state.settings.preset.displayName,
                onValueChange = {},
                readOnly = true,
                label = { Text("Режим") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                TrackRecordingPreset.entries.forEach { preset ->
                    DropdownMenuItem(
                        text = { Text(preset.displayName) },
                        onClick = { state.onPresetSelected(preset); expanded = false },
                    )
                }
            }
        }
        ColorPickerDropdown(
            colorIndex = state.settings.color,
            onColorSelected = state.onColorSelected,
            modifier = Modifier.size(width = 80.dp, height = 56.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TriggerRow(state: TrackRecordingSheetUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        var intervalExpanded by remember { mutableStateOf(false) }
        val intervalLabel = INTERVAL_OPTIONS
            .firstOrNull { it.first == state.settings.intervalSeconds }?.second
            ?: state.settings.intervalSeconds?.let { "${it}с" } ?: "нет"

        ExposedDropdownMenuBox(
            expanded = intervalExpanded,
            onExpandedChange = { intervalExpanded = it },
            modifier = Modifier.weight(1f),
        ) {
            OutlinedTextField(
                value = intervalLabel,
                onValueChange = {},
                readOnly = true,
                label = { Text("Интервал") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(intervalExpanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            )
            ExposedDropdownMenu(expanded = intervalExpanded, onDismissRequest = { intervalExpanded = false }) {
                INTERVAL_OPTIONS.forEach { (value, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = { state.onIntervalSelected(value); intervalExpanded = false },
                    )
                }
            }
        }

        var distExpanded by remember { mutableStateOf(false) }
        val distLabel = MIN_DISTANCE_OPTIONS
            .firstOrNull { it.first == state.settings.minDistanceMeters }?.second
            ?: "${state.settings.minDistanceMeters} м"

        ExposedDropdownMenuBox(
            expanded = distExpanded,
            onExpandedChange = { distExpanded = it },
            modifier = Modifier.weight(1f),
        ) {
            OutlinedTextField(
                value = distLabel,
                onValueChange = {},
                readOnly = true,
                label = { Text("Мин. дист.") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(distExpanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            )
            ExposedDropdownMenu(expanded = distExpanded, onDismissRequest = { distExpanded = false }) {
                MIN_DISTANCE_OPTIONS.forEach { (value, label) ->
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
            label = { Text("Название") },
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
            label = { Text("№") },
            modifier = Modifier.size(width = 72.dp, height = 56.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
    }
}

@Composable
private fun ColorPickerDropdown(
    colorIndex: Int,
    onColorSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val currentColor = Color(GeoMarkColor.colorAt(colorIndex))

    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.matchParentSize(),
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(currentColor, RoundedCornerShape(4.dp))
            )
        }
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

private fun buildHeaderTitle(state: TrackRecordingSheetUiState): String =
    when (val rs = state.recordingState) {
        is TrackRecordingState.Recording -> rs.name
        is TrackRecordingState.Idle -> {
            val name = state.settings.name.trim().ifEmpty { "Трек" }
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

private fun formatDistance(meters: Double): String = when {
    meters >= 1000.0 -> "%.1f км".format(meters / 1000.0)
    else             -> "%.0f м".format(meters)
}

private val TrackRecordingPreset.displayName: String get() = when (this) {
    TrackRecordingPreset.WALKING  -> "Пешком"
    TrackRecordingPreset.BICYCLE  -> "Велосипед"
    TrackRecordingPreset.MOTO     -> "Мото"
    TrackRecordingPreset.CAR      -> "Авто"
    TrackRecordingPreset.AIRPLANE -> "Авиа"
    TrackRecordingPreset.CUSTOM   -> "Ручной"
}
