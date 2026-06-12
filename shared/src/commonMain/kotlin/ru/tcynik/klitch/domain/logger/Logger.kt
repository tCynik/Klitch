package ru.tcynik.klitch.domain.logger

interface Logger {
    fun d(feature: String, message: String)
    fun i(feature: String, message: String)
    fun w(feature: String, message: String)
    fun e(feature: String, message: String, throwable: Throwable? = null)
}
