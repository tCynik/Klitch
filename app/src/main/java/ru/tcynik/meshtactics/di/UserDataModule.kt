package ru.tcynik.meshtactics.di

import org.koin.dsl.module
import ru.tcynik.meshtactics.data.local.user.LocalUserRepositoryImpl
import ru.tcynik.meshtactics.domain.user.repository.UserRepository

val userDataModule = module {
    single<UserRepository> { LocalUserRepositoryImpl() }
}
