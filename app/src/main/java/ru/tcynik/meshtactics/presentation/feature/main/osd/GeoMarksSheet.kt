package ru.tcynik.meshtactics.presentation.feature.main.osd

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import ru.tcynik.meshtactics.domain.marker.model.GeoMarkColor
import ru.tcynik.meshtactics.domain.marker.model.GeoMarkShape
import ru.tcynik.meshtactics.domain.marker.model.GeoMarkType
import ru.tcynik.meshtactics.domain.marker.model.GeoPoint
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
fun GeoMarksSheet(
    state: GeoMarksSheetUiState,
    modifier: Modifier = Modifier,
    pendingPoints: ImmutableList<GeoPoint> = state.pendingPoints,
    trackDistanceLabel: String = state.trackDraftDistanceLabel,
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
                SheetHeader(
                    state = state,
                    pendingPoints = pendingPoints,
                    trackDistanceLabel = trackDistanceLabel,
                )
                AnimatedVisibility(
                    visible = !state.isCollapsed,
                    enter = expandVertically(animationSpec = tween(200)),
                    exit  = shrinkVertically(animationSpec = tween(200)),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        HorizontalDivider()
                        TypeAndColorRow(state)
                        TypeSpecificSection(
                            state = state,
                            pendingPoints = pendingPoints,
                            trackDistanceLabel = trackDistanceLabel,
                        )
                        HorizontalDivider()
                        NameAndTtlRow(state)
                        HorizontalDivider()
                        BottomRow(state)
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetHeader(
    state: GeoMarksSheetUiState,
    pendingPoints: ImmutableList<GeoPoint>,
    trackDistanceLabel: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 4.dp, end = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Edit,
            contentDescription = null,
            modifier = Modifier
                .padding(start = 12.dp, end = 8.dp)
                .size(24.dp),
            tint = MaterialTheme.colorScheme.onSurface,
        )
        if (state.selectedType == GeoMarkType.TRACK) {
            TrackEndTypeIcon(
                endType = state.selectedTrackEndType,
                color = Color(GeoMarkColor.colorAt(state.selectedColor)),
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(24.dp),
            )
        } else {
            ShapeIcon(
                shape = state.selectedShape,
                fillColor = Color(GeoMarkColor.colorAt(state.selectedColor)),
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(24.dp),
            )
        }
        Text(
            text = buildSheetHeaderTitle(state),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (state.isCollapsed && state.selectedType == GeoMarkType.TRACK) {
            Text(
                text = trackDistanceLabel,
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
        if (state.selectedType == GeoMarkType.TRACK) {
            TrackEndTypeDropdown(state, modifier = Modifier.width(80.dp))
        } else {
            ShapeDropdown(state, modifier = Modifier.width(80.dp))
        }
        ColorDropdown(state, modifier = Modifier.width(80.dp))
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
private fun TrackEndTypeDropdown(state: GeoMarksSheetUiState, modifier: Modifier) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = " ",
            onValueChange = {},
            readOnly = true,
            label = { Text("Вид") },
            leadingIcon = {
                TrackEndTypeIcon(endType = state.selectedTrackEndType, modifier = Modifier.size(20.dp))
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            MVP_TRACK_END_TYPES.forEach { endType ->
                DropdownMenuItem(
                    text = { TrackEndTypeIcon(endType = endType, modifier = Modifier.size(24.dp)) },
                    onClick = { state.onTrackEndTypeSelected(endType); expanded = false },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShapeDropdown(state: GeoMarksSheetUiState, modifier: Modifier) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = " ",
            onValueChange = {},
            readOnly = true,
            label = { Text("Вид") },
            leadingIcon = {
                ShapeIcon(shape = state.selectedShape, modifier = Modifier.size(20.dp))
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            GeoMarkShape.entries.forEach { shape ->
                DropdownMenuItem(
                    text = { ShapeIcon(shape = shape, modifier = Modifier.size(20.dp)) },
                    onClick = { state.onShapeSelected(shape); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun ShapeIcon(
    shape: GeoMarkShape,
    modifier: Modifier = Modifier,
    fillColor: Color? = null,
) {
    val color = fillColor ?: MaterialTheme.colorScheme.onSurface
    Canvas(modifier = modifier.aspectRatio(1f)) {
        val side = size.minDimension
        val offsetX = (size.width - side) / 2f
        val offsetY = (size.height - side) / 2f
        val pad = side * 0.08f
        val centerX = offsetX + side / 2f
        val centerY = offsetY + side / 2f
        if (fillColor != null) {
            when (shape) {
                GeoMarkShape.CIRCLE -> drawCircle(
                    color = color,
                    radius = side / 2f - pad,
                    center = androidx.compose.ui.geometry.Offset(centerX, centerY),
                )
                GeoMarkShape.SQUARE -> drawRect(
                    color = color,
                    topLeft = androidx.compose.ui.geometry.Offset(offsetX + pad, offsetY + pad),
                    size = androidx.compose.ui.geometry.Size(side - pad * 2f, side - pad * 2f),
                )
                GeoMarkShape.TRIANGLE -> {
                    val path = Path().apply {
                        moveTo(centerX, offsetY + pad)
                        lineTo(offsetX + side - pad, offsetY + side - pad)
                        lineTo(offsetX + pad, offsetY + side - pad)
                        close()
                    }
                    drawPath(path, color = color)
                }
            }
        } else {
            val stroke = Stroke(width = side * 0.12f)
            val inset = stroke.width / 2f
            when (shape) {
                GeoMarkShape.CIRCLE -> drawCircle(
                    color = color,
                    radius = side / 2f - inset,
                    center = androidx.compose.ui.geometry.Offset(centerX, centerY),
                    style = stroke,
                )
                GeoMarkShape.SQUARE -> drawRect(
                    color = color,
                    topLeft = androidx.compose.ui.geometry.Offset(offsetX + inset, offsetY + inset),
                    size = androidx.compose.ui.geometry.Size(side - stroke.width, side - stroke.width),
                    style = stroke,
                )
                GeoMarkShape.TRIANGLE -> {
                    val path = Path().apply {
                        moveTo(centerX, offsetY + inset)
                        lineTo(offsetX + side - inset, offsetY + side - inset)
                        lineTo(offsetX + inset, offsetY + side - inset)
                        close()
                    }
                    drawPath(path, color = color, style = stroke)
                }
            }
        }
    }
}

@Composable
private fun TrackEndTypeIcon(
    endType: TrackEndType,
    modifier: Modifier = Modifier,
    color: Color? = null,
) {
    val strokeColor = color ?: MaterialTheme.colorScheme.onSurface
    Canvas(modifier = modifier.aspectRatio(1f)) {
        val w = size.width
        val h = size.height
        val cy = h / 2f
        val sw = h * 0.14f
        when (endType) {
            TrackEndType.NONE -> {
                drawLine(strokeColor, androidx.compose.ui.geometry.Offset(w * 0.10f, cy), androidx.compose.ui.geometry.Offset(w * 0.90f, cy), sw)
            }
            TrackEndType.ARROW -> {
                drawLine(strokeColor, androidx.compose.ui.geometry.Offset(w * 0.10f, cy), androidx.compose.ui.geometry.Offset(w * 0.88f, cy), sw)
                drawLine(strokeColor, androidx.compose.ui.geometry.Offset(w * 0.88f, cy), androidx.compose.ui.geometry.Offset(w * 0.62f, cy - h * 0.28f), sw)
                drawLine(strokeColor, androidx.compose.ui.geometry.Offset(w * 0.88f, cy), androidx.compose.ui.geometry.Offset(w * 0.62f, cy + h * 0.28f), sw)
            }
            TrackEndType.SMALL_FILLED_CIRCLE -> {
                drawLine(strokeColor, androidx.compose.ui.geometry.Offset(w * 0.10f, cy), androidx.compose.ui.geometry.Offset(w * 0.72f, cy), sw)
                drawCircle(strokeColor, radius = h * 0.15f, center = androidx.compose.ui.geometry.Offset(w * 0.84f, cy))
            }
            TrackEndType.LARGE_EMPTY_CIRCLE -> {
                drawLine(strokeColor, androidx.compose.ui.geometry.Offset(w * 0.10f, cy), androidx.compose.ui.geometry.Offset(w * 0.65f, cy), sw)
                drawCircle(strokeColor, radius = h * 0.22f, center = androidx.compose.ui.geometry.Offset(w * 0.80f, cy), style = Stroke(width = sw))
            }
        }
    }
}

private fun buildSheetHeaderTitle(state: GeoMarksSheetUiState): String {
    val name = state.markName.trim().ifEmpty { state.selectedType.headerNameFallback }
    val counterPart = state.nameCounter?.let { " $it" }.orEmpty()
    return "$name$counterPart/${formatTtlShort(state.selectedTtlSeconds)}"
}

private fun formatTtlShort(seconds: Long): String = when (seconds) {
    900L    -> "15мин."
    1800L   -> "30мин."
    3600L   -> "1 ч."
    7200L   -> "2 ч."
    18000L  -> "5 ч."
    28800L  -> "8 ч."
    43200L  -> "12 ч."
    86400L  -> "24 ч."
    259200L -> "3 сут."
    else -> when {
        seconds < 3600  -> "${seconds / 60}мин."
        seconds < 86400 -> "${seconds / 3600} ч."
        else            -> "${seconds / 86400} сут."
    }
}

private val GeoMarkType.headerNameFallback: String
    get() = when (this) {
        GeoMarkType.POINT -> "точка"
        GeoMarkType.TRACK -> "трек"
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColorDropdown(state: GeoMarksSheetUiState, modifier: Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val currentColor = Color(GeoMarkColor.colorAt(state.selectedColor))

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = " ",
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
                                .clickable { state.onColorSelected(index); expanded = false },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TypeSpecificSection(
    state: GeoMarksSheetUiState,
    pendingPoints: ImmutableList<GeoPoint>,
    trackDistanceLabel: String,
) {
    when (state.selectedType) {
        GeoMarkType.TRACK -> TrackSection(
            pendingPoints = pendingPoints,
            trackDistanceLabel = trackDistanceLabel,
        )
        else -> Spacer(Modifier.height(0.dp))
    }
}

@Composable
private fun TrackSection(
    pendingPoints: ImmutableList<GeoPoint>,
    trackDistanceLabel: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "точек: ${pendingPoints.size} / 27",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = trackDistanceLabel,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NameAndTtlRow(state: GeoMarksSheetUiState) {
    var ttlExpanded by remember { mutableStateOf(false) }
    val ttlShort = formatTtlShort(state.selectedTtlSeconds)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        MarkNameField(
            value = state.markName,
            onValueChange = state.onMarkNameChanged,
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = state.nameCounter?.toString().orEmpty(),
            onValueChange = { v ->
                when {
                    v.isEmpty() -> state.onNameCounterChanged(null)
                    else -> v.toIntOrNull()?.let { state.onNameCounterChanged(it) }
                }
            },
            label = { Text("№") },
            modifier = Modifier.width(64.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        ExposedDropdownMenuBox(
            expanded = ttlExpanded,
            onExpandedChange = { ttlExpanded = it },
            modifier = Modifier.width(100.dp),
        ) {
            OutlinedTextField(
                value = ttlShort,
                onValueChange = {},
                readOnly = true,
                label = { Text("Время") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(ttlExpanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                singleLine = true,
            )
            ExposedDropdownMenu(expanded = ttlExpanded, onDismissRequest = { ttlExpanded = false }) {
                TTL_OPTIONS.forEach { (seconds, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = { state.onTtlSelected(seconds); ttlExpanded = false },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MarkNameField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val showEllipsisOverlay = !isFocused && value.isNotEmpty()

    Box(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text("Название") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            interactionSource = interactionSource,
            textStyle = MaterialTheme.typography.bodyLarge,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = if (showEllipsisOverlay) Color.Transparent
                else MaterialTheme.colorScheme.onSurface,
            ),
        )
        if (showEllipsisOverlay) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = 16.dp,
                    ),
            )
        }
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

