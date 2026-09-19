package org.app.lyrics.scrobbler

import com.wavora.ktorext.getEngine
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Cliente mínimo de la API de ListenBrainz (https://listenbrainz.org/api/).
 * A diferencia de Last.fm, no necesita ninguna credencial de aplicación: cada
 * usuario genera su propio "User Token" personal en
 * listenbrainz.org/settings/ y lo pega en Ajustes - por eso acá no hay
 * ningún "isConfigured" a nivel app, el token vive por-usuario en DataStore.
 */
object ListenBrainzApi {
    private const val SUBMIT_URL = "https://api.listenbrainz.org/1/submit-listens"

    private val httpClient by lazy {
        HttpClient(getEngine()) {
            expectSuccess = false
            install(HttpTimeout) {
                connectTimeoutMillis = 8_000
                requestTimeoutMillis = 15_000
                socketTimeoutMillis = 15_000
            }
            install(HttpRequestRetry) {
                retryOnExceptionOrServerErrors(maxRetries = 2)
                exponentialDelay()
            }
        }
    }

    suspend fun submitListen(
        userToken: String,
        artist: String,
        track: String,
        album: String?,
        listenedAtSeconds: Long,
    ): Result<Unit> {
        if (userToken.isBlank()) return Result.failure(IllegalArgumentException("ListenBrainz: token vacío"))
        return runCatching {
            val body =
                buildJsonObject {
                    put("listen_type", "single")
                    put(
                        "payload",
                        buildJsonArray {
                            add(
                                buildJsonObject {
                                    put("listened_at", listenedAtSeconds)
                                    put(
                                        "track_metadata",
                                        buildJsonObject {
                                            put("artist_name", artist)
                                            put("track_name", track)
                                            if (!album.isNullOrBlank()) put("release_name", album)
                                        },
                                    )
                                },
                            )
                        },
                    )
                }
            val response =
                httpClient.post(SUBMIT_URL) {
                    header("Authorization", "Token $userToken")
                    contentType(ContentType.Application.Json)
                    setBody(Json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), body))
                }
            val responseBody = response.bodyAsText()
            if (!response.status.isSuccess()) {
                error("ListenBrainz submit-listens HTTP ${response.status.value}: $responseBody")
            }
        }
    }
}
