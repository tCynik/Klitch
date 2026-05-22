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
import ru.tcynik.meshtactics.domain.channel.model.ContourId
import ru.tcynik.meshtactics.domain.channel.usecase.ObserveContoursUseCase
import ru.tcynik.meshtactics.domain.logger.Logger
import ru.tcynik.meshtactics.domain.marker.model.GeoMarkColor
import ru.tcynik.meshtactics.domain.marker.model.GeoMarkModel
import ru.tcynik.meshtactics.domain.marker.usecase.DeleteGeoMarksUseCase
import ru.tcynik.meshtactics.domain.marker.usecase.ExtendGeoMarkUseCase
import ru.tcynik.meshtactics.domain.marker.usecase.ObserveGeoMarksUseCase
import ru.tcynik.meshtactics.domain.marker.usecase.SendGeoMarkParams
import ru.tcynik.meshtactics.domain.marker.usecase.SendGeoMarkUseCase
import ru.tcynik.meshtactics.domain.marker.usecase.ToggleGeoMarkVisibilityUseCase
import ru.tcynik.meshtactics.domain.usecase.base.NoParams
import ru.tcynik.meshtactics.presentation.feature.marks.models.GeoMarkContourOptionUi
import ru.tcynik.meshtactics.presentation.feature.marks.models.GeoMarkDeliveryFilterButtonUi
import ru.tcynik.meshtactics.presentation.feature.marks.models.GeoMarkDeliveryFilterStatus
import ru.tcynik.meshtactics.presentation.feature.marks.models.GeoMarkDeliveryState
import ru.tcynik.meshtactics.presentation.feature.marks.models.GeoMarkListItemUiModel
import ru.tcynik.meshtactics.presentation.feature.marks.models.GeoMarksDeleteConfirmUi
import ru.tcynik.meshtactics.presentation.feature.marks.models.GeoMarksListUiState
import ru.tcynik.meshtactics.presentation.feature.marks.models.GeoMarksSendContourPickerUi
import ru.tcynik.meshtactics.presentation.feature.marks.models.resolveGeoMarkDeliveryState

class GeoMarksListViewModel(
    private val observeGeoMarks: ObserveGeoMarksUseCase,
    private val observeContours: ObserveContoursUseCase,
    private val toggleVisibility: ToggleGeoMarkVisibilityUseCase,
    private val deleteGeoMarks: DeleteGeoMarksUseCase,
    private val extendGeoMark: ExtendGeoMarkUseCase,
    private val sendGeoMark: SendGeoMarkUseCase,
    private val logger: Logger,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GeoMarksListUiState())
    val uiState: StateFlow<GeoMarksListUiState> = _uiState.asStateFlow()

    private var cachedMarks: List<GeoMarkModel> = emptyList()
    private var sendContourOptions: List<GeoMarkContourOptionUi> = emptyList()
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
            observeContours(NoParams).collect { contours ->
                val active = contours.filter { it.isActive }
                val storage = GeoMarkContourOptionUi(LOCAL_STORAGE_ID, "Хранилище")
                sendContourOptions = (active.map { GeoMarkContourOptionUi(it.id.value, it.name) } + storage)
                    .toImmutableList()
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

    fun onDeleteClick() {
        val selected = _uiState.value.items.filter { it.isVisible }
        if (selected.isEmpty()) return
        showDeleteConfirm(selected.map { it.id }, selected)
    }

    fun onItemDeleteClick(markId: String) {
        val item = findItem(markId) ?: return
        showDeleteConfirm(listOf(item.id), listOf(item))
    }

    fun onItemExtendClick(markId: String) {
        viewModelScope.launch {
            extendGeoMark(markId)
            logger.d("Marks", "extended mark ttl: id=$markId")
        }
    }

    fun onItemSendClick(markId: String) {
        val item = findItem(markId) ?: return
        if (sendContourOptions.isEmpty()) return
        _uiState.update {
            it.copy(
                sendContourPicker = GeoMarksSendContourPickerUi(
                    markId = markId,
                    markName = item.name,
                    contours = sendContourOptions.toImmutableList(),
                ),
            )
        }
    }

    fun onDismissSendContourPicker() {
        _uiState.update { it.copy(sendContourPicker = null) }
    }

    fun onSendContourSelected(contourId: String) {
        val picker = _uiState.value.sendContourPicker ?: return
        val mark = cachedMarks.find { it.id == picker.markId } ?: return
        val localOnly = contourId == LOCAL_STORAGE_ID
        val contour = if (localOnly) null else ContourId(contourId)
        viewModelScope.launch {
            sendGeoMark(SendGeoMarkParams(mark, contour, localOnly))
            logger.d("Marks", "resent mark: id=${mark.id} contour=$contourId localOnly=$localOnly")
            _uiState.update { it.copy(sendContourPicker = null) }
        }
    }

    fun onDismissDeleteDialog() {
        _uiState.update { it.copy(deleteConfirm = null) }
    }

    fun onConfirmDelete() {
        val confirm = _uiState.value.deleteConfirm ?: return
        val ids = confirm.markIds
        viewModelScope.launch {
            deleteGeoMarks(ids)
            logger.d("Marks", "deleted marks: count=${ids.size}")
            _uiState.update { it.copy(deleteConfirm = null) }
        }
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

    private fun showDeleteConfirm(
        markIds: List<String>,
        items: List<GeoMarkListItemUiModel>,
    ) {
        val message = when (items.size) {
            1 -> {
                val item = items.single()
                "Удалить метку ${item.name}(от ${item.authorLabel})?"
            }
            else -> "Удалить выбранные метки(${items.size})?"
        }
        _uiState.update {
            it.copy(
                deleteConfirm = GeoMarksDeleteConfirmUi(
                    message = message,
                    markIds = markIds.toImmutableList(),
                ),
            )
        }
    }

    private fun findItem(markId: String): GeoMarkListItemUiModel? =
        _uiState.value.items.find { it.id == markId }

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
                deleteEnabled = visibleItems.any { item -> item.isVisible },
                deleteConfirm = it.deleteConfirm,
                sendContourPicker = it.sendContourPicker,
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

    companion object {
        private const val LOCAL_STORAGE_ID = "__local__"
    }
}
