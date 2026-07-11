package com.wavora.domain.mediaservice.session

import com.wavora.domain.mediaservice.handler.QueueData
import com.wavora.domain.mediaservice.handler.RepeatState
import com.wavora.domain.model.player.GenericMediaItem
import com.wavora.domain.model.player.PlayerError
import kotlinx.coroutines.flow.StateFlow

/**
 * Single source of truth for playback UI state.
 *
 * [PlayerSession] is deliberately **read-only**: it only exposes [StateFlow]s to observe. Mutating
 * playback (play/pause/seek/skip) goes exclusively through [PlayerController]. This split mirrors
 * the existing convention in the codebase (e.g. `MediaPlayerInterface` for control vs
 * `MediaPlayerHandler`'s StateFlows for observation), but consolidates observation into one object
 * instead of several independently-injected StateFlows.
 *
 * No ViewModel, Composable, widget, or notification code should read Media3 (`ExoPlayer`,
 * `Player.Listener`, `MediaController`) or vlcj types directly — everything goes through this
 * interface. (As of this analysis that rule already holds at the ViewModel layer; PlayerSession
 * makes it structural instead of just a convention.)
 *
 * Phase 1 note: this interface has no implementation yet. It exists so call sites can start being
 * written/reviewed against it before MediaServiceHandlerImpl / JvmMediaPlayerHandlerImpl are
 * migrated to produce it (Phase 2/3).
 */
interface PlayerSession {
    /** Currently loaded media item, or null if nothing is loaded ([PlaybackState.Idle]). */
    val currentSong: StateFlow<GenericMediaItem?>

    /** The full queue, including its source (playlist/album/radio/local) and load state. */
    val queue: StateFlow<QueueData>

    /** Single source of truth for play/pause/buffering/error — see [PlaybackState]. */
    val playbackState: StateFlow<PlaybackState>

    /** Position/duration/buffered — independent from [playbackState], see [PlaybackProgress]. */
    val progress: StateFlow<PlaybackProgress>

    val repeat: StateFlow<RepeatState>

    val shuffle: StateFlow<Boolean>

    /** Playback speed multiplier, e.g. 1.0f = normal speed. */
    val speed: StateFlow<Float>

    /** 0f..1f. */
    val volume: StateFlow<Float>

    /**
     * True while the player is waiting for data. Distinct from [PlaybackState.Buffering]: a
     * transient buffering blip while [playbackState] is [PlaybackState.Playing] still reports
     * [buffering] = true without moving the whole state machine to Buffering, matching current
     * `onIsLoadingChanged` behaviour.
     */
    val buffering: StateFlow<Boolean>

    /** Last playback error, if any. Cleared when a new item loads successfully. */
    val error: StateFlow<PlayerError?>
}
