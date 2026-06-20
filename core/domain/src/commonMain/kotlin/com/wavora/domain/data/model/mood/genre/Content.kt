package com.wavora.domain.model.model.mood.genre

import com.wavora.domain.model.model.searchResult.songs.Thumbnail
import com.wavora.domain.model.type.HomeContentType

data class Content(
    val playlistBrowseId: String,
    val thumbnail: List<Thumbnail>?,
    val title: Title,
) : HomeContentType