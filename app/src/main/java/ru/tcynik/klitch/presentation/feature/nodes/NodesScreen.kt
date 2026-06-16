package ru.tcynik.klitch.presentation.feature.nodes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.tcynik.klitch.R
import kotlinx.collections.immutable.toImmutableList
import org.koin.compose.viewmodel.koinViewModel
import ru.tcynik.klitch.presentation.feature.nodes.components.GeoNodesList
import ru.tcynik.klitch.presentation.feature.nodes.state.GeoNodesListState
import ru.tcynik.klitch.presentation.feature.nodes.state.models.GeoNodeUi

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodesScreen(
    onNodeClick: (String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: NodesViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nodes_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nodes_screen_cd_back))
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize()) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
            state.error != null -> Box(Modifier.fillMaxSize()) {
                Text(text = state.error.orEmpty(), modifier = Modifier.align(Alignment.Center))
            }
            else -> GeoNodesList(
                state = GeoNodesListState(
                    nodes = state.nodes.map { node ->
                        GeoNodeUi(
                            nodeId = node.nodeId,
                            shortName = node.shortName,
                            distanceFormatted = node.distanceMeters?.let { formatDistance(it) } ?: "—",
                            positionTime = node.positionTime.toLong(),
                            groundSpeed = node.groundSpeed.toFloat(),
                            groundTrack = node.groundTrack,
                        )
                    }.toImmutableList(),
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
        }
    }
}

private fun formatDistance(meters: Int): String =
    if (meters >= 1000) "${"%.1f".format(meters / 1000.0)} km" else "$meters m"
