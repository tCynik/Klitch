package ru.tcynik.meshtactics.data.map

import io.mockk.every
import io.mockk.mockk
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.tcynik.meshtactics.domain.settings.model.TileCacheMode
import java.util.concurrent.atomic.AtomicReference

class TileCacheInterceptorTest {

    private val request = Request.Builder().url("https://tile.opentopomap.org/tile.png").build()

    private fun fakeResponse() = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("OK")
        .build()

    private fun fakeChain(response: Response = fakeResponse()): Interceptor.Chain = mockk {
        every { request() } returns request
        every { proceed(any()) } returns response
    }

    // ── Mode: DEFAULT ────────────────────────────────────────────────────────

    @Test
    fun `DEFAULT mode — response returned unchanged, no Cache-Control injected`() {
        val interceptor = TileCacheInterceptor(AtomicReference(TileCacheMode.DEFAULT))
        val result = interceptor.intercept(fakeChain())
        assertNull(result.header("Cache-Control"))
    }

    // ── Mode: MONTH ──────────────────────────────────────────────────────────

    @Test
    fun `MONTH mode — Cache-Control set to 30 days`() {
        val interceptor = TileCacheInterceptor(AtomicReference(TileCacheMode.MONTH))
        val result = interceptor.intercept(fakeChain())
        assertEquals("max-age=2592000", result.header("Cache-Control"))
    }

    // ── Mode: MAXIMUM ────────────────────────────────────────────────────────

    @Test
    fun `MAXIMUM mode — Cache-Control set to 1 year`() {
        val interceptor = TileCacheInterceptor(AtomicReference(TileCacheMode.MAXIMUM))
        val result = interceptor.intercept(fakeChain())
        assertEquals("max-age=31536000", result.header("Cache-Control"))
    }

    // ── AtomicReference hot-swap ──────────────────────────────────────────────

    @Test
    fun `mode change via AtomicReference takes effect on next intercept call`() {
        val modeRef = AtomicReference(TileCacheMode.DEFAULT)
        val interceptor = TileCacheInterceptor(modeRef)

        assertNull(interceptor.intercept(fakeChain()).header("Cache-Control"))

        modeRef.set(TileCacheMode.MONTH)
        assertEquals("max-age=2592000", interceptor.intercept(fakeChain()).header("Cache-Control"))

        modeRef.set(TileCacheMode.MAXIMUM)
        assertEquals("max-age=31536000", interceptor.intercept(fakeChain()).header("Cache-Control"))
    }
}
