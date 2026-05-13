package ru.tcynik.meshtactics.di

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module
import ru.tcynik.meshtactics.data.channel.ChannelSlotResolverImpl
import ru.tcynik.meshtactics.data.channel.repository.ContourRepositoryImpl
import ru.tcynik.meshtactics.data.mesh.repository.GpsBroadcastSettingsRepositoryImpl
import ru.tcynik.meshtactics.data.channel.repository.ContourSyncStateRepositoryImpl
import ru.tcynik.meshtactics.data.emergency.EmergencyPositionBroadcastRepositoryImpl
import ru.tcynik.meshtactics.data.user.repository.AppUserRepositoryImpl
import ru.tcynik.meshtactics.domain.channel.ChannelSlotResolver
import ru.tcynik.meshtactics.domain.channel.repository.ContourRepository
import ru.tcynik.meshtactics.domain.channel.repository.ContourSyncStateRepository
import ru.tcynik.meshtactics.domain.channel.usecase.CheckNodeSyncUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.DeleteContourUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.ObserveContoursUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.ObserveNodeChannelsUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.ResolveChannelSlotUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.SaveContourUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.SetContourActiveUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.SyncContoursOnConnectUseCase
import ru.tcynik.meshtactics.domain.emergency.repository.EmergencyPositionBroadcastRepository
import ru.tcynik.meshtactics.domain.mesh.repository.GpsBroadcastSettingsRepository
import ru.tcynik.meshtactics.domain.mesh.usecase.ObserveGpsBroadcastEnabledUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.SetGpsBroadcastEnabledUseCase
import ru.tcynik.meshtactics.domain.emergency.usecase.CancelEmergencyUseCase
import ru.tcynik.meshtactics.domain.emergency.usecase.ObserveEmergencyModeUseCase
import ru.tcynik.meshtactics.domain.emergency.usecase.TriggerEmergencyUseCase
import ru.tcynik.meshtactics.domain.user.repository.AppUserRepository
import ru.tcynik.meshtactics.domain.user.usecase.ObserveAppUserUseCase
import ru.tcynik.meshtactics.domain.user.usecase.SaveAppUserUseCase

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
    single { ObserveNodeChannelsUseCase(get()) }
    single { ResolveChannelSlotUseCase() }
    single { SyncContoursOnConnectUseCase(get(), get(), get(), get(), get(), get(), get()) }
    single { CheckNodeSyncUseCase(get(), get(), get(), get()) }
    single<ContourSyncStateRepository> { ContourSyncStateRepositoryImpl() }
    single<ChannelSlotResolver> { ChannelSlotResolverImpl(get()) }

    single<EmergencyPositionBroadcastRepository>(createdAtStart = true) {
        EmergencyPositionBroadcastRepositoryImpl(get(), get(), get())
    }
    single { ObserveEmergencyModeUseCase(get()) }
    single { TriggerEmergencyUseCase(get(), get(), get(), get(), get()) }
    single { CancelEmergencyUseCase(get(), get(), get(), get()) }
}
