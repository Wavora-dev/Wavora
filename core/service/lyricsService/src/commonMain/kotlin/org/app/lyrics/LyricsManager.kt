@file:OptIn(ExperimentalTime::class)

package com.wavora.lyrics

import com.wavora.logger.Logger
import com.wavora.lyrics.domain.Lyrics
import com.wavora.lyrics.models.request.LyricsBody
import com.wavora.lyrics.models.request.TranslatedLyricsBody
import com.wavora.lyrics.models.response.LyricsResponse
import com.wavora.lyrics.models.response.TranslatedLyricsResponse
import com.wavora.lyrics.parser.parseSyncedLyrics
import com.wavora.lyrics.parser.parseUnsyncedLyrics
import com.wavora.lyrics.provider.LyricsProviderRegistry
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private const val TAG = "LyricsManager"

/**
 * Orchestration layer sitting between the repository
 * (`LyricsCanvasRepositoryImpl`) and the concrete providers held by
 * [LyricsProviderRegistry]. This is the `LyricsManager` in the
 * `NowPlayingViewModel → LyricsManager → LyricsProviderRegistry →
 * WavoraLyricsProvider → Cloudflare Backend` chain.
 *
 * This class used to be called `WavoraLyricsClient` and talked directly
 * to the (now dead) `api-lyrics.wavora.org` domain via HMAC-signed
 * requests. Every method below keeps the exact same name, parameters and
 * return type it had before, so `LyricsCanvasRepositoryImpl` and
 * everything above it (NowPlayingViewModel, Mapping.kt) did not need to
 * change at all - only the constructor parameter's *type* changed, from
 * `WavoraLyricsClient` to `LyricsManager` (see `DatabaseModule.kt` and
 * `LyricsCanvasRepositoryImpl.kt`).
 *
 * HMAC signing is gone: the new backend authenticates public writes by
 * IP-based rate limiting only (see the backend README, "Rate limiting"),
 * so there is nothing left to sign.
 */
