@file:OptIn(ExperimentalTime::class)

package com.wavora.lyrics

import com.wavora.ktorext.encoding.brotli
import com.wavora.ktorext.getEngine
import com.wavora.logger.Logger
import com.wavora.lyrics.models.backend.BackendContributionResult
import com.wavora.lyrics.models.backend.BackendEnvelope
import com.wavora.lyrics.models.backend.BackendLyricsRow
import com.wavora.lyrics.models.backend.BackendPostLyricsBody
import com.wavora.lyrics.models.backend.BackendPostTranslationBody
import com.wavora.lyrics.models.backend.BackendQueuedResult
import com.wavora.lyrics.models.backend.BackendTranslationRow
import com.wavora.lyrics.models.backend.BackendVoteBody
import com.wavora.lyrics.models.backend.BackendVoteResult
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private const val TAG = "WavoraLyricsProvider"

/** Thrown when the backend answers with a well-formed `{ "error": ... }`
 * envelope. [code] is the backend's `ApiErrorCode` (see
 * `wavora-lyrics-backend/src/lib/response.ts`): `"not_found"`,
 * `"validation_error"`, `"rate_limited"`, etc. - useful for callers that
 * want to branch on the specific failure instead of treating every
 * error the same way. */
class WavoraBackendException(
    val code: String,
    val httpStatus: Int,
    message: String,
) : Exception(message)

/** True for the expected "nothing here yet, an import was queued"
 * case - callers should treat this like a cache miss and fall back to
 * another provider; the backend will have it ready shortly after. */
fun WavoraBackendException.isPendingImport(): Boolean = code == "not_found"

/**
 * Talks to the new Cloudflare Workers lyrics backend
 * (`wavora-lyrics-backend`), which fully replaces the old, dead
 * `api-lyrics.wavora.org` domain. This is the `WavoraLyricsProvider` in
 * the `NowPlayingViewModel → LyricsManager → LyricsProviderRegistry →
 * WavoraLyricsProvider → Cloudflare Backend` chain.
 *
 * Responsibilities kept deliberately narrow:
 *  - HTTP transport (timeouts, retry, gzip/brotli).
 *  - A small in-memory read cache: the backend already caches in KV, so
 *    this is only a *second*, session-local layer that avoids re-hitting
 *    the network at all for a lyric the user just scrolled back to. It is
 *    not meant to replace the backend's cache and uses a short TTL.
 *  - Mapping the backend's snake_case row DTOs (`BackendLyricsRow`, etc.)
 *    into plain results. It does NOT know about the legacy app-facing
 *    DTOs (`LyricsResponse`/`TranslatedLyricsResponse`) - mapping those
 *    is [LyricsManager]'s job, keeping this class reusable/testable on
 *    its own.
 *
 * Base URL points at the Worker's `workers.dev` subdomain by default.
 * Once you attach a custom domain/route in `wrangler.jsonc`, pass it via
 * the constructor instead - nothing else in the app needs to change.
 */
