package com.wavora.domain.model.model.browse.artist

import com.wavora.domain.model.model.searchResult.songs.Thumbnail
import com.wavora.domain.model.type.HomeContentType

data class ResultSingle(
    val browseId: String,
    val thumbnails: List<Thumbnail>,
    val title: String,
    val year: String,
) : HomeContentType