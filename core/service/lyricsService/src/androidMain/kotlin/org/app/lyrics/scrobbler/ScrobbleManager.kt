package org.app.lyrics.scrobbler

import com.wavora.domain.manager.DataStoreManager
import com.wavora.logger.Logger
import kotlinx.coroutines.flow.first

private const val TAG = "ScrobbleManager"

/**
 * Punto de entrada único para el hook de reproducción (ver
 * JvmMediaPlayerHandlerImpl.mayBeTrackingListeningLocal, que ya calcula el
 * mismo tipo de condición para el tracking local de analíticas). Nunca lanza
 * - cualquier fallo de red/config queda atrapado y logueado, la reproducción
 * jamás se entera.
 */
object ScrobbleManager {
    /**
     * Llamar cuando una canción terminó (o se saltó) y ya sabemos cuánto se
     * escuchó. [startedAtEpochSeconds] es el momento en que EMPEZÓ a sonar
     * (Last.fm y ListenBrainz timestampean el inicio del listen, no el fin).
     */
    suspend fun maybeScrobble(
        dataStoreManager: DataStoreManager,
        artist: String,
        title: String,
        album: String?,
        startedAtEpochSeconds: Long,
    ) {
        val lastFmEnabled = dataStoreManager.scrobbleLastFmEnabled.first() == DataStoreManager.Values.TRUE
        val listenBrainzEnabled = dataStoreManager.scrobbleListenBrainzEnabled.first() == DataStoreManager.Values.TRUE
        if (!lastFmEnabled && !listenBrainzEnabled) return

        if (lastFmEnabled) {
            val sessionKey = dataStoreManager.scrobbleLastFmSessionKey.first()
            if (sessionKey.isBlank()) {
                Logger.w(TAG, "Last.fm scrobbling activado pero sin sesión iniciada, se omite")
            } else {
                LastFmApi
                    .scrobble(sessionKey, artist, title, album, startedAtEpochSeconds)
                    .onFailure { Logger.w(TAG, "Fallo al scrobblear '$title' a Last.fm: ${it.message}") }
            }
        }

        if (listenBrainzEnabled) {
            val token = dataStoreManager.scrobbleListenBrainzToken.first()
            if (token.isBlank()) {
                Logger.w(TAG, "ListenBrainz scrobbling activado pero sin token configurado, se omite")
            } else {
                ListenBrainzApi
                    .submitListen(token, artist, title, album, startedAtEpochSeconds)
                    .onFailure { Logger.w(TAG, "Fallo al scrobblear '$title' a ListenBrainz: ${it.message}") }
            }
        }
    }
}
