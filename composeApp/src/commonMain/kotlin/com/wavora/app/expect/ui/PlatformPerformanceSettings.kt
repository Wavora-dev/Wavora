package com.wavora.app.expect.ui

import androidx.compose.runtime.Composable

/**
 * Sección "Rendimiento (PC)" de Ajustes (v2.3): aceleración por hardware,
 * teclas multimedia, atajos de teclado y modo diagnóstico.
 * Solo tiene contenido en Desktop; en Android no dibuja nada.
 */
@Composable
expect fun PlatformPerformanceSettings()
