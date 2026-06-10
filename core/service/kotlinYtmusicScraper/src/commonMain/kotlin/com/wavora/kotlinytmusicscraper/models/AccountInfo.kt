package com.wavora.scraper.models

data class AccountInfo(
    val name: String,
    val email: String,
    val pageId: String? = null,
    val thumbnails: List<Thumbnail>,
)