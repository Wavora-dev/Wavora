package com.wavora.domain.mediaservice.session

import com.wavora.domain.model.player.PlayerError

/**
 * Single source of truth for "what is the player doing right now".
 *
 * Replaces the previous combination of [com.wavora.domain.mediaservice.handler.ControlState.isPlaying]
 * and [com.wavora.domain.mediaservice.handler.SimpleMediaState], which required consumers to
 * combine two flows to know whether playback was actually happening.
 *
 * Transitions are owned by whoever maps platform player events (Media3 / vlcj) into this type
 * (see Phase 2/3 of the PlayerSession migration). This type itself has no behaviour.
 */
sealed class PlaybackState {
    /** No media loaded, nothing prepared. Initial state and state after [reset]. */
    data object Idle : PlaybackState()

    /** A media item was set/loaded and the player is getting ready to play it. */
    data object Preparing : PlaybackState()

    /** Media item is prepared and duration is known, but playback hasn't started yet. */
    data object Ready : PlaybackState()

    /** Actively playing audio. */
    data object Playing : PlaybackState()

    /** Playback is paused by the user or the system (e.g. audio focus loss). */
    data object Paused : PlaybackState()

    /** Moving from one media item to the next/previous in the queue. */
    data object Transitioning : PlaybackState()

    /** Waiting for data (network buffering), independent of play/pause intent. */
    data object Buffering : PlaybackState()

    /** Queue finished, nothing left to play. */
    data object Ended : PlaybackState()

    /** Unrecoverable (for this item) playback error. */
    data class Error(
        val error: PlayerError,
    ) : PlaybackState()

    /** True for any state where audio could currently be audible. */
    val isActive: Boolean
        get() = this is Playing || this is Transitioning

    companion object {
        fun initial(): PlaybackState = Idle
    }
}
