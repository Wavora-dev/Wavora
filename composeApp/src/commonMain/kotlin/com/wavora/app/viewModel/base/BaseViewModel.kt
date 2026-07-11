package com.wavora.app.viewModel.base

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wavora.domain.mediaservice.handler.MediaPlayerHandler
import com.wavora.domain.mediaservice.handler.QueueData
import com.wavora.domain.mediaservice.session.PlaybackState
import com.wavora.domain.mediaservice.session.PlayerController
import com.wavora.domain.mediaservice.session.PlayerSession
import com.wavora.logger.LogLevel
import com.wavora.logger.Logger
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import multiplatform.network.cmptoast.ToastDuration
import multiplatform.network.cmptoast.ToastGravity
import multiplatform.network.cmptoast.showToast
import org.jetbrains.compose.resources.StringResource
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import wavora.composeapp.generated.resources.Res
import wavora.composeapp.generated.resources.loading

abstract class BaseViewModel :
    ViewModel(),
    KoinComponent {
    protected val mediaPlayerHandler: MediaPlayerHandler by inject<MediaPlayerHandler>()

    /**
     * Phase 4 of the PlayerSession migration (see PROMPT_01). New ViewModel code should prefer
     * these over [mediaPlayerHandler] where possible, since they depend on the PlayerSession/
     * PlayerController abstractions instead of the full MediaPlayerHandler surface (point 5 of
     * the migration). [mediaPlayerHandler] stays available and unchanged for everything that
     * doesn't have a PlayerSession/PlayerController equivalent yet (queue editing, shuffle,
     * repeat, like, sleep timer, etc.) — this is an addition, not a replacement.
     */
    protected val playerSession: PlayerSession by inject<PlayerSession>()
    protected val playerController: PlayerController by inject<PlayerController>()
    private val _nowPlayingVideoId: MutableStateFlow<String> = MutableStateFlow("")

    /**
     * Get now playing video id
     * If empty, no video is playing
     */
    val nowPlayingVideoId: StateFlow<String> get() = _nowPlayingVideoId

    /**
     * Tag for logging
     */
    protected val tag: String = this::class.simpleName ?: "BaseViewModel"

    /**
     * Log with viewModel tag
     */
    protected fun log(
        message: String,
        logType: LogLevel = LogLevel.WARN,
    ) {
        when (logType) {
            LogLevel.DEBUG -> Logger.d(tag, message)
            LogLevel.INFO -> Logger.i(tag, message)
            LogLevel.WARN -> Logger.w(tag, message)
            LogLevel.ERROR -> Logger.e(tag, message)
        }
    }

    /**
     * Cancel all jobs
     */
    override fun onCleared() {
        super.onCleared()
        viewModelScope.cancel()
        log("ViewModel cleared", LogLevel.WARN)
    }

    /**
     * Public equivalent of [onCleared], for the handful of ViewModels registered as Koin
     * `single { }` (see PROMPT_06 OOM investigation) instead of the `viewModel { }` DSL —
     * `PlayerViewModel`, `NowPlayingViewModel`, `AppViewModel`, `SharedViewModel`,
     * `SearchViewModel`. Because those are constructed directly by Koin instead of through
     * Android's ViewModelStore/ViewModelProvider, the framework never calls `clear()` on them
     * when they're replaced, so [onCleared] (which is `protected`) never runs on its own.
     * Callers that recreate those specific Koin registrations (see `MainActivity.onCreate`) must
     * call this on the previous instance first, or its `init`-block collectors keep running
     * forever on a now-unreachable-from-Koin, but still fully alive and active, object graph.
     */
    fun forceClear() {
        onCleared()
    }

    fun makeToast(message: String?) {
        showToast(
            message = message ?: "NO MESSAGE",
            duration = ToastDuration.Short,
            gravity = ToastGravity.Bottom,
        )
    }

    protected fun getString(resId: StringResource): String =
        runBlocking {
            org.jetbrains.compose.resources
                .getString(resId)
        }

    // Was `MutableStateFlow(false to getString(Res.string.loading))` here — a `getString` call
    // (and therefore a `runBlocking`) executed directly in this property initializer, i.e. in the
    // constructor of *every* BaseViewModel subclass, on whatever thread first resolves that
    // ViewModel (main thread, at cold start, for the Koin-singleton ViewModels). Seeded with an
    // empty label instead and resolved asynchronously in `init` below; the label is only ever
    // visible while `showLoadingDialog.first == true`, and no caller shows the dialog synchronously
    // within the same frame as ViewModel construction, so this has no visible effect. Declared
    // above `init` (rather than below, where it used to sit) so it's guaranteed non-null by the
    // time `init` runs — `viewModelScope.launch` uses Dispatchers.Main.immediate, which can start
    // a coroutine body inline/synchronously when already on the main thread.
    private val _showLoadingDialog: MutableStateFlow<Pair<Boolean, String>> = MutableStateFlow(false to "")
    val showLoadingDialog: StateFlow<Pair<Boolean, String>> get() = _showLoadingDialog

    fun showLoadingDialog(message: String? = null) {
        _showLoadingDialog.value = true to (message ?: getString(Res.string.loading))
    }

    fun hideLoadingDialog() {
        _showLoadingDialog.value = false to getString(Res.string.loading)
    }

    init {
        getNowPlayingVideoId()
        viewModelScope.launch {
            _showLoadingDialog.value = false to org.jetbrains.compose.resources.getString(Res.string.loading)
        }
    }

    /**
     * Which item, across any list on screen, is the one currently playing — used by
     * Library/Album/Search/ArtworkPager to highlight the now-playing row.
     *
     * Phase 4 of the PlayerSession migration (see PROMPT_01): this used to combine
     * `mediaPlayerHandler.nowPlayingState` (a separately, asynchronously resolved `SongEntity`
     * that lags behind actual playback by one DB round-trip, and in one code path could keep the
     * *previous* track's value if the lookup came back empty) with `mediaPlayerHandler.controlState`.
     * `playerSession.currentSong` updates at the exact moment the player transitions, so this is
     * both simpler and more correct, not just an equivalent rewrite. `mediaId` is always equal to
     * `SongEntity.videoId` for how this codebase builds media items, so the string this exposes
     * to the UI doesn't change meaning.
     */
    private fun getNowPlayingVideoId() {
        viewModelScope.launch {
            combine(playerSession.currentSong, playerSession.playbackState) { song, playbackState ->
                Pair(song, playbackState)
            }.collect { (song, playbackState) ->
                _nowPlayingVideoId.value =
                    if (playbackState is PlaybackState.Playing) {
                        song?.mediaId ?: ""
                    } else {
                        ""
                    }
            }
        }
    }

    /**
     * Communicate with SimpleMediaServiceHandler to load media item
     */
    fun setQueueData(queueData: QueueData.Data) {
        mediaPlayerHandler.reset()
        mediaPlayerHandler.setQueueData(queueData)
    }

    fun <T> loadMediaItem(
        anyTrack: T,
        type: String,
        index: Int? = null,
    ) {
        viewModelScope.launch {
            mediaPlayerHandler.loadMediaItem(
                anyTrack = anyTrack,
                type = type,
                index = index,
            )
        }
    }

    fun shufflePlaylist(firstPlayIndex: Int = 0) {
        mediaPlayerHandler.shufflePlaylist(firstPlayIndex)
    }
}