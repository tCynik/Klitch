package ru.tcynik.klitch.di

import com.russhwolf.settings.Settings
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import ru.tcynik.klitch.data.local.map.ImportedMapRepositoryImpl
import ru.tcynik.klitch.data.local.map.KmlOverlayParser
import ru.tcynik.klitch.data.local.map.LastMapPositionRepositoryImpl
import ru.tcynik.klitch.data.map.TileCacheOkHttpConfigurator
import ru.tcynik.klitch.data.map.repository.MapTileRepositoryImpl
import ru.tcynik.klitch.domain.map.repository.ImportedMapRepository
import ru.tcynik.klitch.domain.map.repository.LastMapPositionRepository
import ru.tcynik.klitch.domain.map.repository.MapTileRepository
import ru.tcynik.klitch.domain.map.usecase.DeleteImportedMapUseCase
import ru.tcynik.klitch.domain.map.usecase.GetLastMapPositionUseCase
import ru.tcynik.klitch.domain.map.usecase.GetTileUrlUseCase
import ru.tcynik.klitch.domain.map.usecase.HideImportedMapUseCase
import ru.tcynik.klitch.domain.map.usecase.ImportMapFileUseCase
import ru.tcynik.klitch.domain.map.usecase.ObserveImportedMapsUseCase
import ru.tcynik.klitch.domain.map.usecase.ObserveNodeMarkersUseCase
import ru.tcynik.klitch.domain.map.usecase.ObserveSelectedOverlaysUseCase
import ru.tcynik.klitch.domain.map.usecase.SaveLastMapPositionUseCase
import ru.tcynik.klitch.domain.map.usecase.ToggleImportedMapSelectionUseCase
import ru.tcynik.klitch.domain.settings.repository.MapCacheSettingsRepository
import ru.tcynik.klitch.domain.settings.usecase.GetGeoMarkSizeLevelUseCase
import ru.tcynik.klitch.domain.settings.usecase.GetMarkerSizeLevelUseCase
import ru.tcynik.klitch.domain.settings.usecase.GetShowGeoMarkNamesUseCase
import ru.tcynik.klitch.domain.settings.usecase.GetScreenOrientationLockedUseCase
import ru.tcynik.klitch.domain.settings.usecase.GetScreenOrientationModeUseCase
import ru.tcynik.klitch.domain.settings.usecase.GetTileCacheModeUseCase
import ru.tcynik.klitch.domain.settings.usecase.ObserveNetworkEnabledUseCase
import ru.tcynik.klitch.domain.settings.usecase.ObserveGeoMarkSizeLevelUseCase
import ru.tcynik.klitch.domain.settings.usecase.ObserveScreenOrientationSettingsUseCase
import ru.tcynik.klitch.domain.settings.usecase.ObserveMarkerSizeLevelUseCase
import ru.tcynik.klitch.domain.settings.usecase.ObserveShowGeoMarkNamesUseCase
import ru.tcynik.klitch.domain.settings.usecase.ObserveTileCacheModeUseCase
import ru.tcynik.klitch.domain.settings.usecase.SetNetworkEnabledUseCase
import ru.tcynik.klitch.domain.settings.usecase.SetScreenOrientationLockedUseCase
import ru.tcynik.klitch.domain.settings.usecase.SetScreenOrientationModeUseCase
import ru.tcynik.klitch.domain.settings.usecase.SetTileCacheModeUseCase

val mapDataModule = module {
    // MVP: single hardcoded tile source. Beta 1.0: replace with multi-source implementation.
    single<MapTileRepository> { MapTileRepositoryImpl() }
    single { GetTileUrlUseCase(get()) }

    // Camera position persistence — Settings registered in androidModule
    single<LastMapPositionRepository> { LastMapPositionRepositoryImpl(get<Settings>()) }
    single { GetLastMapPositionUseCase(get()) }
    single { SaveLastMapPositionUseCase(get()) }

    // Node markers — MeshNetworkRepository resolved from meshDataModule
    single { ObserveNodeMarkersUseCase(get(), get(), get(), get(), get()) }

    // Marker size level — MarkerSettingsRepository resolved from commonModule
    single { GetMarkerSizeLevelUseCase(get()) }
    single { ObserveMarkerSizeLevelUseCase(get()) }
    single { GetGeoMarkSizeLevelUseCase(get()) }
    single { ObserveGeoMarkSizeLevelUseCase(get()) }
    single { GetShowGeoMarkNamesUseCase(get()) }
    single { ObserveShowGeoMarkNamesUseCase(get()) }

    // Network enabled — NetworkSettingsRepository resolved from commonModule
    single { ObserveNetworkEnabledUseCase(get()) }
    single { SetNetworkEnabledUseCase(get()) }

    // Tile cache — MapCacheSettingsRepository resolved from commonModule
    single { TileCacheOkHttpConfigurator(androidContext().cacheDir, get<MapCacheSettingsRepository>().getTileCacheMode()) }
    single { GetTileCacheModeUseCase(get()) }
    single { SetTileCacheModeUseCase(get()) }
    single { ObserveTileCacheModeUseCase(get()) }

    // Screen orientation lock — ScreenOrientationRepository resolved from commonModule
    single { GetScreenOrientationLockedUseCase(get()) }
    single { SetScreenOrientationLockedUseCase(get()) }
    single { GetScreenOrientationModeUseCase(get()) }
    single { SetScreenOrientationModeUseCase(get()) }
    single { ObserveScreenOrientationSettingsUseCase(get()) }

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
