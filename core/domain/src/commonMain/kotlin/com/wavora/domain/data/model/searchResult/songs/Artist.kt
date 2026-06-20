package com.wavora.domain.model.model.searchResult.songs

import kotlinx.serialization.Serializable

@Serializable
data class Artist(
    val id: String?,
    val name: String,
)