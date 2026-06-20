package com.wavora.app.viewModel

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.viewModelScope
import com.wavora.domain.extension.decodeHtmlEntities
import com.wavora.domain.extension.isVideo
import com.wavora.domain.extension.isSong
import com.wavora.domain.manager.DataStoreManager
import com.wavora.domain.manager.DataStoreManager.Values.FALSE
import com.wavora.domain.manager.DataStoreManager.Values.TRUE
import com.wavora.domain.mediaservice.handler.NowPlayingTrackState
import com.wavora.domain.model.entities.LyricsEntity
import com.wavora.domain.model.entities.SongEntity
import com.wavora.domain.model.entities.TranslatedLyricsEntity
import com.wavora.domain.model.model.browse.album.Track
import com.wavora.domain.model.model.canvas.CanvasResult
import com.wavora.domain.model.model.metadata.Lyrics
import com.wavora.domain.model.model.streams.TimeLine
import com.wavora.domain.repository.LyricsCanvasRepository
import com.wavora.domain.repository.SongRepository
import com.wavora.domain.repository.StreamRepository
import com.wavora.domain.model.entities.NewFormatEntity
import com.wavora.domain.utils.Resource
import com.wavora.domain.utils.toListName
import com.wavora.domain.utils.toLyrics
import com.wavora.domain.utils.toLyricsEntity
import com.wavora.domain.utils.toSongEntity
import com.wavora.domain.utils.toSyncedLyrics
import com.wavora.domain.utils.toTrack
import com.wavora.app.expect.getDownloadFolderPath
import com.wavora.app.expect.ui.toByteArray
import com.wavora.app.viewModel.base.BaseViewModel
import com.wavora.logger.LogLevel
import com.wavora.logger.Logger
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import wavora.composeapp.generated.resources.Res
import wavora.composeapp.generated.resources.added_to_youtube_liked
import wavora.composeapp.generated.resources.error
import wavora.composeapp.generated.resources.removed_from_youtube_liked
import wavora.composeapp.generated.resources.vote_submitted
import java.io.FileOutputStream
import kotlin.math.abs

