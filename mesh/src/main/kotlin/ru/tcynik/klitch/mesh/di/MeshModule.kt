package ru.tcynik.klitch.mesh.di

import org.koin.core.annotation.Module
import org.koin.core.module.Module as KoinModule
import ru.tcynik.klitch.mesh.ble.di.CoreBleAndroidModule
import ru.tcynik.klitch.mesh.ble.di.CoreBleModule
import ru.tcynik.klitch.mesh.common.di.CoreCommonModule
import ru.tcynik.klitch.mesh.data.di.CoreDataAndroidModule
import ru.tcynik.klitch.mesh.data.di.CoreDataModule
import ru.tcynik.klitch.mesh.database.di.CoreDatabaseAndroidModule
import ru.tcynik.klitch.mesh.database.di.CoreDatabaseModule
import ru.tcynik.klitch.mesh.datastore.di.CoreDatastoreAndroidModule
import ru.tcynik.klitch.mesh.datastore.di.CoreDatastoreModule
import ru.tcynik.klitch.mesh.di.di.CoreDiModule
import ru.tcynik.klitch.mesh.network.di.CoreNetworkAndroidModule
import ru.tcynik.klitch.mesh.network.di.CoreNetworkModule
import ru.tcynik.klitch.mesh.prefs.di.CorePrefsAndroidModule
import ru.tcynik.klitch.mesh.prefs.di.CorePrefsModule
import ru.tcynik.klitch.mesh.repository.di.CoreRepositoryModule
import ru.tcynik.klitch.mesh.service.di.CoreServiceAndroidModule
import ru.tcynik.klitch.mesh.service.di.CoreServiceModule

/**
 * Single entry point for all mesh layer Koin modules.
 * Add this to your startKoin { modules(...) } call as: MeshKoinModule().module()
 */
@Module(
    includes = [
        CoreDiModule::class,
        CoreCommonModule::class,
        CoreDatabaseModule::class,
        CoreDatabaseAndroidModule::class,
        CoreDatastoreModule::class,
        CoreDatastoreAndroidModule::class,
        CoreBleModule::class,
        CoreBleAndroidModule::class,
        CoreNetworkModule::class,
        CoreNetworkAndroidModule::class,
        CoreRepositoryModule::class,
        CoreDataModule::class,
        CoreDataAndroidModule::class,
        CorePrefsModule::class,
        CorePrefsAndroidModule::class,
        CoreServiceModule::class,
        CoreServiceAndroidModule::class,
    ],
)
class MeshKoinModule

/** Pre-built Koin [KoinModule] for the mesh layer. Pass to `startKoin { modules(meshModule) }`. */
val meshModule: KoinModule by lazy { MeshKoinModule().module() }
