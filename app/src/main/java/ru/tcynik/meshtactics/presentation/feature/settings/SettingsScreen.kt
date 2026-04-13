package ru.tcynik.meshtactics.presentation.feature.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import ru.tcynik.meshtactics.R
import ru.tcynik.meshtactics.presentation.feature.main.osd.models.MarkerSizeConfig
import ru.tcynik.meshtactics.presentation.feature.settings.models.MapItem
import ru.tcynik.meshtactics.presentation.feature.settings.models.formatDate
import ru.tcynik.meshtactics.service.GpsService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val exitApp = remember {
        {
            context.stopService(GpsService.createIntent(context))
            val activity = context as? android.app.Activity
            activity?.finishAndRemoveTask()
            Unit
        }
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val savedMessage = stringResource(R.string.settings_saved)

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
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_back_description))
                    }
                },
                actions = {
                    IconButton(onClick = exitApp) {
                        Icon(Icons.Default.PowerSettingsNew, contentDescription = stringResource(R.string.settings_exit_app_description))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            TabRow(selectedTabIndex = state.selectedTab.ordinal) {
                SettingsTab.entries.forEach { tab ->
                    Tab(
                        selected = state.selectedTab == tab,
                        onClick = { viewModel.onTabSelected(tab) },
                        text = { Text(tab.label) },
                    )
                }
            }

            when (state.selectedTab) {
                SettingsTab.Map -> MapTabContent(
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
                SettingsTab.Screen -> ScreenTabContent(
                    markerSizeLevel = state.markerSizeLevelPending,
                    onLevelChange = viewModel::onMarkerSizeLevelChange,
                    onSave = {
                        viewModel.onSave()
                        scope.launch {
                            snackbarHostState.showSnackbar(savedMessage)
                        }
                    },
                )
                else -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("${state.selectedTab.label} — TODO")
                }
            }
        }
    }
}

@Composable
private fun ScreenTabContent(
    markerSizeLevel: Int,
    onLevelChange: (Int) -> Unit,
    onSave: () -> Unit,
) {
    val sizeDp = MarkerSizeConfig.fromLevel(markerSizeLevel).value.toInt()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.settings_marker_size_label, sizeDp, markerSizeLevel))

        Slider(
            value = markerSizeLevel.toFloat(),
            onValueChange = { onLevelChange(it.toInt()) },
            valueRange = 1f..10f,
            steps = 8,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onSave,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
        ) {
            Text(stringResource(R.string.settings_save_button))
        }
    }
}

@Composable
private fun MapTabContent(
    mapItems: List<MapItem>,
    onAddMap: () -> Unit,
    onHide: (String) -> Unit,
    onDelete: (String) -> Unit,
    onToggleSelection: (String, Boolean) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
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
