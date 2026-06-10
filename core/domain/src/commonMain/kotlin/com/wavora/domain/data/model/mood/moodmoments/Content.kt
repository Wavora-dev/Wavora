package com.wavora.domain.model.model.mood.moodmoments

import com.wavora.domain.model.model.searchResult.songs.Thumbnail
import com.wavora.domain.model.type.HomeContentType

data class Content(
    val playlistBrowseId: String,
    val subtitle: String,
    val thumbnails: List<Thumbnail>?,
    val title: String,
) : HomeContentType