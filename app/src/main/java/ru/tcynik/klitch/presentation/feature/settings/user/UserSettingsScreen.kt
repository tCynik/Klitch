package ru.tcynik.klitch.presentation.feature.settings.user

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import ru.tcynik.klitch.R
import ru.tcynik.klitch.presentation.feature.settings.UserSettingsViewModel
import ru.tcynik.klitch.presentation.ui.components.LeaveSettingsDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: UserSettingsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.navigateBack.collect { onNavigateBack() }
    }

    BackHandler(enabled = state.hasUnsavedUserChanges) {
        viewModel.onNavigateBackRequested()
    }

    if (state.showLeaveDialog) {
        LeaveSettingsDialog(
            onConfirm = viewModel::onSaveAndReboot,
            onDiscard = viewModel::onDiscardAndLeave,
            onDismiss = viewModel::onDismissLeaveDialog,
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_user_title)) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.onNavigateBackRequested() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back_description),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            UserTabContent(viewModel = viewModel)
        }
    }
}
