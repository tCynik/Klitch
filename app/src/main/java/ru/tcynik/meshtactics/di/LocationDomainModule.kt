package ru.tcynik.meshtactics.di

import org.koin.dsl.module
import org.maplibre.compose.location.LocationProvider
import ru.tcynik.meshtactics.di.location.AppLocationProvider

val locationDomainModule = module {
    single<LocationProvider> { AppLocationProvider(gpsRepository = get()) }
}
