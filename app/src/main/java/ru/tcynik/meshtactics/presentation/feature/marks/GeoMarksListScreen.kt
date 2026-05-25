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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
                    }
                    HorizontalDivider()
                }
            }
        },
    ) { innerPadding ->
        when {
            !uiState.hasMarks -> EmptyMarksMessage(
                text = "Меток нет",
                modifier = Modifier.padding(innerPadding),
            )
            uiState.items.isEmpty() -> EmptyMarksMessage(
                text = "Нет меток по выбранным фильтрам",
                modifier = Modifier.padding(innerPadding),
            )
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(innerPadding),
            ) {
                items(
                    items = uiState.items,
                    key = { it.id },
                ) { item ->
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
