package ru.tcynik.klitch.di

import org.koin.dsl.module
import ru.tcynik.klitch.data.local.user.LocalUserRepositoryImpl
import ru.tcynik.klitch.domain.user.repository.UserRepository

val userDataModule = module {
    single<UserRepository> { LocalUserRepositoryImpl() }
}
