package ru.tcynik.meshtactics.presentation.feature.settings.map

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import ru.tcynik.meshtactics.R
import ru.tcynik.meshtactics.domain.settings.model.TileCacheMode
import ru.tcynik.meshtactics.presentation.feature.settings.SettingsViewModel
import ru.tcynik.meshtactics.presentation.feature.settings.models.MapItem
import ru.tcynik.meshtactics.presentation.feature.settings.models.formatDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            val name = uri.lastPathSegment?.substringAfterLast('/')
                ?: uri.lastPathSegment
                ?: uri.toString()
            viewModel.onAddMap(uri = uri.toString(), name = name)
        }
    }

    if (state.deleteConfirmId != null) {
        AlertDialog(
            onDismissRequest = viewModel::onDismissDeleteDialog,
            title = { Text(stringResource(R.string.map_delete_confirm_title)) },
            text = { Text(stringResource(R.string.map_delete_confirm_message)) },
            confirmButton = {
                TextButton(onClick = viewModel::onConfirmDelete) {
                    Text(stringResource(R.string.map_delete_confirm_yes))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onDismissDeleteDialog) {
                    Text(stringResource(R.string.map_delete_confirm_no))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_map_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back_description),
                        )
                    }
                },
            )
        },
    ) { padding ->
        MapTabContent(
            modifier = Modifier.padding(padding),
            tileCacheMode = state.tileCacheMode,
            onTileCacheModeSelected = viewModel::onTileCacheModeSelected,
            mapItems = state.mapItems,
            onAddMap = {
                filePickerLauncher.launch(
                    arrayOf(
                        "application/vnd.google-earth.kmz",
                        "application/vnd.google-earth.kml+xml",
                    )
                )
            },
            onHide = viewModel::onHideMap,
            onDelete = viewModel::onRequestDeleteMap,
            onToggleSelection = viewModel::onToggleSelection,
        )
    }
}

@Composable
private fun MapTabContent(
    tileCacheMode: TileCacheMode,
    onTileCacheModeSelected: (TileCacheMode) -> Unit,
    mapItems: List<MapItem>,
    onAddMap: () -> Unit,
    onHide: (String) -> Unit,
    onDelete: (String) -> Unit,
    onToggleSelection: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingMaximumConfirm by remember { mutableStateOf(false) }

    if (pendingMaximumConfirm) {
        AlertDialog(
            onDismissRequest = { pendingMaximumConfirm = false },
            title = { Text(stringResource(R.string.tile_cache_maximum_warning_title)) },
            text = { Text(stringResource(R.string.tile_cache_maximum_warning_message)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingMaximumConfirm = false
                    onTileCacheModeSelected(TileCacheMode.MAXIMUM)
                }) {
                    Text(stringResource(R.string.tile_cache_maximum_warning_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingMaximumConfirm = false }) {
                    Text(stringResource(R.string.tile_cache_maximum_warning_dismiss))
                }
            },
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        TileCacheModeSelector(
            selectedMode = tileCacheMode,
            onModeSelected = { mode ->
                if (mode == TileCacheMode.MAXIMUM) {
                    pendingMaximumConfirm = true
                } else {
                    onTileCacheModeSelected(mode)
                }
            },
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            items(mapItems, key = { it.id }) { item ->
                MapItemRow(
                    item = item,
                    onHide = { onHide(item.id) },
                    onDelete = { onDelete(item.id) },
                    onToggleSelection = { onToggleSelection(item.id, !item.isSelected) },
                )
            }
        }
        Button(
            onClick = onAddMap,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(stringResource(R.string.map_add_button))
        }
    }
}

@Composable
private fun TileCacheModeSelector(
    selectedMode: TileCacheMode,
    onModeSelected: (TileCacheMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.tile_cache_section_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        TileCacheMode.entries.forEach { mode ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = selectedMode == mode,
                        onClick = { onModeSelected(mode) },
                        role = Role.RadioButton,
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = selectedMode == mode,
                    onClick = null,
                )
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(
                        text = stringResource(mode.labelRes()),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = stringResource(mode.descRes()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun TileCacheMode.labelRes(): Int = when (this) {
    TileCacheMode.DEFAULT -> R.string.tile_cache_mode_default
    TileCacheMode.MONTH -> R.string.tile_cache_mode_month
    TileCacheMode.MAXIMUM -> R.string.tile_cache_mode_maximum
}

private fun TileCacheMode.descRes(): Int = when (this) {
    TileCacheMode.DEFAULT -> R.string.tile_cache_mode_default_desc
    TileCacheMode.MONTH -> R.string.tile_cache_mode_month_desc
    TileCacheMode.MAXIMUM -> R.string.tile_cache_mode_maximum_desc
}

@Composable
private fun MapItemRow(
    item: MapItem,
    onHide: () -> Unit,
    onDelete: () -> Unit,
    onToggleSelection: () -> Unit,
) {
    var showDropdown by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = item.isSelected,
            onCheckedChange = { onToggleSelection() },
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
        ) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.formatDate(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            IconButton(onClick = { showDropdown = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.map_item_more_actions))
            }
            DropdownMenu(
                expanded = showDropdown,
                onDismissRequest = { showDropdown = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.map_item_hide)) },
                    onClick = {
                        showDropdown = false
                        onHide()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.map_item_delete)) },
                    onClick = {
                        showDropdown = false
                        onDelete()
                    },
                )
            }
        }
    }
}
