package com.wavora.nowplayingcenter

import com.wavora.nowplayingcenter.domain.NowPlayingListener
import com.wavora.nowplayingcenter.domain.Platform

/**
 * Stub implementation of the NPYC (Now Playing Center) library.
 * The real library (org.simpmusic:nowplayingcenter) is not publicly available.
 * This stub allows compilation while disabling Linux MPRIS integration.
 */
class NPYC(private val platform: Platform) {

    fun setListener(listener: NowPlayingListener) {
        // stub
    }

    fun setNowPlaying(
        title: String,
        artist: String,
        album: String,
        thumbnails: Any?,
    ) {
        // stub
    }

    fun setButtonEnabled(
        isPlaying: Boolean,
        canGoNext: Boolean,
        canGoPrevious: Boolean,
    ) {
        // stub
    }

    fun removeListener() {
        // stub
    }
}
