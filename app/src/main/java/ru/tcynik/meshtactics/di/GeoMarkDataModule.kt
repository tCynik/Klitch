package ru.tcynik.meshtactics.di

import org.koin.dsl.module
import ru.tcynik.meshtactics.data.marker.adapter.GeoMarkWaypointAdapter
import ru.tcynik.meshtactics.data.marker.repository.GeoMarkRepositoryImpl
import ru.tcynik.meshtactics.domain.marker.repository.GeoMarkRepository
import ru.tcynik.meshtactics.domain.marker.usecase.ObserveGeoMarksUseCase
import ru.tcynik.meshtactics.domain.marker.usecase.SendGeoMarkUseCase

val geoMarkDataModule = module {
    single { GeoMarkWaypointAdapter() }
    single<GeoMarkRepository> {
        GeoMarkRepositoryImpl(
            packetRepository = get(),
            commandSender    = get(),
            meshNetwork      = get(),
            adapter          = get(),
            geoMarkQueries   = get(),
        )
    }
    single { ObserveGeoMarksUseCase(get()) }
    single { SendGeoMarkUseCase(get()) }
}
