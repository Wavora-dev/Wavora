package com.wavora.app.viewModel

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.viewModelScope
import com.wavora.domain.manager.DataStoreManager
import com.wavora.domain.manager.DataStoreManager.Values.TRUE
import com.wavora.domain.mediaservice.handler.NowPlayingTrackState
import com.wavora.domain.model.entities.AlbumEntity
import com.wavora.domain.model.entities.DownloadState
import com.wavora.domain.model.entities.LocalPlaylistEntity
import com.wavora.domain.model.entities.PlaylistEntity
import com.wavora.domain.repository.CacheRepository
import com.wavora.common.Config.DOWNLOAD_CACHE
import com.wavora.domain.model.entities.NewFormatEntity
import com.wavora.domain.model.model.canvas.CanvasResult
import com.wavora.domain.model.model.download.DownloadProgress
import com.wavora.domain.model.model.intent.GenericIntent
import com.wavora.domain.model.model.streams.TimeLine
import com.wavora.domain.model.model.update.UpdateData
import com.wavora.domain.repository.AlbumRepository
import com.wavora.domain.repository.LocalPlaylistRepository
import com.wavora.domain.repository.PlaylistRepository
import com.wavora.domain.repository.SongRepository
import com.wavora.app.viewModel.base.BaseViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.component.inject
import kotlin.reflect.KClass
import wavora.composeapp.generated.resources.Res
import wavora.composeapp.generated.resources.updated

