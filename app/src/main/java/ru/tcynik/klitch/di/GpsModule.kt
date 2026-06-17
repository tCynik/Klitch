package ru.tcynik.klitch.di

import org.koin.android.ext.koin.androidApplication
import org.koin.dsl.binds
import org.koin.dsl.module
import ru.tcynik.klitch.data.gps.GpsRepositoryImpl
import ru.tcynik.klitch.data.gps.MeshLocationRepositoryAdapter
import ru.tcynik.klitch.data.service.GpsServiceControllerImpl
import ru.tcynik.klitch.domain.gps.repository.GpsLifecycleController
import ru.tcynik.klitch.domain.gps.repository.GpsRepository
import ru.tcynik.klitch.domain.gps.usecase.ObserveGpsLocationUseCase
import ru.tcynik.klitch.domain.service.GpsServiceController
import ru.tcynik.klitch.mesh.repository.LocationRepository

val gpsModule = module {
    single { GpsRepositoryImpl(context = androidApplication()) } binds arrayOf(
        GpsRepository::class,
        GpsLifecycleController::class,
    )

    single<LocationRepository> {
        MeshLocationRepositoryAdapter(gpsRepository = get())
    }

    single { ObserveGpsLocationUseCase(get()) }

    single<GpsServiceController>(createdAtStart = true) {
        GpsServiceControllerImpl(trackRecordingRepository = get())
    }
}
