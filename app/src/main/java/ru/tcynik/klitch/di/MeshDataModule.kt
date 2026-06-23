package ru.tcynik.klitch.di

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.WorkManager
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module
import ru.tcynik.klitch.AppBuildConfigProvider
import ru.tcynik.klitch.NoOpAppWidgetUpdater
import ru.tcynik.klitch.NoOpPlatformAnalytics
import ru.tcynik.klitch.mesh.common.BuildConfigProvider
import ru.tcynik.klitch.mesh.repository.AppWidgetUpdater
import ru.tcynik.klitch.mesh.repository.PlatformAnalytics
import ru.tcynik.klitch.data.local.mesh.LastConnectedDeviceRepositoryImpl
import ru.tcynik.klitch.data.mesh.repository.MeshConfigRepositoryImpl
import ru.tcynik.klitch.data.mesh.repository.MeshConnectionRepositoryImpl
import ru.tcynik.klitch.data.mesh.repository.MeshMessagingRepositoryImpl
import ru.tcynik.klitch.data.mesh.repository.MeshNetworkRepositoryImpl
import ru.tcynik.klitch.data.mesh.repository.MeshPacketLogRepositoryImpl
import ru.tcynik.klitch.domain.mesh.repository.LastConnectedDeviceRepository
import ru.tcynik.klitch.domain.mesh.repository.MeshConfigRepository
import ru.tcynik.klitch.domain.mesh.repository.MeshConnectionRepository
import ru.tcynik.klitch.domain.mesh.repository.MeshMessagingRepository
import ru.tcynik.klitch.domain.mesh.repository.MeshNetworkRepository
import ru.tcynik.klitch.domain.mesh.repository.MeshPacketLogRepository
import ru.tcynik.klitch.domain.mesh.usecase.ConnectToMeshDeviceUseCase
import ru.tcynik.klitch.domain.mesh.usecase.GetLastConnectedDeviceUseCase
import ru.tcynik.klitch.domain.mesh.usecase.SaveLastConnectedDeviceUseCase
import ru.tcynik.klitch.domain.mesh.usecase.DisconnectFromMeshUseCase
import ru.tcynik.klitch.domain.mesh.usecase.ObserveConnectionStatusUseCase
import ru.tcynik.klitch.domain.mesh.usecase.ObserveDeviceConfigUseCase
import ru.tcynik.klitch.domain.mesh.usecase.RequestDeviceConfigUseCase
import ru.tcynik.klitch.domain.mesh.usecase.WriteChannelUseCase
import ru.tcynik.klitch.domain.mesh.usecase.BeginSettingsEditUseCase
import ru.tcynik.klitch.domain.mesh.usecase.CommitSettingsEditUseCase
import ru.tcynik.klitch.domain.mesh.usecase.WriteOwnerUseCase
import ru.tcynik.klitch.domain.mesh.usecase.ObserveMeshNodesUseCase
import ru.tcynik.klitch.domain.mesh.usecase.ObserveMessagesUseCase
import ru.tcynik.klitch.domain.mesh.usecase.ObserveOurNodeUseCase
import ru.tcynik.klitch.domain.mesh.usecase.ObserveContourNodesUseCase
import ru.tcynik.klitch.domain.mesh.usecase.ObserveGeoNodesUseCase
import ru.tcynik.klitch.domain.mesh.usecase.ObserveLocationConfigUseCase
import ru.tcynik.klitch.domain.mesh.usecase.ObservePacketLogUseCase
import ru.tcynik.klitch.domain.channel.usecase.ObserveContoursUseCase
import ru.tcynik.klitch.domain.channel.usecase.ObserveNodeChannelsUseCase
import ru.tcynik.klitch.domain.channel.usecase.ResolveChannelSlotUseCase
import ru.tcynik.klitch.domain.gps.repository.GpsRepository
import ru.tcynik.klitch.domain.mesh.usecase.NodeProvisioningUseCase
import ru.tcynik.klitch.domain.mesh.usecase.RemoveFixedPositionUseCase
import ru.tcynik.klitch.domain.mesh.usecase.ScanMeshDevicesUseCase
import ru.tcynik.klitch.domain.mesh.usecase.SendMeshMessageUseCase
import ru.tcynik.klitch.domain.mesh.usecase.SetProvideLocationUseCase
import ru.tcynik.klitch.domain.mesh.usecase.WriteChannelPositionPrecisionUseCase
import ru.tcynik.klitch.domain.mesh.usecase.WritePositionConfigUseCase
import ru.tcynik.klitch.domain.mesh.usecase.GetDesiredGpsModeUseCase
import ru.tcynik.klitch.domain.mesh.usecase.SetDesiredGpsModeUseCase
import ru.tcynik.klitch.domain.mesh.usecase.GetGpsModeUseCase
import ru.tcynik.klitch.domain.mesh.usecase.RequestTelemetryUseCase
import ru.tcynik.klitch.domain.mesh.usecase.WriteGpsModeUseCase
import ru.tcynik.klitch.domain.mesh.usecase.CheckOwnPkcHealthUseCase
import ru.tcynik.klitch.domain.mesh.usecase.PrepareNodeForAppDrivenBroadcastUseCase
import ru.tcynik.klitch.domain.mesh.usecase.DisableNodePositionBroadcastUseCase
import ru.tcynik.klitch.domain.mesh.usecase.GetPositionBroadcastSecsUseCase
import ru.tcynik.klitch.domain.mesh.usecase.IsPositionSmartBroadcastEnabledUseCase
import ru.tcynik.klitch.domain.mesh.usecase.ObserveCallsignChangesUseCase
import ru.tcynik.klitch.domain.mesh.usecase.RebootNodeUseCase
import ru.tcynik.klitch.domain.channel.usecase.ConfirmChannelSyncUseCase
import ru.tcynik.klitch.domain.mesh.usecase.ReconnectAfterNodeRebootUseCase
import ru.tcynik.klitch.domain.mesh.usecase.ReconnectViaBleScanUseCase
import ru.tcynik.klitch.domain.mesh.usecase.RefreshNodePublicKeyUseCase
import ru.tcynik.klitch.domain.mesh.usecase.RefreshNodePublicKeysUseCase
import ru.tcynik.klitch.domain.mesh.usecase.ObserveNodeSecurityConfigUseCase
import ru.tcynik.klitch.domain.mesh.usecase.RegeneratePkcKeysUseCase
import ru.tcynik.klitch.data.gps.NodeGpsPositionSource
import ru.tcynik.klitch.data.gps.NodeGpsWatchdog
import ru.tcynik.klitch.domain.gps.usecase.ObservePositionSourceModeUseCase
import ru.tcynik.klitch.data.mesh.BackgroundPositionSession
import ru.tcynik.klitch.data.mesh.MeshWakeLockManager
import ru.tcynik.klitch.domain.gps.repository.GpsLifecycleController
import ru.tcynik.klitch.data.mesh.ContourPositionChannelFilter
import ru.tcynik.klitch.data.mesh.GeoSendPolicyImpl
import ru.tcynik.klitch.data.mesh.OnConnectPositionSender
import ru.tcynik.klitch.mesh.repository.PositionChannelFilter
import ru.tcynik.klitch.data.mesh.repository.RebootStateRepositoryImpl
import ru.tcynik.klitch.domain.mesh.repository.RebootStateRepository
import ru.tcynik.klitch.mesh.repository.GeoSendPolicy
import ru.tcynik.klitch.data.notification.EmergencyNodeNotificationFilter
import ru.tcynik.klitch.mesh.service.AndroidNotificationManager

