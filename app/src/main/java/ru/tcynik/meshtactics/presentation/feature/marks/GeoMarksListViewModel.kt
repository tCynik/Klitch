package ru.tcynik.meshtactics.presentation.feature.marks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.tcynik.meshtactics.domain.logger.Logger
import ru.tcynik.meshtactics.domain.marker.model.GeoMarkColor
import ru.tcynik.meshtactics.domain.marker.model.GeoMarkModel
import ru.tcynik.meshtactics.domain.marker.usecase.ObserveGeoMarksUseCase
import ru.tcynik.meshtactics.domain.marker.usecase.ToggleGeoMarkVisibilityUseCase
import ru.tcynik.meshtactics.domain.usecase.base.NoParams
import ru.tcynik.meshtactics.presentation.feature.marks.models.GeoMarkListItemUiModel
import ru.tcynik.meshtactics.presentation.feature.marks.models.GeoMarksListUiState

class GeoMarksListViewModel(
    private val observeGeoMarks: ObserveGeoMarksUseCase,
    private val toggleVisibility: ToggleGeoMarkVisibilityUseCase,
    private val logger: Logger,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GeoMarksListUiState())
    val uiState: StateFlow<GeoMarksListUiState> = _uiState.asStateFlow()

    private var cachedMarks: List<GeoMarkModel> = emptyList()
    private var nowSeconds: Long = System.currentTimeMillis() / 1000

    init {
        viewModelScope.launch {
            observeGeoMarks(NoParams).collect { marks ->
                cachedMarks = marks
                rebuildItems()
            }
        }
        viewModelScope.launch {
            while (true) {
                delay(60_000L)
                nowSeconds = System.currentTimeMillis() / 1000
                rebuildItems()
            }
        }
    }

    fun onVisibilityToggle(id: String, visible: Boolean) {
        viewModelScope.launch {
            toggleVisibility(id, visible)
            logger.d("Marks", "visibility toggled: id=$id visible=$visible")
        }
    }

    private fun rebuildItems() {
        val now = nowSeconds
        val items = cachedMarks
            .sortedByDescending { it.createdAt }
            .map { mark ->
                GeoMarkListItemUiModel(
                    id = mark.id,
                    colorArgb = GeoMarkColor.colorAt(mark.color),
                    shape = mark.shape,
                    type = mark.type,
                    name = mark.name.ifBlank { "—" },
                    ttlLabel = GeoMarkTtlFormatter.format(mark.expiresAt, now),
                    authorLabel = if (mark.isSelf) "Я" else mark.authorNodeId.take(6),
                    isVisible = mark.isVisible,
                )
            }
        _uiState.update { it.copy(items = items.toImmutableList()) }
    }
}
