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
import ru.tcynik.meshtactics.data.mesh.repository.MeshConfigRepositoryImpl
import ru.tcynik.meshtactics.data.mesh.repository.MeshConnectionRepositoryImpl
import ru.tcynik.meshtactics.data.mesh.repository.MeshMessagingRepositoryImpl
import ru.tcynik.meshtactics.data.mesh.repository.MeshNetworkRepositoryImpl
import ru.tcynik.meshtactics.data.mesh.repository.MeshPacketLogRepositoryImpl
import ru.tcynik.meshtactics.domain.mesh.repository.MeshConfigRepository
import ru.tcynik.meshtactics.domain.mesh.repository.MeshConnectionRepository
import ru.tcynik.meshtactics.domain.mesh.repository.MeshMessagingRepository
import ru.tcynik.meshtactics.domain.mesh.repository.MeshNetworkRepository
import ru.tcynik.meshtactics.domain.mesh.repository.MeshPacketLogRepository
import ru.tcynik.meshtactics.domain.mesh.usecase.ConnectToMeshDeviceUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.DisconnectFromMeshUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.ObserveConnectionStatusUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.ObserveDeviceConfigUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.RequestDeviceConfigUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.WriteChannelUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.WriteOwnerUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.ObserveMeshNodesUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.ObserveMessagesUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.ObserveOurNodeUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.ObservePacketLogUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.ScanMeshDevicesUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.SendMeshMessageUseCase

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
            radioInterfaceService = get(),
            serviceRepository = get(),
            nodeRepository = get(),
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
        )
    }

    // --- Use Cases ---
    single { ObserveConnectionStatusUseCase(get()) }
    single { ScanMeshDevicesUseCase(get()) }
    single { ConnectToMeshDeviceUseCase(get()) }
    single { DisconnectFromMeshUseCase(get()) }
    single { ObserveMeshNodesUseCase(get()) }
    single { ObserveOurNodeUseCase(get()) }
    single { ObserveMessagesUseCase(get()) }
    single { SendMeshMessageUseCase(get()) }
    single { ObservePacketLogUseCase(get()) }
    single { ObserveDeviceConfigUseCase(get()) }
    single { RequestDeviceConfigUseCase(get()) }
    single { WriteOwnerUseCase(get()) }
    single { WriteChannelUseCase(get()) }
}
