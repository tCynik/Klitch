package ru.tcynik.klitch.presentation.feature.groups

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

// Beta 1.0 feature
@Composable
fun GroupManagementScreen(
    uiState: GroupsUiState,
    onNavigateBack: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Group Management — Beta 1.0 TODO")
    }
}
