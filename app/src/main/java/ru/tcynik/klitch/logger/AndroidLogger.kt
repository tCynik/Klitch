package ru.tcynik.klitch.logger

import android.util.Log
import ru.tcynik.klitch.domain.logger.Logger

class AndroidLogger : Logger {
    override fun d(feature: String, message: String) { Log.d("Klitch/$feature", message) }
    override fun i(feature: String, message: String) { Log.i("Klitch/$feature", message) }
    override fun w(feature: String, message: String) { Log.w("Klitch/$feature", message) }
    override fun e(feature: String, message: String, throwable: Throwable?) {
        Log.e("Klitch/$feature", message, throwable)
    }
}
