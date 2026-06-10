package com.wavora.domain.model.model.searchResult.albums

import com.wavora.domain.model.model.searchResult.songs.Artist
import com.wavora.domain.model.model.searchResult.songs.Thumbnail
import com.wavora.domain.model.type.PlaylistType
import com.wavora.domain.model.type.SearchResultType

data class AlbumsResult(
    val artists: List<Artist>,
    val browseId: String,
    val category: String,
    val duration: Any,
    val isExplicit: Boolean,
    val resultType: String,
    val thumbnails: List<Thumbnail>,
    val title: String,
    val type: String,
    val year: String,
) : PlaylistType,
    SearchResultType {
    override fun objectType(): SearchResultType.Type = SearchResultType.Type.ALBUM

    override fun playlistType(): PlaylistType.Type = PlaylistType.Type.ALBUM
}