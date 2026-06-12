package ru.tcynik.meshtactics.di

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.WorkManager
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module
import ru.tcynik.meshtactics.AppBuildConfigProvider
import ru.tcynik.meshtactics.NoOpAppWidgetUpdater
import ru.tcynik.meshtactics.NoOpPlatformAnalytics
import ru.tcynik.meshtactics.mesh.common.BuildConfigProvider
import ru.tcynik.meshtactics.mesh.repository.AppWidgetUpdater
import ru.tcynik.meshtactics.mesh.repository.PlatformAnalytics
import ru.tcynik.meshtactics.data.local.mesh.LastConnectedDeviceRepositoryImpl
import ru.tcynik.meshtactics.data.mesh.repository.MeshConfigRepositoryImpl
import ru.tcynik.meshtactics.data.mesh.repository.MeshConnectionRepositoryImpl
import ru.tcynik.meshtactics.data.mesh.repository.MeshMessagingRepositoryImpl
import ru.tcynik.meshtactics.data.mesh.repository.MeshNetworkRepositoryImpl
import ru.tcynik.meshtactics.data.mesh.repository.MeshPacketLogRepositoryImpl
import ru.tcynik.meshtactics.domain.mesh.repository.LastConnectedDeviceRepository
import ru.tcynik.meshtactics.domain.mesh.repository.MeshConfigRepository
import ru.tcynik.meshtactics.domain.mesh.repository.MeshConnectionRepository
import ru.tcynik.meshtactics.domain.mesh.repository.MeshMessagingRepository
import ru.tcynik.meshtactics.domain.mesh.repository.MeshNetworkRepository
import ru.tcynik.meshtactics.domain.mesh.repository.MeshPacketLogRepository
import ru.tcynik.meshtactics.domain.mesh.usecase.ConnectToMeshDeviceUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.GetLastConnectedDeviceUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.SaveLastConnectedDeviceUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.DisconnectFromMeshUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.ObserveConnectionStatusUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.ObserveDeviceConfigUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.RequestDeviceConfigUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.WriteChannelUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.BeginSettingsEditUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.CommitSettingsEditUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.WriteOwnerUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.ObserveMeshNodesUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.ObserveMessagesUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.ObserveOurNodeUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.ObserveContourNodesUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.ObserveGeoNodesUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.ObserveLocationConfigUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.ObservePacketLogUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.ObserveContoursUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.ObserveNodeChannelsUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.ResolveChannelSlotUseCase
import ru.tcynik.meshtactics.domain.gps.repository.GpsRepository
import ru.tcynik.meshtactics.domain.mesh.usecase.NodeProvisioningUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.RemoveFixedPositionUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.ScanMeshDevicesUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.SendMeshMessageUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.SetProvideLocationUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.WriteChannelPositionPrecisionUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.WritePositionConfigUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.CheckOwnPkcHealthUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.PrepareNodeForAppDrivenBroadcastUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.DisableNodePositionBroadcastUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.GetPositionBroadcastSecsUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.IsPositionSmartBroadcastEnabledUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.ObserveCallsignChangesUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.RebootNodeUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.ConfirmChannelSyncUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.ReconnectAfterNodeRebootUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.ReconnectViaBleScanUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.RefreshNodePublicKeyUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.RefreshNodePublicKeysUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.ObserveNodeSecurityConfigUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.RegeneratePkcKeysUseCase
import ru.tcynik.meshtactics.data.mesh.BackgroundPositionSession
import ru.tcynik.meshtactics.data.mesh.MeshWakeLockManager
import ru.tcynik.meshtactics.domain.gps.repository.GpsLifecycleController
import ru.tcynik.meshtactics.data.mesh.ContourPositionChannelFilter
import ru.tcynik.meshtactics.data.mesh.GeoSendPolicyImpl
import ru.tcynik.meshtactics.data.mesh.OnConnectPositionSender
import ru.tcynik.meshtactics.mesh.repository.PositionChannelFilter
import ru.tcynik.meshtactics.data.mesh.repository.RebootStateRepositoryImpl
import ru.tcynik.meshtactics.domain.mesh.repository.RebootStateRepository
import ru.tcynik.meshtactics.mesh.repository.GeoSendPolicy
import ru.tcynik.meshtactics.data.notification.EmergencyNodeNotificationFilter
import ru.tcynik.meshtactics.mesh.service.AndroidNotificationManager

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

    single {
        BackgroundPositionSession(
            nodeRepository = get(),
            locationManager = get(),
            commandSender = get(),
            uiPrefs = get(),
            geoSendPolicy = get(),
            contourRepository = get(),
            channelSlotResolver = get(),
            gpsLifecycleController = get<GpsLifecycleController>(),
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
    single { RebootNodeUseCase(get()) }
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
