package com.wavora.nowplayingcenter.domain

sealed class Platform {
    object Windows : Platform()
    object MacOs : Platform()
    data class Linux(val appName: String, val appId: String) : Platform()
}
