package ru.tcynik.klitch.di

import org.koin.android.ext.koin.androidApplication
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module
import org.maplibre.compose.location.LocationProvider
import ru.tcynik.klitch.data.location.repository.GpsStatusRepositoryImpl
import ru.tcynik.klitch.di.location.AppLocationProvider
import ru.tcynik.klitch.domain.location.repository.GpsStatusRepository
import ru.tcynik.klitch.domain.location.usecase.ObserveGpsStatusUseCase

val locationDomainModule = module {
    single<GpsStatusRepository> { GpsStatusRepositoryImpl(context = androidApplication()) }
    single<LocationProvider> { AppLocationProvider(gpsRepository = get()) }
    factoryOf(::ObserveGpsStatusUseCase)
}
