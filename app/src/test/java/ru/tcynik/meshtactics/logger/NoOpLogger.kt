package ru.tcynik.meshtactics.logger

import ru.tcynik.meshtactics.domain.logger.Logger

class NoOpLogger : Logger {
    override fun d(feature: String, message: String) = Unit
    override fun i(feature: String, message: String) = Unit
    override fun w(feature: String, message: String) = Unit
    override fun e(feature: String, message: String, throwable: Throwable?) = Unit
}
