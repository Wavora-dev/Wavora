package com.wavora.app.viewModel

import androidx.compose.ui.graphics.ImageBitmap
import com.wavora.domain.model.entities.SongInfoEntity
import com.wavora.domain.model.model.metadata.Lyrics

/**
 * Shared data classes and sealed classes used across
 * PlayerViewModel, NowPlayingViewModel, AppViewModel, and SharedViewModel.
 *
 * Extracted from SharedViewModel so each sub-ViewModel can reference them
 * without circular imports.
 */

sealed class UIEvent {
    data object PlayPause : UIEvent()
    data object Backward : UIEvent()
    data object Forward : UIEvent()
    data object Next : UIEvent()
    data object Previous : UIEvent()

    /**
     * Always advances to the previous track — bypasses the 3-second
     * "seek to start of current track" rule used by [Previous]. Used by the
     * NowPlaying artwork pager swipe.
     */
    data object SkipToPrevious : UIEvent()
    data object Stop : UIEvent()
    data object Shuffle : UIEvent()
    data object Repeat : UIEvent()
    data class UpdateProgress(val newProgress: Float) : UIEvent()
    data class UpdateVolume(val newVolume: Float) : UIEvent()
    data object ToggleLike : UIEvent()
}

enum class LyricsProvider {
    SIMPMUSIC,
    YOUTUBE,
    SPOTIFY,
    LRCLIB,
    BETTER_LYRICS,
    AI,
    OFFLINE,
}

data class NowPlayingScreenData(
    val playlistName: String,
    val nowPlayingTitle: String,
    val artistName: String,
    val isVideo: Boolean,
    val isExplicit: Boolean = false,
    val thumbnailURL: String?,
    val canvasData: CanvasData? = null,
    val lyricsData: LyricsData? = null,
    val songInfoData: SongInfoEntity? = null,
    val bitmap: ImageBitmap? = null,
) {
    data class CanvasData(
        val isVideo: Boolean,
        val url: String,
    )

    data class LyricsData(
        val lyrics: Lyrics,
        val translatedLyrics: Pair<Lyrics, LyricsProvider>? = null,
        val lyricsProvider: LyricsProvider,
    )

    companion object {
        fun initial(): NowPlayingScreenData =
            NowPlayingScreenData(
                nowPlayingTitle = "",
                artistName = "",
                isVideo = false,
                thumbnailURL = null,
                canvasData = null,
                lyricsData = null,
                songInfoData = null,
                playlistName = "",
            )
    }
}

data class VoteData(
    val id: String,
    val vote: Int,
    val state: VoteState,
)

sealed class VoteState {
    data object Idle : VoteState()
    data object Loading : VoteState()
    data class Success(val upvote: Boolean) : VoteState()
    data class Error(val message: String) : VoteState()
}
