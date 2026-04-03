package ru.tcynik.meshtactics.presentation.feature.node

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.koin.compose.viewmodel.koinViewModel

// meshtest migration checklist:
//   ☐ BLE connect / device scan
//   ☐ Channel config (WriteChannel, WriteOwner)
//   ☐ Packet log (debug section)
@Composable
fun NodeSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: NodeSettingsViewModel = koinViewModel(),
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Node Settings — TODO")
    }
}
