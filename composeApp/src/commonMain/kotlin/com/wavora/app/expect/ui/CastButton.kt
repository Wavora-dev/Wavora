package com.wavora.app.expect.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Botón de Google Cast (Fase 4 de la integración de Cast). Android-only:
 * en Desktop no renderiza nada (ver el actual de jvmMain).
 *
 * Se apoya en `MediaRouteButton` + `CastButtonFactory` de la Cast SDK oficial
 * (no un ícono Compose custom), para heredar gratis el manejo de estados
 * (buscando dispositivos, disponible, conectado) que ya provee el SDK.
 */
@Composable
expect fun CastButton(modifier: Modifier = Modifier)
