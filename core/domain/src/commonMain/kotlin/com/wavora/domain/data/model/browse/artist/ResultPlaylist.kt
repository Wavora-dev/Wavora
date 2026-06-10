package com.wavora.domain.model.model.browse.artist

import com.wavora.domain.model.model.searchResult.songs.Thumbnail
import com.wavora.domain.model.type.HomeContentType

data class ResultPlaylist(
    val id: String,
    val author: String,
    val thumbnails: List<Thumbnail>,
    val title: String,
) : HomeContentType