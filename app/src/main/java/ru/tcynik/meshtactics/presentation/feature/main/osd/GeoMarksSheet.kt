package ru.tcynik.meshtactics.presentation.feature.main.osd

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ru.tcynik.meshtactics.domain.marker.model.GeoMarkColor
import ru.tcynik.meshtactics.domain.marker.model.GeoMarkType
import ru.tcynik.meshtactics.domain.marker.model.TrackEndType
import ru.tcynik.meshtactics.presentation.feature.main.osd.models.GeoMarkAddressee
import ru.tcynik.meshtactics.presentation.feature.main.osd.models.GeoMarksSheetUiState

private val MVP_TRACK_END_TYPES = listOf(TrackEndType.NONE, TrackEndType.ARROW)

private val TTL_OPTIONS = listOf(
    900L    to "15 мин",
    1800L   to "30 мин",
    3600L   to "1 час",
    7200L   to "2 часа",
    18000L  to "5 часов",
    28800L  to "8 часов",
    43200L  to "12 часов",
    86400L  to "24 часа",
    259200L to "3 суток",
)

@Composable
fun GeoMarksSheet(state: GeoMarksSheetUiState, modifier: Modifier = Modifier) {
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
                SheetHeader(state)
                AnimatedVisibility(
                    visible = !state.isCollapsed,
                    enter = expandVertically(animationSpec = tween(200)),
                    exit  = shrinkVertically(animationSpec = tween(200)),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        HorizontalDivider()
                        TypeAndColorRow(state)
                        TypeSpecificSection(state)
                        HorizontalDivider()
                        TtlRow(state)
                        HorizontalDivider()
                        NameRow(state)
                        HorizontalDivider()
                        BottomRow(state)
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetHeader(state: GeoMarksSheetUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 4.dp, end = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Создание ${state.selectedType.displayName}",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TypeAndColorRow(state: GeoMarksSheetUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TypeDropdown(state, modifier = Modifier.weight(1f))
        ColorDropdown(state, modifier = Modifier.weight(1f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TypeDropdown(state: GeoMarksSheetUiState, modifier: Modifier) {
    val types = listOf(GeoMarkType.POINT, GeoMarkType.TRACK)
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = state.selectedType.displayName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Тип") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            types.forEach { type ->
                DropdownMenuItem(
                    text = { Text(type.displayName) },
                    onClick = { state.onMarkTypeSelected(type); expanded = false },
                )
            }
            listOf("Примитив", "Полигон").forEach { label ->
                DropdownMenuItem(
                    text = { Text(label, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)) },
                    onClick = {},
                    enabled = false,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColorDropdown(state: GeoMarksSheetUiState, modifier: Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val currentColor = GeoMarkColor.colorAt(state.selectedColor)

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = "",
            onValueChange = {},
            readOnly = true,
            label = { Text("Цвет") },
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
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            GeoMarkColor.palette.chunked(4).forEachIndexed { rowIndex, rowColors ->
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowColors.forEachIndexed { colIndex, color ->
                        val index = rowIndex * 4 + colIndex
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(color, RoundedCornerShape(6.dp))
                                .clickable { state.onColorSelected(index); expanded = false },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TypeSpecificSection(state: GeoMarksSheetUiState) {
    AnimatedContent(targetState = state.selectedType, label = "type_section") { type ->
        when (type) {
            GeoMarkType.TRACK -> TrackSection(state)
            else -> Spacer(Modifier.height(0.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackSection(state: GeoMarksSheetUiState) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier.weight(1f),
        ) {
            OutlinedTextField(
                value = state.selectedTrackEndType.displayName,
                onValueChange = {},
                readOnly = true,
                label = { Text("Законцовка") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                MVP_TRACK_END_TYPES.forEach { endType ->
                    DropdownMenuItem(
                        text = { Text(endType.displayName) },
                        onClick = { state.onTrackEndTypeSelected(endType); expanded = false },
                    )
                }
            }
        }
        Text(
            text = "точек: ${state.pendingPoints.size} / 27",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TtlRow(state: GeoMarksSheetUiState) {
    var expanded by remember { mutableStateOf(false) }
    val currentLabel = TTL_OPTIONS.firstOrNull { it.first == state.selectedTtlSeconds }?.second
        ?: "${state.selectedTtlSeconds / 3600} ч"

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        OutlinedTextField(
            value = currentLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text("Актуальность") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            TTL_OPTIONS.forEach { (seconds, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = { state.onTtlSelected(seconds); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun NameRow(state: GeoMarksSheetUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = state.markName,
            onValueChange = state.onMarkNameChanged,
            label = { Text("Название") },
            modifier = Modifier.weight(1f),
            singleLine = true,
        )
        OutlinedTextField(
            value = state.nameCounter.toString(),
            onValueChange = { v -> v.toIntOrNull()?.let { state.onNameCounterChanged(it) } },
            label = { Text("№") },
            modifier = Modifier.width(72.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
    }
}


private val sendLeadingShape = RoundedCornerShape(
    topStart = 20.dp, bottomStart = 20.dp, topEnd = 4.dp, bottomEnd = 4.dp,
)
private val sendTrailingShape = RoundedCornerShape(
    topStart = 4.dp, bottomStart = 4.dp, topEnd = 20.dp, bottomEnd = 20.dp,
)

@Composable
private fun BottomRow(state: GeoMarksSheetUiState) {
    val hasPending = state.pendingPoints.isNotEmpty()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(
            onClick = state.onClearPendingPoints,
            enabled = hasPending,
        ) {
            Text("Очистить")
        }
        SendSplitButton(state = state, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SendSplitButton(state: GeoMarksSheetUiState, modifier: Modifier) {
    val hasPending = state.pendingPoints.isNotEmpty()
    val selected = state.availableContours.firstOrNull { it.contourId == state.selectedContourId }
        ?: state.availableContours.firstOrNull()
    var expanded by remember { mutableStateOf(false) }

    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Button(
            onClick = state.onSendPendingMark,
            enabled = hasPending,
            shape = sendLeadingShape,
            modifier = Modifier.weight(1f),
        ) {
            Text("Отправить в", maxLines = 1)
        }

        Box(modifier = Modifier.wrapContentSize()) {
            Button(
                onClick = { expanded = true },
                enabled = state.availableContours.isNotEmpty(),
                shape = sendTrailingShape,
            ) {
                Text(
                    text = selected?.displayName ?: "—",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                state.availableContours.forEach { addressee ->
                    DropdownMenuItem(
                        text = { Text(addressee.displayName) },
                        onClick = { state.onAddresseeSelected(addressee.contourId); expanded = false },
                    )
                }
            }
        }
    }
}

private val GeoMarkType.displayName: String get() = when (this) {
    GeoMarkType.POINT -> "Точка"
    GeoMarkType.TRACK -> "Трек"
}

private val TrackEndType.displayName: String get() = when (this) {
    TrackEndType.NONE               -> "Нет"
    TrackEndType.SMALL_FILLED_CIRCLE -> "Круг малый"
    TrackEndType.LARGE_EMPTY_CIRCLE  -> "Круг большой"
    TrackEndType.ARROW               -> "Стрелка"
}
