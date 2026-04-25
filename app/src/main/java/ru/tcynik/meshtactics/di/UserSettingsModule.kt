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
import ru.tcynik.meshtactics.data.user.repository.AppUserRepositoryImpl
import ru.tcynik.meshtactics.domain.channel.ChannelSlotResolver
import ru.tcynik.meshtactics.domain.channel.repository.ContourRepository
import ru.tcynik.meshtactics.domain.channel.usecase.DeleteContourUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.ObserveContoursUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.ObserveNodeChannelsUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.ResolveChannelSlotUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.SaveContourUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.SetContourActiveUseCase
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

    single<AppUserRepository> { AppUserRepositoryImpl(get(named("UserDataStore"))) }
    single<ContourRepository> { ContourRepositoryImpl(get()) }

    single { ObserveAppUserUseCase(get()) }
    single { SaveAppUserUseCase(get()) }
    single { ObserveContoursUseCase(get()) }
    single { SaveContourUseCase(get()) }
    single { DeleteContourUseCase(get()) }
    single { SetContourActiveUseCase(get()) }
    single { ObserveNodeChannelsUseCase(get()) }
    single { ResolveChannelSlotUseCase() }
    single<ChannelSlotResolver> { ChannelSlotResolverImpl(get()) }
}
