package ru.tcynik.klitch.presentation.feature.marks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import ru.tcynik.klitch.R
import ru.tcynik.klitch.presentation.feature.marks.models.GeoMarkDeliveryFilterStatus
import ru.tcynik.klitch.presentation.feature.marks.models.GeoMarkDeliveryState
import ru.tcynik.klitch.presentation.feature.marks.models.GeoMarksListUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeoMarksListScreen(
    uiState: GeoMarksListUiState,
    onVisibilityToggle: (id: String, visible: Boolean) -> Unit,
    onDeliveryFilterToggle: (GeoMarkDeliveryState) -> Unit,
    onToggleAllFilteredVisibility: () -> Unit,
    onDeleteClick: () -> Unit,
    onConfirmDelete: () -> Unit,
    onDismissDeleteDialog: () -> Unit,
    onItemDeleteClick: (String) -> Unit,
    onItemExtendClick: (String) -> Unit,
    onItemSendClick: (String) -> Unit,
    onSendContourSelected: (String) -> Unit,
    onDismissSendContourPicker: () -> Unit,
    onTrackVisibilityToggle: (id: String, visible: Boolean) -> Unit,
    onTrackDeleteClick: (String) -> Unit,
    onTracksFilterToggle: () -> Unit,
    onBack: () -> Unit,
) {
    uiState.sendContourPicker?.let { picker ->
        GeoMarksSendContourDialog(
            picker = picker,
            onContourSelected = onSendContourSelected,
            onDismiss = onDismissSendContourPicker,
        )
    }

    uiState.deleteConfirm?.let { confirm ->
        GeoMarksDeleteConfirmDialog(
            confirm = confirm,
            onConfirm = onConfirmDelete,
            onDismiss = onDismissDeleteDialog,
        )
    }

    Scaffold(
        topBar = {
            val containerColor = TopAppBarDefaults.topAppBarColors().containerColor
            Surface(color = containerColor) {
                Column {
                    TopAppBar(
                        title = { Text("Метки") },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Назад",
                                )
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = onDeleteClick,
                                enabled = uiState.deleteEnabled,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = "Удалить",
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = containerColor),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (uiState.items.isNotEmpty()) {
                            IconButton(
                                onClick = onToggleAllFilteredVisibility,
                                enabled = uiState.bulkVisibilityEnabled,
                            ) {
                                Icon(
                                    imageVector = if (uiState.allFilteredVisible) {
                                        Icons.Outlined.CheckBox
                                    } else {
                                        Icons.Outlined.SelectAll
                                    },
                                    contentDescription = if (uiState.allFilteredVisible) {
                                        "Скрыть все на карте"
                                    } else {
                                        "Показать все на карте"
                                    },
                                )
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        uiState.deliveryFilters.forEach { filter ->
                            GeoMarkDeliveryFilterButton(
                                filter = filter,
                                onClick = { onDeliveryFilterToggle(filter.deliveryState) },
                            )
                        }
                        Spacer(Modifier.size(8.dp))
                        TracksFilterButton(
                            status = uiState.tracksFilterStatus,
                            onClick = onTracksFilterToggle,
                        )
                    }
                    HorizontalDivider()
                }
            }
        },
    ) { innerPadding ->
        CombinedListContent(
            uiState = uiState,
            onVisibilityToggle = onVisibilityToggle,
            onItemDeleteClick = onItemDeleteClick,
            onItemExtendClick = onItemExtendClick,
            onItemSendClick = onItemSendClick,
            onTrackVisibilityToggle = onTrackVisibilityToggle,
            onTrackDeleteClick = onTrackDeleteClick,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

@Composable
private fun TracksFilterButton(
    status: GeoMarkDeliveryFilterStatus,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabled = status != GeoMarkDeliveryFilterStatus.INACTIVE
    val tint = when (status) {
        GeoMarkDeliveryFilterStatus.INACTIVE ->
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        GeoMarkDeliveryFilterStatus.SELECTED ->
            MaterialTheme.colorScheme.primary
        GeoMarkDeliveryFilterStatus.UNSELECTED ->
            MaterialTheme.colorScheme.onSurfaceVariant
    }
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(40.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (status == GeoMarkDeliveryFilterStatus.SELECTED) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = CircleShape,
                        ),
                )
            }
            Icon(
                painter = painterResource(R.drawable.ic_track_record),
                contentDescription = "Фильтр треков",
                modifier = Modifier.size(22.dp),
                tint = tint,
            )
        }
    }
}

@Composable
private fun CombinedListContent(
    uiState: GeoMarksListUiState,
    onVisibilityToggle: (id: String, visible: Boolean) -> Unit,
    onItemDeleteClick: (String) -> Unit,
    onItemExtendClick: (String) -> Unit,
    onItemSendClick: (String) -> Unit,
    onTrackVisibilityToggle: (id: String, visible: Boolean) -> Unit,
    onTrackDeleteClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasAnything = uiState.hasMarks || uiState.tracksFilterStatus != GeoMarkDeliveryFilterStatus.INACTIVE
    if (!hasAnything) {
        EmptyMarksMessage(text = "Ничего нет", modifier = modifier)
        return
    }

    LazyColumn(modifier = modifier.fillMaxWidth()) {
        if (uiState.hasMarks) {
            if (uiState.items.isEmpty()) {
                item(key = "marks_empty") {
                    Text(
                        text = "Нет меток по выбранным фильтрам",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                    HorizontalDivider()
                }
            } else {
                items(items = uiState.items, key = { it.id }) { item ->
                    GeoMarkListItem(
                        item = item,
                        onVisibilityToggle = onVisibilityToggle,
                        onMenuDelete = { onItemDeleteClick(item.id) },
                        onMenuExtend = { onItemExtendClick(item.id) },
                        onMenuSend = { onItemSendClick(item.id) },
                    )
                    HorizontalDivider()
                }
            }
        }

        if (uiState.recordedTracks.isNotEmpty()) {
            item(key = "tracks_header") {
                Text(
                    text = "Записанные",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                )
                HorizontalDivider()
            }
            items(items = uiState.recordedTracks, key = { it.id }) { item ->
                RecordedTrackListItem(
                    item = item,
                    onVisibilityToggle = onTrackVisibilityToggle,
                    onMenuDelete = { onTrackDeleteClick(item.id) },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun EmptyMarksMessage(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