class NowPlayingViewModel(
    private val dataStoreManager: DataStoreManager,
    private val songRepository: SongRepository,
    private val lyricsCanvasRepository: LyricsCanvasRepository,
    private val streamRepository: StreamRepository,
) : BaseViewModel() {

    // ── State exposed to UI ───────────────────────────────────────────────
    private var _nowPlayingScreenData = MutableStateFlow(NowPlayingScreenData.initial())
    val nowPlayingScreenData: StateFlow<NowPlayingScreenData> = _nowPlayingScreenData

    private var _likeStatus = MutableStateFlow(false)
    val likeStatus: StateFlow<Boolean> = _likeStatus

    private var _canvas = MutableStateFlow<CanvasResult?>(null)
    val canvas: StateFlow<CanvasResult?> = _canvas

    private val _shareSavedLyrics = MutableStateFlow(true)
    val shareSavedLyrics: StateFlow<Boolean> get() = _shareSavedLyrics

    private val _translatedVoteState = MutableStateFlow<VoteData?>(null)
    val translatedVoteState: StateFlow<VoteData?> = _translatedVoteState.asStateFlow()

    private val _lyricsVoteState = MutableStateFlow<VoteData?>(null)
    val lyricsVoteState: StateFlow<VoteData?> = _lyricsVoteState.asStateFlow()

    private var _downloadFileProgress = MutableStateFlow<com.wavora.domain.model.model.download.DownloadProgress>(
        com.wavora.domain.model.model.download.DownloadProgress.INIT,
    )
    val downloadFileProgress: StateFlow<com.wavora.domain.model.model.download.DownloadProgress> get() = _downloadFileProgress

    // ── Jobs ──────────────────────────────────────────────────────────────
    private var canvasJob: Job? = null
    private var likeStatusJob: Job? = null
    private var songInfoJob: Job? = null

    init {
        viewModelScope.launch {
            dataStoreManager.helpBuildLyricsDatabase.collectLatest {
                _shareSavedLyrics.value = it == TRUE
            }
        }
    }

    // ── Called by SharedViewModel when song changes ───────────────────────
    fun onSongChanged(state: NowPlayingTrackState, timeline: TimeLine) {
        canvasJob?.cancel()
        _canvas.value = null

        state.songEntity?.let { song ->
            _nowPlayingScreenData.value = NowPlayingScreenData(
                nowPlayingTitle = song.title,
                artistName = song.artistName?.joinToString(", ") ?: "",
                isVideo = false,
                thumbnailURL = null,
                canvasData = null,
                lyricsData = null,
                songInfoData = null,
                playlistName = mediaPlayerHandler.queueData.value?.data?.playlistName ?: "",
            )
        }
        state.mediaItem.let { now ->
            getLikeStatus(now.mediaId)
            getSongInfo(now.mediaId)
            getFormat(now.mediaId, timeline)
            _nowPlayingScreenData.update {
                it.copy(thumbnailURL = now.metadata.artworkUri, isVideo = now.isVideo())
            }
        }
        state.songEntity?.let { song ->
            _nowPlayingScreenData.update { it.copy(isExplicit = song.isExplicit) }
        }
    }

    fun onTimelineReady(nowPlaying: NowPlayingTrackState, timeline: TimeLine) {
        val mediaItem = nowPlaying.mediaItem
        val song = nowPlaying.songEntity ?: return
        if (mediaItem.isSong() && _nowPlayingScreenData.value.canvasData == null) {
            getCanvas(mediaItem.mediaId, (timeline.total / 1000).toInt())
        }
        if (_nowPlayingScreenData.value.lyricsData == null) {
            getLyricsFromFormat(mediaItem.isVideo(), song, (timeline.total / 1000).toInt())
        }
    }

    fun updatePlaylistName(name: String) {
        _nowPlayingScreenData.update { it.copy(playlistName = name) }
    }

    fun clearNowPlayingScreen() {
        _nowPlayingScreenData.value = NowPlayingScreenData.initial()
    }

    fun setBitmap(bitmap: ImageBitmap?) {
        _nowPlayingScreenData.update { it.copy(bitmap = bitmap) }
    }

    fun blurFullscreenLyrics(): Boolean = runBlocking { dataStoreManager.blurFullscreenLyrics.first() == TRUE }

    // ── Like status ───────────────────────────────────────────────────────
    private fun getLikeStatus(videoId: String?) {
        likeStatusJob?.cancel()
        likeStatusJob = viewModelScope.launch {
            if (videoId != null) {
                _likeStatus.value = false
                songRepository.getLikeStatus(videoId).collectLatest { _likeStatus.value = it }
            }
        }
    }

    fun addToYouTubeLiked() {
        viewModelScope.launch {
            val videoId = mediaPlayerHandler.nowPlaying.first()?.mediaId
            if (videoId != null) {
                val like = likeStatus.value
                if (!like) {
                    songRepository.addToYouTubeLiked(mediaPlayerHandler.nowPlaying.first()?.mediaId)
                        .collect { response ->
                            if (response == 200) {
                                makeToast(getString(Res.string.added_to_youtube_liked))
                                getLikeStatus(videoId)
                            } else makeToast(getString(Res.string.error))
                        }
                } else {
                    songRepository.removeFromYouTubeLiked(mediaPlayerHandler.nowPlaying.first()?.mediaId)
                        .collect {
                            if (it == 200) {
                                makeToast(getString(Res.string.removed_from_youtube_liked))
                                getLikeStatus(videoId)
                            } else makeToast(getString(Res.string.error))
                        }
                }
            }
        }
    }

    // ── Canvas ────────────────────────────────────────────────────────────
    private fun getCanvas(videoId: String, duration: Int) {
        viewModelScope.launch {
            if (dataStoreManager.spotifyCanvas.first() == TRUE) {
                lyricsCanvasRepository.getCanvas(dataStoreManager, videoId, duration).cancellable()
                    .collect { response ->
                        val data = response.data
                        when (response) {
                            is Resource.Success if (data != null && nowPlayingScreenData.value.canvasData == null) -> {
                                _canvas.value = data
                                _nowPlayingScreenData.update {
                                    it.copy(canvasData = NowPlayingScreenData.CanvasData(isVideo = data.isVideo, url = data.canvasUrl))
                                }
                                if (data.isVideo) lyricsCanvasRepository.updateCanvasUrl(videoId, data.canvasUrl)
                                data.canvasThumbUrl?.let { lyricsCanvasRepository.updateCanvasThumbUrl(videoId, it) }
                            }
                            else -> {
                                Logger.w(tag, "Get canvas error: ${response.message}")
                                _nowPlayingScreenData.value.let { screen ->
                                    if (screen.canvasData == null) {
                                        val cachedUrl = songRepository.getSongById(videoId).firstOrNull()?.canvasUrl
                                        cachedUrl?.let { url ->
                                            _nowPlayingScreenData.update {
                                                it.copy(canvasData = NowPlayingScreenData.CanvasData(isVideo = url.contains(".mp4"), url = url))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
            }
        }
    }

    // ── Format (audio stream quality info shown in NowPlaying) ────────────
    private val _format = MutableStateFlow<NewFormatEntity?>(null)
    val format: SharedFlow<NewFormatEntity?> = _format.asSharedFlow()

    private var getFormatFlowJob: Job? = null

    private fun getFormat(mediaId: String?, timeline: TimeLine) {
        if (mediaId == _format.value?.videoId || mediaId.isNullOrEmpty()) return
        _format.value = null
        getFormatFlowJob?.cancel()
        getFormatFlowJob = viewModelScope.launch {
            streamRepository.getFormatFlow(mediaId).cancellable().collectLatest { f ->
                _format.value = f
                // Once format is loaded, trigger lyrics if not already present
                if (nowPlayingScreenData.value.lyricsData == null) {
                    val song = mediaPlayerHandler.nowPlayingState.first().songEntity ?: return@collectLatest
                    val isVideo = mediaPlayerHandler.nowPlayingState.first().mediaItem.isVideo()
                    val dur = (timeline.total / 1000).toInt().takeIf { it > 0 }
                        ?: (song.durationSeconds ?: 0)
                    getLyricsFromFormat(isVideo, song, dur)
                }
            }
        }
    }

    // ── Song info ─────────────────────────────────────────────────────────
    fun getSongInfo(mediaId: String?) {
        songInfoJob?.cancel()
        songInfoJob = viewModelScope.launch {
            if (mediaId != null) {
                songRepository.getSongInfo(mediaId).collect { song ->
                    _nowPlayingScreenData.update { it.copy(songInfoData = song) }
                }
            }
        }
    }

    // ── Lyrics insertion ──────────────────────────────────────────────────
    fun insertLyrics(lyrics: LyricsEntity) {
        viewModelScope.launch { lyricsCanvasRepository.insertLyrics(lyrics) }
    }

    private fun getSavedLyrics(track: Track) {
        viewModelScope.launch {
            lyricsCanvasRepository.getSavedLyrics(track.videoId).cancellable().collectLatest { lyrics ->
                if (lyrics != null) {
                    updateLyrics(track.videoId, track.durationSeconds ?: 0, lyrics.toLyrics(), false, LyricsProvider.OFFLINE)
                    getAITranslationLyrics(track.videoId, lyrics.toLyrics())
                }
            }
        }
    }

    // ── Download image ────────────────────────────────────────────────────
    fun downloadFile(bitmap: ImageBitmap) {
        val fileName = "${nowPlayingScreenData.value.nowPlayingTitle} - ${nowPlayingScreenData.value.artistName}"
            .replace(Regex("""[|\\?*<":>]"""), "").replace(" ", "_")
        val path = "${getDownloadFolderPath()}/$fileName"
        viewModelScope.launch {
            val track = mediaPlayerHandler.nowPlayingState.first().track ?: return@launch
            try {
                val fileOutputStream = FileOutputStream("$path.jpg")
                fileOutputStream.write(bitmap.toByteArray())
                fileOutputStream.close()
            } catch (e: Exception) { throw RuntimeException(e) }
            songRepository.downloadToFile(track = track, videoId = track.videoId, path = path, isVideo = nowPlayingScreenData.value.isVideo)
                .collectLatest { _downloadFileProgress.value = it }
        }
    }

    fun downloadFileDone() { _downloadFileProgress.value = com.wavora.domain.model.model.download.DownloadProgress.INIT }

    // ── Lyrics voting ─────────────────────────────────────────────────────
    private fun resetLyricsVoteState() {
        _lyricsVoteState.value = null
        _translatedVoteState.value = null
    }

    fun voteLyrics(upvote: Boolean) {
        val lyricsData = _nowPlayingScreenData.value.lyricsData
        val simpMusicLyricsId = lyricsData?.lyrics?.simpMusicLyrics?.id ?: return
        if (lyricsData.lyricsProvider != LyricsProvider.SIMPMUSIC || simpMusicLyricsId.isEmpty()) return
        viewModelScope.launch {
            _lyricsVoteState.update { it?.copy(state = VoteState.Loading) }
            lyricsCanvasRepository.voteWavoraLyrics(simpMusicLyricsId, upvote).collectLatest { result ->
                when (result) {
                    is Resource.Error -> _lyricsVoteState.update { it?.copy(state = VoteState.Error(result.message ?: "Unknown error")) }
                    is Resource.Success -> {
                        _lyricsVoteState.update { it?.copy(state = VoteState.Success(upvote), vote = (it.vote) + if (upvote) 1 else -1) }
                        makeToast(getString(Res.string.vote_submitted))
                    }
                }
            }
        }
    }

    fun voteTranslatedLyrics(upvote: Boolean) {
        val translatedLyrics = _nowPlayingScreenData.value.lyricsData?.translatedLyrics
        val simpMusicLyricsId = translatedLyrics?.first?.simpMusicLyrics?.id ?: return
        if (translatedLyrics.second != LyricsProvider.SIMPMUSIC || simpMusicLyricsId.isEmpty()) return
        viewModelScope.launch {
            _translatedVoteState.update { it?.copy(state = VoteState.Loading) }
            lyricsCanvasRepository.voteWavoraTranslatedLyrics(simpMusicLyricsId, upvote).collectLatest { result ->
                when (result) {
                    is Resource.Error -> _translatedVoteState.update { it?.copy(state = VoteState.Error(result.message ?: "Unknown error")) }
                    is Resource.Success -> {
                        _translatedVoteState.update { it?.copy(state = VoteState.Success(upvote), vote = (it.vote) + if (upvote) 1 else -1) }
                        makeToast(getString(Res.string.vote_submitted))
                    }
                }
            }
        }
    }

    // ── Lyrics providers ──────────────────────────────────────────────────
    private fun getLyricsFromFormat(isVideo: Boolean, song: SongEntity, duration: Int) {
        viewModelScope.launch {
            val videoId = song.videoId
            val artistName = song.artistName
            val artist = if (artistName?.firstOrNull() != null && artistName.firstOrNull()?.contains("Various Artists") == false) {
                artistName.firstOrNull()
            } else {
                mediaPlayerHandler.nowPlaying.first()?.metadata?.artist ?: ""
            }
            resetLyricsVoteState()
            when (dataStoreManager.lyricsProvider.first()) {
                DataStoreManager.SIMPMUSIC -> getWavoraLyrics(videoId, song, artist ?: "", duration)
                DataStoreManager.LRCLIB -> getLrclibLyrics(song, artist ?: "", duration)
                DataStoreManager.YOUTUBE -> getYouTubeCaption(videoId, song, artist ?: "", duration)
                DataStoreManager.BETTER_LYRICS -> getBetterLyrics(song, artist ?: "", duration)
            }
        }
    }

    fun setLyricsProvider() {
        viewModelScope.launch {
            val songEntity = mediaPlayerHandler.nowPlayingState.first().songEntity ?: return@launch
            val isVideo = mediaPlayerHandler.nowPlayingState.first().mediaItem.isVideo()
            val duration = mediaPlayerHandler.getPlayerDuration().toInt() / 1000
            getLyricsFromFormat(isVideo, songEntity, duration)
        }
    }

    private suspend fun getWavoraLyrics(videoId: String, song: SongEntity, artist: String, duration: Int) {
        lyricsCanvasRepository.getWavoraLyrics(videoId).collectLatest {
            val data = it.data
            if (it is Resource.Success && data != null) {
                updateLyrics(videoId, duration, data, false, LyricsProvider.SIMPMUSIC)
                insertLyrics(data.toLyricsEntity(videoId))
                getWavoraTranslatedLyrics(videoId, data)
            } else if (dataStoreManager.spotifyLyrics.first() == TRUE) {
                getSpotifyLyrics(song.toTrack().copy(durationSeconds = duration), "${song.title} $artist", duration)
            } else {
                getLrclibLyrics(song, artist, duration)
            }
        }
    }

    private suspend fun getYouTubeCaption(videoId: String, song: SongEntity, artist: String, duration: Int) {
        lyricsCanvasRepository.getYouTubeCaption(dataStoreManager.youtubeSubtitleLanguage.first(), videoId).cancellable()
            .collect { response ->
                val data = response.data
                when (response) {
                    is Resource.Success if (data != null) -> {
                        val lyrics = data.first
                        val translatedLyrics = data.second
                        insertLyrics(lyrics.toLyricsEntity(videoId))
                        updateLyrics(videoId, duration, lyrics, false, LyricsProvider.YOUTUBE)
                        if (translatedLyrics != null) {
                            updateLyrics(videoId, duration, translatedLyrics, true, LyricsProvider.YOUTUBE)
                        } else {
                            getAITranslationLyrics(videoId, lyrics)
                        }
                    }
                    else -> getWavoraLyrics(videoId, song, artist, duration)
                }
            }
    }

    private fun getLrclibLyrics(song: SongEntity, artist: String, duration: Int) {
        viewModelScope.launch {
            lyricsCanvasRepository.getLrclibLyricsData(artist, song.title, duration).collectLatest { res ->
                val data = res.data
                when (res) {
                    is Resource.Success if (data != null) -> {
                        updateLyrics(song.videoId, duration, data, false, LyricsProvider.LRCLIB)
                        insertLyrics(data.toLyricsEntity(song.videoId) ?: return@collectLatest)
                        getAITranslationLyrics(song.videoId, data)
                    }
                    else -> getSavedLyrics(song.toTrack().copy(durationSeconds = duration))
                }
            }
        }
    }

    private fun getBetterLyrics(song: SongEntity, artist: String, duration: Int) {
        viewModelScope.launch {
            lyricsCanvasRepository.getBetterLyrics(artist, song.title, duration).collectLatest { res ->
                val data = res.data
                when (res) {
                    is Resource.Success if (data != null) -> {
                        updateLyrics(song.videoId, duration, data, false, LyricsProvider.BETTER_LYRICS)
                        insertLyrics(data.toLyricsEntity(song.videoId))
                        getAITranslationLyrics(song.videoId, data)
                    }
                    else -> getWavoraLyrics(song.videoId, song, artist, duration)
                }
            }
        }
    }

    private suspend fun getWavoraTranslatedLyrics(videoId: String, lyrics: Lyrics) {
        val lang = dataStoreManager.translationLanguage.first()
        lyricsCanvasRepository.getWavoraTranslatedLyrics(videoId, lang).collectLatest { response ->
            val data = response.data
            when (response) {
                is Resource.Success if (data != null) -> {
                    if (data.syncType == "RICH_SYNCED") {
                        val id = data.simpMusicLyrics?.id
                        if (!id.isNullOrEmpty()) {
                            viewModelScope.launch {
                                lyricsCanvasRepository.voteWavoraTranslatedLyrics(id, false).collectLatest {}
                            }
                        }
                        getAITranslationLyrics(videoId, lyrics)
                    } else {
                        updateLyrics(videoId, 0, data, true, LyricsProvider.SIMPMUSIC)
                    }
                }
                else -> getAITranslationLyrics(videoId, lyrics)
            }
        }
    }

    private suspend fun getAITranslationLyrics(videoId: String, lyrics: Lyrics) {
        if (dataStoreManager.useAITranslation.first() == TRUE &&
            dataStoreManager.aiApiKey.first().isNotEmpty() &&
            dataStoreManager.enableTranslateLyric.first() == FALSE
        ) {
            val lang = dataStoreManager.translationLanguage.first()
            val saved = lyricsCanvasRepository.getSavedTranslatedLyrics(videoId, lang).firstOrNull()
            if (saved != null) {
                updateLyrics(videoId, 0, saved.toLyrics(), true, LyricsProvider.AI)
            } else {
                val lyricsForAi = if (lyrics.syncType == "RICH_SYNCED") lyrics.toSyncedLyrics() else lyrics
                lyricsCanvasRepository.getAITranslationLyrics(lyricsForAi, lang).cancellable().collectLatest {
                    val data = it.data
                    if (it is Resource.Success && data != null) {
                        lyricsCanvasRepository.insertTranslatedLyrics(
                            TranslatedLyricsEntity(videoId = videoId, language = lang, error = false, lines = data.lines, syncType = data.syncType),
                        )
                        updateLyrics(videoId, 0, data, true, LyricsProvider.AI)
                    }
                }
            }
        }
    }

    private fun getSpotifyLyrics(track: Track, query: String, duration: Int? = null) {
        viewModelScope.launch {
            lyricsCanvasRepository.getSpotifyLyrics(dataStoreManager, query, duration).cancellable().collect { response ->
                val data = response.data
                when (response) {
                    is Resource.Success if (data != null) -> {
                        insertLyrics(data.toLyricsEntity(track.videoId))
                        updateLyrics(track.videoId, duration ?: 0, data, false, LyricsProvider.SPOTIFY)
                        getAITranslationLyrics(track.videoId, data)
                    }
                    else -> getLrclibLyrics(track.toSongEntity(), track.artists.toListName().firstOrNull() ?: "", duration ?: 0)
                }
            }
        }
    }

    private suspend fun updateLyrics(
        videoId: String,
        duration: Int,
        inputLyrics: Lyrics?,
        isTranslatedLyrics: Boolean,
        lyricsProvider: LyricsProvider = LyricsProvider.SIMPMUSIC,
    ) {
        if (inputLyrics == null) { _nowPlayingScreenData.update { it.copy(lyricsData = null) }; return }
        val lyrics = inputLyrics.copy(lines = inputLyrics.lines?.map { line -> line.copy(words = decodeHtmlEntities(line.words)) })

        if (isTranslatedLyrics && lyricsProvider != LyricsProvider.AI) {
            val originalLines = _nowPlayingScreenData.value.lyricsData?.lyrics?.lines
            val lyricsLines = lyrics.lines
            if (originalLines != null && lyricsLines != null) {
                var timeSyncErrorCount = 0
                val totalLines = originalLines.size
                if (originalLines.size == lyricsLines.size) {
                    originalLines.forEachIndexed { index, originalLine ->
                        val diff = abs((originalLine.startTimeMs.toLongOrNull() ?: 0L) - (lyricsLines[index].startTimeMs.toLongOrNull() ?: 0L))
                        if (diff > 1000L) timeSyncErrorCount++
                    }
                } else {
                    val usedIndices = mutableSetOf<Int>()
                    originalLines.forEach { originalLine ->
                        val originalTime = originalLine.startTimeMs.toLongOrNull() ?: 0L
                        var bestIndex = -1; var bestDiff = Long.MAX_VALUE
                        lyricsLines.forEachIndexed { index, line ->
                            if (index !in usedIndices) {
                                val diff = abs((line.startTimeMs.toLongOrNull() ?: 0L) - originalTime)
                                if (diff < bestDiff) { bestDiff = diff; bestIndex = index }
                            }
                        }
                        if (bestIndex >= 0) { usedIndices.add(bestIndex); if (bestDiff > 1000L) timeSyncErrorCount++ } else timeSyncErrorCount++
                    }
                }
                val syncErrorRatio = if (totalLines > 0) timeSyncErrorCount.toFloat() / totalLines else 0f
                if (syncErrorRatio > 0.25f || (totalLines > 0 && timeSyncErrorCount > totalLines / 2)) {
                    _nowPlayingScreenData.update { it.copy(lyricsData = it.lyricsData?.copy(translatedLyrics = null)) }
                    viewModelScope.launch {
                        lyricsCanvasRepository.removeTranslatedLyrics(videoId, dataStoreManager.translationLanguage.first())
                        val simpMusicLyricsId = lyrics.simpMusicLyrics?.id
                        if (lyricsProvider == LyricsProvider.SIMPMUSIC && !simpMusicLyricsId.isNullOrEmpty()) {
                            lyricsCanvasRepository.voteWavoraTranslatedLyrics(simpMusicLyricsId, false).collectLatest {}
                        }
                        _nowPlayingScreenData.value.lyricsData?.lyrics?.let { getAITranslationLyrics(videoId, it) }
                    }
                    return
                }
            }
        }

        val shouldSend = dataStoreManager.helpBuildLyricsDatabase.first() == TRUE && lyricsProvider != LyricsProvider.SIMPMUSIC
        if (mediaPlayerHandler.nowPlayingState.first().songEntity?.videoId != videoId) return
        val track = mediaPlayerHandler.nowPlayingState.first().track

        if (isTranslatedLyrics) {
            if (lyricsProvider == LyricsProvider.SIMPMUSIC) {
                _translatedVoteState.value = VoteData(id = lyrics.simpMusicLyrics?.id ?: "", vote = lyrics.simpMusicLyrics?.vote ?: 0, state = VoteState.Idle)
            }
            _nowPlayingScreenData.update { it.copy(lyricsData = it.lyricsData?.copy(translatedLyrics = lyrics to lyricsProvider)) }
            if (shouldSend && track != null) {
                viewModelScope.launch {
                    lyricsCanvasRepository.insertWavoraTranslatedLyrics(dataStoreManager, track, lyrics, dataStoreManager.translationLanguage.first()).collect {}
                }
            }
        } else {
            if (lyricsProvider == LyricsProvider.SIMPMUSIC) {
                _lyricsVoteState.value = VoteData(id = lyrics.simpMusicLyrics?.id ?: "", vote = lyrics.simpMusicLyrics?.vote ?: 0, state = VoteState.Idle)
            }
            _nowPlayingScreenData.update { it.copy(lyricsData = NowPlayingScreenData.LyricsData(lyrics = lyrics, lyricsProvider = lyricsProvider)) }
            viewModelScope.launch {
                lyricsCanvasRepository.insertLyrics(LyricsEntity(videoId = videoId, error = false, lines = lyrics.lines, syncType = lyrics.syncType))
            }
            if (shouldSend && track != null) {
                viewModelScope.launch { lyricsCanvasRepository.insertWavoraLyrics(dataStoreManager, track, duration, lyrics).collect {} }
            }
        }
    }
}
