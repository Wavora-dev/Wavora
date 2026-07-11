package com.wavora.lyrics.provider

import com.wavora.lyrics.WavoraLyricsProvider

/**
 * Holds one instance of each external lyrics source and hands them out
 * by id. This is the `LyricsProviderRegistry` in the
 * `NowPlayingViewModel → LyricsManager → LyricsProviderRegistry →
 * WavoraLyricsProvider → Cloudflare Backend` chain.
 *
 * [wavora] is exposed directly (not through [provider]) because its
 * contract is richer than the simple search-only [LyricsProvider]
 * interface: get-by-id, translations, voting and contributions, none of
 * which make sense for LRCLIB/BetterLyrics. [lrclib] and [betterLyrics]
 * both implement [LyricsProvider] and are reachable either directly or
 * through [provider] for callers that only care about the id.
 *
 * Spotify and YouTube captions are NOT part of this registry - see
 * [LyricsProviderId]'s docs for why - and keep being called directly by
 * `LyricsCanvasRepositoryImpl`, unaffected by this integration.
 */
class LyricsProviderRegistry(
    val wavora: WavoraLyricsProvider = WavoraLyricsProvider(),
    val lrclib: LrclibProvider = LrclibProvider(),
    val betterLyrics: BetterLyricsProvider = BetterLyricsProvider(),
) {
    private val searchProviders: Map<LyricsProviderId, LyricsProvider> =
        mapOf(
            LyricsProviderId.LRCLIB to lrclib,
            LyricsProviderId.BETTER_LYRICS to betterLyrics,
        )

    /** Looks up a search-only provider by id. Returns `null` for
     * [LyricsProviderId.WAVORA] - use [wavora] directly instead, its API
     * is richer than [LyricsProvider.searchLyrics]. */
    fun provider(id: LyricsProviderId): LyricsProvider? = searchProviders[id]
}
