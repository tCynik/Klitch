package ru.tcynik.klitch.presentation.feature.network

import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.tcynik.klitch.domain.channel.repository.ContourSyncStateRepository
import ru.tcynik.klitch.domain.channel.usecase.ConfirmChannelSyncUseCase
import ru.tcynik.klitch.domain.mesh.model.MeshConnectionStatus
import ru.tcynik.klitch.domain.mesh.usecase.BeginSettingsEditUseCase
import ru.tcynik.klitch.domain.mesh.usecase.CommitSettingsEditUseCase
import ru.tcynik.klitch.domain.mesh.usecase.GetGpsModeUseCase
import ru.tcynik.klitch.domain.mesh.usecase.SetDesiredGpsModeUseCase
import ru.tcynik.klitch.domain.mesh.usecase.ObserveConnectionStatusUseCase
import ru.tcynik.klitch.domain.mesh.usecase.ObserveDeviceConfigUseCase
import ru.tcynik.klitch.domain.mesh.usecase.ObserveLocationConfigUseCase
import ru.tcynik.klitch.domain.mesh.usecase.ObserveOurNodeUseCase
import ru.tcynik.klitch.domain.mesh.usecase.RemoveFixedPositionUseCase
import ru.tcynik.klitch.domain.mesh.usecase.RequestDeviceConfigUseCase
import ru.tcynik.klitch.domain.mesh.usecase.SetProvideLocationUseCase
import ru.tcynik.klitch.domain.mesh.usecase.WriteChannelPositionPrecisionUseCase
import ru.tcynik.klitch.domain.mesh.usecase.WriteChannelUseCase
import ru.tcynik.klitch.domain.mesh.usecase.WriteOwnerUseCase
import ru.tcynik.klitch.domain.mesh.usecase.WritePositionConfigUseCase
import ru.tcynik.klitch.domain.usecase.base.NoParams
import ru.tcynik.klitch.mesh.repository.UiPrefs

/** Covers Phase 4 (node-gps-position-source plan) 4.4: Sync button + leave-screen dialog that does not disconnect. */
class NetworkSettingsViewModelSyncTest {

    private val observeConnectionStatus: ObserveConnectionStatusUseCase = mockk()
    private val observeDeviceConfig: ObserveDeviceConfigUseCase = mockk()
    private val requestDeviceConfig: RequestDeviceConfigUseCase = mockk(relaxed = true)
    private val beginSettingsEdit: BeginSettingsEditUseCase = mockk(relaxed = true)
    private val commitSettingsEdit: CommitSettingsEditUseCase = mockk(relaxed = true)
    private val writeOwner: WriteOwnerUseCase = mockk(relaxed = true)
    private val writeChannel: WriteChannelUseCase = mockk(relaxed = true)
    private val observeOurNode: ObserveOurNodeUseCase = mockk()
    private val observeLocationConfig: ObserveLocationConfigUseCase = mockk()
    private val setProvideLocation: SetProvideLocationUseCase = mockk(relaxed = true)
    private val writePositionConfig: WritePositionConfigUseCase = mockk(relaxed = true)
    private val setDesiredGpsMode: SetDesiredGpsModeUseCase = mockk(relaxed = true)
    private val getGpsMode: GetGpsModeUseCase = mockk(relaxed = true)
    private val writeChannelPositionPrecision: WriteChannelPositionPrecisionUseCase = mockk(relaxed = true)
    private val removeFixedPosition: RemoveFixedPositionUseCase = mockk(relaxed = true)
    private val syncStateRepository: ContourSyncStateRepository = mockk()
    private val confirmChannelSync: ConfirmChannelSyncUseCase = mockk(relaxed = true)
    private val uiPrefs: UiPrefs = mockk()

    private val syncRequiredFlow = MutableStateFlow(false)
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var viewModel: NetworkSettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { observeConnectionStatus.invoke(any()) } returns flowOf(MeshConnectionStatus.Disconnected)
        every { observeDeviceConfig.invoke(any()) } returns flowOf(null)
        every { observeOurNode.invoke(any()) } returns flowOf(null)
        every { observeLocationConfig.invoke(any()) } returns flowOf()
        every { uiPrefs.useWakeLock } returns MutableStateFlow(false)
        every { syncStateRepository.syncRequired } returns syncRequiredFlow

        viewModel = NetworkSettingsViewModel(
            observeConnectionStatus = observeConnectionStatus,
            observeDeviceConfig = observeDeviceConfig,
            requestDeviceConfig = requestDeviceConfig,
            beginSettingsEdit = beginSettingsEdit,
            commitSettingsEdit = commitSettingsEdit,
            writeOwner = writeOwner,
            writeChannel = writeChannel,
            observeOurNode = observeOurNode,
            observeLocationConfig = observeLocationConfig,
            setProvideLocation = setProvideLocation,
            writePositionConfig = writePositionConfig,
            setDesiredGpsMode = setDesiredGpsMode,
            getGpsMode = getGpsMode,
            writeChannelPositionPrecision = writeChannelPositionPrecision,
            removeFixedPosition = removeFixedPosition,
            syncStateRepository = syncStateRepository,
            confirmChannelSync = confirmChannelSync,
            uiPrefs = uiPrefs,
            logger = ru.tcynik.klitch.logger.NoOpLogger(),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `syncRequired true is reflected in uiState`() = runTest(testDispatcher) {
        syncRequiredFlow.value = true

        assertTrue(viewModel.uiState.value.syncRequired)
    }

    @Test
    fun `onNavigateBackRequested with syncRequired shows dialog instead of navigating`() = runTest(testDispatcher) {
        syncRequiredFlow.value = true
        var navigated = false

        viewModel.onNavigateBackRequested { navigated = true }

        assertTrue(viewModel.uiState.value.showLeaveSyncDialog)
        assertFalse(navigated)
    }

    @Test
    fun `onNavigateBackRequested without syncRequired navigates immediately`() = runTest(testDispatcher) {
        var navigated = false

        viewModel.onNavigateBackRequested { navigated = true }

        assertTrue(navigated)
        assertFalse(viewModel.uiState.value.showLeaveSyncDialog)
    }

    @Test
    fun `dismissing the leave dialog does not disconnect`() = runTest(testDispatcher) {
        syncRequiredFlow.value = true
        viewModel.onNavigateBackRequested {}

        viewModel.onDismissLeaveSyncDialog()

        assertFalse(viewModel.uiState.value.showLeaveSyncDialog)
        coVerify(exactly = 0) { confirmChannelSync.invoke(any()) }
    }

    @Test
    fun `onSyncClick invokes confirmChannelSync`() = runTest(testDispatcher) {
        viewModel.onSyncClick()

        coVerify(exactly = 1) { confirmChannelSync.invoke(NoParams) }
    }
}
