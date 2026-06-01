package ru.tcynik.meshtactics.di

import app.cash.sqldelight.db.SqlDriver
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.core.module.Module
import org.koin.dsl.module
// AppDatabase и NodeQueries генерируются SQLDelight после первого билда
import ru.tcynik.meshtactics.data.local.AppDatabase
import ru.tcynik.meshtactics.data.remote.api.MeshApiService
import ru.tcynik.meshtactics.data.repository.NodeRepositoryImpl
import ru.tcynik.meshtactics.data.settings.AppSettings
import ru.tcynik.meshtactics.domain.repository.NodeRepository
import ru.tcynik.meshtactics.domain.settings.repository.MapCacheSettingsRepository
import ru.tcynik.meshtactics.domain.settings.repository.MarkerSettingsRepository
import ru.tcynik.meshtactics.domain.settings.repository.NetworkSettingsRepository
import ru.tcynik.meshtactics.domain.settings.repository.ScreenOrientationRepository
import ru.tcynik.meshtactics.domain.usecase.node.GetNodesUseCase

val commonModule: Module = module {

    // Serialization
    single<Json> {
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            prettyPrint = false
        }
    }

    // Network
    single<HttpClient> {
        HttpClient {
            install(ContentNegotiation) { json(get()) }
            install(Logging) { level = LogLevel.BODY }
        }
    }

    // Data — remote
    single { MeshApiService(get()) }

    // Data — local (SqlDriver предоставляется платформенным модулем)
    single { AppDatabase(get<SqlDriver>()) }
    single { get<AppDatabase>().nodeQueries }
    single { get<AppDatabase>().importedMapOverlayQueries }
    single { get<AppDatabase>().geoMarkQueries }
    single { get<AppDatabase>().contourQueries }
    single { get<AppDatabase>().chatMessageQueries }
    single { get<AppDatabase>().recordedTrackQueries }
    single { get<AppDatabase>().recordedTrackPointQueries }

    // Data — settings (Settings предоставляется платформенным модулем)
    single { AppSettings(get()) }
    single<MarkerSettingsRepository> { get<AppSettings>() }
    single<MapCacheSettingsRepository> { get<AppSettings>() }
    single<ScreenOrientationRepository> { get<AppSettings>() }
    single<NetworkSettingsRepository> { get<AppSettings>() }

    // Domain
    single<NodeRepository> { NodeRepositoryImpl(get(), get(), get()) }
    single { GetNodesUseCase(get()) }
}
