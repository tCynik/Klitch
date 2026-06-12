package ru.tcynik.klitch.di

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module
import ru.tcynik.klitch.data.channel.ChannelSlotResolverImpl
import ru.tcynik.klitch.data.channel.repository.ContourRepositoryImpl
import ru.tcynik.klitch.data.mesh.repository.GpsBroadcastSettingsRepositoryImpl
import ru.tcynik.klitch.data.channel.repository.ContourSyncStateRepositoryImpl
import ru.tcynik.klitch.data.emergency.EmergencyPositionBroadcastRepositoryImpl
import ru.tcynik.klitch.data.user.repository.AppUserRepositoryImpl
import ru.tcynik.klitch.domain.channel.ChannelSlotResolver
import ru.tcynik.klitch.domain.channel.repository.ContourRepository
import ru.tcynik.klitch.domain.channel.repository.ContourSyncStateRepository
import ru.tcynik.klitch.domain.channel.usecase.CheckNodeSyncUseCase
import ru.tcynik.klitch.domain.channel.usecase.DeleteContourUseCase
import ru.tcynik.klitch.domain.channel.usecase.ObserveContoursUseCase
import ru.tcynik.klitch.domain.channel.usecase.ObserveNodeChannelsUseCase
import ru.tcynik.klitch.domain.channel.usecase.ApplyDeliveryPolicyUseCase
import ru.tcynik.klitch.domain.channel.usecase.ResolveChannelSlotUseCase
import ru.tcynik.klitch.domain.channel.usecase.ResolveContourFromSlotUseCase
import ru.tcynik.klitch.domain.channel.usecase.SaveContourUseCase
import ru.tcynik.klitch.domain.channel.usecase.ActivateExclusiveContourUseCase
import ru.tcynik.klitch.domain.channel.usecase.SetContourActiveUseCase
import ru.tcynik.klitch.domain.channel.usecase.SetPrimaryContourUseCase
import ru.tcynik.klitch.domain.channel.usecase.ConfirmChannelSyncUseCase
import ru.tcynik.klitch.domain.channel.usecase.SyncContoursOnConnectUseCase
import ru.tcynik.klitch.domain.emergency.repository.EmergencyPositionBroadcastRepository
import ru.tcynik.klitch.domain.mesh.repository.GpsBroadcastSettingsRepository
import ru.tcynik.klitch.domain.mesh.usecase.ObserveGpsBroadcastEnabledUseCase
import ru.tcynik.klitch.domain.mesh.usecase.SetGpsBroadcastEnabledUseCase
import ru.tcynik.klitch.domain.emergency.usecase.CancelEmergencyUseCase
import ru.tcynik.klitch.domain.emergency.usecase.ObserveEmergencyModeUseCase
import ru.tcynik.klitch.domain.emergency.usecase.TriggerEmergencyUseCase
import ru.tcynik.klitch.domain.user.repository.AppUserRepository
import ru.tcynik.klitch.domain.user.usecase.ObserveAppUserUseCase
import ru.tcynik.klitch.domain.user.usecase.SaveAppUserUseCase

val userSettingsModule = module {

    single(named("UserDataStore")) {
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            produceFile = { androidContext().preferencesDataStoreFile("user_ds") },
        )
    }

    single(named("ContourDataStore")) {
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            produceFile = { androidContext().preferencesDataStoreFile("contour_ds") },
        )
    }

    single<AppUserRepository> { AppUserRepositoryImpl(get(named("UserDataStore"))) }
    single<GpsBroadcastSettingsRepository> { GpsBroadcastSettingsRepositoryImpl(get(named("UserDataStore"))) }
    single<ContourRepository> { ContourRepositoryImpl(get(), get(named("ContourDataStore"))) }

    single { ObserveAppUserUseCase(get()) }
    single { SaveAppUserUseCase(get()) }
    single { ObserveGpsBroadcastEnabledUseCase(get()) }
    single { SetGpsBroadcastEnabledUseCase(get()) }
    single { ObserveContoursUseCase(get()) }
    single { SaveContourUseCase(get()) }
    single { DeleteContourUseCase(get()) }
    single { SetContourActiveUseCase(get()) }
    single { SetPrimaryContourUseCase(get(), get()) }
    single { ActivateExclusiveContourUseCase(get(), get()) }
    single { ObserveNodeChannelsUseCase(get()) }
    single { ResolveChannelSlotUseCase() }
    single { ResolveContourFromSlotUseCase() }
    single { ApplyDeliveryPolicyUseCase() }
    single {
        SyncContoursOnConnectUseCase(
            contourRepository = get(),
            observeContours = get(),
            observeNodeChannels = get(),
            beginSettingsEdit = get(),
            commitSettingsEdit = get(),
            writeChannel = get(),
            resolveSlot = get(),
            writeOwner = get(),
            observeAppUser = get(),
            observeDeviceConfig = get(),
            prepareNodeForAppDrivenBroadcast = get(),
            disableNodePositionBroadcast = get(),
            observeGpsBroadcastEnabled = get(),
            observeEmergencyMode = get(),
            getPositionBroadcastSecs = get(),
            isPositionSmartBroadcastEnabled = get(),
            logger = get(),
        )
    }
    single {
        ConfirmChannelSyncUseCase(
            syncContoursOnConnect = get(),
            rebootNode = get(),
            reconnectAfterNodeReboot = get(),
            reconnectViaBleScan = get(),
            requestDeviceConfig = get(),
            rebootStateRepository = get(),
            syncStateRepository = get(),
            logger = get(),
        )
    }
    single {
        CheckNodeSyncUseCase(
            observeContours = get(),
            observeNodeChannels = get(),
            observeAppUser = get(),
            observeDeviceConfig = get(),
            contourRepository = get(),
            observeGpsBroadcastEnabled = get(),
            observeEmergencyMode = get(),
            getPositionBroadcastSecs = get(),
            isPositionSmartBroadcastEnabled = get(),
            logger = get(),
        )
    }
    single<ContourSyncStateRepository> { ContourSyncStateRepositoryImpl() }
    single<ChannelSlotResolver> { ChannelSlotResolverImpl(get()) }

    single<EmergencyPositionBroadcastRepository>(createdAtStart = true) {
        EmergencyPositionBroadcastRepositoryImpl(get(), get(), get())
    }
    single { ObserveEmergencyModeUseCase(get()) }
    single { TriggerEmergencyUseCase(get(), get(), get(), get(), get(), get()) }
    single { CancelEmergencyUseCase(get(), get(), get(), get(), get()) }
}
