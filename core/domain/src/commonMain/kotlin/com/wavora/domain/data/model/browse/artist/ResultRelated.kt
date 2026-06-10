package com.wavora.domain.model.model.browse.artist

import com.wavora.domain.model.model.searchResult.songs.Thumbnail

data class ResultRelated(
    val browseId: String,
    val subscribers: String,
    val thumbnails: List<Thumbnail>,
    val title: String,
)