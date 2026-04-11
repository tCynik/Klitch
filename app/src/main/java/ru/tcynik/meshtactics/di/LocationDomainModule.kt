package ru.tcynik.meshtactics.di

import org.koin.android.ext.koin.androidApplication
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module
import org.maplibre.compose.location.LocationProvider
import ru.tcynik.meshtactics.data.location.repository.GpsStatusRepositoryImpl
import ru.tcynik.meshtactics.di.location.AppLocationProvider
import ru.tcynik.meshtactics.domain.location.repository.GpsStatusRepository
import ru.tcynik.meshtactics.domain.location.usecase.ObserveGpsStatusUseCase

val locationDomainModule = module {
    single<GpsStatusRepository> { GpsStatusRepositoryImpl(context = androidApplication()) }
    single<LocationProvider> { AppLocationProvider(gpsRepository = get()) }
    factoryOf(::ObserveGpsStatusUseCase)
}