/**
 * SharedViewModel — thin coordinator.
 *
 * Playback state lives in [PlayerViewModel].
 * NowPlaying / lyrics / canvas live in [NowPlayingViewModel].
 * App-level concerns (update, intent, recreate) live in [AppViewModel].
 *
 * SharedViewModel wires them together and keeps the backwards-compatible
 * public API so every existing screen continues to compile unchanged.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SharedViewModel(
    private val dataStoreManager: DataStoreManager,
    private val songRepository: SongRepository,
    private val albumRepository: AlbumRepository,
    private val localPlaylistRepository: LocalPlaylistRepository,
    private val playlistRepository: PlaylistRepository,
    private val cacheRepository: CacheRepository,
) : BaseViewModel() {

    // ── Sub-ViewModels injected via Koin ──────────────────────────────────
    val player: PlayerViewModel by inject()
    val nowPlaying: NowPlayingViewModel by inject()
    val app: AppViewModel by inject()

    // ── Backwards-compatible delegates — PlayerViewModel ──────────────────
    val nowPlayingState: StateFlow<NowPlayingTrackState?> get() = player.nowPlayingState
    val controllerState get() = player.controllerState
    val timeline: StateFlow<TimeLine> get() = player.timeline
    val sleepTimerState get() = player.sleepTimerState
    val getVideo get() = player.getVideo
    var isServiceRunning: Boolean get() = player.isServiceRunning; set(v) { player.isServiceRunning = v }
    var isFullScreen: Boolean get() = player.isFullScreen; set(v) { player.isFullScreen = v }
    val playlistId get() = player.playlistId
    fun getQueueDataState() = player.getQueueDataState()
    fun onUIEvent(uiEvent: UIEvent) = player.onUIEvent(uiEvent)
    fun addListToQueue(listTrack: ArrayList<com.wavora.domain.model.model.browse.album.Track>) = player.addListToQueue(listTrack)
    fun stopPlayer() { nowPlaying.clearNowPlayingScreen(); player.stopPlayer() }
    fun setSleepTimer(minutes: Int) = player.setSleepTimer(minutes)
    fun stopSleepTimer() = player.stopSleepTimer()
    fun loadSharedMediaItem(videoId: String) = player.loadSharedMediaItem(videoId)
    fun loadMediaItemFromTrack(track: com.wavora.domain.model.model.browse.album.Track, type: String, index: Int? = null) = player.loadMediaItemFromTrack(track, type, index)
    fun getTranslucentBottomBar() = player.getTranslucentBottomBar()
    fun getEnableLiquidGlass() = player.getEnableLiquidGlass()
    fun isUserLoggedIn() = player.isUserLoggedIn()
    fun shouldStopMusicService() = player.shouldStopMusicService()

    // blurBg: DataStore preference for blurring the NowPlaying background.
    // Exposed directly on SharedViewModel (not delegated to a sub-VM) because
    // it only reads from DataStoreManager and needs no extra ViewModel logic.
    val blurBg: StateFlow<Boolean> =
        dataStoreManager.blurPlayerBackground
            .map { it == TRUE }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(500L),
                initialValue = false,
            )

    // Alias kept for screens that use `loadMediaItem` instead of `loadMediaItemFromTrack`.
    fun loadMediaItem(
        track: com.wavora.domain.model.model.browse.album.Track,
        type: String,
        index: Int? = null,
    ) = player.loadMediaItemFromTrack(track, type, index)

    // ── Backwards-compatible delegates — NowPlayingViewModel ──────────────
    val nowPlayingScreenData: StateFlow<NowPlayingScreenData> get() = nowPlaying.nowPlayingScreenData
    val likeStatus: StateFlow<Boolean> get() = nowPlaying.likeStatus
    val canvas: StateFlow<CanvasResult?> get() = nowPlaying.canvas
    val format: SharedFlow<NewFormatEntity?> get() = nowPlaying.format
    val shareSavedLyrics: StateFlow<Boolean> get() = nowPlaying.shareSavedLyrics
    val translatedVoteState: StateFlow<VoteData?> get() = nowPlaying.translatedVoteState
    val lyricsVoteState: StateFlow<VoteData?> get() = nowPlaying.lyricsVoteState
    val downloadFileProgress: StateFlow<DownloadProgress> get() = nowPlaying.downloadFileProgress
    fun getSongInfo(mediaId: String?) = nowPlaying.getSongInfo(mediaId)
    fun addToYouTubeLiked() = nowPlaying.addToYouTubeLiked()
    fun setBitmap(bitmap: ImageBitmap?) = nowPlaying.setBitmap(bitmap)
    fun blurFullscreenLyrics() = nowPlaying.blurFullscreenLyrics()
    fun downloadFile(bitmap: ImageBitmap) = nowPlaying.downloadFile(bitmap)
    fun downloadFileDone() = nowPlaying.downloadFileDone()
    fun onDoneReview(isDismissOnly: Boolean = true) = app.onDoneReview(isDismissOnly)
    fun onDoneRequestingShareLyrics(contributor: Pair<String, String>? = null) = app.onDoneRequestingShareLyrics(contributor)
    fun voteLyrics(upvote: Boolean) = nowPlaying.voteLyrics(upvote)
    fun voteTranslatedLyrics(upvote: Boolean) = nowPlaying.voteTranslatedLyrics(upvote)

    // ── Backwards-compatible delegates — AppViewModel ─────────────────────
    var isFirstLiked: Boolean get() = app.isFirstLiked; set(v) { app.isFirstLiked = v }
    var isFirstMiniplayer: Boolean get() = app.isFirstMiniplayer; set(v) { app.isFirstMiniplayer = v }
    var isFirstSuggestions: Boolean get() = app.isFirstSuggestions; set(v) { app.isFirstSuggestions = v }
    var showedUpdateDialog: Boolean get() = app.showedUpdateDialog; set(v) { app.showedUpdateDialog = v }
    val isCheckingUpdate: StateFlow<Boolean> get() = app.isCheckingUpdate
    val updateResponse: StateFlow<UpdateData?> get() = app.updateResponse
    val intent: StateFlow<GenericIntent?> get() = app.intent
    val showNotificationPermissionDialog: StateFlow<Boolean> get() = app.showNotificationPermissionDialog
    val reloadDestination: StateFlow<KClass<*>?> get() = app.reloadDestination
    val recreateActivity: StateFlow<Boolean> get() = app.recreateActivity
    val openAppTime: StateFlow<Int> get() = app.openAppTime
    val location: StateFlow<String> get() = app.location
    fun getLocation() = app.getLocation()
    fun setIntent(intent: GenericIntent?) = app.setIntent(intent)
    fun showNotificationPermissionDialog() = app.showNotificationPermissionDialog()
    fun dismissNotificationPermissionDialog(doNotShowAgain: Boolean) = app.dismissNotificationPermissionDialog(doNotShowAgain)
    fun reloadDestination(destination: KClass<*>) = app.reloadDestination(destination)
    fun reloadDestinationDone() = app.reloadDestinationDone()
    fun activityRecreate() = app.activityRecreate()
    fun activityRecreateDone() = app.activityRecreateDone()
    suspend fun getString(key: String): String? = app.getString(key)
    suspend fun putString(key: String, value: String) = app.putString(key, value)
    fun checkForUpdateIfEnabled() = app.checkForUpdateIfEnabled()
    fun checkForUpdate(isManual: Boolean = false) = app.checkForUpdate(isManual)

    // ── Init: wire song-change events from player → nowPlaying ────────────
    init {
        viewModelScope.launch {
            // Forward song changes to NowPlayingViewModel
            mediaPlayerHandler.nowPlayingState
                .distinctUntilChangedBy { it.songEntity?.videoId }
                .collectLatest { state ->
                    nowPlaying.onSongChanged(state, player.timeline.value)
                }
        }
        viewModelScope.launch {
            // Forward timeline-ready events to NowPlayingViewModel
            player.nowPlayingState
                .filterNotNull()
                .flatMapLatest { nowPlayingState ->
                    player.timeline.map { timeLine -> Pair(timeLine, nowPlayingState) }
                }.collectLatest { (timeLine, nowPlayingState) ->
                    if (timeLine.total > 0 && nowPlayingState.songEntity != null) {
                        nowPlaying.onTimelineReady(nowPlayingState, timeLine)
                    }
                }
        }
        viewModelScope.launch {
            // Forward queue name changes to NowPlayingViewModel
            mediaPlayerHandler.queueData.collectLatest {
                nowPlaying.updatePlaylistName(it?.data?.playlistName ?: "")
            }
        }
        // One-shot startup cleanup
        checkAllDownloadingSongs()
        checkAllDownloadingPlaylists()
        checkAllDownloadingLocalPlaylists()
    }

    // ── Download state restore (called from MainActivity on Android) ──────
    fun checkIsRestoring() {
        viewModelScope.launch {
            val downloadedCacheKeys = cacheRepository.getAllCacheKeys(DOWNLOAD_CACHE)
            songRepository.getDownloadedSongs().first()?.forEach { song ->
                if (!downloadedCacheKeys.contains(song.videoId)) {
                    songRepository.updateDownloadState(song.videoId, DownloadState.STATE_NOT_DOWNLOADED)
                }
            }
            playlistRepository.getAllDownloadedPlaylist().first().forEach { data ->
                when (data) {
                    is AlbumEntity -> {
                        val tracks = data.tracks ?: emptyList()
                        if (tracks.isEmpty() || !downloadedCacheKeys.containsAll(tracks)) {
                            albumRepository.updateAlbumDownloadState(data.browseId, DownloadState.STATE_NOT_DOWNLOADED)
                        }
                    }
                    is PlaylistEntity -> {
                        val tracks = data.tracks ?: emptyList()
                        if (tracks.isEmpty() || !downloadedCacheKeys.containsAll(tracks)) {
                            playlistRepository.updatePlaylistDownloadState(data.id, DownloadState.STATE_NOT_DOWNLOADED)
                        }
                    }
                    is LocalPlaylistEntity -> {
                        val tracks = data.tracks ?: emptyList()
                        if (tracks.isEmpty() || !downloadedCacheKeys.containsAll(tracks)) {
                            localPlaylistRepository.updateLocalPlaylistDownloadState(
                                DownloadState.STATE_NOT_DOWNLOADED,
                                data.id,
                            )
                        }
                    }
                }
            }
        }
    }

    // ── Download reset (startup one-shot) ─────────────────────────────────
    private fun checkAllDownloadingLocalPlaylists() {
        viewModelScope.launch {
            localPlaylistRepository.getAllDownloadingLocalPlaylists().first().forEach { playlist ->
                localPlaylistRepository.updateDownloadState(playlist.id, 0, successMessage = getString(Res.string.updated)).lastOrNull()
            }
        }
    }

    private fun checkAllDownloadingPlaylists() {
        viewModelScope.launch {
            playlistRepository.getAllDownloadingPlaylist().first().forEach { data ->
                when (data) {
                    is AlbumEntity -> albumRepository.updateAlbumDownloadState(data.browseId, 0)
                    is PlaylistEntity -> playlistRepository.updatePlaylistDownloadState(data.id, 0)
                    else -> {}
                }
            }
        }
    }

    private fun checkAllDownloadingSongs() {
        viewModelScope.launch {
            songRepository.getDownloadingSongs().first()?.forEach {
                songRepository.updateDownloadState(it.videoId, DownloadState.STATE_NOT_DOWNLOADED)
            }
            songRepository.getPreparingSongs().first().forEach {
                songRepository.updateDownloadState(it.videoId, DownloadState.STATE_NOT_DOWNLOADED)
            }
        }
    }
}
