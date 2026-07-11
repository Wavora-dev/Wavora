package com.wavora.domain.mediaservice.session

/**
 * Playback position, independent from [PlaybackState].
 *
 * Previously, position/duration/buffered percentage were folded into
 * [com.wavora.domain.mediaservice.handler.SimpleMediaState] as separate sealed subtypes
 * (`Progress`, `Loading`, `Buffering`), which forced every consumer that only cared about
 * "where are we in the song" to filter that sealed class by type first (see the sponsor-block
 * skip and watch-time tracking collectors in MediaServiceHandlerImpl). This type is emitted on
 * every position tick regardless of [PlaybackState], so consumers no longer need to filter.
 */
data class PlaybackProgress(
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val bufferedPercentage: Int = 0,
) {
    /** 0f..1f, or -1f when duration is unknown (e.g. live/radio streams). */
    val fraction: Float
        get() = if (durationMs > 0L) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else -1f

    companion object {
        fun initial(): PlaybackProgress = PlaybackProgress()
    }
}