val meshDataModule = module {

    single { WorkManager.getInstance(androidContext()) }

    // ProcessLifecycleOwner.lifecycle — required by mesh layer BLE, network, and service components
    single<Lifecycle>(named("ProcessLifecycle")) {
        ProcessLifecycleOwner.get().lifecycle
    }

    // BuildConfig values — required by AndroidRadioTransportFactory and other mesh components
    single<BuildConfigProvider> { AppBuildConfigProvider() }

    // Analytics stub — no analytics in this build
    single<PlatformAnalytics> { NoOpPlatformAnalytics() }

    // AppWidget stub — no widgets in this build
    single<AppWidgetUpdater> { NoOpAppWidgetUpdater() }

    // --- Repositories ---
    single<MeshConnectionRepository> {
        MeshConnectionRepositoryImpl(
            bleScanner = get(),
            bluetoothRepository = get(),
            radioInterfaceService = get(),
            serviceRepository = get(),
            nodeRepository = get(),
            logger = get(),
        )
    }
    single<MeshNetworkRepository> {
        MeshNetworkRepositoryImpl(meshNodeRepository = get())
    }
    single<MeshMessagingRepository> {
        MeshMessagingRepositoryImpl(
            packetRepository = get(),
            commandSender = get(),
            nodeRepository = get(),
            logger = get(),
        )
    }
    single<MeshPacketLogRepository> {
        MeshPacketLogRepositoryImpl(meshLogRepository = get())
    }
    single<MeshConfigRepository> {
        MeshConfigRepositoryImpl(
            meshRouter = get(),
            nodeRepository = get(),
            commandSender = get(),
            packetHandler = get(),
            uiPrefs = get(),
            context = androidContext(),
            logger = get(),
        )
    }

    single<RebootStateRepository> { RebootStateRepositoryImpl(get()) }

    single<GeoSendPolicy> { GeoSendPolicyImpl(get()) }

    single<PositionChannelFilter> {
        ContourPositionChannelFilter(
            contourRepository = get(),
            channelSlotResolver = get(),
        )
    }

    single {
        OnConnectPositionSender(
            connectionRepository = get(),
            gpsRepository = get(),
            contourRepository = get(),
            channelSlotResolver = get(),
            commandSender = get(),
            logger = get(),
        )
    }

    single { NodeGpsPositionSource(nodeRepository = get()) }

    single {
        ObservePositionSourceModeUseCase(
            meshNetworkRepository = get(),
            meshConfigRepository = get(),
        )
    }

    single {
        BackgroundPositionSession(
            nodeRepository = get(),
            locationManager = get(),
            commandSender = get(),
            uiPrefs = get(),
            geoSendPolicy = get(),
            contourRepository = get(),
            syncStateRepository = get(),
            channelSlotResolver = get(),
            gpsLifecycleController = get<GpsLifecycleController>(),
            observePositionSourceMode = get(),
            nodeGpsPositionSource = get(),
            logger = get(),
        )
    }

    single {
        NodeGpsWatchdog(
            nodeRepository = get(),
            observePositionSourceMode = get(),
            syncStateRepository = get(),
            logger = get(),
        )
    }

    single {
        MeshWakeLockManager(
            context = androidContext(),
            uiPrefs = get(),
            geoSendPolicy = get(),
        )
    }

    single {
        EmergencyNodeNotificationFilter(
            androidNotificationManager = get<AndroidNotificationManager>(),
            contourRepository = get(),
            channelSlotResolver = get(),
        )
    }

    single<LastConnectedDeviceRepository> { LastConnectedDeviceRepositoryImpl(get()) }
    single { GetLastConnectedDeviceUseCase(get()) }
    single { SaveLastConnectedDeviceUseCase(get()) }

    // --- Use Cases ---
    single { ObserveConnectionStatusUseCase(get()) }
    single { ScanMeshDevicesUseCase(get()) }
    single { ConnectToMeshDeviceUseCase(get(), get()) }
    single { DisconnectFromMeshUseCase(get()) }
    single { ObserveMeshNodesUseCase(get()) }
    single { ObserveOurNodeUseCase(get()) }
    single { ObserveContourNodesUseCase(get(), get(), get(), get()) }
    single { ObserveGeoNodesUseCase(get(), get(), get(), get()) }
    single { ObserveMessagesUseCase(get()) }
    single { SendMeshMessageUseCase(get()) }
    single { ObservePacketLogUseCase(get()) }
    single { ObserveDeviceConfigUseCase(get()) }
    single { RequestDeviceConfigUseCase(get()) }
    single { BeginSettingsEditUseCase(get()) }
    single { CommitSettingsEditUseCase(get()) }
    single { WriteOwnerUseCase(get()) }
    single { WriteChannelUseCase(get()) }
    single { ObserveLocationConfigUseCase(get()) }
    single { SetProvideLocationUseCase(get()) }
    single { WritePositionConfigUseCase(get()) }
    single { WriteChannelPositionPrecisionUseCase(get()) }
    single { RemoveFixedPositionUseCase(get()) }
    single { PrepareNodeForAppDrivenBroadcastUseCase(get()) }
    single { DisableNodePositionBroadcastUseCase(get()) }
    single { GetPositionBroadcastSecsUseCase(get()) }
    single { IsPositionSmartBroadcastEnabledUseCase(get()) }
    single { GetDesiredGpsModeUseCase(get()) }
    single { SetDesiredGpsModeUseCase(get()) }
    single { GetGpsModeUseCase(get()) }
    single { WriteGpsModeUseCase(get()) }
    single { RebootNodeUseCase(get()) }
    single { RequestTelemetryUseCase(get()) }
    single {
        ReconnectViaBleScanUseCase(
            disconnectFromMesh = get(),
            connectToDevice = get(),
            getLastConnectedDevice = get(),
            observeConnectionStatus = get(),
            meshConnectionRepository = get(),
            logger = get(),
        )
    }
    single {
        ReconnectAfterNodeRebootUseCase(
            disconnectFromMesh = get(),
            reconnectViaBleScan = get(),
            observeConnectionStatus = get(),
            requestDeviceConfig = get(),
            checkNodeSync = get(),
            syncStateRepository = get(),
            rebootStateRepository = get(),
            logger = get(),
        )
    }
    single { CheckOwnPkcHealthUseCase(get()) }
    single { RefreshNodePublicKeysUseCase(get()) }
    single { RefreshNodePublicKeyUseCase(get()) }
    single { RegeneratePkcKeysUseCase(get()) }
    single { ObserveCallsignChangesUseCase(get()) }
    single { ObserveNodeSecurityConfigUseCase(get()) }
    single {
        NodeProvisioningUseCase(
            contourRepository = get(),
            observeContours = get<ObserveContoursUseCase>(),
            writeChannel = get(),
            observeNodeChannels = get<ObserveNodeChannelsUseCase>(),
            resolveSlot = get<ResolveChannelSlotUseCase>(),
            observeOurNode = get<ObserveOurNodeUseCase>(),
            observeDeviceConfig = get<ObserveDeviceConfigUseCase>(),
            observeLocationConfig = get<ObserveLocationConfigUseCase>(),
            writePositionConfig = get<WritePositionConfigUseCase>(),
            gpsRepository = get<GpsRepository>(),
            logger = get(),
        )
    }
}
