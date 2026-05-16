package ru.tcynik.meshtactics.di

import org.koin.dsl.module
import ru.tcynik.meshtactics.domain.logger.Logger
import ru.tcynik.meshtactics.logger.AndroidLogger

val loggerModule = module {
    single<Logger> { AndroidLogger() }
}
