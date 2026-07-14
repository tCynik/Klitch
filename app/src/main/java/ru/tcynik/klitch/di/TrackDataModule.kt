package ru.tcynik.klitch.di

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.binds
import org.koin.dsl.module
import ru.tcynik.klitch.data.track.datasource.TrackSettingsDataSource
import ru.tcynik.klitch.data.track.repository.TrackFileRepositoryImpl
import ru.tcynik.klitch.data.track.repository.TrackRepositoryImpl
import ru.tcynik.klitch.domain.track.repository.RecordedTrackRepository
import ru.tcynik.klitch.domain.track.repository.TrackFileRepository
import ru.tcynik.klitch.domain.track.repository.TrackRecordingRepository
import ru.tcynik.klitch.domain.track.repository.TrackSettingsRepository
import ru.tcynik.klitch.domain.track.usecase.DeleteRecordedTracksUseCase
import ru.tcynik.klitch.domain.track.usecase.DiscardTrackRecordingUseCase
import ru.tcynik.klitch.domain.track.usecase.ExportTrackUseCase
import ru.tcynik.klitch.domain.track.usecase.ImportTrackUseCase
import ru.tcynik.klitch.domain.track.usecase.ObserveRecordedTracksUseCase
import ru.tcynik.klitch.domain.track.usecase.ObserveRecordedTrackPointsUseCase
import ru.tcynik.klitch.domain.track.usecase.ObserveTrackRecordingStateUseCase
import ru.tcynik.klitch.domain.track.usecase.PauseTrackRecordingUseCase
import ru.tcynik.klitch.domain.track.usecase.ResumeTrackRecordingUseCase
import ru.tcynik.klitch.domain.track.usecase.StartTrackRecordingUseCase
import ru.tcynik.klitch.domain.track.usecase.StopTrackRecordingUseCase
import ru.tcynik.klitch.domain.track.usecase.ToggleRecordedTrackVisibilityUseCase
import ru.tcynik.klitch.domain.track.usecase.UpdateTrackRecordingColorUseCase
import ru.tcynik.klitch.domain.track.usecase.UpdateTrackRecordingNameUseCase

val trackDataModule = module {
    single<androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>>(named("TrackSettingsDataStore")) {
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            produceFile = { androidContext().preferencesDataStoreFile("track_settings_ds") },
        )
    }
    single { TrackSettingsDataSource(get(named("TrackSettingsDataStore"))) } binds arrayOf(TrackSettingsRepository::class)

    single(createdAtStart = true) {
        TrackRepositoryImpl(
            trackQueries = get(),
            pointQueries = get(),
            logger = get(),
        )
    } binds arrayOf(RecordedTrackRepository::class, TrackRecordingRepository::class)

    single<TrackFileRepository> {
        TrackFileRepositoryImpl(
            context = androidContext(),
            trackRepository = get(),
            logger = get(),
        )
    }
    single { ExportTrackUseCase(get()) }
    single { ImportTrackUseCase(get()) }

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
    single { UpdateTrackRecordingNameUseCase(get()) }
    single { UpdateTrackRecordingColorUseCase(get()) }
}
