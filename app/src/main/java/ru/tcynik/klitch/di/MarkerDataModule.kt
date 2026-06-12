package ru.tcynik.klitch.di

import org.koin.dsl.module
import ru.tcynik.klitch.data.local.group.LocalGroupRepositoryImpl
import ru.tcynik.klitch.domain.group.repository.GroupRepository

val markerDataModule = module {
    single<GroupRepository> { LocalGroupRepositoryImpl() }
}
