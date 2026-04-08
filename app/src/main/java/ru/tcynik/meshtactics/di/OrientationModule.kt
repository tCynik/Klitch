package ru.tcynik.meshtactics.di

import org.koin.android.ext.koin.androidApplication
import org.koin.dsl.module
import ru.tcynik.meshtactics.di.orientation.DeviceOrientationProvider

val orientationModule = module {
    single<DeviceOrientationProvider> { DeviceOrientationProvider(androidApplication()) }
}
