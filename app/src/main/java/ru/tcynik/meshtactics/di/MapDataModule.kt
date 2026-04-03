package ru.tcynik.meshtactics.di

import org.koin.dsl.module
import ru.tcynik.meshtactics.data.map.repository.MapTileRepositoryImpl
import ru.tcynik.meshtactics.domain.map.repository.MapTileRepository
import ru.tcynik.meshtactics.domain.map.usecase.GetTileUrlUseCase

val mapDataModule = module {
    // MVP: single hardcoded tile source. Beta 1.0: replace with multi-source implementation.
    single<MapTileRepository> { MapTileRepositoryImpl() }
    single { GetTileUrlUseCase(get()) }
}
