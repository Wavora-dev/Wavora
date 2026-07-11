package com.wavora.lyrics.provider

/**
 * Identifies each external lyrics source Wavora can query through the
 * registry. Spotify and YouTube captions are intentionally NOT part of
 * this registry: both need OAuth/session state owned by DataStoreManager
 * (core/domain), and pulling that dependency into core/service/lyricsService
 * would create a cycle. They keep being called directly from
 * LyricsCanvasRepositoryImpl, exactly as before this integration.
 */
enum class LyricsProviderId {
    WAVORA,
    LRCLIB,
    BETTER_LYRICS,
}

/**
 * Common contract for a source [LyricsManager] can query through the
 * registry for a plain text-search style lookup (title + artist +
 * optional duration -> raw lyrics text in some markup: LRC or TTML).
 *
 * [WavoraLyricsProvider] does NOT implement this interface: its contract
 * is richer (get-by-id, translations, voting, contributions) than a
 * simple search, so [LyricsManager] talks to it directly. This interface
 * exists for the two simpler, stateless search providers.
 */
interface LyricsProvider {
    val id: LyricsProviderId

    /** Returns the raw lyrics markup (LRC or TTML depending on the
     * provider) for the best match, or `null` when nothing was found. */
    suspend fun searchLyrics(
        track: String,
        artist: String,
        durationSeconds: Int?,
    ): Result<String?>
}
