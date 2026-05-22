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
import ru.tcynik.meshtactics.presentation.feature.marks.models.GeoMarkDeliveryFilterButtonUi
import ru.tcynik.meshtactics.presentation.feature.marks.models.GeoMarkDeliveryFilterStatus
import ru.tcynik.meshtactics.presentation.feature.marks.models.GeoMarkDeliveryState
import ru.tcynik.meshtactics.presentation.feature.marks.models.GeoMarkListItemUiModel
import ru.tcynik.meshtactics.presentation.feature.marks.models.GeoMarksListUiState
import ru.tcynik.meshtactics.presentation.feature.marks.models.resolveGeoMarkDeliveryState

class GeoMarksListViewModel(
    private val observeGeoMarks: ObserveGeoMarksUseCase,
    private val toggleVisibility: ToggleGeoMarkVisibilityUseCase,
    private val logger: Logger,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GeoMarksListUiState())
    val uiState: StateFlow<GeoMarksListUiState> = _uiState.asStateFlow()

    private var cachedMarks: List<GeoMarkModel> = emptyList()
    private var nowSeconds: Long = System.currentTimeMillis() / 1000
    private val visibleDeliveryFilters = mutableSetOf<GeoMarkDeliveryState>()
    private var knownPresentTypes = emptySet<GeoMarkDeliveryState>()

    init {
        viewModelScope.launch {
            observeGeoMarks(NoParams).collect { marks ->
                cachedMarks = marks
                syncVisibleDeliveryFiltersOnMarksChange()
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

    fun onDeliveryFilterToggle(deliveryState: GeoMarkDeliveryState) {
        if (deliveryState in visibleDeliveryFilters) {
            visibleDeliveryFilters.remove(deliveryState)
        } else {
            visibleDeliveryFilters.add(deliveryState)
        }
        rebuildItems()
        logger.d("Marks", "delivery filter toggled: $deliveryState visible=${deliveryState in visibleDeliveryFilters}")
    }

    fun onToggleAllFilteredVisibility() {
        val filteredItems = _uiState.value.items
        if (filteredItems.isEmpty()) return

        val targetVisible = !filteredItems.all { it.isVisible }
        viewModelScope.launch {
            filteredItems
                .filter { it.isVisible != targetVisible }
                .forEach { item -> toggleVisibility(item.id, targetVisible) }
            logger.d("Marks", "bulk visibility: targetVisible=$targetVisible count=${filteredItems.size}")
        }
    }

    private fun rebuildItems() {
        val now = nowSeconds
        val allItems = cachedMarks
            .sortedByDescending { it.createdAt }
            .map { mark ->
                GeoMarkListItemUiModel(
                    id = mark.id,
                    colorArgb = GeoMarkColor.colorAt(mark.color),
                    shape = mark.shape,
                    trackEndType = mark.trackEndType,
                    type = mark.type,
                    name = mark.name.ifBlank { "—" },
                    ttlLabel = GeoMarkTtlFormatter.format(mark.expiresAt, now),
                    authorLabel = if (mark.isSelf) "Я" else mark.authorNodeId.take(6),
                    deliveryState = resolveGeoMarkDeliveryState(
                        isSelf = mark.isSelf,
                        logicalChannelId = mark.logicalChannelId,
                        authorNodeId = mark.authorNodeId,
                    ),
                    isVisible = mark.isVisible,
                )
            }

        val presentTypes = allItems.map { it.deliveryState }.toSet()

        val deliveryFilters = GeoMarkDeliveryState.entries.map { type ->
            GeoMarkDeliveryFilterButtonUi(
                deliveryState = type,
                status = deliveryFilterStatus(type, presentTypes),
            )
        }

        val visibleItems = allItems.filter { it.deliveryState in visibleDeliveryFilters }

        _uiState.update {
            it.copy(
                items = visibleItems.toImmutableList(),
                hasMarks = allItems.isNotEmpty(),
                deliveryFilters = deliveryFilters.toImmutableList(),
                allFilteredVisible = visibleItems.isNotEmpty() && visibleItems.all { item -> item.isVisible },
                bulkVisibilityEnabled = visibleItems.isNotEmpty(),
            )
        }
    }

    /** Только при изменении списка меток из БД — не сбрасывает выбор пользователя. */
    private fun syncVisibleDeliveryFiltersOnMarksChange() {
        val presentTypes = cachedMarks
            .map { mark ->
                resolveGeoMarkDeliveryState(
                    isSelf = mark.isSelf,
                    logicalChannelId = mark.logicalChannelId,
                    authorNodeId = mark.authorNodeId,
                )
            }
            .toSet()

        val newlyAppearedTypes = presentTypes - knownPresentTypes
        knownPresentTypes = presentTypes

        visibleDeliveryFilters.retainAll(presentTypes)
        when {
            visibleDeliveryFilters.isEmpty() && presentTypes.isNotEmpty() ->
                visibleDeliveryFilters.addAll(presentTypes)
            else ->
                visibleDeliveryFilters.addAll(newlyAppearedTypes)
        }
    }

    private fun deliveryFilterStatus(
        type: GeoMarkDeliveryState,
        presentTypes: Set<GeoMarkDeliveryState>,
    ): GeoMarkDeliveryFilterStatus = when {
        type !in presentTypes -> GeoMarkDeliveryFilterStatus.INACTIVE
        type in visibleDeliveryFilters -> GeoMarkDeliveryFilterStatus.SELECTED
        else -> GeoMarkDeliveryFilterStatus.UNSELECTED
    }
}
