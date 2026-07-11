package com.wavora.domain.mediaservice.session

import com.wavora.domain.manager.DataStoreManager
import com.wavora.domain.mediaservice.handler.MediaPlayerHandler
import com.wavora.domain.mediaservice.handler.QueueData
import com.wavora.domain.mediaservice.handler.RepeatState
import com.wavora.domain.mediaservice.handler.SimpleMediaState
import com.wavora.domain.model.player.GenericMediaItem
import com.wavora.domain.model.player.PlayerError
import com.wavora.logger.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Derives a [PlayerSession] from an existing [MediaPlayerHandler], WITHOUT modifying
 * [MediaPlayerHandler], its Android implementation (`MediaServiceHandlerImpl`), or its Desktop
 * implementation (`JvmMediaPlayerHandlerImpl`).
 *
 * This is intentionally an adapter over the handler's public StateFlows, not a change to the
 * handler itself. Since it only touches the common `MediaPlayerHandler` interface, this single
 * class works for Android AND Desktop, and carries zero risk to Android Auto, notifications,
 * Bluetooth, or widgets: none of that code is touched, and this class does not register any
 * `Player.Listener` of its own — it only re-observes StateFlows the handler already exposes.
 *
 * Two fields needed a deliberate decision instead of a silent guess, resolved as follows:
 *
 * - [speed]: `MediaPlayerHandler` has no reactive speed field — the real source of truth is
 *   `DataStoreManager.playbackSpeed`, which is what `MediaServiceHandlerImpl` /
 *   `JvmMediaPlayerHandlerImpl` already read to apply speed to the player (see the
 *   `playbackSpeedPitchJob` in `MediaServiceHandlerImpl`). `DataStoreManager` already lives in
 *   `core/domain`, so taking it as a second constructor parameter here doesn't cross any layer
 *   boundary, and requires zero changes to the existing handler implementations.
 * - [error]: `MediaPlayerHandler.pushPlayerError` is a single-assignment callback already wired
 *   to Crashlytics/Sentry in `MainActivity.kt` / `DesktopApp.kt`. This adapter does NOT touch
 *   that assignment (doing so could silently break crash reporting depending on init order).
 *   Instead, it exposes [reportError], which those two files call ALONGSIDE their existing
 *   Crashlytics/Sentry call — additive, not a replacement. [error] auto-clears whenever
 *   [currentSong] changes, matching the documented "cleared when a new item loads successfully"
 *   contract on [PlayerSession.error].
 *
 * Everything else here — [currentSong], [queue], [playbackState], [progress], [repeat],
 * [shuffle], [buffering] — is derived purely from flows the handler already emits today.
 */
