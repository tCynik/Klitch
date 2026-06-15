package ru.tcynik.klitch.presentation.feature.main

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.tcynik.klitch.domain.marker.util.WaypointIdConverter
import ru.tcynik.klitch.domain.channel.model.ContourId
import ru.tcynik.klitch.domain.marker.model.GeoMarkFormPreferences
import ru.tcynik.klitch.domain.marker.model.GeoMarkModel
import ru.tcynik.klitch.domain.marker.model.GeoMarkPreset
import ru.tcynik.klitch.domain.marker.model.GeoMarkShape
import ru.tcynik.klitch.domain.marker.model.GeoMarkType
import ru.tcynik.klitch.domain.marker.model.GeoPoint
import ru.tcynik.klitch.domain.marker.model.TrackEndType
import ru.tcynik.klitch.domain.marker.repository.GeoMarkPreferencesRepository
import ru.tcynik.klitch.domain.marker.usecase.AutoExpireGeoMarksUseCase
import ru.tcynik.klitch.domain.marker.usecase.DeleteGeoMarksUseCase
import ru.tcynik.klitch.domain.marker.usecase.IngestReceivedGeoMarksUseCase
import ru.tcynik.klitch.domain.marker.usecase.ObserveGeoMarksUseCase
import ru.tcynik.klitch.domain.marker.usecase.SendGeoMarkParams
import ru.tcynik.klitch.domain.marker.usecase.SendGeoMarkUseCase
import ru.tcynik.klitch.domain.marker.usecase.ToggleGeoMarkVisibilityUseCase
import ru.tcynik.klitch.domain.marker.util.GeoTrackDistance
import ru.tcynik.klitch.domain.mesh.model.MeshConnectionStatus
import ru.tcynik.klitch.domain.mesh.usecase.ObserveConnectionStatusUseCase
import ru.tcynik.klitch.domain.channel.usecase.ObserveContoursUseCase
import ru.tcynik.klitch.domain.usecase.base.NoParams
import ru.tcynik.klitch.presentation.feature.main.osd.models.GeoMarkAddressee
import ru.tcynik.klitch.presentation.feature.main.osd.models.GeoMarkContextMenuEvent
import ru.tcynik.klitch.presentation.feature.main.osd.models.DraftPointContextMenuEvent
import ru.tcynik.klitch.presentation.feature.main.osd.models.ExistingMarkContextMenuEvent
import ru.tcynik.klitch.presentation.feature.main.osd.models.GeoMarksSheetUiState
import ru.tcynik.klitch.presentation.feature.marks.GeoMarkTitleFormatter
import java.util.UUID
import kotlin.math.cos

private const val DRAFT_POINT_TOUCH_RADIUS_M = 30.0
private const val METERS_PER_DEG_LAT_APPROX = 111_320.0
private const val LOCAL_STORAGE_ID = GEO_MARK_LOCAL_STORAGE_ID

data class GeoMarkUiState(
    val markToolActive: Boolean = false,
    val pendingMarkPoints: ImmutableList<GeoPoint> = persistentListOf(),
    val trackDraftDistanceLabel: String = "0.000/0.000км",
    val geoMarks: ImmutableList<GeoMarkModel> = persistentListOf(),
    val selectedGeoMarkId: String? = null,
    val deleteConfirmMarkId: String? = null,
    val isMarksSheetVisible: Boolean = false,
)

