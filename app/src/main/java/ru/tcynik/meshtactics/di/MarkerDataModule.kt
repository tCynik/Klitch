package ru.tcynik.meshtactics.di

import org.koin.dsl.module
import ru.tcynik.meshtactics.data.local.group.LocalGroupRepositoryImpl
import ru.tcynik.meshtactics.data.local.marker.LocalMarkerRepositoryImpl
import ru.tcynik.meshtactics.domain.group.repository.GroupRepository
import ru.tcynik.meshtactics.domain.marker.repository.MarkerRepository

val markerDataModule = module {
    single<MarkerRepository> { LocalMarkerRepositoryImpl() }
    single<GroupRepository> { LocalGroupRepositoryImpl() }
}
