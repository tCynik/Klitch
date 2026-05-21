package ru.tcynik.meshtactics.di

import com.russhwolf.settings.Settings
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import ru.tcynik.meshtactics.data.local.map.ImportedMapRepositoryImpl
import ru.tcynik.meshtactics.data.local.map.KmlOverlayParser
import ru.tcynik.meshtactics.data.local.map.LastMapPositionRepositoryImpl
import ru.tcynik.meshtactics.data.map.TileCacheOkHttpConfigurator
import ru.tcynik.meshtactics.data.map.repository.MapTileRepositoryImpl
import ru.tcynik.meshtactics.domain.map.repository.ImportedMapRepository
import ru.tcynik.meshtactics.domain.map.repository.LastMapPositionRepository
import ru.tcynik.meshtactics.domain.map.repository.MapTileRepository
import ru.tcynik.meshtactics.domain.map.usecase.DeleteImportedMapUseCase
import ru.tcynik.meshtactics.domain.map.usecase.GetLastMapPositionUseCase
import ru.tcynik.meshtactics.domain.map.usecase.GetTileUrlUseCase
import ru.tcynik.meshtactics.domain.map.usecase.HideImportedMapUseCase
import ru.tcynik.meshtactics.domain.map.usecase.ImportMapFileUseCase
import ru.tcynik.meshtactics.domain.map.usecase.ObserveImportedMapsUseCase
import ru.tcynik.meshtactics.domain.map.usecase.ObserveNodeMarkersUseCase
import ru.tcynik.meshtactics.domain.map.usecase.ObserveSelectedOverlaysUseCase
import ru.tcynik.meshtactics.domain.map.usecase.SaveLastMapPositionUseCase
import ru.tcynik.meshtactics.domain.map.usecase.ToggleImportedMapSelectionUseCase
import ru.tcynik.meshtactics.domain.settings.repository.MapCacheSettingsRepository
import ru.tcynik.meshtactics.domain.settings.usecase.GetGeoMarkSizeLevelUseCase
import ru.tcynik.meshtactics.domain.settings.usecase.GetMarkerSizeLevelUseCase
import ru.tcynik.meshtactics.domain.settings.usecase.GetShowGeoMarkNamesUseCase
import ru.tcynik.meshtactics.domain.settings.usecase.GetTileCacheModeUseCase
import ru.tcynik.meshtactics.domain.settings.usecase.ObserveGeoMarkSizeLevelUseCase
import ru.tcynik.meshtactics.domain.settings.usecase.ObserveMarkerSizeLevelUseCase
import ru.tcynik.meshtactics.domain.settings.usecase.ObserveShowGeoMarkNamesUseCase
import ru.tcynik.meshtactics.domain.settings.usecase.ObserveTileCacheModeUseCase
import ru.tcynik.meshtactics.domain.settings.usecase.SetTileCacheModeUseCase

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
    single { GetGeoMarkSizeLevelUseCase(get()) }
    single { ObserveGeoMarkSizeLevelUseCase(get()) }
    single { GetShowGeoMarkNamesUseCase(get()) }
    single { ObserveShowGeoMarkNamesUseCase(get()) }

    // Tile cache — MapCacheSettingsRepository resolved from commonModule
    single { TileCacheOkHttpConfigurator(androidContext().cacheDir, get<MapCacheSettingsRepository>().getTileCacheMode()) }
    single { GetTileCacheModeUseCase(get()) }
    single { SetTileCacheModeUseCase(get()) }
    single { ObserveTileCacheModeUseCase(get()) }

    // Imported map overlays (KMZ/KML via SAF)
    single { KmlOverlayParser(androidContext(), get()) }
    single<ImportedMapRepository> { ImportedMapRepositoryImpl(androidContext(), get(), get()) }
    single { ObserveImportedMapsUseCase(get()) }
    single { ObserveSelectedOverlaysUseCase(get()) }
    single { ImportMapFileUseCase(get()) }
    single { HideImportedMapUseCase(get()) }
    single { DeleteImportedMapUseCase(get()) }
    single { ToggleImportedMapSelectionUseCase(get()) }
}
