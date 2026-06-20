package com.wavora.domain.model.model.home.chart

import com.wavora.domain.model.model.browse.artist.ResultPlaylist

data class ChartItemPlaylist(
    val title: String,
    val playlists: List<ResultPlaylist>,
)