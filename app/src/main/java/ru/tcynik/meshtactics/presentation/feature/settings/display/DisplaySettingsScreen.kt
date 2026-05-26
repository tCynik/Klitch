package ru.tcynik.meshtactics.presentation.feature.settings.display

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import ru.tcynik.meshtactics.R
import ru.tcynik.meshtactics.domain.settings.model.ScreenOrientationMode
import ru.tcynik.meshtactics.presentation.feature.main.osd.models.MarkerSizeConfig
import ru.tcynik.meshtactics.presentation.feature.settings.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisplaySettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val savedMessage = stringResource(R.string.settings_saved)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_display_title)) },
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        ScreenTabContent(
            modifier = Modifier.padding(padding),
            markerSizeLevel = state.markerSizeLevelPending,
            onMarkerLevelChange = viewModel::onMarkerSizeLevelChange,
            geoMarkSizeLevel = state.geoMarkSizeLevelPending,
            onGeoMarkLevelChange = viewModel::onGeoMarkSizeLevelChange,
            showGeoMarkNames = state.showGeoMarkNamesPending,
            onShowGeoMarkNamesChange = viewModel::onShowGeoMarkNamesChange,
            // TODO: restore state.orientationLockedPending / state.orientationModePending when landscape is implemented
            orientationLocked = true,
            onOrientationLockedChange = viewModel::onOrientationLockedChange,
            orientationMode = ScreenOrientationMode.PORTRAIT,
            onOrientationModeChange = viewModel::onOrientationModeChange,
            onSave = {
                viewModel.onSave()
                scope.launch { snackbarHostState.showSnackbar(savedMessage) }
            },
        )
    }
}

@Composable
private fun ScreenTabContent(
    markerSizeLevel: Int,
    onMarkerLevelChange: (Int) -> Unit,
    geoMarkSizeLevel: Int,
    onGeoMarkLevelChange: (Int) -> Unit,
    showGeoMarkNames: Boolean,
    onShowGeoMarkNamesChange: (Boolean) -> Unit,
    orientationLocked: Boolean,
    onOrientationLockedChange: (Boolean) -> Unit,
    orientationMode: ScreenOrientationMode,
    onOrientationModeChange: (ScreenOrientationMode) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val markerSizeDp = MarkerSizeConfig.fromLevel(markerSizeLevel).value.toInt()
    val geoMarkSizeDp = 36 + (geoMarkSizeLevel - 1) * 6

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.settings_marker_size_label, markerSizeDp, markerSizeLevel))

        Slider(
            value = markerSizeLevel.toFloat(),
            onValueChange = { onMarkerLevelChange(it.toInt()) },
            valueRange = 1f..10f,
            steps = 8,
            modifier = Modifier.fillMaxWidth(),
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Text(stringResource(R.string.settings_geo_mark_size_label, geoMarkSizeDp, geoMarkSizeLevel))

        Slider(
            value = geoMarkSizeLevel.toFloat(),
            onValueChange = { onGeoMarkLevelChange(it.toInt()) },
            valueRange = 1f..10f,
            steps = 8,
            modifier = Modifier.fillMaxWidth(),
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.settings_geo_mark_names_label))
            Switch(
                checked = showGeoMarkNames,
                onCheckedChange = onShowGeoMarkNamesChange,
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // TODO: enable when landscape orientation is implemented
            Checkbox(
                checked = orientationLocked,
                onCheckedChange = onOrientationLockedChange,
                enabled = false,
            )
            Text(
                text = stringResource(R.string.settings_orientation_lock_label),
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        AnimatedVisibility(visible = orientationLocked) {
            OrientationModeDropdown(
                selectedMode = orientationMode,
                onModeSelected = onOrientationModeChange,
                enabled = false, // TODO: enable when landscape orientation is implemented
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
        }

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OrientationModeDropdown(
    selectedMode: ScreenOrientationMode,
    onModeSelected: (ScreenOrientationMode) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val modes = listOf(
        ScreenOrientationMode.PORTRAIT to R.string.settings_orientation_portrait,
        ScreenOrientationMode.LANDSCAPE to R.string.settings_orientation_landscape,
    )
    var expanded by remember { mutableStateOf(false) }
    val labelRes = modes.firstOrNull { it.first == selectedMode }?.second
        ?: R.string.settings_orientation_portrait

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = stringResource(labelRes),
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            modes.forEach { (mode, labelRes) ->
                DropdownMenuItem(
                    text = { Text(stringResource(labelRes)) },
                    onClick = {
                        onModeSelected(mode)
                        expanded = false
                    },
                )
            }
        }
    }
}
