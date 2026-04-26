package ru.tcynik.meshtactics.di

import org.koin.dsl.module
import ru.tcynik.meshtactics.data.marker.adapter.GeoMarkWaypointAdapter
import ru.tcynik.meshtactics.data.marker.repository.GeoMarkRepositoryImpl
import ru.tcynik.meshtactics.domain.marker.repository.GeoMarkRepository
import ru.tcynik.meshtactics.domain.marker.usecase.DeleteExpiredGeoMarksUseCase
import ru.tcynik.meshtactics.domain.marker.usecase.IngestReceivedGeoMarksUseCase
import ru.tcynik.meshtactics.domain.marker.usecase.ObserveGeoMarksUseCase
import ru.tcynik.meshtactics.domain.marker.usecase.SendGeoMarkUseCase

val geoMarkDataModule = module {
    single { GeoMarkWaypointAdapter() }
    single<GeoMarkRepository> {
        GeoMarkRepositoryImpl(
            commandSender       = get(),
            meshNetwork         = get(),
            channelRepository   = get(),
            channelSlotResolver = get(),
            adapter             = get(),
            geoMarkQueries      = get(),
        )
    }
    single { ObserveGeoMarksUseCase(get()) }
    single { SendGeoMarkUseCase(get()) }
    single { DeleteExpiredGeoMarksUseCase(get()) }
    single {
        IngestReceivedGeoMarksUseCase(
            packetRepository    = get(),
            channelRepository   = get(),
            geoMarkRepository   = get(),
            adapter             = get(),
            channelSlotResolver = get(),
        )
    }
}
