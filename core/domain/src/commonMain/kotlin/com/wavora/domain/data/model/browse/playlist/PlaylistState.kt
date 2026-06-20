package com.wavora.domain.model.model.browse.playlist

import com.wavora.domain.model.model.streams.YouTubeWatchEndpoint

data class PlaylistState(
    val id: String,
    val title: String,
    val isRadio: Boolean,
    val author: Author,
    val thumbnail: String? = null,
    val description: String? = null,
    val year: String,
    val trackCount: Int = 0,
    val radioEndpoint: YouTubeWatchEndpoint? = null,
    val shuffleEndpoint: YouTubeWatchEndpoint? = null,
)