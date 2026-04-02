package ru.tcynik.mymesh1.mesh.di

import org.koin.core.annotation.Module
import org.koin.core.module.Module as KoinModule
import ru.tcynik.mymesh1.mesh.ble.di.CoreBleAndroidModule
import ru.tcynik.mymesh1.mesh.ble.di.CoreBleModule
import ru.tcynik.mymesh1.mesh.common.di.CoreCommonModule
import ru.tcynik.mymesh1.mesh.data.di.CoreDataAndroidModule
import ru.tcynik.mymesh1.mesh.data.di.CoreDataModule
import ru.tcynik.mymesh1.mesh.database.di.CoreDatabaseAndroidModule
import ru.tcynik.mymesh1.mesh.database.di.CoreDatabaseModule
import ru.tcynik.mymesh1.mesh.datastore.di.CoreDatastoreAndroidModule
import ru.tcynik.mymesh1.mesh.datastore.di.CoreDatastoreModule
import ru.tcynik.mymesh1.mesh.di.di.CoreDiModule
import ru.tcynik.mymesh1.mesh.network.di.CoreNetworkAndroidModule
import ru.tcynik.mymesh1.mesh.network.di.CoreNetworkModule
import ru.tcynik.mymesh1.mesh.prefs.di.CorePrefsAndroidModule
import ru.tcynik.mymesh1.mesh.prefs.di.CorePrefsModule
import ru.tcynik.mymesh1.mesh.repository.di.CoreRepositoryModule
import ru.tcynik.mymesh1.mesh.service.di.CoreServiceAndroidModule
import ru.tcynik.mymesh1.mesh.service.di.CoreServiceModule

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
