package com.wavora.domain.mediaservice.player

import com.wavora.domain.model.player.GenericMediaItem
import com.wavora.domain.model.player.GenericPlaybackParameters

/**
 * Abstract interface for media player implementations
 */
interface MediaPlayerInterface {
    // Playback control
    fun play()

    fun pause()

    fun stop()

    fun seekTo(positionMs: Long)

    fun seekTo(
        mediaItemIndex: Int,
        positionMs: Long,
    )

    fun seekBack()

    fun seekForward()

    fun seekToNext()

    fun seekToPrevious()

    /**
     * Always advances to the previous media item, regardless of the current playback
     * position. This is the version used by UI affordances that should NOT exhibit
     * the "tap once to restart, tap again to go back" behaviour of [seekToPrevious]
     * (e.g. swiping the artwork pager). Implementations must skip the 3-second
     * "seek to start" threshold and go straight to the previous track.
     */
    fun seekToPreviousMediaItem()

    fun prepare()

    // Media item management
    fun setMediaItem(mediaItem: GenericMediaItem)

    fun addMediaItem(mediaItem: GenericMediaItem)

    fun addMediaItem(
        index: Int,
        mediaItem: GenericMediaItem,
    )

    fun removeMediaItem(index: Int)

    fun moveMediaItem(
        fromIndex: Int,
        toIndex: Int,
    )

    fun clearMediaItems()

    fun replaceMediaItem(
        index: Int,
        mediaItem: GenericMediaItem,
    )

    fun getMediaItemAt(index: Int): GenericMediaItem?

    fun getCurrentMediaTimeLine(): List<GenericMediaItem>

    fun getUnshuffledIndex(shuffledIndex: Int): Int

    // Playback state properties
    val isPlaying: Boolean
    val currentPosition: Long
    val duration: Long
    val bufferedPosition: Long
    val bufferedPercentage: Int
    val currentMediaItem: GenericMediaItem?
    val currentMediaItemIndex: Int
    val mediaItemCount: Int
    val contentPosition: Long
    val playbackState: Int

    // Navigation
    fun hasNextMediaItem(): Boolean

    fun hasPreviousMediaItem(): Boolean

    // Playback modes
    var shuffleModeEnabled: Boolean
    var repeatMode: Int
    var playWhenReady: Boolean
    var playbackParameters: GenericPlaybackParameters

    // Audio settings
    val audioSessionId: Int
    var volume: Float
    var skipSilenceEnabled: Boolean

    // AUDIO EQUALIZER (feature nueva - Desktop/VLC únicamente por ahora).
    // Estos métodos tienen cuerpo por defecto a propósito: así se agregan a la
    // interfaz compartida sin tocar ni romper ningún otro implementador
    // existente (ExoPlayerAdapter, CrossfadeExoPlayerAdapter en Android/iOS).
    // Solo VlcPlayerAdapter los sobreescribe con lógica real; en el resto de
    // las plataformas supportsEqualizer() devuelve false y la UI se encarga
    // de no mostrar el ecualizador ahí.
    fun supportsEqualizer(): Boolean = false

    /** Frecuencias centrales (Hz) de cada banda del ecualizador, en orden. */
    fun getEqualizerBandFrequencies(): List<Float> = emptyList()

    /** Nombres de los presets nativos disponibles (Flat, Rock, Pop, etc). */
    fun getEqualizerPresetNames(): List<String> = emptyList()

    fun isEqualizerEnabled(): Boolean = false

    fun setEqualizerEnabled(enabled: Boolean) {}

    /** Ganancia de pre-amplificación general, en dB (-20.0 a 20.0). */
    fun getEqualizerPreamp(): Float = 0f

    fun setEqualizerPreamp(preamp: Float) {}

    /** Ganancia actual de cada banda, en dB (-20.0 a 20.0), mismo orden que [getEqualizerBandFrequencies]. */
    fun getEqualizerBandAmps(): List<Float> = emptyList()

    fun setEqualizerBandAmp(
        index: Int,
        amp: Float,
    ) {}

    /** Nombre del preset actualmente cargado, o null si el usuario ajustó bandas manualmente. */
    fun getEqualizerPresetName(): String? = null

    /** Carga un preset por nombre (uno de [getEqualizerPresetNames]) y reemplaza todas las bandas + preamp. */
    fun loadEqualizerPreset(name: String) {}

    /** Carga la ranura de usuario Custom 1 (slot=1) o Custom 2 (slot=2). Arrancan planas (0 en todo) hasta que el usuario las edita. */
    suspend fun loadEqualizerCustomSlot(slot: Int) {}

    /** Restablece lo seleccionado actualmente (preset curado o ranura Custom) a su estado por defecto. */
    fun resetEqualizerToDefault() {}

    // SCROBBLING (Last.fm) - mismo criterio que el ecualizador de arriba:
    // cuerpo por defecto vacío/no-soportado, solo VlcPlayerAdapter (Desktop)
    // lo sobreescribe. La UI usa supportsScrobbling() para decidir si mostrar
    // la pantalla de conexión.
    fun supportsScrobbling(): Boolean = false

    /** true si la app tiene configuradas sus credenciales de Last.fm (LASTFM_API_KEY/SECRET). */
    fun isLastFmConfigured(): Boolean = false

    /** Intercambia usuario/contraseña por una session key permanente (la contraseña nunca se guarda). */
    suspend fun loginToLastFm(
        username: String,
        password: String,
    ): Result<String> = Result.failure(UnsupportedOperationException("Scrobbling no soportado en esta plataforma"))

    // Listener management
    fun addListener(listener: MediaPlayerListener)

    fun removeListener(listener: MediaPlayerListener)

    // Release resources
    fun release()

    companion object {
        // Nombres fijos (sin traducir a propósito, igual que los presets
        // curados tipo "Rock"/"Pop") de las 2 ranuras de usuario del EQ.
        // Únicos acá para que VlcPlayerAdapter (quien los asigna) y la UI
        // (quien los compara para saber qué chip resaltar) nunca se
        // desincronicen.
        const val EQUALIZER_CUSTOM_1_NAME = "Custom 1"
        const val EQUALIZER_CUSTOM_2_NAME = "Custom 2"
    }
}