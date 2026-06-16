package ru.tcynik.klitch.presentation.feature.main

import androidx.lifecycle.ViewModel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import ru.tcynik.klitch.domain.channel.model.Contour
import ru.tcynik.klitch.domain.channel.model.ContourHash
import ru.tcynik.klitch.domain.channel.model.ContourId
import ru.tcynik.klitch.domain.channel.model.ContourTransport
import ru.tcynik.klitch.domain.channel.model.DefaultActiveContour
import ru.tcynik.klitch.domain.channel.model.MeshtasticChannel
import ru.tcynik.klitch.domain.channel.usecase.ObserveContoursUseCase
import ru.tcynik.klitch.domain.marker.model.GeoMarkFormPreferences
import ru.tcynik.klitch.domain.marker.model.GeoMarkPreset
import ru.tcynik.klitch.domain.marker.repository.GeoMarkPreferencesRepository
import ru.tcynik.klitch.domain.marker.usecase.AutoExpireGeoMarksUseCase
import ru.tcynik.klitch.domain.marker.usecase.DeleteGeoMarksUseCase
import ru.tcynik.klitch.domain.marker.usecase.IngestReceivedGeoMarksUseCase
import ru.tcynik.klitch.domain.marker.usecase.ObserveGeoMarksUseCase
import ru.tcynik.klitch.domain.marker.usecase.SendGeoMarkUseCase
import ru.tcynik.klitch.domain.marker.usecase.ToggleGeoMarkVisibilityUseCase
import ru.tcynik.klitch.domain.mesh.model.MeshConnectionStatus
import ru.tcynik.klitch.domain.mesh.usecase.ObserveConnectionStatusUseCase
import java.util.Base64

class GeoMarkViewModelGeoMarkAddresseeTest {

    private val observeGeoMarks: ObserveGeoMarksUseCase = mockk()
    private val ingestReceivedGeoMarks: IngestReceivedGeoMarksUseCase = mockk()
    private val autoExpireGeoMarks: AutoExpireGeoMarksUseCase = mockk(relaxed = true)
    private val observeContours: ObserveContoursUseCase = mockk()
    private val observeConnectionStatus: ObserveConnectionStatusUseCase = mockk()
    private val toggleGeoMarkVisibility: ToggleGeoMarkVisibilityUseCase = mockk(relaxed = true)
    private val deleteGeoMarks: DeleteGeoMarksUseCase = mockk(relaxed = true)
    private val sendGeoMark: SendGeoMarkUseCase = mockk(relaxed = true)

    private val channelsFlow = MutableStateFlow<List<Contour>>(emptyList())
    private val connectionStatusFlow = MutableStateFlow<MeshConnectionStatus>(MeshConnectionStatus.Disconnected)
    private val prefsFlow = MutableStateFlow(GeoMarkFormPreferences())
    private val geoMarkPrefsRepository: GeoMarkPreferencesRepository = object : GeoMarkPreferencesRepository {
        override fun observePreferences(): Flow<GeoMarkFormPreferences> = prefsFlow
        override fun observePresets(): Flow<List<GeoMarkPreset>> = flowOf(emptyList())
        override suspend fun savePreferences(prefs: GeoMarkFormPreferences) {}
        override suspend fun addPreset(preset: GeoMarkPreset) {}
    }

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var viewModel: GeoMarkViewModel

