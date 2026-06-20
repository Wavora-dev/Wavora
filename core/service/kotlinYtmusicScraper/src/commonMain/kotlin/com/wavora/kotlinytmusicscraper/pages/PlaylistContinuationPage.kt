package com.wavora.scraper.pages

import com.wavora.scraper.models.SongItem

data class PlaylistContinuationPage(
    val songs: List<SongItem>,
    val continuation: String?,
)