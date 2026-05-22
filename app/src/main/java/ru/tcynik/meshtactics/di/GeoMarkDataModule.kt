package ru.tcynik.meshtactics.di

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module
import ru.tcynik.meshtactics.data.markprefs.GeoMarkPreferencesRepositoryImpl
import ru.tcynik.meshtactics.data.markprefs.GeoMarkPrefsDataSource
import ru.tcynik.meshtactics.data.marker.adapter.GeoMarkWaypointAdapter
import ru.tcynik.meshtactics.data.marker.repository.GeoMarkRepositoryImpl
import ru.tcynik.meshtactics.domain.marker.repository.GeoMarkPreferencesRepository
import ru.tcynik.meshtactics.domain.marker.repository.GeoMarkRepository
import ru.tcynik.meshtactics.domain.marker.usecase.AutoExpireGeoMarksUseCase
import ru.tcynik.meshtactics.domain.marker.usecase.DeleteExpiredGeoMarksUseCase
import ru.tcynik.meshtactics.domain.marker.usecase.DeleteGeoMarksUseCase
import ru.tcynik.meshtactics.domain.marker.usecase.IngestReceivedGeoMarksUseCase
import ru.tcynik.meshtactics.domain.marker.usecase.ObserveGeoMarksUseCase
import ru.tcynik.meshtactics.domain.marker.usecase.SendGeoMarkUseCase
import ru.tcynik.meshtactics.domain.marker.usecase.ToggleGeoMarkVisibilityUseCase

val geoMarkDataModule = module {
    single<androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>>(named("GeoMarkPrefsDataStore")) {
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            produceFile = { androidContext().preferencesDataStoreFile("geomark_prefs_ds") },
        )
    }

    single { GeoMarkPrefsDataSource(get(named("GeoMarkPrefsDataStore"))) }
    single<GeoMarkPreferencesRepository> { GeoMarkPreferencesRepositoryImpl(get()) }

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
    single { ToggleGeoMarkVisibilityUseCase(get()) }
    single { DeleteGeoMarksUseCase(get()) }
    single { SendGeoMarkUseCase(get()) }
    single { DeleteExpiredGeoMarksUseCase(get()) }
    single { AutoExpireGeoMarksUseCase(get()) }
    single {
        IngestReceivedGeoMarksUseCase(
            packetRepository    = get(),
            channelRepository   = get(),
            geoMarkRepository   = get(),
            adapter             = get(),
            channelSlotResolver = get(),
            logger              = get(),
        )
    }
}
