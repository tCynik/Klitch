package ru.tcynik.meshtactics

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import org.maplibre.android.MapLibre
import org.maplibre.android.module.http.HttpRequestUtil
import org.maplibre.android.offline.OfflineManager
import ru.tcynik.meshtactics.data.map.TileCacheOkHttpConfigurator
import ru.tcynik.meshtactics.domain.channel.repository.ContourRepository
import ru.tcynik.meshtactics.data.notification.EmergencyNodeNotificationFilter
import ru.tcynik.meshtactics.domain.settings.repository.MapCacheSettingsRepository
import ru.tcynik.meshtactics.di.androidModule
import ru.tcynik.meshtactics.di.chatDataModule
import ru.tcynik.meshtactics.di.loggerModule
import ru.tcynik.meshtactics.domain.logger.Logger
import ru.tcynik.meshtactics.di.commonModule
import ru.tcynik.meshtactics.di.gpsModule
import ru.tcynik.meshtactics.di.locationDomainModule
import ru.tcynik.meshtactics.di.mapDataModule
import ru.tcynik.meshtactics.di.geoMarkDataModule
import ru.tcynik.meshtactics.di.markerDataModule
import ru.tcynik.meshtactics.di.meshDataModule
import ru.tcynik.meshtactics.di.orientationModule
import ru.tcynik.meshtactics.di.presentationModule
import ru.tcynik.meshtactics.di.userDataModule
import ru.tcynik.meshtactics.di.userSettingsModule
import ru.tcynik.meshtactics.mesh.common.ContextServices
import ru.tcynik.meshtactics.mesh.di.meshModule
import ru.tcynik.meshtactics.mesh.service.MeshServiceOrchestrator

class MyMeshApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        ContextServices.app = this
        startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@MyMeshApplication)
            allowOverride(true)
            modules(
                loggerModule,
                commonModule,
                androidModule,
                meshModule,
                gpsModule,
                meshDataModule,
                chatDataModule,
                mapDataModule,
                markerDataModule,
                geoMarkDataModule,
                userDataModule,
                userSettingsModule,
                locationDomainModule,
                orientationModule,
                presentationModule,
            )
        }

        val logger = GlobalContext.get().get<Logger>()
        MapLibre.getInstance(this)
        val configurator = GlobalContext.get().get<TileCacheOkHttpConfigurator>()
        runCatching {
            HttpRequestUtil.setOkHttpClient(configurator.client)
        }.onFailure { error ->
            // Do not crash app startup if MapLibre HTTP internals fail early initialization.
            // Fallback: continue with MapLibre default HTTP client for this session.
            logger.e("App", "Failed to set custom MapLibre OkHttp client", error)
        }
        OfflineManager.getInstance(this).setMaximumAmbientCacheSize(100L * 1024 * 1024, null)
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            GlobalContext.get().get<MapCacheSettingsRepository>().tileCacheModeFlow.collect { mode ->
                configurator.updateMode(mode)
            }
        }

        GlobalContext.get().get<EmergencyNodeNotificationFilter>()
        GlobalContext.get().get<MeshServiceOrchestrator>().start()
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            GlobalContext.get().get<ContourRepository>().seedDefaultsIfAbsent()
        }
    }
}
