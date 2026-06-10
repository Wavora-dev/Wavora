package com.wavora.domain.model.model.home.chart

data class Chart(
    val artists: Artists,
    val countries: Countries?,
    val listChartItem: List<ChartItemPlaylist>,
)