class LyricsManager(
    private val registry: LyricsProviderRegistry = LyricsProviderRegistry(),
) {
    // Prevents firing a second concurrent contribution for a videoId/
    // lyricsId that is already mid-flight - e.g. the user rating a lyric
    // twice before the first vote round-trips. Replaces the old
    // single-slot `insertingLyrics: Pair<String?, Boolean>` guard with a
    // set so more than one track's contribution can be in flight at once
    // (the old guard only ever tracked the single most recent videoId).
    private val lyricsSubmissionsInFlight = mutableSetOf<String>()
    private val translationSubmissionsInFlight = mutableSetOf<String>()

    // Mirrors the old `tooManyRequest` flag, but self-clearing: once the
    // backend answers 429 we back off until `retryAfterSeconds` (or a
    // conservative default) has elapsed, instead of relying on the next
    // call to reset a boolean.
    private var rateLimitedUntilMs: Long = 0L

    private fun now(): Long = Clock.System.now().toEpochMilliseconds()

    private fun assertNotRateLimited() {
        val remaining = rateLimitedUntilMs - now()
        if (remaining > 0) {
            throw IllegalStateException("Rate limited by Wavora backend, retry in ${remaining / 1000}s")
        }
    }

    private fun markRateLimitedIfNeeded(throwable: Throwable) {
        if (throwable is WavoraBackendException && throwable.code == "rate_limited") {
            Logger.e(TAG, "Rate limited by backend: ${throwable.message}")
            rateLimitedUntilMs = now() + 30_000L
        }
    }

    /** `GET /v1/lyrics/:videoId`. Returns a single-element list on a hit
     * (kept as `List` for source compatibility with the old API, which
     * could in theory return several candidates) or an empty list when
     * nothing is stored yet - never an error for the "not found" case. */
    suspend fun getLyrics(videoId: String): Result<List<LyricsResponse>> =
        registry.wavora
            .getLyrics(videoId)
            .map { row -> listOfNotNull(row?.toLegacyResponse()) }
            .onFailure { Logger.e(TAG, "getLyrics($videoId) failed: ${it.message}") }

    /** `GET /v1/translations/:lyricsId?language=..` (resolved from
     * [videoId] internally - see [WavoraLyricsProvider.getTranslatedLyrics]). */
    suspend fun getTranslatedLyrics(
        videoId: String,
        language: String,
    ): Result<TranslatedLyricsResponse> =
        runCatching {
            require(language.length == 2) { "Language code must be a 2-letter code" }
            val row =
                registry.wavora
                    .getTranslatedLyrics(videoId, language)
                    .getOrThrow()
                    ?: throw WavoraBackendException(
                        code = "not_found",
                        httpStatus = 404,
                        message = "No translation for videoId=$videoId language=$language",
                    )
            row.toLegacyResponse(videoId)
        }.onFailure { Logger.e(TAG, "getTranslatedLyrics($videoId, $language) failed: ${it.message}") }

    /** `POST /v1/lyrics`. Submits [lyricsBody] as a contribution (pending
     * moderation - see backend README). `syncType` is inferred from which
     * content fields are populated, since the legacy [LyricsBody] has no
     * explicit field for it. */
    suspend fun insertLyrics(lyricsBody: LyricsBody): Result<LyricsResponse> =
        runCatching {
            assertNotRateLimited()
            check(lyricsSubmissionsInFlight.add(lyricsBody.videoId)) {
                "Already inserting lyrics for ${lyricsBody.videoId}, please wait until the current operation is complete."
            }
            try {
                val syncType =
                    when {
                        !lyricsBody.richSyncLyrics.isNullOrBlank() -> "RICH_SYNCED"
                        !lyricsBody.syncedLyrics.isNullOrBlank() -> "LINE_SYNCED"
                        else -> "PLAIN"
                    }
                val id =
                    registry.wavora
                        .submitLyrics(
                            videoId = lyricsBody.videoId,
                            title = lyricsBody.songTitle,
                            artistName = lyricsBody.artistName,
                            albumName = lyricsBody.albumName,
                            durationSeconds = lyricsBody.durationSeconds,
                            trackType = lyricsBody.trackType,
                            language = "und",
                            syncType = syncType,
                            plainLyrics = lyricsBody.plainLyric,
                            syncedLyrics = lyricsBody.syncedLyrics,
                            richSyncedLyrics = lyricsBody.richSyncLyrics,
                            contributorName = lyricsBody.contributor,
                            contributorEmail = lyricsBody.contributorEmail,
                        ).getOrThrow()
                LyricsResponse(
                    id = id,
                    videoId = lyricsBody.videoId,
                    songTitle = lyricsBody.songTitle,
                    artistName = lyricsBody.artistName,
                    albumName = lyricsBody.albumName,
                    durationSeconds = lyricsBody.durationSeconds,
                    plainLyric = lyricsBody.plainLyric,
                    syncedLyrics = lyricsBody.syncedLyrics,
                    richSyncLyrics = lyricsBody.richSyncLyrics,
                    trackType = lyricsBody.trackType,
                    vote = 0,
                    contributor = lyricsBody.contributor,
                    contributorEmail = lyricsBody.contributorEmail,
                )
            } finally {
                lyricsSubmissionsInFlight.remove(lyricsBody.videoId)
            }
        }.onFailure(::markRateLimitedIfNeeded)

    /** `POST /v1/translations`. Translations attach to the *lyrics id* of
     * the original-language entry, not to a videoId directly, so this
     * first resolves that id via [WavoraLyricsProvider.getLyrics]. When
     * the original lyrics aren't in the backend yet, the translation
     * cannot be attached and this fails with a `not_found`
     * [WavoraBackendException] - the caller (repository) already treats
     * any failure here as "could not save", matching prior behavior.
     *
     * `translationSource` defaults to `"ai_generated"`: in practice this
     * path is only reached from `NowPlayingViewModel.updateLyrics` when
     * `helpBuildLyricsDatabase` is enabled and the lyrics on screen came
     * from AI translation (Spotify/LRCLIB/YouTube lyrics aren't
     * re-translated through this path). If your fork also calls this for
     * genuinely user-typed translations, pass `translationSource =
     * "user_contributed"` explicitly instead. */
    suspend fun insertTranslatedLyrics(
        translatedLyricsBody: TranslatedLyricsBody,
        translationSource: String = "ai_generated",
    ): Result<TranslatedLyricsResponse> =
        runCatching {
            require(translatedLyricsBody.language.length == 2) { "Language code must be a 2-letter code" }
            assertNotRateLimited()
            val inFlightKey = "${translatedLyricsBody.videoId}:${translatedLyricsBody.language}"
            check(translationSubmissionsInFlight.add(inFlightKey)) {
                "Already inserting translated lyrics for $inFlightKey, please wait until the current operation is complete."
            }
            try {
                val lyricsRow =
                    registry.wavora
                        .getLyrics(translatedLyricsBody.videoId, "und")
                        .getOrThrow()
                        ?: throw WavoraBackendException(
                            code = "not_found",
                            httpStatus = 404,
                            message = "Cannot attach translation: original lyrics for ${translatedLyricsBody.videoId} are not in the backend yet",
                        )
                val id =
                    registry.wavora
                        .submitTranslation(
                            lyricsId = lyricsRow.id,
                            language = translatedLyricsBody.language,
                            translatedLyrics = translatedLyricsBody.translatedLyric,
                            translationSource = translationSource,
                            contributorName = translatedLyricsBody.contributor,
                            contributorEmail = translatedLyricsBody.contributorEmail,
                        ).getOrThrow()
                TranslatedLyricsResponse(
                    id = id,
                    videoId = translatedLyricsBody.videoId,
                    translatedLyric = translatedLyricsBody.translatedLyric,
                    language = translatedLyricsBody.language,
                    vote = 0,
                    contributor = translatedLyricsBody.contributor,
                    contributorEmail = translatedLyricsBody.contributorEmail,
                )
            } finally {
                translationSubmissionsInFlight.remove(inFlightKey)
            }
        }.onFailure(::markRateLimitedIfNeeded)

    /** `POST /v1/vote` with `targetType = "lyrics"`. Only `id` and `vote`
     * are meaningful on the returned [LyricsResponse] - every other field
     * is blank because the vote endpoint doesn't echo back track/content
     * data, matching what `LyricsCanvasRepositoryImpl.voteWavoraLyrics`
     * actually reads (`it.id` only). */
    suspend fun voteLyrics(
        lyricsId: String,
        upvote: Boolean,
    ): Result<LyricsResponse> =
        registry.wavora
            .vote(targetType = "lyrics", targetId = lyricsId, value = if (upvote) 1 else -1)
            .map { result ->
                LyricsResponse(
                    id = result.targetId,
                    videoId = "",
                    songTitle = "",
                    artistName = "",
                    albumName = "",
                    durationSeconds = 0,
                    plainLyric = "",
                    syncedLyrics = null,
                    richSyncLyrics = null,
                    trackType = null,
                    vote = result.voteScore,
                    contributor = "",
                    contributorEmail = "",
                )
            }.onFailure { Logger.e(TAG, "voteLyrics($lyricsId) failed: ${it.message}") }

    /** `POST /v1/vote` with `targetType = "translation"`. See
     * [voteLyrics] for why only `id`/`vote` are populated. */
    suspend fun voteTranslatedLyrics(
        translatedLyricsId: String,
        upvote: Boolean,
    ): Result<TranslatedLyricsResponse> =
        registry.wavora
            .vote(targetType = "translation", targetId = translatedLyricsId, value = if (upvote) 1 else -1)
            .map { result ->
                TranslatedLyricsResponse(
                    id = result.targetId,
                    videoId = "",
                    translatedLyric = "",
                    language = "",
                    vote = result.voteScore,
                    contributor = "",
                    contributorEmail = "",
                )
            }.onFailure { Logger.e(TAG, "voteTranslatedLyrics($translatedLyricsId) failed: ${it.message}") }

    /** LRCLIB search - untouched by the Cloudflare migration, LRCLIB was
     * never served by the old Wavora backend. Kept here (rather than
     * called directly by the repository) purely so the repository keeps
     * a single dependency (`LyricsManager`) instead of two. */
    suspend fun searchLrclibLyrics(
        qTrack: String,
        qArtist: String,
        duration: Int?,
    ): Result<Lyrics?> =
        registry.lrclib
            .search(qTrack, qArtist, duration)
            .map { match ->
                val syncedLyrics = match?.syncedLyrics
                val plainLyrics = match?.plainLyrics
                when {
                    !syncedLyrics.isNullOrEmpty() -> parseSyncedLyrics(syncedLyrics)
                    !plainLyrics.isNullOrEmpty() -> parseUnsyncedLyrics(plainLyrics)
                    else -> null
                }
            }

    /** BetterLyrics search - same note as [searchLrclibLyrics]. */
    suspend fun searchBetterLyrics(
        qTrack: String,
        qArtist: String,
        durationSeconds: Int?,
    ): Result<String?> = registry.betterLyrics.searchLyrics(qTrack, qArtist, durationSeconds)
}

