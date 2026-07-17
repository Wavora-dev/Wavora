package com.wavora.media_jvm

import com.wavora.common.MERGING_DATA_TYPE
import com.wavora.domain.model.player.GenericMediaItem
import com.wavora.domain.model.player.GenericPlaybackParameters
import com.wavora.domain.model.player.PlayerConstants
import com.wavora.domain.model.player.PlayerError
import com.wavora.domain.extension.isVideo
import com.wavora.domain.manager.DataStoreManager
import com.wavora.domain.mediaservice.player.MediaPlayerInterface
import com.wavora.domain.mediaservice.player.MediaPlayerListener
import com.wavora.domain.repository.StreamRepository
import com.wavora.logger.Logger
import com.wavora.media_jvm.download.getDownloadPath
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import javax.swing.JPanel

private const val TAG = "VlcPlayerAdapter"

/**
 * Structured, single-purpose logger for the Windows crossfade audit
 * (see the user's request: exact-cause instrumentation, not another
 * "this probably fixes it" patch). Every line goes through here so
 * they're all searchable under one tag/format and carry a timestamp +
 * thread name, which matters because the whole bug class under
 * investigation is about *ordering* across the VLC native callback
 * thread, the coroutine (Main/Swing) thread, and the position-poll
 * loop.
 *
 * Log line shape:
 *   CROSSFADE_AUDIT | t=<epochMs> | thread=<name> | event=<EVENT> | player=<id|none> | role=<role|-> | <details>
 *
 * `player` is the [VlcPlayer.id] (assigned at construction, stable for
 * that instance's whole lifetime) so two players active at once during
 * a crossfade can always be told apart in the log, including after one
 * of them is released.
 */
private object CrossfadeAudit {
    private const val AUDIT_TAG = "CROSSFADE_AUDIT"
    private val nextId = java.util.concurrent.atomic.AtomicInteger(0)

    fun nextPlayerId(): Int = nextId.incrementAndGet()

    fun log(
        event: String,
        playerId: Int? = null,
        role: String? = null,
        details: String = "",
    ) {
        val playerStr = playerId?.toString() ?: "none"
        val roleStr = role ?: "-"
        Logger.d(
            AUDIT_TAG,
            "t=${System.currentTimeMillis()} thread=${Thread.currentThread().name} " +
                "event=$event player=$playerStr role=$roleStr $details",
        )
    }
}

/**
 * VLC (vlcj) implementation of MediaPlayerInterface
 * Features:
 * - Queue management with auto-load for next track
 * - Precaching system for smooth transitions
 * - Crossfade transitions
 * - Audio + Video merging via --input-slave (equivalent to Android MergingMediaSource)
 * - Built-in equalizer support
 * - No external installation required (when bundled via vlc-setup plugin)
 */
