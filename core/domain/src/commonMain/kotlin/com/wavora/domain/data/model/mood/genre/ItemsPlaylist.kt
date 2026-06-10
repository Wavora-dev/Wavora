package com.wavora.domain.model.model.mood.genre

data class ItemsPlaylist(
    val contents: List<Content>,
    val header: String,
    val type: String,
)