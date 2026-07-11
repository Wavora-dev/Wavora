package com.wavora.lyrics.models.backend

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Wire-level DTOs for the new Cloudflare Workers backend
 * (wavora-lyrics-backend). These mirror the JSON shape the Worker
 * actually returns - snake_case field names, taken straight from the D1
 * row types in `src/db/schema.ts` - and are deliberately kept separate
 * from the legacy app-facing DTOs in `models/response`/`models/request`.
 *
 * [WavoraLyricsProvider] is the only place these types are used; it maps
 * them into the legacy [com.wavora.lyrics.models.response.LyricsResponse]
 * / [com.wavora.lyrics.models.response.TranslatedLyricsResponse] shapes so
 * every other layer of the app (LyricsCanvasRepositoryImpl, Mapping.kt,
 * NowPlayingViewModel) keeps working unmodified.
 *
 * Every success response from the backend is wrapped in
 * `{ "data": ..., "meta"?: ... }`; every error in
 * `{ "error": { "code", "message", "details"? } }`. See the backend's
 * `src/lib/response.ts` for the canonical definition.
 */
@Serializable
data class BackendEnvelope<T>(
    val data: T? = null,
    val meta: BackendMeta? = null,
    val error: BackendError? = null,
)

@Serializable
data class BackendMeta(
    val cacheHit: Boolean? = null,
    val message: String? = null,
    val limit: Int? = null,
    val offset: Int? = null,
)

@Serializable
data class BackendError(
    val code: String,
    val message: String,
    val details: JsonElement? = null,
)

/** Mirrors `LyricsRow` in the backend's `src/db/schema.ts`. */
@Serializable
data class BackendLyricsRow(
    val id: String,
    @SerialName("video_id") val videoId: String,
    val source: String,
    val language: String,
    @SerialName("sync_type") val syncType: String,
    @SerialName("plain_lyrics") val plainLyrics: String? = null,
    @SerialName("synced_lyrics") val syncedLyrics: String? = null,
    @SerialName("rich_synced_lyrics") val richSyncedLyrics: String? = null,
    @SerialName("content_hash") val contentHash: String,
    @SerialName("vote_score") val voteScore: Int = 0,
    @SerialName("contributor_name") val contributorName: String? = null,
    @SerialName("contributor_email") val contributorEmail: String? = null,
    @SerialName("schema_version") val schemaVersion: Int = 1,
    @SerialName("created_at") val createdAt: Long = 0,
    @SerialName("updated_at") val updatedAt: Long = 0,
)

/** Mirrors `LyricsTranslationRow`. Note translations are keyed by
 * `lyrics_id`, not `video_id` - see [WavoraLyricsProvider.getTranslatedLyrics]
 * for the two-step lookup this implies. */
@Serializable
data class BackendTranslationRow(
    val id: String,
    @SerialName("lyrics_id") val lyricsId: String,
    val language: String,
    @SerialName("translated_lyrics") val translatedLyrics: String,
    @SerialName("translation_source") val translationSource: String,
    @SerialName("vote_score") val voteScore: Int = 0,
    @SerialName("contributor_name") val contributorName: String? = null,
    @SerialName("contributor_email") val contributorEmail: String? = null,
    @SerialName("created_at") val createdAt: Long = 0,
    @SerialName("updated_at") val updatedAt: Long = 0,
)

/** Response of `POST /v1/vote`. Unlike [BackendLyricsRow]/[BackendTranslationRow]
 * (which mirror raw, snake_case D1 rows), this is a hand-built JS object
 * in `vote.ts` (`{ targetType, targetId, voteScore }`) - already
 * camelCase, so no `@SerialName` mapping here. (Previously annotated as
 * snake_case by mistake, which made every vote call fail to deserialize
 * with a MissingFieldException since the backend never actually sends
 * `target_type`/`target_id`/`vote_score` for this endpoint.) */
@Serializable
data class BackendVoteResult(
    val targetType: String,
    val targetId: String,
    val voteScore: Int = 0,
)

/** Response of `POST /v1/lyrics` when the body has no lyrics content
 * (pure track registration - triggers an async import). */
@Serializable
data class BackendQueuedResult(
    val videoId: String,
    val queued: Boolean = false,
)

/** Response of `POST /v1/lyrics` when the body includes lyrics content
 * (user contribution - lands as a pending contribution, not a live row). */
@Serializable
data class BackendContributionResult(
    val contributionId: String,
    val status: String,
)

/** Mirrors `TrackRow`, returned by `GET /v1/search`. */
@Serializable
data class BackendTrackRow(
    @SerialName("video_id") val videoId: String,
    val title: String,
    @SerialName("artist_name") val artistName: String,
    @SerialName("album_name") val albumName: String? = null,
    @SerialName("duration_seconds") val durationSeconds: Int? = null,
    @SerialName("track_type") val trackType: String,
)

/** Body of `POST /v1/lyrics` (track registration and/or contribution). */
@Serializable
data class BackendPostLyricsBody(
    val videoId: String,
    val title: String,
    val artistName: String,
    val albumName: String? = null,
    val durationSeconds: Int? = null,
    val trackType: String = "SONG",
    val language: String? = null,
    val syncType: String? = null,
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null,
    val richSyncedLyrics: String? = null,
    val contributorName: String? = null,
    val contributorEmail: String? = null,
)

/** Body of `POST /v1/translations`. */
@Serializable
data class BackendPostTranslationBody(
    val lyricsId: String,
    val language: String,
    val translatedLyrics: String,
    val translationSource: String,
    val contributorName: String? = null,
    val contributorEmail: String? = null,
)

/** Body of `POST /v1/vote`. */
@Serializable
data class BackendVoteBody(
    val targetType: String,
    val targetId: String,
    val value: Int,
)
