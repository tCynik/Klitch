package ru.tcynik.meshtactics.presentation.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import ru.tcynik.meshtactics.R
import ru.tcynik.meshtactics.presentation.feature.main.osd.models.MarkerSizeConfig
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
