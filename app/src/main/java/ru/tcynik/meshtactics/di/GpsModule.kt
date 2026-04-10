package ru.tcynik.meshtactics.di

import org.koin.android.ext.koin.androidApplication
import org.koin.dsl.bind
import org.koin.dsl.module
import ru.tcynik.meshtactics.data.gps.GpsRepositoryImpl
import ru.tcynik.meshtactics.data.gps.MeshLocationRepositoryAdapter
import ru.tcynik.meshtactics.domain.gps.repository.GpsRepository
import ru.tcynik.meshtactics.mesh.repository.LocationRepository

val gpsModule = module {
    // GpsRepositoryImpl регистрируется и как конкретный класс (для GpsService.inject()),
    // и как GpsRepository интерфейс (для AppLocationProvider и MeshLocationRepositoryAdapter)
    single { GpsRepositoryImpl(context = androidApplication()) } bind GpsRepository::class

    single<LocationRepository> {
        MeshLocationRepositoryAdapter(gpsRepository = get())
    }
}
