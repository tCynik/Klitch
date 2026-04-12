package ru.tcynik.meshtactics.di

import com.russhwolf.settings.Settings
import org.koin.dsl.module
import ru.tcynik.meshtactics.data.local.map.LastMapPositionRepositoryImpl
import ru.tcynik.meshtactics.data.map.repository.MapTileRepositoryImpl
import ru.tcynik.meshtactics.domain.map.repository.LastMapPositionRepository
import ru.tcynik.meshtactics.domain.map.repository.MapTileRepository
import ru.tcynik.meshtactics.domain.map.usecase.GetLastMapPositionUseCase
import ru.tcynik.meshtactics.domain.map.usecase.GetTileUrlUseCase
import ru.tcynik.meshtactics.domain.map.usecase.ObserveNodeMarkersUseCase
import ru.tcynik.meshtactics.domain.map.usecase.SaveLastMapPositionUseCase
import ru.tcynik.meshtactics.domain.settings.usecase.GetMarkerSizeLevelUseCase
import ru.tcynik.meshtactics.domain.settings.usecase.ObserveMarkerSizeLevelUseCase

val mapDataModule = module {
    // MVP: single hardcoded tile source. Beta 1.0: replace with multi-source implementation.
    single<MapTileRepository> { MapTileRepositoryImpl() }
    single { GetTileUrlUseCase(get()) }

    // Camera position persistence — Settings registered in androidModule
    single<LastMapPositionRepository> { LastMapPositionRepositoryImpl(get<Settings>()) }
    single { GetLastMapPositionUseCase(get()) }
    single { SaveLastMapPositionUseCase(get()) }

    // Node markers — MeshNetworkRepository resolved from meshDataModule
    single { ObserveNodeMarkersUseCase(get()) }

    // Marker size level — MarkerSettingsRepository resolved from commonModule
    single { GetMarkerSizeLevelUseCase(get()) }
    single { ObserveMarkerSizeLevelUseCase(get()) }
}
