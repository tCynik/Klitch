package ru.tcynik.mymesh1.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import ru.tcynik.mymesh1.data.remote.api.MeshApiService
import kotlin.test.Test
import kotlin.test.assertEquals

class MeshApiServiceTest {

    private val mockEngine = MockEngine {
        respond(
            content = """[
                {"id":"1","name":"Node A","address":"AA:BB:CC:DD:EE:FF",
                 "rssi":-70,"is_connected":true,"last_seen":1700000000000}
            ]""",
            headers = headersOf(
                HttpHeaders.ContentType,
                ContentType.Application.Json.toString(),
            ),
        )
    }

    private val service = MeshApiService(
        HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        },
    )

    @Test
    fun `getNodes returns parsed list`() = runTest {
        val nodes = service.getNodes()
        assertEquals(1, nodes.size)
        assertEquals("Node A", nodes[0].name)
        assertEquals(-70, nodes[0].rssi)
        assertEquals(true, nodes[0].isConnected)
    }
}