class WavoraLyricsProvider(
    baseUrl: String = DEFAULT_BASE_URL,
) {
    companion object {
        /** Production Worker URL from `wrangler.jsonc` (`name: wavora-lyrics`).
         * See the deploy guide, section "Dominio propio (opcional)", to
         * point this at a custom domain instead. */
        const val DEFAULT_BASE_URL = "https://wavora-lyrics.wavora-lyrics.workers.dev"

        private const val CACHE_TTL_MS = 5 * 60 * 1000L // 5 minutes, session-local only
    }

    private val v1 = baseUrl.trimEnd('/') + "/v1"

    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            explicitNulls = false
            encodeDefaults = true
        }

    private val httpClient by lazy {
        HttpClient(getEngine()) {
            expectSuccess = false
            install(HttpTimeout) {
                connectTimeoutMillis = 8_000
                requestTimeoutMillis = 15_000
                socketTimeoutMillis = 15_000
            }
            install(HttpRequestRetry) {
                // Covers both transient network exceptions (timeouts,
                // connection reset) and 5xx responses. Deliberately does
                // NOT retry 4xx (validation_error, rate_limited, etc.) -
                // retrying those immediately would just make things worse.
                retryOnExceptionOrServerErrors(maxRetries = 2)
                exponentialDelay()
            }
            install(HttpSend) { maxSendCount = 20 }
            install(ContentNegotiation) { json(json) }
            install(ContentEncoding) {
                brotli(1.0F)
                gzip(0.9F)
                deflate(0.8F)
            }
        }
    }

    // ---- Tiny session-local read cache ------------------------------
    // Not thread-safe by construction, and deliberately so: a rare race
    // between two coroutines populating the same key just costs one
    // redundant network call, never incorrect data (every write path
    // below calls invalidate() first). Avoiding a lock here also avoids
    // pulling in an extra multiplatform concurrency dependency for a
    // cache that's a pure "nice to have" on top of the backend's own KV.
    private data class CacheEntry(
        val value: Any?,
        val expiresAt: Long,
    )

    private val cache = HashMap<String, CacheEntry>()

    private fun now(): Long = Clock.System.now().toEpochMilliseconds()

    /** Distinguishes "cached, and the value was `null`" (a confirmed
     * not-found we don't want to re-fetch yet) from "no valid entry"
     * (either never cached or expired - must re-fetch). Collapsing these
     * into a single nullable return would incorrectly treat an *expired*
     * hit as a confirmed not-found instead of re-fetching it. */
    private sealed interface CacheLookup<out T> {
        data class Hit<T>(val value: T?) : CacheLookup<T>

        object Miss : CacheLookup<Nothing>
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> cacheLookup(key: String): CacheLookup<T> {
        val entry = cache[key] ?: return CacheLookup.Miss
        if (entry.expiresAt < now()) {
            cache.remove(key)
            return CacheLookup.Miss
        }
        return CacheLookup.Hit(entry.value as T?)
    }

    private fun cachePut(
        key: String,
        value: Any?,
    ) {
        cache[key] = CacheEntry(value, now() + CACHE_TTL_MS)
    }

    /** Drops any cached entry whose key starts with [prefix]. Called after
     * every write (vote, contribution) so a subsequent read is never
     * served stale session-local data. Mirrors the backend's own
     * `invalidate()` in `lib/cache.ts`, one layer up. */
    fun invalidate(prefix: String) {
        cache.keys.filter { it.startsWith(prefix) }.forEach { cache.remove(it) }
    }

    private fun HttpRequestBuilder.defaultHeaders() {
        headers {
            header(HttpHeaders.Accept, "application/json")
            header(HttpHeaders.ContentType, "application/json")
            header(HttpHeaders.UserAgent, "Wavora/1.0 (WavoraLyricsProvider)")
        }
    }

    /** Decodes `{ "data": T, "meta"?: ... }` on success or throws
     * [WavoraBackendException] built from `{ "error": {...} }` on failure.
     * Both shapes are the same `BackendEnvelope<T>` type since `data` and
     * `error` are both nullable with defaults, so a single decode call
     * covers both cases. */
    private suspend inline fun <reified T> HttpResponse.decodeOrThrow(): T {
        val text = bodyAsText()
        val envelope =
            try {
                json.decodeFromString(BackendEnvelope.serializer(serializer<T>()), text)
            } catch (e: Exception) {
                throw WavoraBackendException("internal_error", status.value, "Malformed response: ${e.message}")
            }
        if (status.isSuccess()) {
            return envelope.data ?: throw WavoraBackendException("internal_error", status.value, "Empty response body")
        }
        val error = envelope.error ?: throw WavoraBackendException("internal_error", status.value, "HTTP ${status.value}")
        Logger.e(TAG, "Backend error ${error.code}: ${error.message}")
        throw WavoraBackendException(error.code, status.value, error.message)
    }

    /** `GET /v1/lyrics/:videoId?language=..`. Returns `null` (success, not
     * an error) when nothing is stored yet - the backend has already
     * queued an opportunistic LRCLIB import in that case, so the caller
     * should fall back to another provider now and expect this to
     * succeed on a later request. */
    suspend fun getLyrics(
        videoId: String,
        language: String = "und",
    ): Result<BackendLyricsRow?> =
        runCatching {
            val cacheKey = "lyrics:$videoId:$language"
            when (val cached = cacheLookup<BackendLyricsRow?>(cacheKey)) {
                is CacheLookup.Hit -> return@runCatching cached.value
                CacheLookup.Miss -> Unit
            }

            val response =
                httpClient.get("$v1/lyrics/$videoId") {
                    defaultHeaders()
                    parameter("language", language)
                }
            // Always goes through decodeOrThrow (even for 404) so the
            // response body is consumed exactly once regardless of
            // status, instead of relying on engine-specific behavior for
            // discarding an unread body.
            try {
                response.decodeOrThrow<BackendLyricsRow>().also { cachePut(cacheKey, it) }
            } catch (e: WavoraBackendException) {
                if (e.isPendingImport()) {
                    cachePut(cacheKey, null)
                    null
                } else {
                    throw e
                }
            }
        }

    /** Resolves the translation for [videoId] in [language]. Translations
     * are keyed by `lyrics_id` server-side (not `video_id`), so this first
     * looks up the original-language lyrics row (`language = "und"`) to
     * get its id, then fetches the translation for that id - at most one
     * extra request, and that lookup is itself covered by [getLyrics]'s
     * cache. */
    suspend fun getTranslatedLyrics(
        videoId: String,
        language: String,
    ): Result<BackendTranslationRow?> =
        runCatching {
            val lyricsRow = getLyrics(videoId, "und").getOrThrow() ?: return@runCatching null
            val cacheKey = "translation:${lyricsRow.id}:$language"
            when (val cached = cacheLookup<BackendTranslationRow?>(cacheKey)) {
                is CacheLookup.Hit -> return@runCatching cached.value
                CacheLookup.Miss -> Unit
            }

            val response =
                httpClient.get("$v1/translations/${lyricsRow.id}") {
                    defaultHeaders()
                    parameter("language", language)
                }
            try {
                response.decodeOrThrow<BackendTranslationRow>().also { cachePut(cacheKey, it) }
            } catch (e: WavoraBackendException) {
                if (e.isPendingImport()) {
                    cachePut(cacheKey, null)
                    null
                } else {
                    throw e
                }
            }
        }

    /** `POST /v1/lyrics`. Registers/updates the track and, when
     * [plainLyrics]/[syncedLyrics]/[richSyncedLyrics] are provided,
     * submits them as a pending contribution (subject to moderation - see
     * the backend README's "Moderación" section). Mirrors the old
     * `insertLyrics` call, minus the HMAC signing the dead API required:
     * the new backend authenticates public writes by rate limit only. */
    suspend fun submitLyrics(
        videoId: String,
        title: String,
        artistName: String,
        albumName: String?,
        durationSeconds: Int?,
        trackType: String,
        language: String,
        syncType: String?,
        plainLyrics: String?,
        syncedLyrics: String?,
        richSyncedLyrics: String?,
        contributorName: String?,
        contributorEmail: String?,
    ): Result<String> =
        runCatching {
            val response =
                httpClient.post("$v1/lyrics") {
                    defaultHeaders()
                    setBody(
                        BackendPostLyricsBody(
                            videoId = videoId,
                            title = title,
                            artistName = artistName,
                            albumName = albumName,
                            durationSeconds = durationSeconds,
                            trackType = trackType,
                            language = language,
                            syncType = syncType,
                            plainLyrics = plainLyrics,
                            syncedLyrics = syncedLyrics,
                            richSyncedLyrics = richSyncedLyrics,
                            contributorName = contributorName,
                            contributorEmail = contributorEmail,
                        ),
                    )
                }
            invalidate("lyrics:$videoId:")
            val hasContent = !plainLyrics.isNullOrBlank() || !syncedLyrics.isNullOrBlank() || !richSyncedLyrics.isNullOrBlank()
            if (hasContent) {
                response.decodeOrThrow<BackendContributionResult>().contributionId
            } else {
                response.decodeOrThrow<BackendQueuedResult>().videoId
            }
        }

    /** `POST /v1/translations`. [lyricsId] is the id returned by
     * [getLyrics], NOT the videoId - see that method's docs for why.
     *
     * The backend now routes every translation through the same
     * contributions -> pending -> admin approval flow as
     * [submitLyrics]'s content branch (previously this wrote directly
     * and was visible instantly - see backend CHANGELOG). The returned
     * [Result.getOrNull] is therefore a `contributionId`, not a live
     * translation id - it won't resolve via [getTranslation] until an
     * admin approves it. */
    suspend fun submitTranslation(
        lyricsId: String,
        language: String,
        translatedLyrics: String,
        translationSource: String = "user_contributed",
        contributorName: String?,
        contributorEmail: String?,
    ): Result<String> =
        runCatching {
            val response =
                httpClient.post("$v1/translations") {
                    defaultHeaders()
                    setBody(
                        BackendPostTranslationBody(
                            lyricsId = lyricsId,
                            language = language,
                            translatedLyrics = translatedLyrics,
                            translationSource = translationSource,
                            contributorName = contributorName,
                            contributorEmail = contributorEmail,
                        ),
                    )
                }
            invalidate("translation:$lyricsId:")
            response.decodeOrThrow<BackendContributionResult>().contributionId
        }

    /** `POST /v1/vote`. [value] must be `1` (upvote) or `-1` (downvote) -
     * the new backend rejects `0`, unlike the old API. */
    suspend fun vote(
        targetType: String,
        targetId: String,
        value: Int,
    ): Result<BackendVoteResult> =
        runCatching {
            val response =
                httpClient.post("$v1/vote") {
                    defaultHeaders()
                    setBody(BackendVoteBody(targetType = targetType, targetId = targetId, value = value))
                }
            val result = response.decodeOrThrow<BackendVoteResult>()
            // We only know the opaque target id here, not its
            // videoId/language, so invalidate conservatively rather than
            // risk serving a stale vote count. The cache is small and
            // short-lived, so this is cheap.
            invalidate(if (targetType == "lyrics") "lyrics:" else "translation:")
            result
        }
}
