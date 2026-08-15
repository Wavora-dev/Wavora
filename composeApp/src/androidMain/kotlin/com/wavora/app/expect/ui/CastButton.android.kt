package com.wavora.app.expect.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import androidx.media3.common.util.UnstableApi
import com.google.android.gms.cast.framework.CastButtonFactory
import com.wavora.media3.cast.CastPlayerManager
import org.koin.compose.koinInject

/**
 * Fase 4 de la integración de Cast: el ícono real en la topbar.
 *
 * Usa `MediaRouteButton` (vista clásica de Android) + `CastButtonFactory`
 * — la forma oficial recomendada por Google — en vez de un ícono Compose
 * custom. Esto delega en el propio SDK: descubrimiento de dispositivos,
 * animación de "buscando", y el ícono de "conectado" cuando hay una sesión
 * activa. No reimplementamos nada de eso a mano.
 *
 * Todavía no hace nada más que abrir el diálogo de selección de dispositivo
 * del sistema — conectar una sesión Cast en este punto NO cambia qué
 * reproduce Wavora (eso es la Fase 5, el swap en
 * `DelegatingForwardingPlayer`). Tocar el botón y elegir un dispositivo hoy
 * simplemente deja armada la sesión Cast, sin efecto todavía sobre el
 * audio.
 */
@Composable
@UnstableApi
actual fun CastButton(modifier: Modifier) {
    val castPlayerManager = koinInject<CastPlayerManager>()

    // Sin Google Play Services no hay Cast posible — ni intentamos crear la
    // vista (CastButtonFactory asume que CastContext puede inicializarse).
    if (!castPlayerManager.isCastAvailable) return

    AndroidView(
        modifier = modifier,
        factory = { context ->
            MediaRouteButton(context).apply {
                CastButtonFactory.setUpMediaRouteButton(context.applicationContext, this)
            }
        },
    )
}
