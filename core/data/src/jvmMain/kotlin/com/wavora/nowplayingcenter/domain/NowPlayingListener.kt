package com.wavora.nowplayingcenter.domain

interface NowPlayingListener {
    fun onPlayPause()
    fun onNext()
    fun onPrevious()
    fun onStop()
}
