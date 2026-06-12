package ru.tcynik.klitch.di

import org.koin.android.ext.koin.androidApplication
import org.koin.dsl.module
import ru.tcynik.klitch.di.orientation.DeviceOrientationProvider

val orientationModule = module {
    single<DeviceOrientationProvider> { DeviceOrientationProvider(androidApplication()) }
}
