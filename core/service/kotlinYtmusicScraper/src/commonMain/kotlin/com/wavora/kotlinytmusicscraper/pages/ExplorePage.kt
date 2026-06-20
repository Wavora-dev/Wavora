package com.wavora.scraper.pages

import com.wavora.scraper.models.AlbumItem
import com.wavora.scraper.models.VideoItem

data class ExplorePage(
    val released: List<AlbumItem>,
    val musicVideo: List<VideoItem>,
)