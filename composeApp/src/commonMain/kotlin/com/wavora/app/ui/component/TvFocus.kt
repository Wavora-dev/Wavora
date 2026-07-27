package com.wavora.app.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Indicador visual de foco para navegación con control remoto (Android TV / D-pad /
 * teclado). Encadenalo en cualquier elemento clickable/seleccionable (botones,
 * filas de lista, items de navegación) para que se note claramente cuál elemento
 * tiene el foco en este momento.
 *
 * En celular/touch esto no cambia nada visualmente: el foco por toque no dispara
 * un estado de foco persistente de la misma forma, así que no hay downside de
 * agregarlo en toda la app - donde de verdad importa es al navegar con D-pad,
 * que es exactamente el problema que estamos resolviendo (antes no había NINGÚN
 * indicador de qué elemento tenía el foco, por eso la navegación se sentía "a
 * ciegas").
 *
 * Nota: esto es la pieza base reutilizable. Falta aplicarla al resto de las
 * pantallas (listas de canciones, grillas de biblioteca, settings, etc.) - por
 * ahora está cableada en la barra de navegación principal y en los controles
 * de reproducción (PlayerControlLayout), que son los puntos de entrada más
 * críticos para no sentirse perdido con el control remoto.
 */
@Composable
fun Modifier.tvFocusIndicator(
    focusedScale: Float = 1.12f,
    borderColor: Color = Color.White,
    cornerRadius: androidx.compose.ui.unit.Dp = 8.dp,
): Modifier {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) focusedScale else 1f,
        label = "tvFocusScale",
    )
    return this
        .onFocusChanged { state -> isFocused = state.isFocused || state.hasFocus }
        .scale(scale)
        .then(
            if (isFocused) {
                Modifier.border(2.dp, borderColor, RoundedCornerShape(cornerRadius))
            } else {
                Modifier
            },
        )
}
