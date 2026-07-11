package com.wavora.domain.mediaservice.session

import com.wavora.domain.mediaservice.handler.MediaPlayerHandler
import com.wavora.domain.mediaservice.handler.PlayerEvent
import com.wavora.logger.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Implements [PlayerController] on top of the existing [MediaPlayerHandler.onPlayerEvent], WITHOUT
 * adding any new commands to `MediaPlayerHandler` or bypassing it (e.g. by calling
 * `handler.player.play()` directly, which would skip side effects like
 * `startProgressUpdate()`/`stopProgressUpdate()` that `onPlayerEvent` already handles today).
 *
 * One caveat worth flagging rather than hiding: [MediaPlayerHandler] only exposes a single
 * toggling `PlayerEvent.PlayPause` — there's no distinct "Play" / "Pause" command today. [play]
 * and [pause] below make this directional by checking current `controlState.isPlaying` before
 * toggling, which is correct in the common case but is technically a check-then-act (a
 * near-simultaneous external toggle, e.g. a Bluetooth headset button, could race with it). This
 * matches the level of correctness the rest of the codebase already has around PlayPause and is
 * not a regression — just not a place to silently claim more atomicity than exists.
 *
 * Fase 1 instrumentation (see PROMPT_02 Playback Transition Report): every command logs under the
 * shared "PB_TRACE" tag with a timestamp and the player state right before the command is issued.
 * Thread/caller aren't included here — this class lives in commonMain (Android/Desktop/iOS), and
 * there's no portable way to read the calling thread name or stack from common code. The
 * platform-level logs (MediaServiceHandlerImpl, CrossfadeExoPlayerAdapter, VlcPlayerAdapter) do
 * include thread name, since those files are JVM/Android-specific.
 */
@OptIn(ExperimentalTime::class)
class PlayerControllerAdapter(
    private val handler: MediaPlayerHandler,
    private val scope: CoroutineScope,
) : PlayerController {
    override fun play() {
        Logger.d("PB_TRACE", "t=${Clock.System.now()} PlayerController.play() | wasPlaying=${handler.controlState.value.isPlaying}")
        scope.launch {
            if (!handler.controlState.value.isPlaying) {
                handler.onPlayerEvent(PlayerEvent.PlayPause)
            }
        }
    }

    override fun pause() {
        Logger.d("PB_TRACE", "t=${Clock.System.now()} PlayerController.pause() | wasPlaying=${handler.controlState.value.isPlaying}")
        scope.launch {
            if (handler.controlState.value.isPlaying) {
                handler.onPlayerEvent(PlayerEvent.PlayPause)
            }
        }
    }

    override fun seek(positionMs: Long) {
        Logger.d("PB_TRACE", "t=${Clock.System.now()} PlayerController.seek($positionMs)")
        scope.launch {
            val durationMs = handler.getPlayerDuration()
            if (durationMs > 0L) {
                val percentage = (positionMs.toFloat() / durationMs.toFloat()) * 100f
                handler.onPlayerEvent(PlayerEvent.UpdateProgress(percentage.coerceIn(0f, 100f)))
            }
        }
    }

    override fun next() {
        Logger.d(
            "PB_TRACE",
            "t=${Clock.System.now()} PlayerController.next() | song=${handler.nowPlaying.value?.mediaId} " +
                "isPlaying=${handler.controlState.value.isPlaying}",
        )
        scope.launch { handler.onPlayerEvent(PlayerEvent.Next) }
    }

    override fun previous() {
        Logger.d(
            "PB_TRACE",
            "t=${Clock.System.now()} PlayerController.previous() | song=${handler.nowPlaying.value?.mediaId} " +
                "isPlaying=${handler.controlState.value.isPlaying}",
        )
        scope.launch { handler.onPlayerEvent(PlayerEvent.Previous) }
    }
}
