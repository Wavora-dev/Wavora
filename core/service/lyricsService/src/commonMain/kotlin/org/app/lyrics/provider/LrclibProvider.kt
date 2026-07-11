package com.wavora.lyrics.provider

import com.wavora.ktorext.encoding.brotli
import com.wavora.ktorext.getEngine
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import com.wavora.lyrics.models.response.LrclibObject
import kotlinx.serialization.json.Json
import kotlin.math.abs

/**
 * Registry provider wrapping https://lrclib.net/api/search. Unrelated to
 * the api-lyrics.wavora.org migration - LRCLIB was never served by the
 * old Wavora backend, it is (and remains) queried directly.
 */
class LrclibProvider : LyricsProvider {
    override val id = LyricsProviderId.LRCLIB

    private val httpClient by lazy {
        HttpClient(getEngine()) {
            expectSuccess = false
            install(HttpTimeout) {
                connectTimeoutMillis = 8_000
                requestTimeoutMillis = 12_000
                socketTimeoutMillis = 12_000
            }
            install(HttpSend) { maxSendCount = 20 }
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false })
            }
            install(ContentEncoding) {
                brotli(1.0F)
                gzip(0.9F)
                deflate(0.8F)
            }
        }
    }

    /** Returns the best matching [LrclibObject], preferring synced lyrics,
     * or null when LRCLIB has nothing usable for this track. */
    suspend fun search(
        track: String,
        artist: String,
        durationSeconds: Int?,
    ): Result<LrclibObject?> =
        runCatching {
            val results =
                httpClient
                    .get("https://lrclib.net/api/search") {
                        headers {
                            header(HttpHeaders.Accept, "application/json")
                            header(HttpHeaders.UserAgent, "Wavora/1.0")
                        }
                        parameter("q", "$artist $track")
                    }.body<List<LrclibObject>>()

            if (durationSeconds != null) {
                results.find { abs(it.duration.toInt() - durationSeconds) <= 10 }
            } else {
                results.firstOrNull()
            }
        }

    override suspend fun searchLyrics(
        track: String,
        artist: String,
        durationSeconds: Int?,
    ): Result<String?> =
        search(track, artist, durationSeconds).map { match ->
            match?.syncedLyrics?.takeIf { it.isNotBlank() }
                ?: match?.plainLyrics?.takeIf { it.isNotBlank() }
        }
}
