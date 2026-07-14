package ru.tcynik.klitch.presentation.feature.marks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.tcynik.klitch.domain.channel.model.ContourId
import ru.tcynik.klitch.domain.channel.usecase.ObserveContoursUseCase
import ru.tcynik.klitch.domain.logger.Logger
import ru.tcynik.klitch.domain.marker.model.GeoMarkColor
import ru.tcynik.klitch.domain.marker.model.GeoMarkModel
import ru.tcynik.klitch.domain.marker.usecase.DeleteGeoMarksUseCase
import ru.tcynik.klitch.domain.marker.usecase.ExtendGeoMarkUseCase
import ru.tcynik.klitch.domain.marker.usecase.ObserveGeoMarksUseCase
import ru.tcynik.klitch.domain.marker.usecase.SendGeoMarkParams
import ru.tcynik.klitch.domain.marker.usecase.SendGeoMarkUseCase
import ru.tcynik.klitch.domain.marker.usecase.ToggleGeoMarkVisibilityUseCase
import ru.tcynik.klitch.domain.mesh.usecase.ObserveMeshNodesUseCase
import ru.tcynik.klitch.domain.track.model.RecordedTrack
import ru.tcynik.klitch.domain.track.usecase.DeleteRecordedTracksUseCase
import ru.tcynik.klitch.domain.track.usecase.ExportTrackUseCase
import ru.tcynik.klitch.domain.track.usecase.ImportTrackUseCase
import ru.tcynik.klitch.domain.track.usecase.ObserveRecordedTracksUseCase
import ru.tcynik.klitch.domain.track.usecase.ToggleRecordedTrackVisibilityUseCase
import ru.tcynik.klitch.domain.usecase.base.NoParams
import ru.tcynik.klitch.presentation.feature.marks.models.GeoMarkContourOptionUi
import ru.tcynik.klitch.presentation.feature.marks.models.GeoMarkDeliveryFilterButtonUi
import ru.tcynik.klitch.presentation.feature.marks.models.GeoMarkDeliveryFilterStatus
import ru.tcynik.klitch.presentation.feature.marks.models.GeoMarkDeliveryState
import ru.tcynik.klitch.presentation.feature.marks.models.GeoMarkListItemUiModel
import ru.tcynik.klitch.presentation.feature.marks.models.GeoMarksDeleteConfirmUi
import ru.tcynik.klitch.presentation.feature.marks.models.GeoMarksListUiState
import ru.tcynik.klitch.presentation.feature.marks.models.RecordedTrackListItemUiModel
import ru.tcynik.klitch.R
import ru.tcynik.klitch.presentation.feature.main.GEO_MARK_LOCAL_STORAGE_ID
import ru.tcynik.klitch.presentation.feature.marks.models.GeoMarksSendContourPickerUi
import ru.tcynik.klitch.presentation.feature.marks.models.resolveGeoMarkDeliveryState
import ru.tcynik.klitch.presentation.ui.UiText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GeoMarksListViewModel(
    private val observeGeoMarks: ObserveGeoMarksUseCase,
    private val observeContours: ObserveContoursUseCase,
    private val observeMeshNodes: ObserveMeshNodesUseCase,
    private val toggleVisibility: ToggleGeoMarkVisibilityUseCase,
    private val deleteGeoMarks: DeleteGeoMarksUseCase,
    private val extendGeoMark: ExtendGeoMarkUseCase,
    private val sendGeoMark: SendGeoMarkUseCase,
    private val observeRecordedTracks: ObserveRecordedTracksUseCase,
    private val toggleTrackVisibility: ToggleRecordedTrackVisibilityUseCase,
    private val deleteRecordedTracks: DeleteRecordedTracksUseCase,
    private val exportTrack: ExportTrackUseCase,
    private val importTrack: ImportTrackUseCase,
    private val logger: Logger,
    /** Periodic TTL label refresh; disable in unit tests to avoid non-terminating delay loops. */
    private val refreshTtlLabels: Boolean = true,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GeoMarksListUiState())
    val uiState: StateFlow<GeoMarksListUiState> = _uiState.asStateFlow()

    private var cachedMarks: List<GeoMarkModel> = emptyList()
    private var cachedNodeNames: Map<String, String> = emptyMap()
    private var sendContourOptions: List<GeoMarkContourOptionUi> = emptyList()
    private var nowSeconds: Long = System.currentTimeMillis() / 1000
    private val visibleDeliveryFilters = mutableSetOf<GeoMarkDeliveryState>()
    private var knownPresentTypes = emptySet<GeoMarkDeliveryState>()
    private var allCachedTracks: List<RecordedTrackListItemUiModel> = emptyList()
    private var tracksFilterEnabled = true

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
                val storage = GeoMarkContourOptionUi(GEO_MARK_LOCAL_STORAGE_ID, UiText.Static(R.string.geo_marks_storage_name))
                sendContourOptions = (active.map { GeoMarkContourOptionUi(it.id.value, UiText.Raw(it.name)) } + storage)
                    .toImmutableList()
            }
        }
        viewModelScope.launch {
            observeMeshNodes(NoParams).collect { nodes ->
                cachedNodeNames = nodes.associate { it.nodeId to it.longName }
                rebuildItems()
            }
        }
        if (refreshTtlLabels) {
            viewModelScope.launch {
                while (true) {
                    delay(60_000L)
                    nowSeconds = System.currentTimeMillis() / 1000
                    rebuildItems()
                }
            }
        }
        viewModelScope.launch {
            observeRecordedTracks(NoParams).collect { tracks ->
                allCachedTracks = tracks.map { it.toUiModel() }
                rebuildTracksState()
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
        val localOnly = contourId == GEO_MARK_LOCAL_STORAGE_ID
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
        val message: UiText = when (items.size) {
            1 -> {
                val item = items.single()
                if (item.isSelf) {
                    UiText.Dynamic(R.string.geo_marks_delete_single_self, item.name)
                } else {
                    val mark = cachedMarks.find { it.id == item.id }
                    val authorName = mark?.let { cachedNodeNames[it.authorNodeId] ?: it.authorNodeId.take(6).ifBlank { "—" } } ?: "—"
                    UiText.Dynamic(R.string.geo_marks_delete_single, item.name, authorName)
                }
            }
            else -> UiText.Dynamic(R.string.geo_marks_delete_multi, items.size)
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
                    createdAtLabel = GeoMarkCreatedAtFormatter.format(mark.createdAt, now),
                    ttlLabel = GeoMarkTtlFormatter.format(mark.expiresAt, now),
                    authorLabel = GeoMarkTitleFormatter.authorLabel(mark, cachedNodeNames),
                    isSelf = mark.isSelf,
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

    fun onTracksFilterToggle() {
        tracksFilterEnabled = !tracksFilterEnabled
        rebuildTracksState()
        logger.d("Tracks", "tracks filter toggled: enabled=$tracksFilterEnabled")
    }

    private fun rebuildTracksState() {
        val hasTracks = allCachedTracks.isNotEmpty()
        val status = when {
            !hasTracks -> GeoMarkDeliveryFilterStatus.INACTIVE
            tracksFilterEnabled -> GeoMarkDeliveryFilterStatus.SELECTED
            else -> GeoMarkDeliveryFilterStatus.UNSELECTED
        }
        val visibleTracks = if (tracksFilterEnabled) allCachedTracks else emptyList()
        _uiState.update {
            it.copy(
                recordedTracks = visibleTracks.toImmutableList(),
                tracksFilterStatus = status,
            )
        }
    }

    fun onTrackVisibilityToggle(id: String, visible: Boolean) {
        viewModelScope.launch {
            toggleTrackVisibility(id, visible)
            logger.d("Tracks", "visibility toggled: id=$id visible=$visible")
        }
    }

    fun onTrackDeleteClick(id: String) {
        viewModelScope.launch {
            deleteRecordedTracks(listOf(id))
            logger.d("Tracks", "deleted track: id=$id")
        }
    }

    fun onExportTrackResult(trackId: String, destinationUri: String) {
        viewModelScope.launch {
            exportTrack(trackId, destinationUri)
                .onSuccess { logger.d("Tracks", "exported track: id=$trackId") }
                .onFailure { e -> logger.e("Tracks", "export failed: id=$trackId", e) }
        }
    }

    fun onImportTrackResult(sourceUri: String) {
        viewModelScope.launch {
            importTrack(sourceUri)
                .onSuccess { track -> logger.d("Tracks", "imported track: id=${track.id}") }
                .onFailure { e -> logger.e("Tracks", "import failed: uri=$sourceUri", e) }
        }
    }

    private val dateFormat = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())

    private fun RecordedTrack.toUiModel(): RecordedTrackListItemUiModel {
        val startedAtLabel = dateFormat.format(Date(startedAt * 1000L))
        val durationLabel: UiText = when {
            finishedAt == null -> UiText.Static(R.string.track_list_duration_recording)
            !hasTimestamps -> UiText.Raw("—")
            else -> {
                val secs = finishedAt - startedAt
                val h = secs / 3600
                val m = (secs % 3600) / 60
                if (h > 0) UiText.Dynamic(R.string.track_list_duration_hours_minutes, h.toInt(), m.toInt())
                else UiText.Dynamic(R.string.track_list_duration_minutes, m.toInt())
            }
        }
        val distanceLabel: UiText = finishedAt?.let {
            if (totalDistanceMeters >= 1000.0) UiText.Dynamic(R.string.track_list_distance_km, totalDistanceMeters / 1000.0)
            else UiText.Dynamic(R.string.track_list_distance_m, totalDistanceMeters)
        } ?: UiText.Raw("—")
        return RecordedTrackListItemUiModel(
            id = id,
            name = name,
            colorArgb = GeoMarkColor.colorAt(color),
            startedAtLabel = startedAtLabel,
            durationLabel = durationLabel,
            distanceLabel = distanceLabel,
            isVisible = isVisible,
            isFinished = finishedAt != null,
        )
    }

}
