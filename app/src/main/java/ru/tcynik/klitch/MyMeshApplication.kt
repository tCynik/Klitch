package ru.tcynik.klitch

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
import ru.tcynik.klitch.data.map.TileCacheOkHttpConfigurator
import ru.tcynik.klitch.domain.channel.repository.ContourRepository
import ru.tcynik.klitch.data.mesh.BackgroundPositionSession
import ru.tcynik.klitch.data.mesh.MeshWakeLockManager
import ru.tcynik.klitch.data.mesh.OnConnectPositionSender
import ru.tcynik.klitch.data.notification.EmergencyNodeNotificationFilter
import ru.tcynik.klitch.domain.settings.repository.MapCacheSettingsRepository
import ru.tcynik.klitch.di.androidModule
import ru.tcynik.klitch.di.chatDataModule
import ru.tcynik.klitch.di.loggerModule
import ru.tcynik.klitch.domain.logger.Logger
import ru.tcynik.klitch.di.commonModule
import ru.tcynik.klitch.di.gpsModule
import ru.tcynik.klitch.di.locationDomainModule
import ru.tcynik.klitch.di.mapDataModule
import ru.tcynik.klitch.di.geoMarkDataModule
import ru.tcynik.klitch.di.markerDataModule
import ru.tcynik.klitch.di.meshDataModule
import ru.tcynik.klitch.di.orientationModule
import ru.tcynik.klitch.di.presentationModule
import ru.tcynik.klitch.di.userDataModule
import ru.tcynik.klitch.di.trackDataModule
import ru.tcynik.klitch.di.userSettingsModule
import ru.tcynik.klitch.mesh.common.ContextServices
import ru.tcynik.klitch.mesh.di.meshModule
import ru.tcynik.klitch.mesh.service.MeshServiceOrchestrator

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
                trackDataModule,
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
        GlobalContext.get().get<OnConnectPositionSender>()
        GlobalContext.get().get<BackgroundPositionSession>()
        GlobalContext.get().get<MeshWakeLockManager>()
        GlobalContext.get().get<MeshServiceOrchestrator>().start()
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            GlobalContext.get().get<ContourRepository>().seedDefaultsIfAbsent()
        }
    }
}
