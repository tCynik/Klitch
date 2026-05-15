package ru.tcynik.meshtactics.logger

import android.util.Log
import ru.tcynik.meshtactics.domain.logger.Logger

class AndroidLogger : Logger {
    override fun d(feature: String, message: String) { Log.d("MT/$feature", message) }
    override fun i(feature: String, message: String) { Log.i("MT/$feature", message) }
    override fun w(feature: String, message: String) { Log.w("MT/$feature", message) }
    override fun e(feature: String, message: String, throwable: Throwable?) {
        Log.e("MT/$feature", message, throwable)
    }
}
