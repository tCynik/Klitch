package ru.tcynik.meshtactics.di

import org.koin.dsl.module
import ru.tcynik.meshtactics.data.channel.repository.FakeLogicalChannelRepository
import ru.tcynik.meshtactics.data.user.repository.FakeAppUserRepository
import ru.tcynik.meshtactics.domain.channel.repository.LogicalChannelRepository
import ru.tcynik.meshtactics.domain.channel.usecase.DeleteLogicalChannelUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.ObserveLogicalChannelsUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.SaveLogicalChannelUseCase
import ru.tcynik.meshtactics.domain.user.repository.AppUserRepository
import ru.tcynik.meshtactics.domain.user.usecase.ObserveAppUserUseCase
import ru.tcynik.meshtactics.domain.user.usecase.SaveAppUserUseCase

val userSettingsModule = module {
    single<AppUserRepository> { FakeAppUserRepository() }
    single<LogicalChannelRepository> { FakeLogicalChannelRepository() }

    single { ObserveAppUserUseCase(get()) }
    single { SaveAppUserUseCase(get()) }
    single { ObserveLogicalChannelsUseCase(get()) }
    single { SaveLogicalChannelUseCase(get()) }
    single { DeleteLogicalChannelUseCase(get()) }
}