class GeoMarkViewModel(
    observeGeoMarks: ObserveGeoMarksUseCase,
    ingestReceivedGeoMarks: IngestReceivedGeoMarksUseCase,
    autoExpireGeoMarks: AutoExpireGeoMarksUseCase,
    observeContours: ObserveContoursUseCase,
    observeConnectionStatus: ObserveConnectionStatusUseCase,
    private val toggleGeoMarkVisibility: ToggleGeoMarkVisibilityUseCase,
    private val deleteGeoMarks: DeleteGeoMarksUseCase,
    private val sendGeoMark: SendGeoMarkUseCase,
    private val geoMarkPrefsRepository: GeoMarkPreferencesRepository,
) : ViewModel() {

    private val _geoMarkState = MutableStateFlow(GeoMarkUiState())
    private val _formState = MutableStateFlow(GeoMarksFormState())

    val uiState: StateFlow<GeoMarkUiState> = combine(_geoMarkState, _formState) { state, form ->
        state.copy(isMarksSheetVisible = form.isSheetVisible)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = GeoMarkUiState(),
    )

    val geoMarksSheetUiState: StateFlow<GeoMarksSheetUiState> =
        combine(_geoMarkState, _formState) { state, form ->
            buildGeoMarksSheetUiState(state, form)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = buildGeoMarksSheetUiState(GeoMarkUiState(), GeoMarksFormState()),
        )

    private val _contextMenuEvent = MutableSharedFlow<GeoMarkContextMenuEvent>()
    val contextMenuEvent: SharedFlow<GeoMarkContextMenuEvent> = _contextMenuEvent.asSharedFlow()

    private var lastOnMapClickUptimeMs = 0L

    init {
        observeGeoMarks(NoParams)
            .onEach { marks -> _geoMarkState.update { it.copy(geoMarks = marks.toImmutableList()) } }
            .launchIn(viewModelScope)

        ingestReceivedGeoMarks.observe().launchIn(viewModelScope)
        autoExpireGeoMarks.observe().launchIn(viewModelScope)

        combine(
            observeContours(NoParams),
            observeConnectionStatus(NoParams).distinctUntilChanged(),
        ) { contours, connectionStatus ->
            val isConnected = connectionStatus is MeshConnectionStatus.Connected
            val active = contours.filter { it.isActive }
            val storage = GeoMarkAddressee(LOCAL_STORAGE_ID, "Хранилище")
            val addressees = (active.map { GeoMarkAddressee(it.id.value, it.name) } + listOf(storage))
                .toImmutableList()
            Triple(addressees, isConnected, active)
        }
            .onEach { (addressees, isConnected, active) ->
                _formState.update { form ->
                    val currentId = form.selectedContourId
                    val stillInList = addressees.any { it.contourId == currentId }
                    val explicitChoice = form.wasAddresseeExplicitlySelected
                        || isPersistedGeoMarkAddresseeChoice(currentId)
                    val newId = when {
                        explicitChoice && stillInList -> currentId
                        explicitChoice && active.isEmpty() -> currentId
                        else -> resolveDefaultGeoMarkAddresseeId(active, isConnected, LOCAL_STORAGE_ID)
                    }
                    form.copy(
                        availableContours = addressees,
                        selectedContourId = newId,
                        wasAddresseeExplicitlySelected = explicitChoice && (stillInList || active.isEmpty()),
                    )
                }
            }
            .launchIn(viewModelScope)

        geoMarkPrefsRepository.observePreferences()
            .onEach { prefs -> applyPrefsToFormState(prefs) }
            .launchIn(viewModelScope)

        geoMarkPrefsRepository.observePresets()
            .onEach { presets -> _formState.update { it.copy(savedPresets = presets.toImmutableList()) } }
            .launchIn(viewModelScope)
    }

    fun onMainDestinationVisible() {
        if (_formState.value.isSheetVisible && !_geoMarkState.value.markToolActive) {
            _geoMarkState.update { it.copy(markToolActive = true) }
        }
    }

    fun toggleMarkTool() {
        _geoMarkState.update { state ->
            if (state.markToolActive) {
                state.copy(markToolActive = false).withPendingMarkPoints(persistentListOf())
            } else {
                state.copy(markToolActive = true)
            }
        }
    }

    fun toggleGeoMarksSheet() {
        val isOpening = !_formState.value.isSheetVisible
        _formState.update { it.copy(isSheetVisible = isOpening) }
        if (isOpening) {
            _geoMarkState.update { it.copy(markToolActive = true) }
        }
    }

    fun toggleSheetCollapsed() {
        _formState.update { it.copy(isCollapsed = !it.isCollapsed) }
    }

    fun closeGeoMarksSheet() {
        _formState.update { it.copy(isSheetVisible = false) }
        if (_geoMarkState.value.markToolActive) {
            _geoMarkState.update { it.copy(markToolActive = false).withPendingMarkPoints(persistentListOf()) }
        }
    }

    fun onMapClick(lat: Double, lon: Double, screenX: Float, screenY: Float, nodeNames: Map<String, String> = emptyMap()) {
        findNearestVisibleMarkId(lat, lon)?.let { markId ->
            val mark = _geoMarkState.value.geoMarks.firstOrNull { it.id == markId } ?: return@let
            _geoMarkState.update { it.copy(selectedGeoMarkId = markId) }
            viewModelScope.launch {
                _contextMenuEvent.emit(
                    ExistingMarkContextMenuEvent(
                        markId = markId,
                        title = GeoMarkTitleFormatter.selectionTitle(mark, nodeNames),
                        screenX = screenX,
                        screenY = screenY,
                    ),
                )
            }
            return
        }

        _geoMarkState.update { it.copy(selectedGeoMarkId = null) }

        if (!_geoMarkState.value.markToolActive) return
        val now = SystemClock.uptimeMillis()
        if (now - lastOnMapClickUptimeMs < 80L) return
        lastOnMapClickUptimeMs = now
        val markType = _formState.value.selectedType
        val newPoint = GeoPoint(lat, lon)
        _geoMarkState.update { state ->
            val updatedPoints = when (markType) {
                GeoMarkType.TRACK -> (state.pendingMarkPoints + newPoint).toImmutableList()
                GeoMarkType.POINT -> persistentListOf(newPoint)
            }
            state.withPendingMarkPoints(updatedPoints)
        }
    }

    fun onMapDoubleClick(lat: Double, lon: Double) {
        if (!_geoMarkState.value.markToolActive) return
        val markType = _formState.value.selectedType
        when (markType) {
            GeoMarkType.POINT -> {
                _geoMarkState.update { it.withPendingMarkPoints(persistentListOf()) }
                viewModelScope.launch {
                    sendGeoMarkAtPoints(listOf(GeoPoint(lat, lon)), GeoMarkType.POINT)
                }
            }
            GeoMarkType.TRACK -> {
                val newPoint = GeoPoint(lat, lon)
                _geoMarkState.update { state ->
                    state.withPendingMarkPoints((state.pendingMarkPoints + newPoint).toImmutableList())
                }
                sendPendingMark()
            }
        }
    }

    fun onMapLongClick(lat: Double, lon: Double, screenX: Float, screenY: Float) {
        val state = _geoMarkState.value
        if (state.markToolActive) {
            val pending = state.pendingMarkPoints
            val radiusSq = DRAFT_POINT_TOUCH_RADIUS_M * DRAFT_POINT_TOUCH_RADIUS_M
            val nearestIndex = pending.indexOfFirst { pt -> distanceSqMeters(pt, lat, lon) < radiusSq }
            if (nearestIndex >= 0) {
                viewModelScope.launch {
                    _contextMenuEvent.emit(DraftPointContextMenuEvent(nearestIndex, screenX, screenY))
                }
            }
        }
    }

    fun clearSelectedGeoMark() {
        _geoMarkState.update { it.copy(selectedGeoMarkId = null) }
    }

    fun hideGeoMark(markId: String) {
        clearSelectedGeoMark()
        viewModelScope.launch { toggleGeoMarkVisibility(markId, visible = false) }
    }

    fun requestDeleteGeoMark(markId: String) {
        clearSelectedGeoMark()
        _geoMarkState.update { it.copy(deleteConfirmMarkId = markId) }
    }

    fun confirmDeleteGeoMark() {
        val markId = _geoMarkState.value.deleteConfirmMarkId ?: return
        _geoMarkState.update { it.copy(deleteConfirmMarkId = null) }
        viewModelScope.launch { deleteGeoMarks(listOf(markId)) }
    }

    fun dismissDeleteGeoMarkConfirm() {
        _geoMarkState.update { it.copy(deleteConfirmMarkId = null) }
    }

    fun prepareGeoMarkForResend(markId: String) {
        val mark = _geoMarkState.value.geoMarks.firstOrNull { it.id == markId } ?: return
        clearSelectedGeoMark()
        _formState.update { form ->
            if (mark.type == GeoMarkType.POINT) {
                form.copy(
                    isSheetVisible = true,
                    isCollapsed = false,
                    selectedType = mark.type,
                    selectedColor = mark.color,
                    selectedShape = mark.shape,
                    selectedTrackEndType = mark.trackEndType,
                    pointMarkName = mark.name,
                )
            } else {
                form.copy(
                    isSheetVisible = true,
                    isCollapsed = false,
                    selectedType = mark.type,
                    selectedColor = mark.color,
                    selectedShape = mark.shape,
                    selectedTrackEndType = mark.trackEndType,
                    trackMarkName = mark.name,
                )
            }
        }
        _geoMarkState.update { it.copy(markToolActive = true).withPendingMarkPoints(mark.points.toImmutableList()) }
    }

    fun sendPendingMark() {
        val points = _geoMarkState.value.pendingMarkPoints.toList()
        if (points.isEmpty()) return
        if (_formState.value.selectedType == GeoMarkType.TRACK && points.size < 2) return
        val type = _formState.value.selectedType
        _geoMarkState.update { it.withPendingMarkPoints(persistentListOf()) }
        viewModelScope.launch { sendGeoMarkAtPoints(points, type) }
    }

    fun clearPendingPoints() {
        _geoMarkState.update { it.withPendingMarkPoints(persistentListOf()) }
    }

    fun deletePendingPoint(index: Int) {
        _geoMarkState.update { state ->
            val updated = state.pendingMarkPoints.toMutableList().also { it.removeAt(index) }
            state.withPendingMarkPoints(updated.toImmutableList())
        }
    }

    fun setMarkType(type: GeoMarkType) {
        val previousType = _formState.value.selectedType
        _formState.update { it.copy(selectedType = type) }
        if (type == GeoMarkType.POINT && previousType == GeoMarkType.TRACK) {
            _geoMarkState.update { state ->
                val pending = state.pendingMarkPoints
                if (pending.size > 1) state.withPendingMarkPoints(persistentListOf(pending.last()))
                else state
            }
        }
        viewModelScope.launch { persistFormState() }
    }

    fun setMarkColor(colorIndex: Int) {
        _formState.update { it.copy(selectedColor = colorIndex) }
        viewModelScope.launch { persistFormState() }
    }

    fun setMarkShape(shape: GeoMarkShape) {
        _formState.update { it.copy(selectedShape = shape) }
        viewModelScope.launch { persistFormState() }
    }

    fun setTrackEndType(endType: TrackEndType) {
        _formState.update { it.copy(selectedTrackEndType = endType) }
        viewModelScope.launch { persistFormState() }
    }

    fun setTtl(ttlSeconds: Long) {
        _formState.update { it.copy(selectedTtlSeconds = ttlSeconds) }
        viewModelScope.launch { persistFormState() }
    }

    fun setMarkName(name: String) {
        _formState.update { form ->
            if (form.selectedType == GeoMarkType.POINT)
                form.copy(pointMarkName = name, pointNameCounter = 1)
            else
                form.copy(trackMarkName = name, trackNameCounter = 1)
        }
        viewModelScope.launch { persistFormState() }
    }

    fun setNameCounter(counter: Int?) {
        val v = counter?.coerceAtLeast(1)
        _formState.update { form ->
            if (form.selectedType == GeoMarkType.POINT)
                form.copy(pointNameCounter = v)
            else
                form.copy(trackNameCounter = v)
        }
        viewModelScope.launch { persistFormState() }
    }

    fun setAddressee(contourId: String) {
        _formState.update { it.copy(selectedContourId = contourId, wasAddresseeExplicitlySelected = true) }
        viewModelScope.launch { persistFormState() }
    }

    fun applyPreset(preset: GeoMarkPreset) {
        applyPrefsToFormState(preset.prefs)
        viewModelScope.launch { persistFormState() }
    }

    private fun findNearestVisibleMarkId(lat: Double, lon: Double): String? {
        val radiusSq = DRAFT_POINT_TOUCH_RADIUS_M * DRAFT_POINT_TOUCH_RADIUS_M
        return _geoMarkState.value.geoMarks
            .asSequence()
            .filter { it.isVisible }
            .mapNotNull { mark ->
                val nearestDistance = mark.points.minOfOrNull { pt -> distanceSqMeters(pt, lat, lon) }
                    ?: return@mapNotNull null
                mark.id to nearestDistance
            }
            .minByOrNull { it.second }
            ?.takeIf { it.second < radiusSq }
            ?.first
    }

    private fun distanceSqMeters(pt: GeoPoint, lat: Double, lon: Double): Double {
        val dLat = (pt.latitude - lat) * METERS_PER_DEG_LAT_APPROX
        val dLon = (pt.longitude - lon) * METERS_PER_DEG_LAT_APPROX * cos(Math.toRadians(lat))
        return dLat * dLat + dLon * dLon
    }

    private fun GeoMarkUiState.withPendingMarkPoints(points: ImmutableList<GeoPoint>): GeoMarkUiState =
        copy(
            pendingMarkPoints = points,
            trackDraftDistanceLabel = GeoTrackDistance.formatKmRatio(
                GeoTrackDistance.lastSegmentMeters(points),
                GeoTrackDistance.totalMeters(points),
            ),
        )

    private suspend fun sendGeoMarkAtPoints(points: List<GeoPoint>, type: GeoMarkType) {
        val form = _formState.value
        val nowSeconds = System.currentTimeMillis() / 1_000
        val markLabel = buildMarkLabel(form, type)
        val localOnly = form.selectedContourId == LOCAL_STORAGE_ID
        val contourId = if (localOnly) null
                        else form.selectedContourId.takeIf { it.isNotEmpty() }?.let { ContourId(it) }
        val markId = UUID.randomUUID().toString()
        val mark = GeoMarkModel(
            id           = markId,
            waypointId   = WaypointIdConverter.waypointIdFromMarkId(markId),
            type         = type,
            points       = points,
            authorNodeId = "",
            createdAt    = nowSeconds,
            expiresAt    = nowSeconds + form.selectedTtlSeconds,
            isSelf       = true,
            color        = form.selectedColor,
            name         = markLabel,
            trackEndType = form.selectedTrackEndType,
            shape        = form.selectedShape,
        )
        sendGeoMark(SendGeoMarkParams(mark, contourId, localOnly))
        _formState.update { s ->
            if (type == GeoMarkType.POINT) s.copy(pointNameCounter = s.pointNameCounter?.plus(1))
            else s.copy(trackNameCounter = s.trackNameCounter?.plus(1))
        }
        persistFormState()
        savePreset(_formState.value, markLabel)
    }

    private fun buildMarkLabel(form: GeoMarksFormState, type: GeoMarkType): String {
        val base = if (type == GeoMarkType.POINT) form.pointMarkName.trim() else form.trackMarkName.trim()
        val counter = if (type == GeoMarkType.POINT) form.pointNameCounter else form.trackNameCounter
        return when {
            counter == null && base.isEmpty() -> ""
            counter == null -> base
            base.isEmpty() -> "$counter"
            else -> "$base $counter"
        }
    }

    private fun applyPrefsToFormState(prefs: GeoMarkFormPreferences) {
        val prefsType = runCatching { GeoMarkType.valueOf(prefs.selectedType) }
            .getOrDefault(GeoMarkType.POINT)
        _formState.update { form ->
            val preserveType = _geoMarkState.value.pendingMarkPoints.isNotEmpty()
            val persistedAddressee = isPersistedGeoMarkAddresseeChoice(prefs.selectedContourId)
            form.copy(
                selectedType         = if (preserveType) form.selectedType else prefsType,
                selectedColor        = prefs.selectedColor,
                selectedShape        = runCatching { GeoMarkShape.valueOf(prefs.selectedShape) }.getOrDefault(GeoMarkShape.CIRCLE),
                selectedTrackEndType = TrackEndType.fromByte(prefs.selectedTrackEndType.toByte()),
                selectedTtlSeconds   = prefs.selectedTtlSeconds,
                pointMarkName        = prefs.pointMarkName,
                trackMarkName        = prefs.trackMarkName,
                pointNameCounter     = prefs.pointNameCounter,
                trackNameCounter     = prefs.trackNameCounter,
                selectedContourId    = when {
                    form.wasAddresseeExplicitlySelected && form.selectedContourId.isNotEmpty() ->
                        form.selectedContourId
                    persistedAddressee -> prefs.selectedContourId
                    form.selectedContourId.isNotEmpty() -> form.selectedContourId
                    else -> ""
                },
                wasAddresseeExplicitlySelected = form.wasAddresseeExplicitlySelected || persistedAddressee,
            )
        }
    }

    private suspend fun persistFormState() {
        val form = _formState.value
        geoMarkPrefsRepository.savePreferences(
            GeoMarkFormPreferences(
                selectedType         = form.selectedType.name,
                selectedColor        = form.selectedColor,
                selectedShape        = form.selectedShape.name,
                selectedTrackEndType = form.selectedTrackEndType.ends.toInt(),
                selectedTtlSeconds   = form.selectedTtlSeconds,
                pointMarkName        = form.pointMarkName,
                trackMarkName        = form.trackMarkName,
                pointNameCounter     = form.pointNameCounter,
                trackNameCounter     = form.trackNameCounter,
                selectedContourId    = form.selectedContourId,
            )
        )
    }

    private suspend fun savePreset(form: GeoMarksFormState, markLabel: String) {
        val preset = GeoMarkPreset(
            id          = UUID.randomUUID().toString(),
            displayName = "${form.selectedType.name} $markLabel",
            prefs       = GeoMarkFormPreferences(
                selectedType         = form.selectedType.name,
                selectedColor        = form.selectedColor,
                selectedShape        = form.selectedShape.name,
                selectedTrackEndType = form.selectedTrackEndType.ends.toInt(),
                selectedTtlSeconds   = form.selectedTtlSeconds,
                pointMarkName        = form.pointMarkName,
                trackMarkName        = form.trackMarkName,
                selectedContourId    = form.selectedContourId,
            ),
        )
        geoMarkPrefsRepository.addPreset(preset)
    }

    private fun buildGeoMarksSheetUiState(state: GeoMarkUiState, form: GeoMarksFormState): GeoMarksSheetUiState =
        GeoMarksSheetUiState(
            isVisible            = form.isSheetVisible,
            isCollapsed          = form.isCollapsed,
            markToolActive       = state.markToolActive,
            selectedType         = form.selectedType,
            selectedColor        = form.selectedColor,
            selectedShape        = form.selectedShape,
            selectedTrackEndType = form.selectedTrackEndType,
            selectedTtlSeconds   = form.selectedTtlSeconds,
            markName             = if (form.selectedType == GeoMarkType.POINT) form.pointMarkName else form.trackMarkName,
            nameCounter          = if (form.selectedType == GeoMarkType.POINT) form.pointNameCounter else form.trackNameCounter,
            pendingPoints          = state.pendingMarkPoints,
            trackDraftDistanceLabel = state.trackDraftDistanceLabel,
            availableContours    = form.availableContours,
            selectedContourId    = form.selectedContourId,
            savedPresets         = form.savedPresets,
            onClose              = ::closeGeoMarksSheet,
            onToggleCollapsed    = ::toggleSheetCollapsed,
            onToggleMarkTool     = ::toggleMarkTool,
            onMarkTypeSelected   = ::setMarkType,
            onColorSelected      = ::setMarkColor,
            onShapeSelected      = ::setMarkShape,
            onTrackEndTypeSelected = ::setTrackEndType,
            onTtlSelected        = ::setTtl,
            onMarkNameChanged    = ::setMarkName,
            onNameCounterChanged = ::setNameCounter,
            onAddresseeSelected  = ::setAddressee,
            onApplyPreset        = ::applyPreset,
            onSendPendingMark    = ::sendPendingMark,
            onDeletePendingPoint = ::deletePendingPoint,
            onClearPendingPoints = ::clearPendingPoints,
        )
}