@OptIn(ExperimentalTime::class)
class PlayerSessionAdapter(
    handler: MediaPlayerHandler,
    dataStoreManager: DataStoreManager,
    scope: CoroutineScope,
) : PlayerSession {
    override val currentSong: StateFlow<GenericMediaItem?> = handler.nowPlaying

    override val queue: StateFlow<QueueData> = handler.queueData.let { flow ->
        // handler.queueData is StateFlow<QueueData?> in the public interface's declared type
        // inference but is always initialized non-null; guard defensively anyway.
        flow.map { it ?: QueueData() }.stateIn(scope, SharingStarted.Eagerly, flow.value ?: QueueData())
    }

    override val repeat: StateFlow<RepeatState> =
        handler.controlState
            .map { it.repeatState }
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.Eagerly, handler.controlState.value.repeatState)

    override val shuffle: StateFlow<Boolean> =
        handler.controlState
            .map { it.isShuffle }
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.Eagerly, handler.controlState.value.isShuffle)

    override val speed: StateFlow<Float> =
        dataStoreManager.playbackSpeed
            .stateIn(scope, SharingStarted.Eagerly, 1f)

    override val volume: StateFlow<Float> =
        handler.controlState
            .map { it.volume }
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.Eagerly, handler.controlState.value.volume)

    // PROMPT_03 optimization (see Flow & Coroutine Audit report). `handler.simpleMediaState` is a
    // "god flow" (Fase 4): it carries BOTH playback-lifecycle info (Initial/Ended/Ready/Loading/
    // Buffering) AND high-frequency position ticks (Progress every 100ms via the progressJob
    // poll, Loading's bufferedPercentage every 500ms via bufferedJob — see
    // MediaServiceHandlerImpl.startProgressUpdate/startBufferedUpdate). `playbackState` and
    // `buffering` below only care about which "kind" of state we're in, never the embedded
    // position/duration/buffered payload — but without this distinctUntilChanged, combine()/map()
    // would still re-run their lambda on every single one of those ~10-12 ticks/second even
    // though `mapPlaybackState`/`is Loading || is Buffering` produce the exact same result for
    // consecutive Progress (or consecutive Loading) emissions. `progress` below is UNAFFECTED —
    // it deliberately keeps consuming every raw tick, since position is exactly what it exists to
    // report (Fase 5's "PlaybackProgress must live separately" was already true for `progress`
    // itself; this fix makes `playbackState`/`buffering` actually take advantage of that
    // separation instead of still reacting to every tick that flows past them).
    private val simpleMediaStateKind: Flow<SimpleMediaState> =
        handler.simpleMediaState.distinctUntilChanged { old, new -> mediaStateKind(old) == mediaStateKind(new) }

    private fun mediaStateKind(state: SimpleMediaState): Int =
        when (state) {
            is SimpleMediaState.Initial -> 0
            is SimpleMediaState.Ended -> 1
            is SimpleMediaState.Loading -> 2
            is SimpleMediaState.Buffering -> 3
            // Ready and Progress are grouped together: mapPlaybackState() treats them
            // identically (both resolve purely from controlState.isPlaying), so re-running that
            // computation on every Progress tick between them is pure waste.
            is SimpleMediaState.Ready, is SimpleMediaState.Progress -> 4
        }

    override val buffering: StateFlow<Boolean> =
        simpleMediaStateKind
            .map { it is SimpleMediaState.Loading || it is SimpleMediaState.Buffering }
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.Eagerly, false)

    private val _error = MutableStateFlow<PlayerError?>(null)
    override val error: StateFlow<PlayerError?> = _error.asStateFlow()

    /**
     * Call this from wherever `MediaPlayerHandler.pushPlayerError` is already assigned
     * (`MainActivity.kt`, `DesktopApp.kt`), ALONGSIDE the existing Crashlytics/Sentry call —
     * never instead of it. See class doc.
     */
    fun reportError(error: PlayerError) {
        _error.value = error
    }

    // PROMPT_03 optimization, second half of the same fix: `handler.controlState` is ALSO a
    // fairly wide object (isPlaying, isShuffle, repeatState, isLiked, isNextAvailable,
    // isPreviousAvailable, isCrossfading, volume — Fase 4's "god flow" pattern again, one level
    // up). `playbackState` only ever reads `isPlaying` from it, so toggling shuffle/repeat/
    // volume/like while a song plays would otherwise re-run `mapPlaybackState()` for no reason.
    private val isPlayingOnly: Flow<Boolean> =
        handler.controlState.map { it.isPlaying }.distinctUntilChanged()

    override val playbackState: StateFlow<PlaybackState> =
        combine(simpleMediaStateKind, isPlayingOnly) { mediaState, isPlaying ->
            mapPlaybackState(mediaState, isPlaying)
        }.stateIn(scope, SharingStarted.Eagerly, PlaybackState.initial())

    override val progress: StateFlow<PlaybackProgress> =
        handler.simpleMediaState
            .scan(PlaybackProgress.initial()) { previous, mediaState ->
                mapProgress(previous, mediaState)
            }.stateIn(scope, SharingStarted.Eagerly, PlaybackProgress.initial())

    init {
        // Clears a previously reported error once a new item starts loading, per the documented
        // contract on PlayerSession.error. Skips the very first emission so a null->null clear
        // doesn't run before any real song has ever loaded.
        currentSong
            .onEach { if (_error.value != null) _error.value = null }
            .launchIn(scope)

        // Fase 1/8/9 instrumentation (see PROMPT_02 Playback Transition Report). Logs every
        // PlaybackState transition PlayerSession computes — e.g. "Playing -> Paused" with no
        // Buffering/Transitioning in between is exactly the failure signature we're looking for.
        // Deliberately placed in `init` (runs after every property above is assigned) rather than
        // inline in `playbackState`'s own definition, since `playbackState` uses
        // SharingStarted.Eagerly and starts collecting during construction — reading sibling
        // properties like `progress`/`currentSong` from inside that same combine() would risk
        // reading them before they're initialized. Tag PB_TRACE is shared with the platform-level
        // logs added in MediaServiceHandlerImpl / CrossfadeExoPlayerAdapter / VlcPlayerAdapter, so
        // filtering logcat/log output by that single tag shows the whole chain in order.
        var lastLoggedState: PlaybackState? = null
        playbackState
            .onEach { state ->
                val previous = lastLoggedState
                lastLoggedState = state
                if (previous != null && previous != state) {
                    Logger.d(
                        "PB_TRACE",
                        "t=${Clock.System.now()} PlayerSession.playbackState: $previous -> $state | " +
                            "song=${currentSong.value?.mediaId} pos=${progress.value.positionMs} buffered=${progress.value.bufferedPositionMs}",
                    )
                }
            }.launchIn(scope)
    }

    private fun mapPlaybackState(
        mediaState: SimpleMediaState,
        isPlaying: Boolean,
    ): PlaybackState =
        when (mediaState) {
            is SimpleMediaState.Initial -> PlaybackState.Idle
            is SimpleMediaState.Ended -> PlaybackState.Ended
            // Loading covers both the initial buffer-before-first-play and any mid-stream
            // re-buffer; the current codebase doesn't distinguish those two cases (both raise
            // onIsLoadingChanged the same way), so both collapse to Buffering here. Splitting
            // "Preparing" (first load) from "Buffering" (re-buffer) would need a new signal from
            // MediaServiceHandlerImpl, not something this read-only adapter can infer.
            is SimpleMediaState.Loading -> PlaybackState.Buffering
            is SimpleMediaState.Buffering -> PlaybackState.Buffering
            is SimpleMediaState.Ready, is SimpleMediaState.Progress ->
                if (isPlaying) PlaybackState.Playing else PlaybackState.Paused
        }

    private fun mapProgress(
        previous: PlaybackProgress,
        mediaState: SimpleMediaState,
    ): PlaybackProgress =
        when (mediaState) {
            is SimpleMediaState.Ready -> previous.copy(durationMs = mediaState.duration)
            is SimpleMediaState.Loading ->
                previous.copy(
                    durationMs = mediaState.duration,
                    bufferedPercentage = mediaState.bufferedPercentage,
                )
            is SimpleMediaState.Progress -> previous.copy(positionMs = mediaState.progress)
            is SimpleMediaState.Buffering -> previous.copy(positionMs = mediaState.position)
            is SimpleMediaState.Initial -> PlaybackProgress.initial()
            is SimpleMediaState.Ended -> previous
        }
}
