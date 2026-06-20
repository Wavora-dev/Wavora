package com.wavora.scraper.models

sealed class MediaType {
    data object Song : MediaType()

    data object Video : MediaType()
}