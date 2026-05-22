package ru.tcynik.meshtactics.presentation.feature.marks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
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
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        uiState.deliveryFilters.forEach { filter ->
                            GeoMarkDeliveryFilterButton(
                                filter = filter,
                                onClick = { onDeliveryFilterToggle(filter.deliveryState) },
                            )
                        }
                    }
                },
            )
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