class VlcPlayerAdapter(
    private val coroutineScope: CoroutineScope,
    private val dataStoreManager: DataStoreManager,
    private val streamRepository: StreamRepository,
) : MediaPlayerInterface {
    // Internal state enum for proper state machine
    private enum class InternalState {
        IDLE,
        PREPARING,
        READY,
        PLAYING,
        PAUSED,
        ENDED,
        ERROR,
    }

    private fun InternalState.isInReadyState(): Boolean = this == InternalState.READY || this == InternalState.PLAYING || this == InternalState.PAUSED

    // ========== VLC Factory ==========
    private val mediaPlayerFactory: MediaPlayerFactory

    init {
        System
            .getProperty("compose.application.resources.dir")
            ?.let { System.setProperty("jna.library.path", it) }
        // Use custom NativeDiscoveryStrategy to find bundled VLC libraries
        // DefaultVlcDiscoverer handles Windows/Linux, MacOsVlcDiscoverer handles macOS
        val discovery =
            NativeDiscovery(
                DefaultVlcDiscoverer(),
                MacOsVlcDiscoverer(),
            )
        val found = discovery.discover()
        if (!found) {
            Logger.e(TAG, "VLC native libraries not found! Please install VLC media player.")
        }

        val factoryArgs =
            mutableListOf(
                "--no-video-title-show",
                "--no-video",
                "--quiet",
                "--no-metadata-network-access",
                "--network-caching=3000",
            )

        mediaPlayerFactory = MediaPlayerFactory(discovery, *factoryArgs.toTypedArray())

        // Load crossfade settings
        coroutineScope.launch {
            dataStoreManager.crossfadeEnabled.collect { enabled ->
                crossfadeEnabled = (enabled == DataStoreManager.TRUE)
                Logger.d(TAG, "Crossfade enabled: $crossfadeEnabled")
            }
        }

        coroutineScope.launch {
            dataStoreManager.crossfadeDuration.collect { duration ->
                crossfadeDurationMs = duration
                Logger.d(TAG, "Crossfade duration: $crossfadeDurationMs ms")
            }
        }

        coroutineScope.launch {
            dataStoreManager.crossfadeDjMode.collect { enabled ->
                djCrossfadeEnabled = (enabled == DataStoreManager.TRUE)
                Logger.d(TAG, "DJ crossfade mode: $djCrossfadeEnabled")
            }
        }
    }

    // ========== State Management ==========
    private val listeners = mutableListOf<MediaPlayerListener>()

    @Volatile
    private var currentPlayer: VlcPlayer? = null

    // Tracks whether the current player is actually rendering video
    // (based on PlayableSource.isVideo, not GenericMediaItem metadata)
    @Volatile
    private var currentPlayerIsVideo = false

    @Volatile
    private var internalState = InternalState.IDLE

    @Volatile
    private var internalPlayWhenReady = true

    @Volatile
    private var internalVolume = 1.0f

    @Volatile
    private var internalRepeatMode = PlayerConstants.REPEAT_MODE_OFF

    @Volatile
    private var internalShuffleModeEnabled = false

    @Volatile
    private var internalPlaybackSpeed = 1.0f

    // Position tracking
    @Volatile
    private var cachedPosition = 0L

    @Volatile
    private var cachedDuration = 0L

    @Volatile
    private var cachedBufferedPosition = 0L

    @Volatile
    private var cachedIsLoading = false

    // Position update job (fallback polling for crossfade detection)
    private var positionUpdateJob: Job? = null

    // Precaching system
    private data class PrecachedPlayer(
        val player: VlcPlayer,
        val mediaItem: GenericMediaItem,
        val source: PlayableSource,
    )

    private val precachedPlayers = ConcurrentHashMap<String, PrecachedPlayer>()
    private var precacheEnabled = true
    private val maxPrecacheCount = 2
    private var precacheJob: Job? = null

    // ROOT CAUSE FIX (Windows crossfade double-freeze): triggerPrecachingInternal()
    // used to fire the instant a track transition (esp. finalizeCrossfade()) landed,
    // spinning up up to `maxPrecacheCount` (2) brand-new native VLC players — each
    // doing network resolution + media().prepare(), which opens a connection and
    // starts demuxing — on the SAME shared MediaPlayerFactory/libVLC instance the
    // just-promoted `currentPlayer` is using to decode in real time. That is exactly
    // the "arranca, se congela ~1-2s, sigue, se vuelve a congelar, después fluye
    // normal" pattern: freeze #1 = first precache player's create+prepare stealing
    // native decode/network bandwidth right as the new track's own
    // `:network-caching` buffer is still filling; freeze #2 = the second one
    // (maxPrecacheCount=2, staggered only 100ms apart — not nearly enough). This
    // isn't a timing/fade-duration issue at all — it's decoder contention.
    //
    // Fix: give the just-started track a real head start before we let precaching
    // touch the native player factory at all. `buildVlcOptions()` sets
    // `:network-caching=3000` for audio (the largest value currently used), so this
    // grace period needs to comfortably clear that fill window with margin — after
    // this, the current track's pipeline is stable and precaching for tracks that
    // are, at minimum, a full song away is in no hurry to start.
    private val precacheStartupGraceMs = 4000L

    // Minimum gap between successive precache players within one precache pass.
    // 100ms was not a real stagger — creating a second native player+prepare() that
    // soon after the first still lands inside the same contention window. This is
    // the second half of the double-freeze fix.
    private val precacheStaggerMs = 1500L

    // Crossfade system
    @Volatile
    private var crossfadeEnabled = false

    @Volatile
    private var crossfadeDurationMs = 5000

    @Volatile
    private var djCrossfadeEnabled = false

    @Volatile
    private var secondaryPlayer: VlcPlayer? = null

    @Volatile
    private var crossfadeJob: Job? = null

    @Volatile
    private var isCrossfading = false

    /** Index we're crossfading from; used when cancelling to revert localCurrentMediaItemIndex. */
    @Volatile
    private var crossfadeFromIndex = -1

    // AutoMix metadata cache (in-memory, populated from Tidal via NewFormatEntity)
    private val audioMetaCache = ConcurrentHashMap<String, SongAudioMeta>()

    private fun setCrossfading(value: Boolean) {
        if (isCrossfading != value) {
            isCrossfading = value
            notifyListeners { onCrossfadeStateChanged(value) }
            Logger.d(TAG, "Crossfade state changed: $value")
        }
    }

    // Retry system - mirrors Android CrossfadeExoPlayerAdapter retry logic
    private var retryCount = 0
    private var retryVideoId: String? = null
    private val maxRetryCount = 2

    // Playlist management
    private val playlist = mutableListOf<GenericMediaItem>()
    private var localCurrentMediaItemIndex = -1

    // Shuffle management
    private var shuffleIndices = mutableListOf<Int>()
    private var shuffleOrder = mutableListOf<Int>()

    // Loading management
    private var currentLoadJob: Job? = null

    fun getCurrentPlayer(): VlcPlayer? = currentPlayer

    // Video surface state - UI collects this to display video
    private val _currentVideoSurface = MutableStateFlow<Component?>(null)
    val currentVideoSurface: StateFlow<Component?> = _currentVideoSurface.asStateFlow()

    // ========== Playback Source ==========
    private data class PlayableSource(
        val isVideo: Boolean,
        val url: String,
        val audioSlaveUrl: String? = null, // For merging: audio URL as --input-slave
    )

    // ========== Playback Control ==========

    override fun play() {
        Logger.d(TAG, "play() called (current state: $internalState)")
        coroutineScope.launch {
            when (internalState) {
                InternalState.READY, InternalState.ENDED, InternalState.PAUSED -> {
                    currentPlayer?.let { player ->
                        Logger.d(TAG, "Play: calling VLC play")
                        player.play()
                        transitionToState(InternalState.PLAYING)
                        internalPlayWhenReady = true
                    } ?: Logger.w(TAG, "Play called but currentPlayer is null")
                }

                InternalState.PREPARING -> {
                    if (!cachedIsLoading) {
                        cachedIsLoading = true
                        notifyListeners { onIsLoadingChanged(true) }
                    }
                    internalPlayWhenReady = true
                    Logger.d(TAG, "Play: During PREPARING - will auto-play when ready")
                }

                InternalState.PLAYING -> {
                    internalPlayWhenReady = true
                    cachedIsLoading = false
                }

                else -> {
                    Logger.w(TAG, "Play: Called in invalid state: $internalState")
                }
            }
        }
    }

    override fun pause() {
        Logger.d(
            "PB_TRACE",
            "t=${java.time.Instant.now()} thread=${Thread.currentThread().name} " +
                "VlcPlayerAdapter.pause() CALLED | state=$internalState playWhenReady=$internalPlayWhenReady " +
                "isCrossfading=$isCrossfading song=${currentPlayer?.let { playlist.getOrNull(localCurrentMediaItemIndex)?.mediaId }}",
        )
        Logger.d(TAG, "pause() called (current state: $internalState)")
        coroutineScope.launch {
            // Cancel any ongoing crossfade and await completion before proceeding
            if (isCrossfading) {
                Logger.d(TAG, "Pause: Cancelling crossfade")
                cancelCrossfadeAndCleanup(revertIndex = true)
            }

            // A precached player may be mid pre-roll (playing muted ahead of the
            // real crossfade, see PRECACHE_AUDIO_PREROLL_LEAD_MS). If the user
            // pauses during that window, stop it from continuing to play silently
            // in the background and reset its position/primed flag so the real
            // crossfade — whenever playback resumes — starts it fresh from 0
            // instead of resuming from wherever the pre-roll had drifted to.
            pauseAnyPrimedPrecachePlayer()

            when (internalState) {
                InternalState.PLAYING, InternalState.READY -> {
                    currentPlayer?.let { player ->
                        Logger.d(TAG, "Pause: calling VLC pause")
                        player.pause()
                        transitionToState(InternalState.PAUSED)
                        internalPlayWhenReady = false
                    }
                }

                InternalState.PREPARING -> {
                    internalPlayWhenReady = false
                    Logger.d(TAG, "Pause: During PREPARING - will not auto-play")
                }

                else -> {
                    Logger.w(TAG, "Pause: Called in invalid state: $internalState")
                }
            }
        }
    }

    override fun stop() {
        coroutineScope.launch {
            currentPlayer?.let { player ->
                Logger.d(TAG, "Stop called")
                player.stop()
                transitionToState(InternalState.IDLE)
                stopPositionUpdates()
                notifyEqualizerIntent(false)
            }
        }
    }

    override fun seekTo(positionMs: Long) {
        currentPlayer?.let { player ->
            try {
                player.seekTo(positionMs)
                cachedPosition = positionMs
                Logger.d(TAG, "Seeked to position: $positionMs")
            } catch (e: Exception) {
                Logger.e(TAG, "Seek exception: ${e.message}", e)
            }
        }
    }

    override fun seekTo(
        mediaItemIndex: Int,
        positionMs: Long,
    ) {
        if (mediaItemIndex !in playlist.indices) return

        // ROOT CAUSE FIX (see PROMPT_02 Playback Transition Report) — same fix as
        // CrossfadeExoPlayerAdapter.seekTo() on Android, same underlying bug: `shouldPlay` must
        // be captured synchronously here, before the coroutine below is even launched, not read
        // from inside it. handleTrackEndInternal() -> seekToNext() -> this function runs
        // synchronously from the VLC end-reached callback, at the exact instant we know playback
        // was active. Reading `internalPlayWhenReady` later, inside `coroutineScope.launch { }`,
        // left a TOCTOU window where a concurrently-dispatched pause() (which also mutates
        // `internalPlayWhenReady` inside its own coroutine) could run first and silently leave
        // the next track paused, intermittently, whenever some other event landed in that gap.
        val shouldPlay = internalPlayWhenReady
        Logger.d(
            "PB_TRACE",
            "t=${java.time.Instant.now()} thread=${Thread.currentThread().name} " +
                "VlcPlayerAdapter.seekTo(index=$mediaItemIndex) shouldPlay captured synchronously = $shouldPlay",
        )

        coroutineScope.launch {
            // Cancel any ongoing crossfade and await completion
            if (isCrossfading) {
                Logger.d(TAG, "seekTo: Cancelling crossfade")
                cancelCrossfadeAndCleanup(revertIndex = false)
            }

            // Cancel any ongoing load
            currentLoadJob?.cancel()

            localCurrentMediaItemIndex = mediaItemIndex
            // Release any precached VLC players that fall outside the new precache window
            // (current+1..current+maxPrecacheCount). Without this, jumping around the queue
            // (tapping a track, shuffle, repeated skips) leaks native VLC player instances —
            // each one already prepared/buffering — which is a direct contributor to sustained
            // high CPU usage on Desktop. The track we're about to play, if already precached,
            // is unaffected: it's matched and reused by mediaId inside loadAndPlayTrackInternal.
            clearPrecacheExceptCurrentInternal()
            currentPlayer?.release()
            currentPlayer = null
            currentPlayerIsVideo = false
            _currentVideoSurface.value = null
            loadAndPlayTrackInternal(mediaItemIndex, positionMs, shouldPlay)
        }
    }

    override fun seekBack() {
        val newPosition = (cachedPosition - 5000).coerceAtLeast(0)
        seekTo(newPosition)
    }

    override fun seekForward() {
        val newPosition = (cachedPosition + 5000).coerceAtMost(cachedDuration)
        seekTo(newPosition)
    }

    override fun seekToNext() {
        if (hasNextMediaItem()) {
            // During crossfade A→A+1: user pressing "next" means go to the track we're fading in (A+1).
            // localCurrentMediaItemIndex was already updated to A+1 in triggerCrossfadeTransition,
            // so getNextMediaItemIndex() would return A+2. We must seek to localCurrentMediaItemIndex instead.
            if (isCrossfading) {
                val targetIndex = localCurrentMediaItemIndex
                Logger.d(TAG, "seekToNext: Cancelling crossfade, seeking to track we're fading in (index $targetIndex)")
                coroutineScope.launch {
                    cancelCrossfadeAndCleanup(revertIndex = false)
                    seekTo(targetIndex, 0)
                }
                return
            }

            val nextIndex = getNextMediaItemIndex()
            seekTo(nextIndex, 0)
        }
    }

    override fun seekToPrevious() {
        coroutineScope.launch {
            // Cancel any ongoing crossfade first and revert index, awaiting completion
            if (isCrossfading) {
                Logger.d(TAG, "seekToPrevious: Cancelling crossfade")
                cancelCrossfadeAndCleanup(revertIndex = true)
            }

            // Standard music player behavior:
            // Position > 3s → seek to start of current track
            // Position <= 3s → go to previous track
            val positionThresholdMs = 3000L
            if (cachedPosition > positionThresholdMs) {
                Logger.d(TAG, "seekToPrevious: pos=${cachedPosition}ms > ${positionThresholdMs}ms — seeking to start")
                currentPlayer?.seekTo(0)
                cachedPosition = 0
            } else if (hasPreviousMediaItem()) {
                Logger.d(TAG, "seekToPrevious: pos=${cachedPosition}ms <= ${positionThresholdMs}ms — going to previous track")
                val prevIndex = getPreviousMediaItemIndex()
                seekTo(prevIndex, 0)
            } else {
                Logger.d(TAG, "seekToPrevious: No previous item, seeking to start")
                currentPlayer?.seekTo(0)
                cachedPosition = 0
            }
        }
    }

    override fun seekToPreviousMediaItem() {
        coroutineScope.launch {
            // Cancel any ongoing crossfade first (mirror seekToPrevious()).
            if (isCrossfading) {
                Logger.d(TAG, "seekToPreviousMediaItem: Cancelling crossfade")
                cancelCrossfadeAndCleanup(revertIndex = true)
            }

            // Always go to the previous track — skips the 3-second "seek to start"
            // rule used by seekToPrevious().
            if (hasPreviousMediaItem()) {
                val prevIndex = getPreviousMediaItemIndex()
                Logger.d(TAG, "seekToPreviousMediaItem: jumping to previous index=$prevIndex")
                seekTo(prevIndex, 0)
            } else {
                Logger.d(TAG, "seekToPreviousMediaItem: No previous item — no-op")
            }
        }
    }

    override fun prepare() {
        if (playlist.isNotEmpty() && localCurrentMediaItemIndex >= 0) {
            coroutineScope.launch {
                loadAndPlayTrackInternal(localCurrentMediaItemIndex, 0, false)
            }
        }
    }

    // ========== Media Item Management ==========

    override fun setMediaItem(mediaItem: GenericMediaItem) {
        coroutineScope.launch {
            currentLoadJob?.cancel()
            cancelPrecaching()

            playlist.clear()
            clearAllPrecacheInternal()
            playlist.add(mediaItem)
            localCurrentMediaItemIndex = 0

            if (internalShuffleModeEnabled) {
                createShuffleOrder()
            }

            notifyTimelineChanged("TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED")
            loadAndPlayTrackInternal(0, 0, internalPlayWhenReady)
        }
    }

    override fun addMediaItem(mediaItem: GenericMediaItem) {
        playlist.add(mediaItem)

        if (internalShuffleModeEnabled) {
            createShuffleOrder()
        }

        notifyTimelineChanged("TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED")

        if (playlist.size - 1 - currentMediaItemIndex <= maxPrecacheCount) {
            coroutineScope.launch {
                clearPrecacheExceptCurrentInternal()
                triggerPrecachingInternal()
            }
        }
    }

    override fun addMediaItem(
        index: Int,
        mediaItem: GenericMediaItem,
    ) {
        if (index in 0..playlist.size) {
            val currentIndexBeforeInsert = localCurrentMediaItemIndex

            playlist.add(index, mediaItem)

            if (index <= localCurrentMediaItemIndex) {
                localCurrentMediaItemIndex++
            }

            if (internalShuffleModeEnabled) {
                if (currentIndexBeforeInsert >= 0 && index == currentIndexBeforeInsert + 1) {
                    val currentShufflePos = shuffleIndices.getOrNull(currentIndexBeforeInsert) ?: 0
                    insertIntoShuffleOrder(index, currentShufflePos)
                } else {
                    createShuffleOrder()
                }
            }

            notifyTimelineChanged("TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED")

            if (index - 1 - currentMediaItemIndex <= maxPrecacheCount) {
                coroutineScope.launch {
                    clearPrecacheExceptCurrentInternal()
                    triggerPrecachingInternal()
                }
            }
        }
    }

    override fun removeMediaItem(index: Int) {
        if (index !in playlist.indices) return

        coroutineScope.launch {
            val track = playlist.removeAt(index)

            precachedPlayers.remove(track.mediaId)?.let { cached ->
                cleanupPlayerInternal(cached.player)
            }

            when {
                index < localCurrentMediaItemIndex -> {
                    localCurrentMediaItemIndex--
                    clearPrecacheExceptCurrentInternal()
                    triggerPrecachingInternal()
                }

                index == localCurrentMediaItemIndex -> {
                    if (localCurrentMediaItemIndex >= playlist.size) {
                        localCurrentMediaItemIndex = playlist.size - 1
                    }
                    if (localCurrentMediaItemIndex >= 0) {
                        loadAndPlayTrackInternal(localCurrentMediaItemIndex, 0, internalPlayWhenReady)
                    } else {
                        cleanupCurrentPlayerInternal()
                    }
                }

                else -> {
                    clearPrecacheExceptCurrentInternal()
                    triggerPrecachingInternal()
                }
            }

            if (internalShuffleModeEnabled) {
                createShuffleOrder()
            }

            notifyTimelineChanged("TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED")
        }
    }

    override fun moveMediaItem(
        fromIndex: Int,
        toIndex: Int,
    ) {
        if (fromIndex !in playlist.indices || toIndex !in playlist.indices) return

        coroutineScope.launch {
            val item = playlist.removeAt(fromIndex)
            playlist.add(toIndex, item)

            localCurrentMediaItemIndex =
                when {
                    localCurrentMediaItemIndex == fromIndex -> {
                        toIndex
                    }
                    fromIndex < localCurrentMediaItemIndex && toIndex >= localCurrentMediaItemIndex -> {
                        localCurrentMediaItemIndex - 1
                    }
                    fromIndex > localCurrentMediaItemIndex && toIndex <= localCurrentMediaItemIndex -> {
                        localCurrentMediaItemIndex + 1
                    }
                    else -> {
                        localCurrentMediaItemIndex
                    }
                }

            if (internalShuffleModeEnabled) {
                createShuffleOrder()
            }

            notifyTimelineChanged("TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED")

            clearPrecacheExceptCurrentInternal()
            triggerPrecachingInternal()
        }
    }

    override fun clearMediaItems() {
        coroutineScope.launch {
            playlist.clear()
            localCurrentMediaItemIndex = -1
            clearShuffleOrder()
            notifyTimelineChanged("TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED")
            cleanupCurrentPlayerInternal()
            clearAllPrecacheInternal()
        }
    }

    override fun replaceMediaItem(
        index: Int,
        mediaItem: GenericMediaItem,
    ) {
        if (index !in playlist.indices) return

        coroutineScope.launch {
            playlist[index] = mediaItem

            precachedPlayers.remove(mediaItem.mediaId)?.let { cached ->
                cleanupPlayerInternal(cached.player)
            }

            if (internalShuffleModeEnabled) {
                createShuffleOrder()
            }

            notifyTimelineChanged("TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED")

            if (index == localCurrentMediaItemIndex) {
                loadAndPlayTrackInternal(index, 0, internalPlayWhenReady)
            } else {
                triggerPrecachingInternal()
            }
        }
    }

    override fun getMediaItemAt(index: Int): GenericMediaItem? = playlist.getOrNull(index)

    override fun getCurrentMediaTimeLine(): List<GenericMediaItem> =
        if (internalShuffleModeEnabled) {
            shuffleOrder.mapNotNull { shuffledIndex -> playlist.getOrNull(shuffledIndex) }
        } else {
            playlist.toList()
        }

    override fun getUnshuffledIndex(shuffledIndex: Int): Int =
        if (internalShuffleModeEnabled) {
            shuffleOrder.getOrNull(shuffledIndex) ?: -1
        } else {
            shuffledIndex
        }

    // ========== Playback State Properties ==========

    override val isPlaying: Boolean
        get() = internalState == InternalState.PLAYING

    override val currentPosition: Long
        get() = cachedPosition

    override val duration: Long
        get() = cachedDuration

    override val bufferedPosition: Long
        get() = cachedBufferedPosition

    override val bufferedPercentage: Int
        get() {
            val dur = duration
            if (dur <= 0) return 0
            return ((bufferedPosition * 100) / dur).toInt().coerceIn(0, 100)
        }

    override val currentMediaItem: GenericMediaItem?
        get() = playlist.getOrNull(localCurrentMediaItemIndex)

    override val currentMediaItemIndex: Int
        get() = localCurrentMediaItemIndex

    override val mediaItemCount: Int
        get() = playlist.size

    override val contentPosition: Long
        get() = cachedPosition

    override val playbackState: Int
        get() =
            when (internalState) {
                InternalState.IDLE -> PlayerConstants.STATE_IDLE
                InternalState.PREPARING -> PlayerConstants.STATE_BUFFERING
                InternalState.READY -> PlayerConstants.STATE_READY
                InternalState.PLAYING -> PlayerConstants.STATE_READY
                InternalState.ENDED -> PlayerConstants.STATE_ENDED
                InternalState.ERROR -> PlayerConstants.STATE_IDLE
                InternalState.PAUSED -> PlayerConstants.STATE_READY
            }

    // ========== Navigation ==========

    override fun hasNextMediaItem(): Boolean =
        when (internalRepeatMode) {
            PlayerConstants.REPEAT_MODE_ONE -> true
            PlayerConstants.REPEAT_MODE_ALL -> true
            else -> localCurrentMediaItemIndex < playlist.size - 1
        }

    override fun hasPreviousMediaItem(): Boolean =
        when (internalRepeatMode) {
            PlayerConstants.REPEAT_MODE_ONE -> true
            PlayerConstants.REPEAT_MODE_ALL -> true
            else -> localCurrentMediaItemIndex > 0
        }

    private fun getNextMediaItemIndex(): Int =
        when (internalRepeatMode) {
            PlayerConstants.REPEAT_MODE_ONE -> {
                localCurrentMediaItemIndex
            }
            PlayerConstants.REPEAT_MODE_ALL -> {
                if (internalShuffleModeEnabled && shuffleOrder.isNotEmpty()) {
                    val currentShufflePos = shuffleIndices.getOrNull(localCurrentMediaItemIndex) ?: 0
                    val nextShufflePos = (currentShufflePos + 1) % shuffleOrder.size
                    shuffleOrder.getOrNull(nextShufflePos) ?: localCurrentMediaItemIndex
                } else {
                    if (localCurrentMediaItemIndex < playlist.size - 1) {
                        localCurrentMediaItemIndex + 1
                    } else {
                        0
                    }
                }
            }
            else -> {
                if (internalShuffleModeEnabled && shuffleOrder.isNotEmpty()) {
                    val currentShufflePos = shuffleIndices.getOrNull(localCurrentMediaItemIndex) ?: 0
                    val nextShufflePos = currentShufflePos + 1
                    if (nextShufflePos < shuffleOrder.size) {
                        shuffleOrder.getOrNull(nextShufflePos) ?: localCurrentMediaItemIndex
                    } else {
                        localCurrentMediaItemIndex
                    }
                } else {
                    (localCurrentMediaItemIndex + 1).coerceAtMost(playlist.size - 1)
                }
            }
        }

    private fun getPreviousMediaItemIndex(): Int =
        when (internalRepeatMode) {
            PlayerConstants.REPEAT_MODE_ONE -> {
                localCurrentMediaItemIndex
            }
            PlayerConstants.REPEAT_MODE_ALL -> {
                if (internalShuffleModeEnabled && shuffleOrder.isNotEmpty()) {
                    val currentShufflePos = shuffleIndices.getOrNull(localCurrentMediaItemIndex) ?: 0
                    val prevShufflePos =
                        if (currentShufflePos > 0) {
                            currentShufflePos - 1
                        } else {
                            shuffleOrder.size - 1
                        }
                    shuffleOrder.getOrNull(prevShufflePos) ?: localCurrentMediaItemIndex
                } else {
                    if (localCurrentMediaItemIndex > 0) {
                        localCurrentMediaItemIndex - 1
                    } else {
                        playlist.size - 1
                    }
                }
            }
            else -> {
                if (internalShuffleModeEnabled && shuffleOrder.isNotEmpty()) {
                    val currentShufflePos = shuffleIndices.getOrNull(localCurrentMediaItemIndex) ?: 0
                    val prevShufflePos = currentShufflePos - 1
                    if (prevShufflePos >= 0) {
                        shuffleOrder.getOrNull(prevShufflePos) ?: localCurrentMediaItemIndex
                    } else {
                        localCurrentMediaItemIndex
                    }
                } else {
                    (localCurrentMediaItemIndex - 1).coerceAtLeast(0)
                }
            }
        }

    // ========== Playback Modes ==========

    override var shuffleModeEnabled: Boolean
        get() = internalShuffleModeEnabled
        set(value) {
            if (internalShuffleModeEnabled == value) return

            internalShuffleModeEnabled = value

            if (value) {
                createShuffleOrder()
            } else {
                clearShuffleOrder()
            }

            val mediaItemList = getShuffledMediaItemList()
            notifyListeners { onShuffleModeEnabledChanged(value, mediaItemList) }
            notifyTimelineChanged("TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED")
        }

    override var repeatMode: Int
        get() = internalRepeatMode
        set(value) {
            if (internalRepeatMode == value) return
            internalRepeatMode = value
            notifyListeners { onRepeatModeChanged(value) }
        }

    override var playWhenReady: Boolean
        get() = internalPlayWhenReady
        set(value) {
            internalPlayWhenReady = value
            if (value) play() else pause()
        }

    override var playbackParameters: GenericPlaybackParameters
        get() = GenericPlaybackParameters(internalPlaybackSpeed, internalPlaybackSpeed)
        set(value) {
            internalPlaybackSpeed = value.speed
            currentPlayer?.let { player ->
                try {
                    player.mediaPlayer.controls().setRate(value.speed)
                } catch (e: Exception) {
                    Logger.e(TAG, "Failed to set playback speed: ${e.message}")
                }
            }
        }

    // ========== Audio Settings ==========

    override val audioSessionId: Int
        get() = 0 // VLC doesn't provide audio session ID

    override var volume: Float
        get() = internalVolume
        set(value) {
            Logger.w(TAG, "Setting volume to $value")
            internalVolume = value.coerceIn(0f, 1f)
            // VLC volume: 0-200 (100 = normal). Map our 0.0-1.0 to 0-100.
            currentPlayer?.setVolume((internalVolume * 100).toInt())
            notifyListeners { onVolumeChanged(internalVolume) }
        }

    override var skipSilenceEnabled: Boolean = false

    // ========== Listener Management ==========

    override fun addListener(listener: MediaPlayerListener) {
        listeners.add(listener)
    }

    override fun removeListener(listener: MediaPlayerListener) {
        listeners.remove(listener)
    }

    // ========== Release Resources ==========

    override fun release() {
        currentLoadJob?.cancel()
        precacheJob?.cancel()
        positionUpdateJob?.cancel()
        crossfadeJob?.cancel()

        secondaryPlayer?.release()
        secondaryPlayer = null
        isCrossfading = false
        crossfadeFromIndex = -1

        coroutineScope.cancel()
        cleanupCurrentPlayerInternal()
        clearAllPrecacheInternal()
        listeners.clear()

        try {
            mediaPlayerFactory.release()
        } catch (e: Exception) {
            Logger.w(TAG, "Error releasing VLC factory: ${e.message}")
        }
    }

    // ========== Internal Methods ==========

    /**
     * Transition internal state and notify listeners.
     * Listeners are called on the VLC thread (not Main) because
     * JvmMediaPlayerHandlerImpl uses thread-safe StateFlow updates
     * and contains runBlocking calls that would deadlock on Main.
     */
    private fun transitionToState(newState: InternalState) {
        if (internalState == newState) return

        val oldState = internalState
        internalState = newState

        Logger.d(TAG, "State transition: $oldState -> $newState (playWhenReady=$internalPlayWhenReady)")

        // Query duration from VLC
        currentPlayer?.let { player ->
            val dur = player.length
            if (dur > 0L) {
                cachedDuration = dur
            }
        }

        // Notify listeners on VLC thread — safe because JvmMediaPlayerHandlerImpl
        // uses thread-safe StateFlow/MutableStateFlow for all state updates.
        when (newState) {
            InternalState.PAUSED -> {
                listeners.forEach { it.onPlaybackStateChanged(PlayerConstants.STATE_READY) }
                listeners.forEach { it.onIsPlayingChanged(false) }
            }

            InternalState.IDLE -> {
                listeners.forEach { it.onPlaybackStateChanged(PlayerConstants.STATE_IDLE) }
                listeners.forEach { it.onIsPlayingChanged(false) }
            }

            InternalState.PREPARING -> {
                Logger.d(TAG, "transitionToState PREPARING -> isLoading=true")
                cachedIsLoading = true
                listeners.forEach { it.onPlaybackStateChanged(PlayerConstants.STATE_BUFFERING) }
                listeners.forEach { it.onIsLoadingChanged(true) }
            }

            InternalState.READY -> {
                Logger.d(TAG, "transitionToState READY -> isLoading=false")
                cachedIsLoading = false
                listeners.forEach { it.onPlaybackStateChanged(PlayerConstants.STATE_READY) }
                listeners.forEach { it.onIsLoadingChanged(false) }
                if (internalPlayWhenReady) {
                    play()
                } else {
                    listeners.forEach { it.onIsPlayingChanged(false) }
                }
            }

            InternalState.PLAYING -> {
                Logger.d(TAG, "transitionToState PLAYING -> isLoading=false")
                cachedIsLoading = false
                listeners.forEach { it.onPlaybackStateChanged(PlayerConstants.STATE_READY) }
                listeners.forEach { it.onIsLoadingChanged(false) }
                listeners.forEach { it.onIsPlayingChanged(true) }
            }

            InternalState.ENDED -> {
                listeners.forEach { it.onPlaybackStateChanged(PlayerConstants.STATE_ENDED) }
                listeners.forEach { it.onIsPlayingChanged(false) }
            }

            InternalState.ERROR -> {
                listeners.forEach { it.onPlaybackStateChanged(PlayerConstants.STATE_IDLE) }
                listeners.forEach { it.onIsPlayingChanged(false) }
                listeners.forEach {
                    it.onPlayerError(
                        PlayerError(
                            errorCode = 403,
                            errorCodeName = "ERROR_UNKNOWN",
                            message = "Can not extract playable URL or playback error",
                        ),
                    )
                }
            }
        }
    }

    /**
     * Load and play track - MUST run on coroutineScope
     */
    private fun loadAndPlayTrackInternal(
        index: Int,
        startPositionMs: Long,
        shouldPlay: Boolean,
    ) {
        if (index !in playlist.indices) return

        val mediaItem = playlist[index]
        val videoId = mediaItem.mediaId

        currentLoadJob?.cancel()

        currentLoadJob =
            coroutineScope.launch {
                try {
                    transitionToState(InternalState.PREPARING)

                    // Notify media item transition (fire-and-forget to avoid
                    // blocking VLC thread with runBlocking inside handler)
                    notifyListeners {
                        onMediaItemTransition(
                            mediaItem,
                            PlayerConstants.MEDIA_ITEM_TRANSITION_REASON_AUTO,
                        )
                    }

                    // Detach event listener from old player IMMEDIATELY before
                    // spending time on URL extraction. This prevents stale events
                    // (error/finished) from the old player seeing the updated
                    // playlist/index and interfering with the new track load.
                    cleanupEventListenerInternal()
                    stopPositionUpdates()

                    // Extract URL on IO thread (network), VLC native calls stay on VLC thread
                    val cachedPrecache = precachedPlayers.remove(videoId)
                    var resolvedSource: PlayableSource? = null
                    val player =
                        if (cachedPrecache?.player != null) {
                            cachedPrecache.player
                        } else {
                            var source = withContext(Dispatchers.IO) { extractPlayableUrl(mediaItem) }
                            if (source == null || source.url.isEmpty()) {
                                Logger.w(TAG, "First extract failed for $videoId, invalidating and retrying...")
                                withContext(Dispatchers.IO) {
                                    streamRepository.invalidateFormat(videoId)
                                    streamRepository.invalidateFormat("${MERGING_DATA_TYPE.VIDEO}$videoId")
                                }
                                source = withContext(Dispatchers.IO) { extractPlayableUrl(mediaItem) }
                            }
                            if (source == null || source.url.isEmpty()) {
                                Logger.e(TAG, "Failed to extract playable URL for $videoId after retry")
                                transitionToState(InternalState.ERROR)
                                return@launch
                            }
                            resolvedSource = source
                            createMediaPlayerInternal(source)
                        }

                    // VLC native calls on VLC thread
                    cleanupCurrentPlayerInternal()
                    currentPlayer = player
                    currentPlayerIsVideo = player.videoSurface != null
                    _currentVideoSurface.value = player.videoSurface
                    setupPlayerEventsInternal(player)
                    player.setVolume((internalVolume * 100).toInt())

                    if (cachedPrecache != null) {
                        if (shouldPlay) {
                            player.mediaPlayer.controls().play()
                        }
                        Logger.d(TAG, "Playing from precache for $videoId")
                    } else {
                        val source = resolvedSource
                        if (source != null) {
                            val options = buildVlcOptions(source)
                            if (shouldPlay) {
                                player.mediaPlayer.media().play(source.url, *options)
                            } else {
                                player.mediaPlayer.media().startPaused(source.url, *options)
                            }
                        } else {
                            Logger.e(TAG, "resolvedSource is null — should not happen")
                            transitionToState(InternalState.ERROR)
                            return@launch
                        }
                    }

                    if (startPositionMs > 0) {
                        delay(100)
                        player.seekTo(startPositionMs)
                        cachedPosition = startPositionMs
                    }

                    // Always transition to READY first so UI receives
                    // isLoading=false via STATE_READY. The READY handler
                    // will auto-call play() when internalPlayWhenReady is true.
                    transitionToState(InternalState.READY)

                    // Start position updates
                    startPositionUpdates()

                    // Eagerly load audio metadata for auto crossfade / DJ mode calculations
                    if (crossfadeEnabled && (crossfadeDurationMs == DataStoreManager.CROSSFADE_DURATION_AUTO || djCrossfadeEnabled)) {
                        val videoId = playlist.getOrNull(localCurrentMediaItemIndex)?.mediaId ?: ""
                        loadAudioMetaIfNeeded(videoId)
                    }

                    // Trigger precaching
                    triggerPrecachingInternal()
                } catch (e: Exception) {
                    if (e !is CancellationException) {
                        Logger.e(TAG, "Load track error: ${e.message}", e)
                        transitionToState(InternalState.ERROR)
                    }
                }
            }
    }

    private fun buildVlcOptions(source: PlayableSource): Array<String> {
        val options = mutableListOf<String>()
        if (source.audioSlaveUrl != null) {
            options.add(":input-slave=${source.audioSlaveUrl}")
        }
        if (source.isVideo) {
            // Extra buffering for video streams to prevent stalls near end
            options.add(":network-caching=5000")
            options.add(":http-reconnect")
        } else {
            // WAVORA CROSSFADE FIX (Windows root cause): audio-only sources — which
            // is effectively ALL playback in Wavora, since it's a music app — had NO
            // explicit `:network-caching` here, so libVLC fell back to its own low
            // internal default. That's fine for steady-state playback (it catches up
            // after a moment), but it's exactly what breaks the crossfade: the
            // secondary player's `playing()` event fires as soon as VLC's pipeline
            // starts producing frames, which — with almost no cache — can happen
            // before there's enough decoded audio queued to survive a network hiccup
            // or scheduling jitter. That's the "arranca, se traba un par de segundos,
            // después fluye" symptom. Giving audio the same kind of real cache that
            // video already had (smaller, since audio is cheap to buffer and we still
            // want manual track switches to feel fast) fixes it at the source instead
            // of hiding it with a longer fade or a fixed delay.
            options.add(":network-caching=$AUDIO_NETWORK_CACHING_MS")
            options.add(":http-reconnect")
        }
        return options.toTypedArray()
    }

    /**
     * Create a VLC media player instance
     *
     * For video: Creates an EmbeddedMediaPlayer with a Canvas video surface
     * For audio: Creates a headless MediaPlayer (no video)
     *
     * When source has audioSlaveUrl, VLC will merge video + audio streams
     * via --input-slave (equivalent to Android's MergingMediaSource)
     */
    private fun createMediaPlayerInternal(source: PlayableSource): VlcPlayer {
        if (source.isVideo) {
            Logger.d(TAG, "Creating video player with callback surface")
            val videoPanel = VlcVideoSurfacePanel()

            val embeddedPlayer = mediaPlayerFactory.mediaPlayers().newEmbeddedMediaPlayer()
            val surface = videoPanel.createVideoSurface(mediaPlayerFactory)
            embeddedPlayer.videoSurface().set(surface)

            return VlcPlayer(
                mediaPlayer = embeddedPlayer,
                videoSurface = videoPanel,
            ).also { CrossfadeAudit.log("CREATE_KIND", playerId = it.id, details = "kind=video source=${source.url}") }
        }

        // Audio-only player
        Logger.d(TAG, "Creating audio-only player")
        val player = mediaPlayerFactory.mediaPlayers().newMediaPlayer()
        return VlcPlayer(
            mediaPlayer = player,
            videoSurface = null,
        ).also { CrossfadeAudit.log("CREATE_KIND", playerId = it.id, details = "kind=audio source=${source.url}") }
    }

    /**
     * Setup VLC event listeners for a player
     */
    private fun setupPlayerEventsInternal(player: VlcPlayer) {
        // Remove old listener
        cleanupEventListenerInternal()

        // IMPORTANT: VLC callbacks run on a native thread. Calling stop()/release()
        // from within a callback causes deadlock (native thread waits for itself to finish).
        // All heavy operations must be dispatched to coroutineScope (runs on Main/Swing).
        val listener =
            object : MediaPlayerEventAdapter() {
                override fun finished(mediaPlayer: MediaPlayer) {
                    Logger.d(TAG, "End of stream reached")
                    val emittingId = playerIdFor(mediaPlayer)
                    CrossfadeAudit.log("CB_FINISHED", playerId = emittingId, details = "isCrossfading=$isCrossfading")
                    coroutineScope.launch {
                        // Ignore events from a player that is no longer current
                        if (currentPlayer?.mediaPlayer != mediaPlayer) {
                            Logger.w(TAG, "Ignoring finished() from stale player")
                            CrossfadeAudit.log("CB_FINISHED_IGNORED_STALE", playerId = emittingId)
                            return@launch
                        }
                        // During crossfade, the old player will naturally finish near the end.
                        // Ignore it - the crossfade is already handling the transition.
                        if (isCrossfading) {
                            Logger.d(TAG, "Ignoring finished() during crossfade (old player ended)")
                            CrossfadeAudit.log("CB_FINISHED_IGNORED_CROSSFADING", playerId = emittingId)
                            return@launch
                        }
                        transitionToState(InternalState.ENDED)
                        handleTrackEndInternal()
                    }
                }

                override fun error(mediaPlayer: MediaPlayer) {
                    Logger.e(TAG, "VLC playback error")
                    val emittingId = playerIdFor(mediaPlayer)
                    CrossfadeAudit.log("CB_ERROR", playerId = emittingId, details = "isCrossfading=$isCrossfading")
                    coroutineScope.launch {
                        // Ignore errors from a player that is no longer current.
                        // This can happen when the old player fires error() after
                        // a new track has started loading (playlist/index already updated).
                        if (currentPlayer?.mediaPlayer != mediaPlayer) {
                            Logger.w(TAG, "Ignoring error() from stale player")
                            CrossfadeAudit.log("CB_ERROR_IGNORED_STALE", playerId = emittingId)
                            return@launch
                        }
                        // During crossfade, ignore errors from the old player -
                        // it's fading out and will be released soon anyway.
                        if (isCrossfading) {
                            Logger.w(TAG, "Ignoring error() during crossfade (old player error)")
                            CrossfadeAudit.log("CB_ERROR_IGNORED_CROSSFADING", playerId = emittingId)
                            return@launch
                        }
                        val currentVideoId = playlist.getOrNull(localCurrentMediaItemIndex)?.mediaId
                        if (currentVideoId != null) {
                            // Reset retry count for new track
                            if (retryVideoId != currentVideoId) {
                                retryVideoId = currentVideoId
                                retryCount = 0
                            }
                            if (retryCount < maxRetryCount) {
                                retryCount++
                                Logger.w(TAG, "Retrying playback (attempt $retryCount/$maxRetryCount) for $currentVideoId")
                                try {
                                    // Invalidate cached format so fresh URL is fetched
                                    streamRepository.invalidateFormat(currentVideoId)
                                    streamRepository.invalidateFormat("${MERGING_DATA_TYPE.VIDEO}$currentVideoId")
                                    // Evict stale precache
                                    precachedPlayers.remove(currentVideoId)?.player?.release()
                                    // Reload the track
                                    loadAndPlayTrackInternal(localCurrentMediaItemIndex, 0L, shouldPlay = true)
                                    return@launch
                                } catch (e: Exception) {
                                    if (e is CancellationException) throw e
                                    Logger.e(TAG, "Retry failed: ${e.message}", e)
                                }
                            }
                            Logger.e(TAG, "Max retries ($maxRetryCount) exhausted for $currentVideoId")
                        }
                        val error =
                            PlayerError(
                                errorCode = PlayerConstants.ERROR_CODE_TIMEOUT,
                                errorCodeName = "VLC_ERROR",
                                message = "Playback error",
                            )
                        listeners.forEach { it.onPlayerError(error) }
                        transitionToState(InternalState.ERROR)
                    }
                }

                override fun playing(mediaPlayer: MediaPlayer) {
                    CrossfadeAudit.log("CB_PLAYING", playerId = playerIdFor(mediaPlayer))
                    coroutineScope.launch {
                        if (currentPlayer?.mediaPlayer != mediaPlayer) return@launch
                        if (internalState != InternalState.PLAYING) {
                            transitionToState(InternalState.PLAYING)
                            notifyEqualizerIntent(true)
                            // Reset retry counter on successful playback
                            retryCount = 0
                            retryVideoId = null
                        }
                    }
                }

                override fun paused(mediaPlayer: MediaPlayer) {
                    CrossfadeAudit.log("CB_PAUSED", playerId = playerIdFor(mediaPlayer))
                    coroutineScope.launch {
                        if (currentPlayer?.mediaPlayer != mediaPlayer) return@launch
                        if (internalState == InternalState.PLAYING) {
                            transitionToState(InternalState.PAUSED)
                            notifyEqualizerIntent(false)
                        }
                    }
                }

                override fun stopped(mediaPlayer: MediaPlayer) {
                    CrossfadeAudit.log("CB_STOPPED", playerId = playerIdFor(mediaPlayer))
                    coroutineScope.launch {
                        notifyEqualizerIntent(false)
                    }
                }

                override fun timeChanged(
                    mediaPlayer: MediaPlayer,
                    newTime: Long,
                ) {
                    // During crossfade, this fires from the OLD player — ignore it.
                    // Position updates come from the poll loop using secondaryPlayer's time.
                    if (!isCrossfading) {
                        cachedPosition = newTime
                    }
                }

                override fun lengthChanged(
                    mediaPlayer: MediaPlayer,
                    newLength: Long,
                ) {
                    // During crossfade, this fires from the OLD player — ignore it.
                    if (!isCrossfading && newLength > 0) {
                        cachedDuration = newLength
                    }
                }

                override fun buffering(
                    mediaPlayer: MediaPlayer,
                    newCache: Float,
                ) {
                    // During crossfade, ignore buffering events from old player
                    if (isCrossfading) return

                    // Update buffered position first
                    if (cachedDuration > 0) {
                        cachedBufferedPosition = (cachedDuration * newCache / 100f).toLong()
                    }

                    // Only report loading when buffer is actually behind the playhead
                    // AND the player intends to play. Ignore buffering while paused
                    // to avoid showing a loading spinner when user has explicitly paused.
                    val isStalled = newCache < 100f && cachedBufferedPosition <= cachedPosition && internalPlayWhenReady
                    Logger.d(
                        TAG,
                        "buffering: cache=$newCache%, bufferedPos=$cachedBufferedPosition, currentPos=$cachedPosition, isStalled=$isStalled, cachedIsLoading=$cachedIsLoading",
                    )
                    if (isStalled != cachedIsLoading) {
                        Logger.w(TAG, "isLoading changed: $cachedIsLoading -> $isStalled")
                        cachedIsLoading = isStalled
                        notifyListeners { onIsLoadingChanged(isStalled) }
                    }
                }

                override fun opening(mediaPlayer: MediaPlayer) {
                    Logger.d(TAG, "VLC opening media")
                    CrossfadeAudit.log("CB_OPENING", playerId = playerIdFor(mediaPlayer))
                }
            }

        player.setEventListener(listener)
    }

    /**
     * Best-effort reverse lookup from a raw vlcj [MediaPlayer] (what native
     * callbacks hand back) to our own [VlcPlayer.id], purely for the
     * crossfade audit log - so a callback firing on a released/stale/old
     * player can still be identified instead of logged as "player=none".
     * Checks the two slots that can ever be alive (current + secondary/
     * precached) rather than keeping a separate registry, since those are
     * the only players with listeners attached at any given time.
     */
    private fun playerIdFor(mediaPlayer: MediaPlayer): Int? {
        if (currentPlayer?.mediaPlayer === mediaPlayer) return currentPlayer?.id
        if (secondaryPlayer?.mediaPlayer === mediaPlayer) return secondaryPlayer?.id
        precachedPlayers.values.forEach { if (it.player.mediaPlayer === mediaPlayer) return it.player.id }
        return null
    }

    /**
     * Clean up event listener from current player
     */
    private fun cleanupEventListenerInternal() {
        currentPlayer?.setEventListener(null)
    }

    /**
     * Cleanup a player instance
     */
    private fun cleanupPlayerInternal(player: VlcPlayer) {
        try {
            player.release()
        } catch (e: Exception) {
            Logger.w(TAG, "Error cleaning up player: ${e.message}")
        }
    }

    /**
     * Pause + reset any precached player currently mid pre-roll (playing muted
     * ahead of the real crossfade — see PRECACHE_AUDIO_PREROLL_LEAD_MS). Called
     * when playback pauses so a pre-rolled player doesn't keep decoding silently
     * in the background, and so it starts fresh from 0 the next time it's needed
     * instead of resuming from a drifted position.
     */
    private fun pauseAnyPrimedPrecachePlayer() {
        precachedPlayers.values.forEach { cached ->
            if (cached.player.isPrimed) {
                CrossfadeAudit.log("PREROLL_RESET_ON_PAUSE", playerId = cached.player.id, role = "next-precached")
                try {
                    cached.player.pause()
                    cached.player.seekTo(0)
                } catch (e: Exception) {
                    Logger.w(TAG, "Error resetting pre-rolled precache player: ${e.message}")
                }
                cached.player.isPrimed = false
            }
        }
    }

    /**
     * Cleanup current player
     */
    private fun cleanupCurrentPlayerInternal() {
        stopPositionUpdates()
        cleanupEventListenerInternal()

        crossfadeJob?.cancel()
        crossfadeJob = null
        setCrossfading(false)

        currentPlayer?.let { cleanupPlayerInternal(it) }
        currentPlayer = null
        currentPlayerIsVideo = false
        _currentVideoSurface.value = null
    }

    /**
     * Handle track end
     */
    private fun handleTrackEndInternal() {
        Logger.d(
            "PB_TRACE",
            "t=${java.time.Instant.now()} thread=${Thread.currentThread().name} " +
                "VlcPlayerAdapter.handleTrackEndInternal() | internalPlayWhenReady=$internalPlayWhenReady " +
                "repeatMode=$internalRepeatMode crossfadeEnabled=$crossfadeEnabled index=$localCurrentMediaItemIndex",
        )
        CrossfadeAudit.log(
            "TRACK_END",
            playerId = currentPlayer?.id,
            role = "current",
            details = "isCrossfading=$isCrossfading crossfadeEnabled=$crossfadeEnabled index=$localCurrentMediaItemIndex",
        )
        // If crossfade is already in progress (triggered by position update before track ended),
        // don't interrupt it. The old player ending is expected — the crossfade will complete
        // and finalizeCrossfade() will handle the transition.
        if (isCrossfading) {
            Logger.d(TAG, "handleTrackEndInternal: crossfade in progress, ignoring track end")
            return
        }

        val shouldCrossfade =
            crossfadeEnabled &&
                hasNextMediaItem()

        if (shouldCrossfade) {
            val nextIndex = getNextMediaItemIndex()
            triggerCrossfadeTransition(nextIndex)
        } else {
            when (internalRepeatMode) {
                PlayerConstants.REPEAT_MODE_ONE -> {
                    seekTo(localCurrentMediaItemIndex, 0)
                }

                PlayerConstants.REPEAT_MODE_ALL -> {
                    if (hasNextMediaItem()) {
                        seekToNext()
                    }
                }

                else -> {
                    if (localCurrentMediaItemIndex < playlist.size - 1) {
                        seekToNext()
                    } else {
                        notifyEqualizerIntent(false)
                    }
                }
            }
        }
    }

    /**
     * Trigger crossfade to next track
     */
    private fun triggerCrossfadeTransition(nextIndex: Int) {
        if (nextIndex !in playlist.indices || isCrossfading) return

        crossfadeJob =
            coroutineScope.launch {
                try {
                    setCrossfading(true)
                    val nextMediaItem = playlist[nextIndex]
                    val nextVideoId = nextMediaItem.mediaId

                    Logger.d(TAG, "Starting crossfade to track $nextIndex")
                    CrossfadeAudit.log(
                        "CROSSFADE_START",
                        playerId = currentPlayer?.id,
                        role = "current",
                        details = "nextIndex=$nextIndex nextVideoId=$nextVideoId currentTime=${currentPlayer?.time} currentLength=${currentPlayer?.length}",
                    )

                    // Extract URL on IO thread (network), VLC native calls stay on VLC thread
                    val cachedPrecache = precachedPlayers.remove(nextVideoId)
                    val nextPlayer: VlcPlayer? =
                        if (cachedPrecache?.player != null) {
                            Logger.d(TAG, "Using precached player for crossfade")
                            CrossfadeAudit.log(
                                "CROSSFADE_USING_PRECACHED",
                                playerId = cachedPrecache.player.id,
                                role = "next",
                                details = "isPrimed=${cachedPrecache.player.isPrimed} time=${cachedPrecache.player.time}",
                            )
                            cachedPrecache.player
                        } else {
                            val nextSource = withContext(Dispatchers.IO) { extractPlayableUrl(nextMediaItem) }
                            if (nextSource == null || nextSource.url.isEmpty()) {
                                Logger.e(TAG, "Failed to extract URL for crossfade")
                                null
                            } else {
                                createMediaPlayerInternal(nextSource).also { newPlayer ->
                                    val options = buildVlcOptions(nextSource)
                                    newPlayer.mediaPlayer.media().startPaused(nextSource.url, *options)
                                    CrossfadeAudit.log("CROSSFADE_CREATED_FRESH", playerId = newPlayer.id, role = "next")
                                }
                            }
                        }

                    if (nextPlayer != null) {
                        // Setup secondary player with its OWN listener.
                        // DO NOT call setupPlayerEventsInternal() here - that would remove
                        // the event listener from the current player (which is still playing).
                        secondaryPlayer = nextPlayer
                        // WAVORA CROSSFADE FIX (Objetivo 3): antes se hacía un `delay(50)` fijo
                        // entre `play()` y desmutear, asumiendo que a los 50ms VLC ya estaba
                        // produciendo audio real. En streams de red o máquinas más lentas esa
                        // asunción podía fallar: se desmuteaba antes de que hubiera audio
                        // decodificado, lo que se percibe como un click/corte al iniciar el
                        // crossfade. En vez de un timer arbitrario, esperamos la señal real
                        // `playing()` de VLC (el mismo callback que ya usa
                        // setupPlayerEventsInternal más abajo para marcar InternalState.PLAYING),
                        // con un timeout de seguridad para no colgar el crossfade si, por lo que
                        // sea, VLC nunca dispara el evento (p.ej. error silencioso).
                        // WAVORA CROSSFADE FIX (Windows stutter, remaining gap): if
                        // startPositionUpdates() already pre-rolled this player (see
                        // PRECACHE_AUDIO_PREROLL_LEAD_MS), it has been playing muted for
                        // up to ~3.5s already and its buffer is stable — calling play()
                        // again here would be a no-op at best, and re-running the
                        // play()+wait(800ms) sequence would just delay the fade for no
                        // reason. Only do the play()+wait dance for a player that's
                        // starting fresh right now (non-precached fallback, or a
                        // precached one whose crossfade fired before pre-roll had a
                        // chance to run — e.g. a very short track).
                        if (nextPlayer.isPrimed) {
                            // The pre-rolled player has been playing muted for up to
                            // PRECACHE_AUDIO_PREROLL_LEAD_MS (~3.5s) to give its buffer
                            // real time to stabilize — so its position has drifted
                            // forward from 0 by roughly that much. Seek back to the
                            // true start before unmuting, or the listener would hear
                            // the next track starting a few seconds in instead of from
                            // its actual intro. This is a LOCAL seek backwards into
                            // data VLC already has buffered/demuxed from having just
                            // played through it — not a fresh network fetch — so it's
                            // effectively fast and doesn't reintroduce the original
                            // network-caching stutter.
                            //
                            // ROOT CAUSE (confirmed by API contract, not a guess):
                            // vlcj's seekTo()/setTime() is fire-and-forget — it asks
                            // VLC to reposition the demuxer/decoder but returns
                            // immediately, before that reposition has actually
                            // happened. The previous version of this code flipped
                            // `isMute = false` on the very next line, with nothing in
                            // between to confirm the seek had landed. That is an
                            // unconditional synchronization bug regardless of how
                            // often it manifests audibly: for some window after
                            // unmuting, this player could still be emitting audio
                            // from wherever it was before the seek (~3.5s in), not
                            // from 0 — and if the seek finally lands mid-fade, once
                            // the fade-in coroutine has already been raising this
                            // player's volume for a bit, the listener hears an
                            // abrupt position jump in the middle of what should be a
                            // smooth transition. That sequence — hear a fragment
                            // that isn't the intro, then the "official" transition,
                            // then a jump/restart, then instability — matches what
                            // was reported.
                            //
                            // FIX: wait for VLC's OWN confirmation that the seek
                            // landed — the next timeChanged callback reporting a
                            // position close to 0 — before touching isMute. This is
                            // not a sleep/timeout: it's a real signal from the
                            // native player, and if that signal never comes (seek
                            // genuinely fails), the wait is cancelled the same way
                            // any other suspend point in this coroutine already is
                            // (track skip, crossfadeJob?.cancel() from
                            // cancelCrossfadeAndCleanup) — no separate arbitrary
                            // bound was added here.
                            val timeBeforeSeek = nextPlayer.time
                            Logger.d(TAG, "Secondary player already pre-rolled, seeking back to 0 before unmute")
                            CrossfadeAudit.log(
                                "PREROLL_SEEK_BEFORE",
                                playerId = nextPlayer.id,
                                role = "next",
                                details = "timeBeforeSeek=$timeBeforeSeek",
                            )

                            val seekAppliedSignal = CompletableDeferred<Unit>()
                            // Threshold, not "first event": a stale timeChanged that
                            // was already in flight from BEFORE seekTo(0) was issued
                            // would report a time near timeBeforeSeek (~3.5s), not
                            // near 0 — so filtering on the actual reported value
                            // (rather than just "the next callback, whatever it
                            // says") can't be fooled by that in-flight event and
                            // only resolves once VLC reports a position that
                            // genuinely reflects the seek having landed.
                            val seekConfirmListener =
                                object : MediaPlayerEventAdapter() {
                                    override fun timeChanged(
                                        mediaPlayer: MediaPlayer,
                                        newTime: Long,
                                    ) {
                                        if (!seekAppliedSignal.isCompleted && newTime < SEEK_APPLIED_TIME_THRESHOLD_MS) {
                                            CrossfadeAudit.log(
                                                "PREROLL_SEEK_CONFIRMED",
                                                playerId = nextPlayer.id,
                                                role = "next",
                                                details = "confirmedTime=$newTime",
                                            )
                                            seekAppliedSignal.complete(Unit)
                                        }
                                    }

                                    override fun error(mediaPlayer: MediaPlayer) {
                                        // Seek can't land if the player itself errored out —
                                        // don't leave the fade waiting on a signal that will
                                        // never come.
                                        if (!seekAppliedSignal.isCompleted) {
                                            CrossfadeAudit.log("PREROLL_SEEK_ERROR_DURING_WAIT", playerId = nextPlayer.id, role = "next")
                                            seekAppliedSignal.complete(Unit)
                                        }
                                    }
                                }
                            // Additive listener via the raw vlcj API (not through
                            // VlcPlayer.setEventListener, which replaces the single
                            // tracked listener atomically) so this temporary
                            // confirmation hook doesn't disturb whatever listener
                            // setEventListener(null) is about to clear right after.
                            nextPlayer.mediaPlayer.events().addMediaPlayerEventListener(seekConfirmListener)
                            nextPlayer.setEventListener(null)
                            nextPlayer.seekTo(0)
                            seekAppliedSignal.await()
                            nextPlayer.mediaPlayer.events().removeMediaPlayerEventListener(seekConfirmListener)

                            val timeRightBeforeUnmute = nextPlayer.time
                            nextPlayer.mediaPlayer.audio().isMute = false
                            CrossfadeAudit.log(
                                "PREROLL_SEEK_AFTER",
                                playerId = nextPlayer.id,
                                role = "next",
                                details = "timeRightBeforeUnmute=$timeRightBeforeUnmute (confirmed via real VLC signal, not a timeout)",
                            )
                        } else {
                            val secondaryPlayingSignal = CompletableDeferred<Unit>()
                            nextPlayer.setEventListener(
                                object : MediaPlayerEventAdapter() {
                                    override fun playing(mediaPlayer: MediaPlayer) {
                                        // No se llama a ninguna API de VLC acá, solo se resuelve
                                        // una primitiva de coroutines — seguro de invocar
                                        // directamente desde el hilo nativo de VLC.
                                        CrossfadeAudit.log("PREROLL_FRESH_PLAYING_SIGNAL", playerId = nextPlayer.id, role = "next")
                                        secondaryPlayingSignal.complete(Unit)
                                    }

                                    override fun error(mediaPlayer: MediaPlayer) {
                                        Logger.e(TAG, "Secondary player error during crossfade")
                                        CrossfadeAudit.log("PREROLL_FRESH_ERROR", playerId = nextPlayer.id, role = "next")
                                        secondaryPlayingSignal.complete(Unit)
                                        coroutineScope.launch {
                                            crossfadeJob?.cancel()
                                            secondaryPlayer?.release()
                                            secondaryPlayer = null
                                            setCrossfading(false)
                                            seekTo(nextIndex, 0)
                                        }
                                    }
                                },
                            )
                            nextPlayer.mediaPlayer.audio().isMute = true
                            nextPlayer.setVolume(0)
                            nextPlayer.mediaPlayer.controls().play()
                            val signalled = withTimeoutOrNull(800L) { secondaryPlayingSignal.await() } != null
                            CrossfadeAudit.log(
                                "PREROLL_FRESH_WAIT_RESULT",
                                playerId = nextPlayer.id,
                                role = "next",
                                details = "signalledBeforeTimeout=$signalled",
                            )
                            nextPlayer.mediaPlayer.audio().isMute = false
                        }
                    }


                    if (nextPlayer == null) {
                        setCrossfading(false)
                        seekTo(nextIndex, 0)
                        return@launch
                    }

                    // Capture current index BEFORE advancing localCurrentMediaItemIndex
                    val currentVideoId = playlist.getOrNull(localCurrentMediaItemIndex)?.mediaId ?: ""
                    crossfadeFromIndex = localCurrentMediaItemIndex

                    // AutoMix: load metadata and calculate parameters
                    val isAutoMode = crossfadeDurationMs == DataStoreManager.CROSSFADE_DURATION_AUTO
                    if (isAutoMode || djCrossfadeEnabled) {
                        loadAudioMetaIfNeeded(currentVideoId)
                        loadAudioMetaIfNeeded(nextVideoId)
                    }

                    // Update now playing and video surface immediately
                    localCurrentMediaItemIndex = nextIndex
                    if (nextPlayer.videoSurface != null) {
                        _currentVideoSurface.value = nextPlayer.videoSurface
                    }
                    notifyListeners {
                        onMediaItemTransition(
                            nextMediaItem,
                            PlayerConstants.MEDIA_ITEM_TRANSITION_REASON_AUTO,
                        )
                    }

                    Logger.d(TAG, "Now playing updated to track $nextIndex during crossfade")

                    // AutoMix: resolve duration and BPM ratio
                    val resolvedConfigDurationMs =
                        if (isAutoMode) {
                            resolveAutoCrossfadeDurationMs(currentVideoId, nextVideoId)
                        } else {
                            crossfadeDurationMs
                        }
                    // VLC cannot ramp setRate() without audio glitches — skip speed matching,
                    // rely on duration gap factors (longer crossfade for different tempos)

                    // Calculate effective crossfade duration based on ACTUAL remaining time.
                    val actualTimeRemaining =
                        currentPlayer?.let { player ->
                            val dur = player.length
                            val pos = player.time
                            if (dur > 0 && pos >= 0) (dur - pos) else resolvedConfigDurationMs.toLong()
                        } ?: resolvedConfigDurationMs.toLong()

                    val effectiveCrossfadeDurationMs =
                        minOf(
                            resolvedConfigDurationMs.toLong(),
                            actualTimeRemaining,
                        ).coerceAtLeast(1000L).toInt()

                    Logger.d(
                        TAG,
                        "Crossfade duration: configured=${resolvedConfigDurationMs}ms (auto=$isAutoMode), " +
                            "actualRemaining=${actualTimeRemaining}ms, effective=${effectiveCrossfadeDurationMs}ms",
                    )

                    performCrossfade(nextIndex, nextPlayer, effectiveCrossfadeDurationMs)
                } catch (e: Exception) {
                    if (e !is CancellationException) {
                        Logger.e(TAG, "Crossfade error: ${e.message}", e)
                        setCrossfading(false)
                        seekTo(nextIndex, 0)
                    }
                }
            }
    }

    /**
     * Cancel the ongoing crossfade, await its completion, and clean up state.
     * Uses [Job.cancelAndJoin] to ensure the crossfade coroutine's catch block
     * has finished before the caller proceeds, preventing race conditions.
     *
     * @param revertIndex If true, revert [localCurrentMediaItemIndex] to the track
     *   that was playing before the crossfade started.
     */
    private suspend fun cancelCrossfadeAndCleanup(revertIndex: Boolean) {
        val job = crossfadeJob
        crossfadeJob = null
        CrossfadeAudit.log(
            "CROSSFADE_CANCEL_REQUESTED",
            playerId = secondaryPlayer?.id,
            role = "next",
            details = "revertIndex=$revertIndex",
        )
        job?.cancel()
        job?.join()
        // secondaryPlayer may already be released by performCrossfade's catch block,
        // but with isReleased guard in VlcPlayer, this is safe.
        secondaryPlayer?.release()
        secondaryPlayer = null
        if (revertIndex && crossfadeFromIndex >= 0) {
            localCurrentMediaItemIndex = crossfadeFromIndex
            playlist.getOrNull(crossfadeFromIndex)?.let { mediaItem ->
                notifyListeners {
                    onMediaItemTransition(
                        mediaItem,
                        PlayerConstants.MEDIA_ITEM_TRANSITION_REASON_SEEK,
                    )
                }
            }
        }
        crossfadeFromIndex = -1
        setCrossfading(false)
    }

    /**
     * Perform the actual crossfade animation
     *
     * @param effectiveDurationMs The actual crossfade duration to use. May be shorter than
     *   the configured [crossfadeDurationMs] if URL resolution / buffering consumed
     *   part of the crossfade window.
     */
    private suspend fun performCrossfade(
        nextIndex: Int,
        nextPlayer: VlcPlayer,
        effectiveDurationMs: Int,
    ) {
        val steps = 50
        val delayPerStep = (effectiveDurationMs / steps).coerceAtLeast(20)
        val targetVolume = (internalVolume * 100).toInt()
        Logger.d(
            TAG,
            "Crossfade animation: ${effectiveDurationMs}ms, $steps steps, ${delayPerStep}ms/step, " +
                "internalVolume=$internalVolume",
        )
        val fadeOutId = currentPlayer?.id
        CrossfadeAudit.log(
            "FADE_LOOP_START",
            playerId = fadeOutId,
            details = "nextPlayerId=${nextPlayer.id} durationMs=$effectiveDurationMs steps=$steps targetVolume=$targetVolume",
        )

        try {
            for (step in 0..steps) {
                currentCoroutineContext().ensureActive()

                val progress = step.toFloat() / steps
                val angle = progress * Math.PI / 2.0

                val fadeOutVolume = (targetVolume * kotlin.math.cos(angle)).toInt()
                currentPlayer?.setVolume(fadeOutVolume)

                val fadeInVolume = (targetVolume * kotlin.math.sin(angle)).toInt()
                nextPlayer.setVolume(fadeInVolume)

                // Snapshot both players' actual position/playing state at the
                // start, middle and end of the fade (not every step - 50 steps
                // of this would drown the log). This is the direct evidence for
                // "does the incoming track's position stay monotonic through the
                // fade" and "are both players genuinely playing at once".
                if (step == 0 || step == steps / 2 || step == steps) {
                    CrossfadeAudit.log(
                        "FADE_LOOP_SNAPSHOT",
                        playerId = fadeOutId,
                        details =
                            "step=$step/$steps outVolume=$fadeOutVolume outTime=${currentPlayer?.time} " +
                                "outIsPlaying=${runCatching { currentPlayer?.mediaPlayer?.status()?.isPlaying }.getOrNull()} " +
                                "nextPlayerId=${nextPlayer.id} inVolume=$fadeInVolume inTime=${nextPlayer.time} " +
                                "inIsPlaying=${runCatching { nextPlayer.mediaPlayer.status().isPlaying }.getOrNull()}",
                    )
                }

                delay(delayPerStep.toLong())
            }

            finalizeCrossfade(nextIndex, nextPlayer)
        } catch (e: CancellationException) {
            Logger.d(TAG, "Crossfade cancelled")
            CrossfadeAudit.log("FADE_LOOP_CANCELLED", playerId = fadeOutId, details = "nextPlayerId=${nextPlayer.id}")
            nextPlayer.release()
            secondaryPlayer = null
            setCrossfading(false)
        }
    }

    /**
     * Finalize crossfade: swap players and cleanup
     */
    private fun finalizeCrossfade(
        nextIndex: Int,
        nextPlayer: VlcPlayer,
    ) {
        Logger.d(TAG, "Crossfade complete, swapping players")
        val oldPlayerId = currentPlayer?.id
        val oldIsPlaying = runCatching { currentPlayer?.mediaPlayer?.status()?.isPlaying }.getOrNull()
        val nextIsPlaying = runCatching { nextPlayer.mediaPlayer.status().isPlaying }.getOrNull()
        CrossfadeAudit.log(
            "CROSSFADE_SWAP",
            playerId = oldPlayerId,
            role = "old-current",
            details =
                "oldIsPlaying=$oldIsPlaying newPlayerId=${nextPlayer.id} newIsPlaying=$nextIsPlaying " +
                    "bothPlayingSimultaneously=${oldIsPlaying == true && nextIsPlaying == true}",
        )

        stopPositionUpdates()

        // Cleanup old current player
        currentPlayer?.let { oldPlayer ->
            try {
                oldPlayer.release()
            } catch (e: Exception) {
                Logger.w(TAG, "Error cleaning up old player: ${e.message}")
            }
        }

        // Promote secondary to current
        currentPlayer = nextPlayer
        currentPlayerIsVideo = nextPlayer.videoSurface != null
        _currentVideoSurface.value = nextPlayer.videoSurface
        secondaryPlayer = null

        // Now set up the full event listener on the new current player
        // (replaces the minimal crossfade error listener)
        setupPlayerEventsInternal(nextPlayer)

        // Ensure correct volume
        currentPlayer?.setVolume((internalVolume * 100).toInt())

        // Reset state
        setCrossfading(false)
        crossfadeFromIndex = -1
        transitionToState(InternalState.PLAYING)

        CrossfadeAudit.log(
            "CROSSFADE_SWAP_COMPLETE",
            playerId = nextPlayer.id,
            role = "new-current",
            details = "oldPlayerId=$oldPlayerId time=${nextPlayer.time}",
        )

        // Start position tracking
        startPositionUpdates()

        // Trigger next precache
        triggerPrecachingInternal()
    }

    /**
     * Start position updates (periodic polling for crossfade detection)
     * VLC timeChanged callback handles position caching, but we need
     * this loop for crossfade trigger detection.
     */

    data class SongAudioMeta(
        val bpm: Int?,
        val key: String?,
        val keyScale: String?,
    )

    private suspend fun loadAudioMetaIfNeeded(videoId: String) {
        if (videoId.isBlank() || audioMetaCache.containsKey(videoId)) return
        try {
            val format = streamRepository.getNewFormat(videoId).firstOrNull()
            if (format == null) {
                Logger.d(TAG, "AutoMix meta: no NewFormatEntity found for videoId=$videoId")
                return
            }
            if (format.bpm != null || format.musicKey != null) {
                audioMetaCache[videoId] = SongAudioMeta(format.bpm, format.musicKey, format.keyScale)
                Logger.d(TAG, "AutoMix meta loaded: videoId=$videoId, bpm=${format.bpm}, key=${format.musicKey} ${format.keyScale}")
            } else {
                Logger.d(TAG, "AutoMix meta: format exists but no bpm/key data for videoId=$videoId")
            }
        } catch (e: Exception) {
            Logger.w(TAG, "Failed to load AutoMix meta for $videoId: ${e.message}")
        }
    }

    private fun getAutoTargetDurationMs(bpm: Int): Double {
        val clampedBpm = bpm.coerceIn(70, 170)
        return 30000.0 - (clampedBpm - 70) * 230.0
    }

    private fun resolveAutoCrossfadeDurationMs(
        currentVideoId: String,
        nextVideoId: String,
    ): Int {
        val currentBpm = audioMetaCache[currentVideoId]?.bpm
        val nextBpm = audioMetaCache[nextVideoId]?.bpm
        if (currentBpm == null || nextBpm == null) return AUTO_FALLBACK_DURATION_MS
        if (currentBpm <= 0 || nextBpm <= 0) return AUTO_FALLBACK_DURATION_MS

        val beatMs = 60_000.0 / currentBpm
        val baseTargetMs = getAutoTargetDurationMs(currentBpm)

        val bpmGapFactor = calculateBpmGapDurationFactor(currentBpm, nextBpm)
        val keyGapFactor = calculateKeyGapDurationFactor(currentVideoId, nextVideoId)
        val adjustedTargetMs = baseTargetMs * bpmGapFactor * keyGapFactor

        val bestBeatCount =
            BEAT_COUNT_OPTIONS.minByOrNull { abs(it * beatMs - adjustedTargetMs) }
                ?: DEFAULT_BEAT_COUNT
        val duration = (bestBeatCount * beatMs).toInt()

        Logger.d(
            TAG,
            "AutoMix duration: bpm=$currentBpm→$nextBpm, base=${baseTargetMs.toInt()}ms, " +
                "bpmGap=${"%.2f".format(bpmGapFactor)}, keyGap=${"%.2f".format(keyGapFactor)}, " +
                "adjusted=${adjustedTargetMs.toInt()}ms, beats=$bestBeatCount, final=${duration}ms",
        )

        return duration.coerceIn(AUTO_MIN_DURATION_MS, AUTO_MAX_DURATION_MS)
    }

    private fun calculateBpmGapDurationFactor(currentBpm: Int, nextBpm: Int): Double {
        if (currentBpm <= 0 || nextBpm <= 0) return 1.0
        var ratio = nextBpm.toDouble() / currentBpm.toDouble()
        while (ratio > 1.5) ratio /= 2.0
        while (ratio < 0.67) ratio *= 2.0
        val gapPercent = abs(1.0 - ratio)
        return 1.0 + gapPercent * BPM_GAP_DURATION_SCALE
    }

    private fun calculateKeyGapDurationFactor(
        currentVideoId: String,
        nextVideoId: String,
    ): Double {
        val currentMeta = audioMetaCache[currentVideoId]
        val nextMeta = audioMetaCache[nextVideoId]
        val currentKey = currentMeta?.key ?: return UNKNOWN_GAP_DEFAULT_FACTOR
        val nextKey = nextMeta?.key ?: return UNKNOWN_GAP_DEFAULT_FACTOR

        val currentCamelot = keyToCamelot(currentKey, currentMeta.keyScale) ?: return 1.0
        val nextCamelot = keyToCamelot(nextKey, nextMeta.keyScale) ?: return 1.0

        val dist = camelotDistance(currentCamelot, nextCamelot)
        return when {
            dist <= 1 -> 1.0
            dist == 2 -> 1.1
            dist <= 4 -> 1.25
            else -> 1.4
        }
    }

    // ========== Camelot Wheel ==========

    private data class CamelotCode(val number: Int, val isMinor: Boolean) {
        override fun toString(): String = "$number${if (isMinor) "A" else "B"}"
    }

    private fun keyToCamelot(key: String, keyScale: String?): CamelotCode? {
        val semitone = keyToSemitone(key)
        if (semitone < 0) return null
        val isMinor = keyScale?.uppercase()?.contains("MIN") == true
        val minorCamelotByPitch = intArrayOf(5, 12, 7, 2, 9, 4, 11, 6, 1, 8, 3, 10)
        val majorCamelotByPitch = intArrayOf(8, 3, 10, 5, 12, 7, 2, 9, 4, 11, 6, 1)
        val number = if (isMinor) minorCamelotByPitch[semitone] else majorCamelotByPitch[semitone]
        return CamelotCode(number, isMinor)
    }

    private fun camelotDistance(a: CamelotCode, b: CamelotCode): Int {
        val numberDiff = abs(a.number - b.number)
        val circularDist = minOf(numberDiff, 12 - numberDiff)
        val typeDiff = if (a.isMinor != b.isMinor) 1 else 0
        return circularDist + typeDiff
    }

    private fun keyToSemitone(key: String): Int =
        when (key.trim()) {
            "C" -> 0; "C#", "Db" -> 1; "D" -> 2; "D#", "Eb" -> 3
            "E" -> 4; "F" -> 5; "F#", "Gb" -> 6; "G" -> 7
            "G#", "Ab" -> 8; "A" -> 9; "A#", "Bb" -> 10; "B" -> 11
            else -> -1
        }

    companion object {
        private const val AUTO_FALLBACK_DURATION_MS = 30000
        private const val AUTO_MIN_DURATION_MS = 20000
        private const val AUTO_MAX_DURATION_MS = 45000
        private val BEAT_COUNT_OPTIONS = intArrayOf(8, 16, 24, 32, 40, 48, 64, 80, 96)
        private const val DEFAULT_BEAT_COUNT = 32
        private const val BPM_RATIO_MIN = 0.75f
        private const val BPM_RATIO_MAX = 1.25f
        private const val BPM_GAP_DURATION_SCALE = 2.0
        private const val UNKNOWN_GAP_DEFAULT_FACTOR = 1.25

        // Must match the `:network-caching` value buildVlcOptions() sets for audio.
        // Shared here (instead of duplicated as a magic number) so the pre-roll lead
        // time below is always derived from the real buffer target, not guessed.
        const val AUDIO_NETWORK_CACHING_MS = 3000L

        // WAVORA CROSSFADE FIX (Windows stutter, remaining gap): a precached player
        // only had `media().prepare()` called on it — URL resolved, demuxer opened,
        // but NOT playing yet. The actual audio decode + output pipeline (the thing
        // `:network-caching=3000` is buffering for) only started when `play()` was
        // called, which happened at the SAME instant the crossfade needed the audio
        // to already be stable: `preparationBufferMs` was `0L` for the precached case
        // in startPositionUpdates() (below), meaning the trigger fired as if a
        // precached player needed no extra lead time at all. That's backwards —
        // "precached" only means the URL is resolved, not that the buffer is filled.
        // Fix: give the precached player's `play()` a real head start — muted, at
        // volume 0 — a few seconds before the visible crossfade actually begins, so
        // its buffer has time to stabilize before the fade makes it audible. This
        // margin mirrors AUDIO_NETWORK_CACHING_MS with a small safety cushion.
        const val PRECACHE_AUDIO_PREROLL_LEAD_MS = AUDIO_NETWORK_CACHING_MS + 500L

        // How close to 0 a post-seek timeChanged report has to be before we
        // trust it as confirmation the seek actually landed (see the
        // isPrimed branch of triggerCrossfadeTransition). Generous enough to
        // tolerate VLC's own polling granularity, tight enough that a stale
        // pre-seek event (reporting ~PRECACHE_AUDIO_PREROLL_LEAD_MS) can
        // never satisfy it by accident.
        const val SEEK_APPLIED_TIME_THRESHOLD_MS = 500L
    }

    // ========== Position Updates ==========

    private fun startPositionUpdates() {
        stopPositionUpdates()

        positionUpdateJob =
            coroutineScope.launch {
                while (isActive && currentPlayer != null) {
                    try {
                        if (internalState == InternalState.PLAYING ||
                            internalState == InternalState.READY ||
                            internalState == InternalState.PAUSED
                        ) {
                            // During crossfade, show the incoming track's timeline exclusively
                            if (isCrossfading) {
                                val nextPlayer = secondaryPlayer
                                if (nextPlayer != null) {
                                    val pos = nextPlayer.time
                                    val dur = nextPlayer.length
                                    if (pos > 0) cachedPosition = pos
                                    if (dur > 0) cachedDuration = dur
                                }
                            } else {
                                val player = currentPlayer
                                if (player != null) {
                                    val pos = player.time
                                    val dur = player.length
                                    if (pos > 0) cachedPosition = pos
                                    if (dur > 0) cachedDuration = dur
                                }
                            }

                            // Check if should trigger crossfade.
                            // Use currentPlayer's time (not the timeline player) for trigger detection.
                            // Block when paused (internalPlayWhenReady=false) to prevent
                            // crossfade during queue restore.
                            if (crossfadeEnabled &&
                                !isCrossfading &&
                                internalPlayWhenReady
                            ) {
                                val player = currentPlayer
                                if (player != null) {
                                    val dur = player.length
                                    val pos = player.time
                                    if (dur > 0 && pos > 0) {
                                        val timeRemaining = dur - pos
                                        val nextVideoId = playlist.getOrNull(getNextMediaItemIndex())?.mediaId
                                        val precachedNext = nextVideoId?.let { precachedPlayers[it] }
                                        // A precached player only has media().prepare() called on it —
                                        // URL resolved, demuxer opened — NOT playing. It still needs
                                        // preparationBufferMs of real buffer fill after play() starts,
                                        // same as a non-precached one; the difference is WHEN that
                                        // play() happens (see the pre-roll block below), not whether
                                        // the buffer requirement exists at all.
                                        val preparationBufferMs = 3000L
                                        val resolvedDurationMs =
                                            if (crossfadeDurationMs == DataStoreManager.CROSSFADE_DURATION_AUTO) {
                                                val currentVideoId = playlist.getOrNull(localCurrentMediaItemIndex)?.mediaId ?: ""
                                                resolveAutoCrossfadeDurationMs(currentVideoId, nextVideoId ?: "")
                                            } else {
                                                crossfadeDurationMs
                                            }
                                        val triggerThreshold = resolvedDurationMs.toLong() + preparationBufferMs

                                        // WAVORA CROSSFADE FIX (Windows stutter, remaining gap): pre-roll
                                        // the precached player — muted, volume 0 — a few seconds before
                                        // the visible crossfade actually starts, so play() (and the real
                                        // buffer fill it kicks off) is no longer called at the exact
                                        // instant the fade needs stable audio. Bounded window (a few
                                        // seconds), so there's no meaningful position drift by the time
                                        // the real crossfade picks this player up.
                                        if (precachedNext != null &&
                                            !precachedNext.player.isPrimed &&
                                            timeRemaining > triggerThreshold &&
                                            timeRemaining <= triggerThreshold + PRECACHE_AUDIO_PREROLL_LEAD_MS
                                        ) {
                                            val precachedPlayer = precachedNext.player
                                            precachedPlayer.isPrimed = true
                                            precachedPlayer.mediaPlayer.audio().isMute = true
                                            precachedPlayer.setVolume(0)
                                            precachedPlayer.play()
                                            Logger.d(TAG, "Pre-rolling precached player for $nextVideoId (${PRECACHE_AUDIO_PREROLL_LEAD_MS}ms lead)")
                                            CrossfadeAudit.log(
                                                "PREROLL_START",
                                                playerId = precachedPlayer.id,
                                                role = "next-precached",
                                                details = "nextVideoId=$nextVideoId leadMs=$PRECACHE_AUDIO_PREROLL_LEAD_MS timeRemaining=$timeRemaining",
                                            )
                                        }

                                        if (timeRemaining in 1..triggerThreshold) {
                                            if (hasNextMediaItem()) {
                                                val nextIndex = getNextMediaItemIndex()
                                                CrossfadeAudit.log(
                                                    "CROSSFADE_TRIGGER_DECISION",
                                                    playerId = currentPlayer?.id,
                                                    role = "current",
                                                    details = "timeRemaining=$timeRemaining triggerThreshold=$triggerThreshold nextIndex=$nextIndex",
                                                )
                                                triggerCrossfadeTransition(nextIndex)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {
                        // Ignore query errors
                    }

                    delay(200) // Update every 200ms
                }
            }
    }

    private fun stopPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = null
    }

    /**
     * Trigger precaching
     */
    private fun triggerPrecachingInternal() {
        if (!precacheEnabled || playlist.isEmpty()) return

        cancelPrecaching()
        Logger.d(TAG, "Trigger precache (grace period: ${precacheStartupGraceMs}ms)")
        precacheJob =
            coroutineScope.launch {
                try {
                    // Let the just-started current track's own buffer fill window pass
                    // before we let precaching touch the native player factory — see
                    // precacheStartupGraceMs doc comment for why. Precache targets are
                    // whole tracks away by definition, so this delay costs us nothing
                    // functionally; it's the fix for the post-transition double-freeze.
                    delay(precacheStartupGraceMs)
                    if (!isActive) return@launch

                    val indicesToPrecache = mutableListOf<Int>()

                    // Re-read the index AFTER the grace period, not before: the user may
                    // have skipped/seeked while we were waiting, and precaching stale
                    // targets would both waste work and reintroduce the same contention
                    // this delay exists to avoid.
                    val index = localCurrentMediaItemIndex
                    for (i in 1..maxPrecacheCount) {
                        val nextIndex =
                            when (internalRepeatMode) {
                                PlayerConstants.REPEAT_MODE_ALL -> {
                                    (index + i) % playlist.size
                                }
                                else -> {
                                    val next = index + i
                                    if (next < playlist.size) next else break
                                }
                            }

                        if (nextIndex != localCurrentMediaItemIndex &&
                            !precachedPlayers.containsKey(playlist.getOrNull(nextIndex)?.mediaId)
                        ) {
                            indicesToPrecache.add(nextIndex)
                        }
                    }

                    // Run all I/O and VLC native calls off EDT
                    for (idx in indicesToPrecache) {
                        if (!isActive) break

                        val mediaItem = playlist.getOrNull(idx) ?: continue

                        // Network extraction on IO, VLC native calls on VLC thread
                        val source = withContext(Dispatchers.IO) { extractPlayableUrl(mediaItem) }

                        if (source != null && source.url.isNotEmpty()) {
                            try {
                                val player = createMediaPlayerInternal(source)
                                val options = buildVlcOptions(source)
                                player.mediaPlayer.media().prepare(source.url, *options)
                                precachedPlayers[mediaItem.mediaId] =
                                    PrecachedPlayer(player, mediaItem, source)
                                Logger.d(TAG, "Precached player for index $idx")
                            } catch (e: Exception) {
                                Logger.e(TAG, "Precaching error for $idx: ${e.message}")
                            }
                        }

                        // Real stagger between precache attempts — see precacheStaggerMs
                        // doc comment. Only matters when maxPrecacheCount > 1.
                        delay(precacheStaggerMs)
                    }
                } catch (e: Exception) {
                    if (e !is CancellationException) {
                        Logger.e(TAG, "Precaching error: ${e.message}")
                    }
                }
            }
    }

    private fun cancelPrecaching() {
        precacheJob?.cancel()
        precacheJob = null
    }

    private fun clearPrecacheExceptCurrentInternal() {
        Logger.d(TAG, "Clearing precache")
        precachedPlayers.entries.removeIf { (videoId, cached) ->
            if (videoId != currentMediaItem?.mediaId) {
                cleanupPlayerInternal(cached.player)
                true
            } else {
                false
            }
        }
    }

    private fun clearAllPrecacheInternal() {
        Logger.d(TAG, "Clearing all precache")
        precachedPlayers.values.forEach { cleanupPlayerInternal(it.player) }
        precachedPlayers.clear()
    }

    /**
     * Dispatch listener notifications on VLC thread.
     * Listeners (JvmMediaPlayerHandlerImpl) use thread-safe StateFlow updates
     * and contain runBlocking calls that would deadlock on Main/Swing EDT.
     */
    private fun notifyListeners(block: MediaPlayerListener.() -> Unit) {
        coroutineScope.launch {
            listeners.forEach(block)
        }
    }

    private fun notifyEqualizerIntent(shouldOpen: Boolean) {
        notifyListeners { shouldOpenOrCloseEqualizerIntent(shouldOpen) }
    }

    // ========== Shuffle Management ==========

    private fun createShuffleOrder() {
        if (playlist.isEmpty()) {
            shuffleIndices.clear()
            shuffleOrder.clear()
            return
        }

        val indices = playlist.indices.toMutableList()
        val currentIndex = localCurrentMediaItemIndex
        if (currentIndex in indices) {
            indices.removeAt(currentIndex)
        }

        indices.shuffle()

        if (currentIndex in playlist.indices) {
            indices.add(0, currentIndex)
        }

        shuffleOrder.clear()
        shuffleOrder.addAll(indices)

        shuffleIndices.clear()
        shuffleIndices.addAll(List(playlist.size) { 0 })
        shuffleOrder.forEachIndexed { shuffledPos, originalIndex ->
            shuffleIndices[originalIndex] = shuffledPos
        }

        Logger.d(TAG, "Created shuffle order: $shuffleOrder")
    }

    private fun clearShuffleOrder() {
        shuffleIndices.clear()
        shuffleOrder.clear()
    }

    private fun insertIntoShuffleOrder(
        insertedOriginalIndex: Int,
        afterShufflePos: Int,
    ) {
        if (playlist.isEmpty() || insertedOriginalIndex !in playlist.indices) return

        for (i in shuffleOrder.indices) {
            if (shuffleOrder[i] >= insertedOriginalIndex) {
                shuffleOrder[i]++
            }
        }

        val insertPos = (afterShufflePos + 1).coerceIn(0, shuffleOrder.size)
        shuffleOrder.add(insertPos, insertedOriginalIndex)

        shuffleIndices.clear()
        shuffleIndices.addAll(List(playlist.size) { 0 })
        shuffleOrder.forEachIndexed { shuffledPos, origIndex ->
            if (origIndex < shuffleIndices.size) {
                shuffleIndices[origIndex] = shuffledPos
            }
        }
    }

    private fun getShuffledMediaItemList(): List<GenericMediaItem> {
        if (!internalShuffleModeEnabled || shuffleOrder.isEmpty()) {
            return playlist.toList()
        }
        return shuffleOrder.mapNotNull { playlist.getOrNull(it) }
    }

    private fun notifyTimelineChanged(reason: String) {
        val list = getShuffledMediaItemList()
        notifyListeners { onTimelineChanged(list, reason) }
    }

    fun setPrecachingEnabled(enabled: Boolean) {
        precacheEnabled = enabled
        if (!enabled) {
            clearPrecacheExceptCurrentInternal()
        } else {
            triggerPrecachingInternal()
        }
    }

    fun setMaxPrecacheCount(count: Int) {
        // maxPrecacheCount is val, but can be changed to var if needed
    }

    // ========== URL Extraction ==========

    /**
     * Extract playable URL for a media item.
     * KEY IMPROVEMENT over GStreamer: Returns both video AND audio URLs for merging
     * via VLC's --input-slave option (equivalent to Android's MergingMediaSource).
     */
    private suspend fun extractPlayableUrl(mediaItem: GenericMediaItem): PlayableSource? {
        Logger.w(TAG, "Extracting playable URL for ${mediaItem.mediaId}")
        val shouldFindVideo =
            mediaItem.isVideo() &&
                dataStoreManager.watchVideoInsteadOfPlayingAudio.first() == DataStoreManager.TRUE
        val videoId = mediaItem.mediaId

        // Check downloads first
        val downloadFiles =
            File(getDownloadPath()).listFiles()?.filter {
                it.name.contains(videoId)
            }
        if (!downloadFiles.isNullOrEmpty()) {
            val audioFile = downloadFiles.firstOrNull { !it.name.contains(MERGING_DATA_TYPE.VIDEO) }
            if (audioFile != null && audioFile.length() > 0 && audioFile.exists()) {
                // VLC accepts absolute file paths directly as MRL
                return PlayableSource(isVideo = false, url = audioFile.absolutePath)
            }
        }

        // Try new format API (returns both audio and video URLs)
        streamRepository.getNewFormat(videoId).lastOrNull()?.let { format ->
            val audioUrl = format.audioUrl
            val videoUrl = format.videoUrl

            if (shouldFindVideo && !videoUrl.isNullOrEmpty()) {
                val is403Video = streamRepository.is403Url(videoUrl).firstOrNull() != false
                if (!is403Video) {
                    // Return video URL with audio as slave for merging
                    val audioSlave =
                        if (!audioUrl.isNullOrEmpty()) {
                            val is403Audio = streamRepository.is403Url(audioUrl).firstOrNull() != false
                            if (!is403Audio) audioUrl else null
                        } else {
                            null
                        }

                    Logger.w("Stream", "Video from format (with audio slave: ${audioSlave != null})")
                    return PlayableSource(
                        isVideo = true,
                        url = videoUrl,
                        audioSlaveUrl = audioSlave,
                    )
                }
            } else if (!shouldFindVideo && !audioUrl.isNullOrEmpty()) {
                val is403Url = streamRepository.is403Url(audioUrl).firstOrNull() != false
                if (!is403Url) {
                    Logger.w("Stream", "Audio from format")
                    return PlayableSource(isVideo = false, url = audioUrl)
                }
            }
        }

        // Fallback to stream extraction
        if (shouldFindVideo) {
            val videoUrl =
                streamRepository
                    .getStream(
                        dataStoreManager,
                        videoId,
                        isDownloading = false,
                        isVideo = true,
                        muxed = true,
                    ).lastOrNull()
            if (videoUrl != null) {
                Logger.d(TAG, "Stream Video $videoUrl")
                return PlayableSource(isVideo = true, url = videoUrl)
            }
        } else {
            val audioUrl =
                streamRepository
                    .getStream(
                        dataStoreManager,
                        videoId,
                        isDownloading = false,
                        isVideo = false,
                    ).lastOrNull()
            if (audioUrl != null) {
                Logger.d(TAG, "Stream Audio $audioUrl")
                return PlayableSource(isVideo = false, url = audioUrl)
            }
        }

        return null
    }
}

/**
 * VLC Player wrapper - equivalent to the old GstreamerPlayer.
 * Wraps a VLC MediaPlayer instance with optional video surface component.
 */
class VlcPlayer(
    val mediaPlayer: MediaPlayer,
    val videoSurface: Component? = null,
) {
    companion object {
        private const val TAG = "VlcPlayer"
    }

    // Stable identity for this instance across its whole lifetime, so log
    // lines from two players alive at once during a crossfade (and from a
    // released one whose events may still trickle in) can be told apart.
    // See CrossfadeAudit's doc.
    val id: Int = CrossfadeAudit.nextPlayerId()

    init {
        CrossfadeAudit.log("CREATE", playerId = id)
    }

    @Volatile
    var isReleased = false
        private set

    // Set once play() has been called ahead of time (muted) to give the buffer a
    // real head start before a crossfade needs this player's audio. Lets the
    // crossfade path skip a redundant play()+wait when the player is already
    // stable instead of starting decode at the exact instant it's needed.
    @Volatile
    var isPrimed = false

    private var eventListener: MediaPlayerEventAdapter? = null

    fun setEventListener(listener: MediaPlayerEventAdapter?) {
        CrossfadeAudit.log(
            "LISTENER_SET",
            playerId = id,
            details = "attaching=${listener != null} hadPrevious=${eventListener != null}",
        )
        eventListener?.let {
            try {
                mediaPlayer.events().removeMediaPlayerEventListener(it)
            } catch (_: Exception) {
            }
        }
        eventListener = listener
        listener?.let {
            try {
                mediaPlayer.events().addMediaPlayerEventListener(it)
            } catch (_: Exception) {
            }
        }
    }

    fun play() {
        if (isReleased) {
            CrossfadeAudit.log("PLAY_IGNORED_RELEASED", playerId = id)
            return
        }
        CrossfadeAudit.log("PLAY", playerId = id)
        try {
            mediaPlayer.controls().play()
        } catch (e: Exception) {
            Logger.w(TAG, "Error playing: ${e.message}")
        }
    }

    fun pause() {
        if (isReleased) {
            CrossfadeAudit.log("PAUSE_IGNORED_RELEASED", playerId = id)
            return
        }
        CrossfadeAudit.log("PAUSE", playerId = id)
        try {
            mediaPlayer.controls().setPause(true)
        } catch (e: Exception) {
            Logger.w(TAG, "Error pausing: ${e.message}")
        }
    }

    fun stop() {
        if (isReleased) {
            CrossfadeAudit.log("STOP_IGNORED_RELEASED", playerId = id)
            return
        }
        CrossfadeAudit.log("STOP", playerId = id)
        try {
            mediaPlayer.controls().stop()
        } catch (e: Exception) {
            Logger.w(TAG, "Error stopping: ${e.message}")
        }
    }

    /**
     * Set volume. VLC range: 0-200 (100 = normal).
     * We use 0-100 mapping from our 0.0-1.0 interface range.
     *
     * Logged on every call (not throttled) - this is exactly the "was the
     * volume accidentally left at 0" and "what was each player's volume
     * during the fade" evidence that was requested. If this turns out too
     * noisy in practice, filter by player id in the log, not by removing
     * calls here.
     */
    fun setVolume(volume: Int) {
        if (isReleased) {
            CrossfadeAudit.log("SET_VOLUME_IGNORED_RELEASED", playerId = id, details = "requested=$volume")
            return
        }
        CrossfadeAudit.log("SET_VOLUME", playerId = id, details = "value=$volume")
        try {
            mediaPlayer.audio().setVolume(volume)
        } catch (e: Exception) {
            Logger.w(TAG, "Error setting volume: ${e.message}")
        }
    }

    fun seekTo(timeMs: Long) {
        if (isReleased) {
            CrossfadeAudit.log("SEEK_IGNORED_RELEASED", playerId = id, details = "requested=$timeMs")
            return
        }
        // NOTE for the audit: seekTo() on vlcj is fire-and-forget - this log
        // line fires the instant the seek is *issued*, not when VLC has
        // actually finished repositioning the decoder. Compare this
        // timestamp against the next SET_VOLUME/LISTENER_SET/PLAY entries
        // for this same player id, and against this player's own
        // TIME_SNAPSHOT entries (added around the isPrimed crossfade path)
        // to see whether other calls proceed before the seek has visibly
        // taken effect (i.e. before .time actually reads back near timeMs).
        CrossfadeAudit.log("SEEK_ISSUED", playerId = id, details = "targetMs=$timeMs currentTimeReadback=${runCatching { mediaPlayer.status().time() }.getOrDefault(-1)}")
        try {
            mediaPlayer.controls().setTime(timeMs)
        } catch (e: Exception) {
            Logger.w(TAG, "Error seeking: ${e.message}")
        }
    }

    val time: Long
        get() =
            if (isReleased) {
                0L
            } else {
                try {
                    mediaPlayer.status().time()
                } catch (_: Exception) {
                    0L
                }
            }

    val length: Long
        get() =
            if (isReleased) {
                0L
            } else {
                try {
                    mediaPlayer.status().length()
                } catch (_: Exception) {
                    0L
                }
            }

    fun release() {
        if (isReleased) {
            // This is exactly the "existe algún release doble" case requested -
            // logged distinctly (WARN) since it should never legitimately happen.
            Logger.w(TAG, "Double release() attempted on player $id - ignoring")
            CrossfadeAudit.log("RELEASE_DOUBLE_ATTEMPT", playerId = id)
            return
        }
        isReleased = true
        CrossfadeAudit.log(
            "RELEASE",
            playerId = id,
            details = "wasPlaying=${runCatching { mediaPlayer.status().isPlaying }.getOrDefault(false)}",
        )
        try {
            setEventListener(null)
            // Run stop+release on a separate thread to avoid deadlock
            // if called from VLC callback thread (which can happen during transitions)
            Thread {
                try {
                    mediaPlayer.controls().stop()
                    mediaPlayer.release()
                    CrossfadeAudit.log("RELEASE_COMPLETE", playerId = id)
                } catch (e: Exception) {
                    Logger.w(TAG, "Error in async release: ${e.message}")
                    CrossfadeAudit.log("RELEASE_ERROR", playerId = id, details = "error=${e.message}")
                }
            }.start()
        } catch (e: Exception) {
            Logger.w(TAG, "Error releasing player: ${e.message}")
        }
    }
}

/**
 * JPanel that renders VLC video frames via callback.
 * Works on all platforms including macOS (unlike Canvas-based approach which
 * requires a native window handle that macOS VLC can't use).
 *
 * VLC renders frames to a BufferedImage via native buffer callbacks,
 * then this panel paints the image scaled to fit.
 */
class VlcVideoSurfacePanel : JPanel() {
    @Volatile
    private var videoImage: BufferedImage? = null

    @Volatile
    private var videoWidth = 0

    @Volatile
    private var videoHeight = 0

    // Strong references to prevent JNA from garbage collecting native callback pointers.
    // JNA wraps these in CallbackReference with weak refs; without strong refs here,
    // the GC can collect them while VLC native code still holds the function pointer → SIGSEGV.
    private val bufferFormatCb =
        object : BufferFormatCallback {
            override fun getBufferFormat(
                sourceWidth: Int,
                sourceHeight: Int,
            ): BufferFormat {
                videoWidth = sourceWidth
                videoHeight = sourceHeight
                videoImage = BufferedImage(sourceWidth, sourceHeight, BufferedImage.TYPE_INT_ARGB)
                return RV32BufferFormat(sourceWidth, sourceHeight)
            }

            override fun newFormatSize(
                p0: Int,
                p1: Int,
                p2: Int,
                p3: Int,
            ) {
                // No-op
            }

            override fun allocatedBuffers(buffers: Array<out ByteBuffer>) {
                // No-op
            }
        }

    private val renderCb =
        object : RenderCallback {
            override fun display(
                p0: MediaPlayer,
                p1: Array<ByteBuffer>,
                p2: BufferFormat,
                p3: Int,
                p4: Int,
            ) {
                val img = videoImage ?: return
                try {
                    val rgbArray = (img.raster.dataBuffer as DataBufferInt).data
                    val intBuffer = p1[0].asIntBuffer()
                    intBuffer.get(rgbArray, 0, minOf(rgbArray.size, intBuffer.remaining()))
                    repaint()
                } catch (_: Exception) {
                    // Buffer size mismatch during format change - skip frame
                }
            }

            override fun lock(p0: MediaPlayer?) {
                // No-op
            }

            override fun unlock(p0: MediaPlayer?) {
                // No-op
            }
        }

    // The CallbackVideoSurface itself also must be strongly referenced
    @Volatile
    private var videoSurfaceRef: Any? = null

    init {
        background = Color.BLACK
        isOpaque = true
    }

    /**
     * Create a callback video surface bound to this panel.
     * The surface and all callbacks are strongly referenced by this panel
     * to prevent JNA garbage collection.
     */
    fun createVideoSurface(factory: MediaPlayerFactory): uk.co.caprica.vlcj.player.embedded.videosurface.VideoSurface {
        val surface =
            factory.videoSurfaces().newVideoSurface(
                bufferFormatCb,
                renderCb,
                true, // lock buffers for thread safety
            )
        videoSurfaceRef = surface
        return surface
    }

    override fun getPreferredSize(): java.awt.Dimension =
        if (videoWidth > 0 && videoHeight > 0) {
            java.awt.Dimension(videoWidth, videoHeight)
        } else {
            java.awt.Dimension(640, 360)
        }

    override fun getMinimumSize(): java.awt.Dimension = java.awt.Dimension(1, 1)

    override fun getMaximumSize(): java.awt.Dimension = java.awt.Dimension(Int.MAX_VALUE, Int.MAX_VALUE)

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val img = videoImage ?: return
        val g2 = g as Graphics2D
        g2.setRenderingHint(
            RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_BILINEAR,
        )
        // Maintain aspect ratio, center in panel
        val panelW = width.toDouble()
        val panelH = height.toDouble()
        val imgW = img.width.toDouble()
        val imgH = img.height.toDouble()
        if (imgW <= 0 || imgH <= 0) return

        val scale = minOf(panelW / imgW, panelH / imgH)
        val drawW = (imgW * scale).toInt()
        val drawH = (imgH * scale).toInt()
        val x = ((panelW - drawW) / 2).toInt()
        val y = ((panelH - drawH) / 2).toInt()

        g2.drawImage(img, x, y, drawW, drawH, null)
    }
}