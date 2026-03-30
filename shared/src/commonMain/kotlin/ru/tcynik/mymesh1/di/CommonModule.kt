package ru.tcynik.mymesh1.di

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
import ru.tcynik.mymesh1.data.local.AppDatabase
import ru.tcynik.mymesh1.data.remote.api.MeshApiService
import ru.tcynik.mymesh1.data.repository.NodeRepositoryImpl
import ru.tcynik.mymesh1.data.settings.AppSettings
import ru.tcynik.mymesh1.domain.repository.NodeRepository
import ru.tcynik.mymesh1.domain.usecase.node.GetNodesUseCase

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

    // Data — settings (Settings предоставляется платформенным модулем)
    single { AppSettings(get()) }

    // Domain
    single<NodeRepository> { NodeRepositoryImpl(get(), get(), get()) }
    single { GetNodesUseCase(get()) }
}
