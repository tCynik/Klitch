package ru.tcynik.meshtactics.di

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module
import ru.tcynik.meshtactics.data.local.AppDatabase

val androidModule: Module = module {

    // SQLite driver (Android)
    single<SqlDriver> {
        AndroidSqliteDriver(
            schema = AppDatabase.Schema,
            context = androidContext(),
            name = "mesh_v2.db",
        )
    }

    // SharedPreferences-based settings
    single<Settings> {
        SharedPreferencesSettings(
            delegate = androidContext()
                .getSharedPreferences("app_settings", Context.MODE_PRIVATE),
        )
    }
}
