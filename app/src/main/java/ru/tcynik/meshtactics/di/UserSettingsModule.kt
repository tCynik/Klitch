package ru.tcynik.meshtactics.di

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module
import ru.tcynik.meshtactics.data.channel.repository.LogicalChannelRepositoryImpl
import ru.tcynik.meshtactics.data.user.repository.AppUserRepositoryImpl
import ru.tcynik.meshtactics.domain.channel.repository.LogicalChannelRepository
import ru.tcynik.meshtactics.domain.channel.usecase.DeleteLogicalChannelUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.ObserveLogicalChannelsUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.ObserveNodeChannelsUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.ResolveChannelSlotUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.SaveLogicalChannelUseCase
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
    single<LogicalChannelRepository> { LogicalChannelRepositoryImpl(get()) }

    single { ObserveAppUserUseCase(get()) }
    single { SaveAppUserUseCase(get()) }
    single { ObserveLogicalChannelsUseCase(get()) }
    single { SaveLogicalChannelUseCase(get()) }
    single { DeleteLogicalChannelUseCase(get()) }
    single { ObserveNodeChannelsUseCase(get()) }
    single { ResolveChannelSlotUseCase() }
}
