package com.wavora.domain.mediaservice.session

/**
 * Single entry point for playback commands.
 *
 * Centralizes `play()`, `pause()`, `seek()`, `next()` and `previous()` as requested by point 8 of
 * the PlayerSession migration. This intentionally covers only the five core transport commands —
 * everything else that [com.wavora.domain.mediaservice.handler.MediaPlayerHandler] exposes today
 * (shuffle, repeat, like, sleep timer, queue editing, crossfade, etc.) stays on that interface for
 * now and is out of scope for this phase, to avoid re-deciding a much larger API surface in one
 * step. [PlayerController] is meant to eventually be the only thing that calls into
 * `MediaPlayerHandler.onPlayerEvent(...)` for these five commands.
 */
interface PlayerController {
    fun play()

    fun pause()

    fun seek(positionMs: Long)

    fun next()

    fun previous()
}
