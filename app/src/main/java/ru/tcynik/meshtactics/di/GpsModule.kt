package ru.tcynik.meshtactics.di

import org.koin.android.ext.koin.androidApplication
import org.koin.dsl.binds
import org.koin.dsl.module
import ru.tcynik.meshtactics.data.gps.GpsRepositoryImpl
import ru.tcynik.meshtactics.data.gps.MeshLocationRepositoryAdapter
import ru.tcynik.meshtactics.domain.gps.repository.GpsLifecycleController
import ru.tcynik.meshtactics.domain.gps.repository.GpsRepository
import ru.tcynik.meshtactics.mesh.repository.LocationRepository

val gpsModule = module {
    single { GpsRepositoryImpl(context = androidApplication()) } binds arrayOf(
        GpsRepository::class,
        GpsLifecycleController::class,
    )

    single<LocationRepository>(override = true) {
        MeshLocationRepositoryAdapter(gpsRepository = get())
    }
}
