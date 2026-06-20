package com.wavora.domain.model.model.browse.album

import com.wavora.domain.model.model.browse.artist.ResultAlbum
import com.wavora.domain.model.model.searchResult.songs.Artist
import com.wavora.domain.model.model.searchResult.songs.Thumbnail

data class AlbumBrowse(
    val artists: List<Artist>,
    val audioPlaylistId: String,
    val description: String?,
    val duration: String?,
    val durationSeconds: Int,
    val thumbnails: List<Thumbnail>?,
    val title: String,
    val trackCount: Int,
    val tracks: List<Track>,
    val type: String,
    val year: String?,
    val otherVersion: List<ResultAlbum> = emptyList(),
)