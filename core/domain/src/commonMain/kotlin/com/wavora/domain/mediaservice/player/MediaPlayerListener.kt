package com.wavora.domain.mediaservice.player

import com.wavora.domain.model.player.GenericMediaItem
import com.wavora.domain.model.player.GenericTracks
import com.wavora.domain.model.player.PlayerError

/**
 * Listener interface for media player events
 */
interface MediaPlayerListener {
    fun onPlaybackStateChanged(playbackState: Int) {}

    fun onIsPlayingChanged(isPlaying: Boolean) {}

    fun onMediaItemTransition(
        mediaItem: GenericMediaItem?,
        reason: Int,
    ) {}

    fun onTimelineChanged(
        list: List<GenericMediaItem>, reason: String
    ) {}

    fun onTracksChanged(tracks: GenericTracks) {}

    fun onPlayerError(error: PlayerError) {}

    fun shouldOpenOrCloseEqualizerIntent(shouldOpen: Boolean) {}

    fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean, list: List<GenericMediaItem>) {}

    fun onRepeatModeChanged(repeatMode: Int) {}

    fun onIsLoadingChanged(isLoading: Boolean) {}

    fun onCrossfadeStateChanged(isCrossfading: Boolean) {}

    fun onVolumeChanged(volume: Float) {}
}