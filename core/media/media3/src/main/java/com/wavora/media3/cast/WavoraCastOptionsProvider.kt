package com.wavora.media3.cast

import android.content.Context
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider
import com.google.android.gms.cast.framework.media.CastMediaOptions
import com.google.android.gms.cast.framework.media.NotificationOptions

/**
 * Configuración requerida por el Cast SDK. Se referencia desde
 * `AndroidManifest.xml` vía el meta-data
 * `com.google.android.gms.cast.framework.OPTIONS_PROVIDER_CLASS_NAME`.
 *
 * Fase 3 de la integración de Cast: usa el "Default Media Receiver" de
 * Google (gratis, ya hosteado, soporta streams HTTP directos con metadata
 * sin necesitar programar un receiver propio). Cambiar a un receiver con
 * estilo propio es una mejora posterior, no un requisito para que esto
 * funcione.
 *
 * Esta clase todavía no está conectada a ningún Player ni a la UI — eso es
 * responsabilidad de fases posteriores (CastPlayerManager, el botón de Cast,
 * y el swap en DelegatingForwardingPlayer). Por ahora solo declara qué
 * notificaciones/controles usar cuando exista una sesión Cast activa.
 */
class WavoraCastOptionsProvider : OptionsProvider {
    override fun getCastOptions(context: Context): CastOptions {
        val notificationOptions =
            NotificationOptions
                .Builder()
                .build()

        val mediaOptions =
            CastMediaOptions
                .Builder()
                .setNotificationOptions(notificationOptions)
                .build()

        return CastOptions
            .Builder()
            .setReceiverApplicationId(com.google.android.gms.cast.CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
            .setCastMediaOptions(mediaOptions)
            // No necesitamos que el SDK cree su propia notificación de reproducción
            // fuera de sesión Cast — Wavora ya tiene la suya vía MediaSession
            // (SimpleMediaService). Evita una segunda notificación duplicada.
            .setResumeSavedSession(false)
            .build()
    }

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? = null
}
