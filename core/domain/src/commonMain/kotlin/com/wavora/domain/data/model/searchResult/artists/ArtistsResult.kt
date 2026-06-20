package com.wavora.domain.model.model.searchResult.artists

import com.wavora.domain.model.model.searchResult.songs.Thumbnail
import com.wavora.domain.model.type.ArtistType
import com.wavora.domain.model.type.SearchResultType

data class ArtistsResult(
    val artist: String,
    val browseId: String,
    val category: String,
    val radioId: String,
    val resultType: String,
    val shuffleId: String,
    val thumbnails: List<Thumbnail>,
) : ArtistType,
    SearchResultType {
    override fun objectType(): SearchResultType.Type = SearchResultType.Type.ARTIST
}