package ru.tcynik.meshtactics.di

import org.koin.dsl.module
import ru.tcynik.meshtactics.data.local.group.LocalGroupRepositoryImpl
import ru.tcynik.meshtactics.domain.group.repository.GroupRepository

val markerDataModule = module {
    single<GroupRepository> { LocalGroupRepositoryImpl() }
}
