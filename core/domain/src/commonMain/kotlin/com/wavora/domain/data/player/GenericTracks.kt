package com.wavora.domain.model.player

/**
 * Generic tracks information
 */
data class GenericTracks(
    val groups: List<GenericTrackGroup>,
) {
    data class GenericTrackGroup(
        val trackCount: Int,
    )
}