package ru.tcynik.meshtactics.presentation.feature.groups

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.koin.compose.viewmodel.koinViewModel

// Beta 1.0 feature
@Composable
fun GroupManagementScreen(
    onNavigateBack: () -> Unit,
    viewModel: GroupsViewModel = koinViewModel(),
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Group Management — Beta 1.0 TODO")
    }
}
