package ru.tcynik.klitch.data.map

import okhttp3.Interceptor
import okhttp3.Response
import ru.tcynik.klitch.domain.settings.model.TileCacheMode
import java.util.concurrent.atomic.AtomicReference

class TileCacheInterceptor(private val modeRef: AtomicReference<TileCacheMode>) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        return when (modeRef.get()) {
            TileCacheMode.DEFAULT -> response
            TileCacheMode.MONTH -> response.newBuilder()
                .header("Cache-Control", "max-age=2592000")
                .build()
            TileCacheMode.MAXIMUM -> response.newBuilder()
                .header("Cache-Control", "max-age=31536000")
                .build()
        }
    }
}
