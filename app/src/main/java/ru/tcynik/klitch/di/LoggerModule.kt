package ru.tcynik.klitch.di

import org.koin.dsl.module
import ru.tcynik.klitch.domain.logger.Logger
import ru.tcynik.klitch.logger.AndroidLogger

val loggerModule = module {
    single<Logger> { AndroidLogger() }
}