    private val psk = byteArrayOf(0x01)
    private val pskBase64 = Base64.getEncoder().encodeToString(psk)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { observeGeoMarks.invoke(any()) } returns flowOf(emptyList())
        every { ingestReceivedGeoMarks.observe() } returns flowOf(Unit)
        every { autoExpireGeoMarks.observe() } returns flowOf(Unit)
        every { observeContours.invoke(any()) } returns channelsFlow
        every { observeConnectionStatus.invoke(any()) } returns connectionStatusFlow
        viewModel = createViewModel()
    }

    @After
    fun tearDown() {
        val onCleared = ViewModel::class.java.getDeclaredMethod("onCleared")
        onCleared.isAccessible = true
        onCleared.invoke(viewModel)
        Dispatchers.resetMain()
    }

    private fun createViewModel(): GeoMarkViewModel = GeoMarkViewModel(
        observeGeoMarks = observeGeoMarks,
        ingestReceivedGeoMarks = ingestReceivedGeoMarks,
        autoExpireGeoMarks = autoExpireGeoMarks,
        observeContours = observeContours,
        observeConnectionStatus = observeConnectionStatus,
        toggleGeoMarkVisibility = toggleGeoMarkVisibility,
        deleteGeoMarks = deleteGeoMarks,
        sendGeoMark = sendGeoMark,
        geoMarkPrefsRepository = geoMarkPrefsRepository,
    )

    private fun makeBasicContour(): Contour {
        val hash = ContourHash.compute(DefaultActiveContour.CHANNEL_NAME, psk)
        return Contour(
            id = DefaultActiveContour.ID,
            name = DefaultActiveContour.DISPLAY_NAME,
            description = null,
            expiration = null,
            exclusivityTime = null,
            isActive = true,
            transport = ContourTransport(meshtastic = MeshtasticChannel(psk = pskBase64, channelHash = hash)),
        )
    }

    private fun makeCustomContour(id: String): Contour {
        val hash = ContourHash.compute("Team", psk)
        return Contour(
            id = ContourId(id),
            name = "Team",
            description = null,
            expiration = null,
            exclusivityTime = null,
            isActive = true,
            transport = ContourTransport(meshtastic = MeshtasticChannel(psk = pskBase64, channelHash = hash)),
        )
    }

    @Test
    fun `disconnected — default addressee is storage`() {
        channelsFlow.value = listOf(makeBasicContour())
        connectionStatusFlow.value = MeshConnectionStatus.Disconnected
        assertEquals(GEO_MARK_LOCAL_STORAGE_ID, viewModel.geoMarksSheetUiState.value.selectedContourId)
    }

    @Test
    fun `connected with Basic active — default addressee is Basic`() {
        channelsFlow.value = listOf(makeBasicContour())
        connectionStatusFlow.value = MeshConnectionStatus.Connected("!abc", "SN", "Meshtastic SN", -70, 80)
        assertEquals(DefaultActiveContour.ID.value, viewModel.geoMarksSheetUiState.value.selectedContourId)
    }

    @Test
    fun `prefs with local storage and connected — switches to Basic not storage`() {
        prefsFlow.value = GeoMarkFormPreferences(selectedContourId = GEO_MARK_LOCAL_STORAGE_ID)
        viewModel = createViewModel()
        channelsFlow.value = listOf(makeBasicContour())
        connectionStatusFlow.value = MeshConnectionStatus.Connected("!abc", "SN", "Meshtastic SN", -70, 80)
        assertEquals(DefaultActiveContour.ID.value, viewModel.geoMarksSheetUiState.value.selectedContourId)
    }

    @Test
    fun `prefs with custom contour and connected — keeps custom contour`() {
        val customId = "00000000-0000-0000-0000-000000000099"
        prefsFlow.value = GeoMarkFormPreferences(selectedContourId = customId)
        viewModel = createViewModel()
        channelsFlow.value = listOf(makeBasicContour(), makeCustomContour(customId))
        connectionStatusFlow.value = MeshConnectionStatus.Connected("!abc", "SN", "Meshtastic SN", -70, 80)
        assertEquals(customId, viewModel.geoMarksSheetUiState.value.selectedContourId)
    }

    @Test
    fun `setAddressee storage while connected — keeps storage in session`() {
        channelsFlow.value = listOf(makeBasicContour())
        connectionStatusFlow.value = MeshConnectionStatus.Connected("!abc", "SN", "Meshtastic SN", -70, 80)
        viewModel.setAddressee(GEO_MARK_LOCAL_STORAGE_ID)
        assertEquals(GEO_MARK_LOCAL_STORAGE_ID, viewModel.geoMarksSheetUiState.value.selectedContourId)
    }
}
