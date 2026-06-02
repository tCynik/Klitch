package ru.tcynik.meshtactics.di

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.binds
import org.koin.dsl.module
import ru.tcynik.meshtactics.data.track.datasource.TrackSettingsDataSource
import ru.tcynik.meshtactics.data.track.repository.TrackRepositoryImpl
import ru.tcynik.meshtactics.domain.track.repository.RecordedTrackRepository
import ru.tcynik.meshtactics.domain.track.repository.TrackRecordingRepository
import ru.tcynik.meshtactics.domain.track.usecase.DeleteRecordedTracksUseCase
import ru.tcynik.meshtactics.domain.track.usecase.DiscardTrackRecordingUseCase
import ru.tcynik.meshtactics.domain.track.usecase.ObserveRecordedTracksUseCase
import ru.tcynik.meshtactics.domain.track.usecase.ObserveRecordedTrackPointsUseCase
import ru.tcynik.meshtactics.domain.track.usecase.ObserveTrackRecordingStateUseCase
import ru.tcynik.meshtactics.domain.track.usecase.PauseTrackRecordingUseCase
import ru.tcynik.meshtactics.domain.track.usecase.ResumeTrackRecordingUseCase
import ru.tcynik.meshtactics.domain.track.usecase.StartTrackRecordingUseCase
import ru.tcynik.meshtactics.domain.track.usecase.StopTrackRecordingUseCase
import ru.tcynik.meshtactics.domain.track.usecase.ToggleRecordedTrackVisibilityUseCase

val trackDataModule = module {
    single<androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>>(named("TrackSettingsDataStore")) {
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            produceFile = { androidContext().preferencesDataStoreFile("track_settings_ds") },
        )
    }
    single { TrackSettingsDataSource(get(named("TrackSettingsDataStore"))) }

    single(createdAtStart = true) {
        TrackRepositoryImpl(
            trackQueries = get(),
            pointQueries = get(),
            logger = get(),
        )
    } binds arrayOf(RecordedTrackRepository::class, TrackRecordingRepository::class)

    single { ObserveRecordedTracksUseCase(get()) }
    single { ObserveRecordedTrackPointsUseCase(get()) }
    single { ObserveTrackRecordingStateUseCase(get()) }
    single { StartTrackRecordingUseCase(get()) }
    single { PauseTrackRecordingUseCase(get()) }
    single { ResumeTrackRecordingUseCase(get()) }
    single { StopTrackRecordingUseCase(get()) }
    single { DiscardTrackRecordingUseCase(get()) }
    single { ToggleRecordedTrackVisibilityUseCase(get()) }
    single { DeleteRecordedTracksUseCase(get()) }
}
