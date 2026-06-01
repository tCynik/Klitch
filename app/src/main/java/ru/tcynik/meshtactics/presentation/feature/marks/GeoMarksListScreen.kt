package ru.tcynik.meshtactics.presentation.feature.marks

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.tcynik.meshtactics.presentation.feature.marks.models.GeoMarkDeliveryState
import ru.tcynik.meshtactics.presentation.feature.marks.models.GeoMarksListUiState

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
    onBack: () -> Unit,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

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
                            if (selectedTab == 0) {
                                IconButton(
                                    onClick = onDeleteClick,
                                    enabled = uiState.deleteEnabled,
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Delete,
                                        contentDescription = "Удалить",
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = containerColor),
                    )
                    PrimaryTabRow(selectedTabIndex = selectedTab) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("Метки") },
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text("Треки") },
                        )
                    }
                    if (selectedTab == 0) {
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
                        }
                    }
                    HorizontalDivider()
                }
            }
        },
    ) { innerPadding ->
        when (selectedTab) {
            0 -> MarksTabContent(
                uiState = uiState,
                onVisibilityToggle = onVisibilityToggle,
                onItemDeleteClick = onItemDeleteClick,
                onItemExtendClick = onItemExtendClick,
                onItemSendClick = onItemSendClick,
                modifier = Modifier.padding(innerPadding),
            )
            else -> TracksTabContent(
                uiState = uiState,
                onTrackVisibilityToggle = onTrackVisibilityToggle,
                onTrackDeleteClick = onTrackDeleteClick,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

@Composable
private fun MarksTabContent(
    uiState: GeoMarksListUiState,
    onVisibilityToggle: (id: String, visible: Boolean) -> Unit,
    onItemDeleteClick: (String) -> Unit,
    onItemExtendClick: (String) -> Unit,
    onItemSendClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        !uiState.hasMarks -> EmptyMarksMessage(
            text = "Меток нет",
            modifier = modifier,
        )
        uiState.items.isEmpty() -> EmptyMarksMessage(
            text = "Нет меток по выбранным фильтрам",
            modifier = modifier,
        )
        else -> LazyColumn(modifier = modifier.fillMaxWidth()) {
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
}

@Composable
private fun TracksTabContent(
    uiState: GeoMarksListUiState,
    onTrackVisibilityToggle: (id: String, visible: Boolean) -> Unit,
    onTrackDeleteClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (uiState.recordedTracks.isEmpty()) {
        EmptyMarksMessage(text = "Записей нет", modifier = modifier)
    } else {
        LazyColumn(modifier = modifier.fillMaxWidth()) {
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
