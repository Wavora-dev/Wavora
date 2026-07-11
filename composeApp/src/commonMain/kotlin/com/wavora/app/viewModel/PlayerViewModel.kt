package com.wavora.app.viewModel

import androidx.lifecycle.viewModelScope
import com.wavora.common.Config.ALBUM_CLICK
import com.wavora.common.Config.PLAYLIST_CLICK
import com.wavora.common.Config.RECOVER_TRACK_QUEUE
import com.wavora.common.Config.SHARE
import com.wavora.common.Config.SONG_CLICK
import com.wavora.common.Config.VIDEO_CLICK
import com.wavora.domain.extension.toGenericMediaItem
import com.wavora.domain.manager.DataStoreManager
import com.wavora.domain.manager.DataStoreManager.Values.TRUE
import com.wavora.domain.mediaservice.handler.ControlState
import com.wavora.domain.mediaservice.handler.NowPlayingTrackState
import com.wavora.domain.mediaservice.handler.PlayerEvent
import com.wavora.domain.mediaservice.handler.PlaylistType
import com.wavora.domain.mediaservice.handler.QueueData
import com.wavora.domain.mediaservice.handler.RepeatState
import com.wavora.domain.mediaservice.handler.SimpleMediaState
import com.wavora.domain.mediaservice.handler.SleepTimerState
import com.wavora.domain.model.model.browse.album.Track
import com.wavora.domain.model.model.streams.TimeLine
import com.wavora.domain.repository.SongRepository
import com.wavora.domain.repository.StreamRepository
import com.wavora.domain.utils.Resource
import com.wavora.domain.utils.toSongEntity
import com.wavora.domain.utils.toTrack
import com.wavora.app.viewModel.base.BaseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import wavora.composeapp.generated.resources.Res
import wavora.composeapp.generated.resources.added_to_queue
import wavora.composeapp.generated.resources.error
import wavora.composeapp.generated.resources.play_next
import wavora.composeapp.generated.resources.shared

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModel(
    private val dataStoreManager: DataStoreManager,
    private val songRepository: SongRepository,
    private val streamRepository: StreamRepository,
) : BaseViewModel() {

    var isServiceRunning: Boolean = false
    var isFullScreen: Boolean = false
    var playlistId: MutableStateFlow<String?> = MutableStateFlow(null)

    private val _nowPlayingState = MutableStateFlow<NowPlayingTrackState?>(null)
    val nowPlayingState: StateFlow<NowPlayingTrackState?> = _nowPlayingState

    // Phase 4 of the PlayerSession migration (see PROMPT_01): playerSession.queue is the same
    // underlying data as mediaPlayerHandler.queueData, but non-null (StateFlow<out T> is
    // covariant, so this stays binary-compatible with the 4 existing call sites that still treat
    // the result as StateFlow<QueueData?>).
    fun getQueueDataState() = playerSession.queue

    private val _controllerState = MutableStateFlow(
        ControlState(
            isPlaying = false,
            isShuffle = false,
            repeatState = RepeatState.None,
            isLiked = false,
            isNextAvailable = false,
            isPreviousAvailable = false,
            isCrossfading = false,
            volume = 1f,
        ),
    )
    val controllerState: StateFlow<ControlState> = _controllerState

    private val _timeline = MutableStateFlow(
        TimeLine(current = -1L, total = -1L, bufferedPercent = 0, loading = true),
    )
    val timeline: StateFlow<TimeLine> = _timeline

    private val _sleepTimerState = MutableStateFlow(SleepTimerState(false, 0))
    val sleepTimerState: StateFlow<SleepTimerState> = _sleepTimerState

    private val _getVideo = MutableStateFlow(false)
    val getVideo: StateFlow<Boolean> = _getVideo

    init {
        viewModelScope.launch {
            launch {
                mediaPlayerHandler.nowPlayingState
                    .distinctUntilChangedBy { it.songEntity?.videoId }
                    .collectLatest { _nowPlayingState.value = it }
            }
            launch {
                mediaPlayerHandler.simpleMediaState.collect { mediaState ->
                    when (mediaState) {
                        is SimpleMediaState.Buffering -> _timeline.update { it.copy(loading = true) }
                        SimpleMediaState.Initial -> _timeline.update { it.copy(loading = true) }
                        SimpleMediaState.Ended -> _timeline.update {
                            it.copy(current = -1L, total = -1L, bufferedPercent = 0, loading = true)
                        }
                        is SimpleMediaState.Progress -> {
                            if (mediaState.progress >= 0L && mediaState.progress != _timeline.value.current) {
                                if (_timeline.value.total > 0L) {
                                    _timeline.update {
                                        it.copy(
                                            total = mediaPlayerHandler.getPlayerDuration(),
                                            current = mediaState.progress,
                                            loading = false,
                                        )
                                    }
                                } else {
                                    _timeline.update {
                                        it.copy(
                                            current = mediaState.progress,
                                            loading = true,
                                            total = mediaPlayerHandler.getPlayerDuration(),
                                        )
                                    }
                                }
                            }
                        }
                        is SimpleMediaState.Loading -> _timeline.update {
                            it.copy(bufferedPercent = mediaState.bufferedPercentage, total = mediaState.duration, loading = true)
                        }
                        is SimpleMediaState.Ready -> _timeline.update {
                            it.copy(current = mediaPlayerHandler.getProgress(), loading = false, total = mediaState.duration)
                        }
                    }
                }
            }
            launch {
                mediaPlayerHandler.controlState.collectLatest {
                    _controllerState.value = it
                    _timeline.update { t -> t.copy(isCrossfading = it.isCrossfading) }
                }
            }
            launch { mediaPlayerHandler.sleepTimerState.collectLatest { _sleepTimerState.value = it } }
            launch {
                dataStoreManager.watchVideoInsteadOfPlayingAudio.collectLatest {
                    _getVideo.value = it == TRUE
                }
            }
        }
    }

    fun onUIEvent(uiEvent: UIEvent) = viewModelScope.launch {
        when (uiEvent) {
            UIEvent.Backward -> mediaPlayerHandler.onPlayerEvent(PlayerEvent.Backward)
            UIEvent.Forward -> mediaPlayerHandler.onPlayerEvent(PlayerEvent.Forward)
            UIEvent.PlayPause -> mediaPlayerHandler.onPlayerEvent(PlayerEvent.PlayPause)
            UIEvent.Next -> mediaPlayerHandler.onPlayerEvent(PlayerEvent.Next)
            UIEvent.Previous -> mediaPlayerHandler.onPlayerEvent(PlayerEvent.Previous)
            UIEvent.SkipToPrevious -> mediaPlayerHandler.onPlayerEvent(PlayerEvent.SkipToPrevious)
            UIEvent.Stop -> mediaPlayerHandler.onPlayerEvent(PlayerEvent.Stop)
            is UIEvent.UpdateProgress -> mediaPlayerHandler.onPlayerEvent(PlayerEvent.UpdateProgress(uiEvent.newProgress))
            UIEvent.Repeat -> mediaPlayerHandler.onPlayerEvent(PlayerEvent.Repeat)
            UIEvent.Shuffle -> mediaPlayerHandler.onPlayerEvent(PlayerEvent.Shuffle)
            UIEvent.ToggleLike -> mediaPlayerHandler.onPlayerEvent(PlayerEvent.ToggleLike)
            is UIEvent.UpdateVolume -> {
                dataStoreManager.setPlayerVolume(uiEvent.newVolume)
                mediaPlayerHandler.onPlayerEvent(PlayerEvent.UpdateVolume(uiEvent.newVolume))
            }
        }
    }

    fun addListToQueue(listTrack: ArrayList<Track>) {
        viewModelScope.launch {
            if (listTrack.size == 1 && dataStoreManager.endlessQueue.first() == TRUE) {
                mediaPlayerHandler.playNext(listTrack.first())
                makeToast(getString(Res.string.play_next))
            } else {
                mediaPlayerHandler.loadMoreCatalog(listTrack)
                makeToast(getString(Res.string.added_to_queue))
            }
        }
    }

    fun stopPlayer() {
        mediaPlayerHandler.resetSongAndQueue()
        onUIEvent(UIEvent.Stop)
    }

    fun setSleepTimer(minutes: Int) = mediaPlayerHandler.sleepStart(minutes)
    fun stopSleepTimer() = mediaPlayerHandler.sleepStop()

    // Cached in-memory so isUserLoggedIn() below can return synchronously without touching disk
    // on the calling thread. It's called directly from Composable bodies in NowPlayingScreen, so
    // the previous `runBlocking { ... }` re-read DataStore's cookie on every recomposition.
    private val _cookieFlag: StateFlow<String> =
        dataStoreManager.cookie.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    fun loadSharedMediaItem(videoId: String) {
        viewModelScope.launch {
            val localSong = songRepository.getSongById(videoId).firstOrNull()
            if (localSong != null) {
                val track = localSong.toTrack()
                mediaPlayerHandler.setQueueData(
                    QueueData.Data(
                        listTracks = arrayListOf(track),
                        firstPlayedTrack = track,
                        playlistId = "RDAMVM$videoId",
                        playlistName = getString(Res.string.shared),
                        playlistType = PlaylistType.RADIO,
                        continuation = null,
                    ),
                )
                loadMediaItemFromTrack(track, SONG_CLICK)
            } else {
                streamRepository.getFullMetadata(videoId).collectLatest { response ->
                    val data = response.data
                    when (response) {
                        is Resource.Success if (data != null) -> {
                            mediaPlayerHandler.setQueueData(
                                QueueData.Data(
                                    listTracks = arrayListOf(data),
                                    firstPlayedTrack = data,
                                    playlistId = "RDAMVM$videoId",
                                    playlistName = getString(Res.string.shared),
                                    playlistType = PlaylistType.RADIO,
                                    continuation = null,
                                ),
                            )
                            loadMediaItemFromTrack(data, SONG_CLICK)
                        }
                        else -> makeToast("${getString(Res.string.error)}: ${response.message}")
                    }
                }
            }
        }
    }

    fun loadMediaItemFromTrack(track: Track, type: String, index: Int? = null) {
        viewModelScope.launch {
            mediaPlayerHandler.clearMediaItems()
            songRepository.insertSong(track.toSongEntity()).lastOrNull()
            track.durationSeconds?.let { songRepository.updateDurationSeconds(it, track.videoId) }
            withContext(Dispatchers.Main) {
                mediaPlayerHandler.addMediaItem(track.toGenericMediaItem(), playWhenReady = type != RECOVER_TRACK_QUEUE)
            }
            when (type) {
                SONG_CLICK, VIDEO_CLICK, SHARE -> mediaPlayerHandler.getRelated(track.videoId)
                PLAYLIST_CLICK -> mediaPlayerHandler.loadPlaylistOrAlbum(index = index ?: 0)
                ALBUM_CLICK -> mediaPlayerHandler.loadPlaylistOrAlbum(index = index ?: 0)
            }
        }
    }

    fun getTranslucentBottomBar() = dataStoreManager.translucentBottomBar
    fun getEnableLiquidGlass() = dataStoreManager.enableLiquidGlass
    fun isUserLoggedIn(): Boolean = _cookieFlag.value.isNotEmpty()

    /**
     * Whether the foreground music service is safe to stop when the hosting Activity is
     * destroyed (e.g. user pressed back / finished the Activity). We must NOT stop it while a
     * track is actively playing, since that's a foreground media session the user expects to
     * keep going in the background/notification. Only stop it if nothing is currently playing.
     */
    fun shouldStopMusicService(): Boolean = !_controllerState.value.isPlaying
}