private fun com.wavora.lyrics.models.backend.BackendLyricsRow.toLegacyResponse(): LyricsResponse =
    LyricsResponse(
        id = id,
        videoId = videoId,
        // The new backend normalizes track metadata away from the lyrics
        // row (see `tracks` table in the backend schema); GET /v1/lyrics
        // doesn't return title/artist/album/duration/trackType at all.
        // None of these are read by `LyricsResponse.toLyrics()` (see
        // core/data/.../Mapping.kt) or by the repository for this call
        // path, so leaving them blank/zero is safe.
        songTitle = "",
        artistName = "",
        albumName = "",
        durationSeconds = 0,
        plainLyric = plainLyrics ?: "",
        syncedLyrics = syncedLyrics,
        richSyncLyrics = richSyncedLyrics,
        trackType = null,
        vote = voteScore,
        contributor = contributorName ?: "",
        contributorEmail = contributorEmail ?: "",
    )

private fun com.wavora.lyrics.models.backend.BackendTranslationRow.toLegacyResponse(videoId: String): TranslatedLyricsResponse =
    TranslatedLyricsResponse(
        id = id,
        videoId = videoId,
        translatedLyric = translatedLyrics,
        language = language,
        vote = voteScore,
        contributor = contributorName ?: "",
        contributorEmail = contributorEmail ?: "",
    )
