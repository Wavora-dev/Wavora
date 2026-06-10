package com.wavora.domain.model.model.metadata

import kotlinx.serialization.Serializable

@Serializable
data class Lyrics(
    val error: Boolean = false,
    val lines: List<Line>?,
    val syncType: String?,
    val simpMusicLyrics: WavoraLyrics? = null,
)

@Serializable
data class WavoraLyrics(
    val id: String,
    val vote: Int,
)