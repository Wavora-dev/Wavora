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
import com.wavora.lyrics.models.response.BetterLyricsResponse
import kotlinx.serialization.json.Json

/**
 * Registry provider wrapping https://lyrics-api.boidu.dev/getLyrics.
 * Unrelated to the api-lyrics.wavora.org migration - BetterLyrics was
 * never served by the old Wavora backend, it is (and remains) queried
 * directly.
 */
class BetterLyricsProvider : LyricsProvider {
    override val id = LyricsProviderId.BETTER_LYRICS

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

    override suspend fun searchLyrics(
        track: String,
        artist: String,
        durationSeconds: Int?,
    ): Result<String?> =
        runCatching {
            val response =
                httpClient
                    .get("https://lyrics-api.boidu.dev/getLyrics") {
                        headers {
                            header(HttpHeaders.Accept, "application/json")
                            header(HttpHeaders.UserAgent, "Wavora/1.0")
                        }
                        parameter("s", track)
                        parameter("a", artist)
                        durationSeconds?.let { parameter("d", it) }
                    }.body<BetterLyricsResponse>()
            response.ttml.takeIf { it.isNotBlank() }
        }
}
