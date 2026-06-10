package com.wavora.domain.model.model.home

import com.wavora.domain.model.model.searchResult.songs.Album
import com.wavora.domain.model.model.searchResult.songs.Artist
import com.wavora.domain.model.model.searchResult.songs.Thumbnail
import com.wavora.domain.model.type.HomeContentType

data class Content(
    val album: Album?,
    val artists: List<Artist>?,
    val description: String?,
    val isExplicit: Boolean?,
    val playlistId: String?,
    val browseId: String?,
    val thumbnails: List<Thumbnail>,
    val title: String,
    val videoId: String?,
    val views: String?,
    val durationSeconds: Int? = null,
    val radio: String? = null,
) : HomeContentType