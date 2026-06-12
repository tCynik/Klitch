package ru.tcynik.klitch.data.map

import okhttp3.Cache
import okhttp3.OkHttpClient
import ru.tcynik.klitch.domain.settings.model.TileCacheMode
import java.io.File
import java.util.concurrent.atomic.AtomicReference

class TileCacheOkHttpConfigurator(
    cacheDir: File,
    initialMode: TileCacheMode,
) {
    private val modeRef = AtomicReference(initialMode)
    private val interceptor = TileCacheInterceptor(modeRef)

    val client: OkHttpClient = OkHttpClient.Builder()
        .cache(Cache(File(cacheDir, "map_tiles"), 100L * 1024 * 1024))
        .addNetworkInterceptor(interceptor)
        .build()

    fun updateMode(mode: TileCacheMode) {
        modeRef.set(mode)
    }
}
