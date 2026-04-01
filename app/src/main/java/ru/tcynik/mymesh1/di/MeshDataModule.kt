package ru.tcynik.mymesh1.di

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.WorkManager
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module
import ru.tcynik.mymesh1.AppBuildConfigProvider
import ru.tcynik.mymesh1.NoOpAppWidgetUpdater
import ru.tcynik.mymesh1.NoOpPlatformAnalytics
import ru.tcynik.mymesh1.mesh.common.BuildConfigProvider
import ru.tcynik.mymesh1.mesh.repository.AppWidgetUpdater
import ru.tcynik.mymesh1.mesh.repository.PlatformAnalytics
import ru.tcynik.mymesh1.data.mesh.repository.MeshConfigRepositoryImpl
import ru.tcynik.mymesh1.data.mesh.repository.MeshConnectionRepositoryImpl
import ru.tcynik.mymesh1.data.mesh.repository.MeshMessagingRepositoryImpl
import ru.tcynik.mymesh1.data.mesh.repository.MeshNetworkRepositoryImpl
import ru.tcynik.mymesh1.data.mesh.repository.MeshPacketLogRepositoryImpl
import ru.tcynik.mymesh1.domain.mesh.repository.MeshConfigRepository
import ru.tcynik.mymesh1.domain.mesh.repository.MeshConnectionRepository
import ru.tcynik.mymesh1.domain.mesh.repository.MeshMessagingRepository
import ru.tcynik.mymesh1.domain.mesh.repository.MeshNetworkRepository
import ru.tcynik.mymesh1.domain.mesh.repository.MeshPacketLogRepository
import ru.tcynik.mymesh1.domain.mesh.usecase.ConnectToMeshDeviceUseCase
import ru.tcynik.mymesh1.domain.mesh.usecase.DisconnectFromMeshUseCase
import ru.tcynik.mymesh1.domain.mesh.usecase.ObserveConnectionStatusUseCase
import ru.tcynik.mymesh1.domain.mesh.usecase.ObserveDeviceConfigUseCase
import ru.tcynik.mymesh1.domain.mesh.usecase.ObserveMeshNodesUseCase
import ru.tcynik.mymesh1.domain.mesh.usecase.ObserveMessagesUseCase
import ru.tcynik.mymesh1.domain.mesh.usecase.ObserveOurNodeUseCase
import ru.tcynik.mymesh1.domain.mesh.usecase.ObservePacketLogUseCase
import ru.tcynik.mymesh1.domain.mesh.usecase.ScanMeshDevicesUseCase
import ru.tcynik.mymesh1.domain.mesh.usecase.SendMeshMessageUseCase

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
}
