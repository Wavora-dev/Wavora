package com.wavora.domain.model.model.mood.genre

import com.wavora.domain.model.model.searchResult.songs.Artist

data class ItemsSong(
    val title: String,
    val artist: List<Artist>?,
    val videoId: String,
)