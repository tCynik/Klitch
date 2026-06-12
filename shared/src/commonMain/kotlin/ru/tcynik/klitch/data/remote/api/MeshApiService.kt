package ru.tcynik.klitch.data.remote.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import ru.tcynik.klitch.data.remote.dto.NodeDto

class MeshApiService(private val client: HttpClient) {

    suspend fun getNodes(): List<NodeDto> =
        client.get("$BASE_URL/nodes").body()

    companion object {
        private const val BASE_URL = "https://api.mymesh.example.com/v1"
    }
}
