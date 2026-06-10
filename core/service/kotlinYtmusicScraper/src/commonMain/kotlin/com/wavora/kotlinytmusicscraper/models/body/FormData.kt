package com.wavora.scraper.models.body

import kotlinx.serialization.Serializable

@Serializable
data class FormData(
    val selectedValues: List<String> = listOf("ZZ"),
)