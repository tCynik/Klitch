package ru.tcynik.mymesh1.presentation.feature.nodes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import ru.tcynik.mymesh1.presentation.feature.nodes.components.NodeCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodesScreen(
    onNodeClick: (String) -> Unit,
    viewModel: NodesViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Mesh Nodes") }) },
    ) { padding ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize()) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
            state.error != null -> Box(Modifier.fillMaxSize()) {
                Text(
                    text = state.error.orEmpty(),
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            else -> LazyColumn(Modifier.padding(padding)) {
                items(state.nodes, key = { it.id }) { node ->
                    NodeCard(node = node, onClick = { onNodeClick(node.id) })
                }
            }
        }
    }
}
