package com.wavora.app.ui.component

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.wavora.app.ui.theme.LocalAppTypography

/**
 * Tooltip simple para botones de solo ícono (Ajustes, Mix, Recientes,
 * Notificaciones, etc.) - en Desktop aparece al pasar el mouse por encima y
 * desaparece solo al sacarlo (comportamiento nativo de TooltipBox, no algo
 * que manejemos a mano); en Android/touch aparece con mantener presionado.
 * Usa los colores por defecto de Material3 (inverseSurface/inverseOnSurface)
 * para que quede visualmente coherente con el resto de la app sin necesidad
 * de definir un estilo aparte.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpleTooltip(
    text: String,
    content: @Composable () -> Unit,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
        tooltip = {
            PlainTooltip {
                Text(text = text, style = LocalAppTypography.current.bodySmall, modifier = androidx.compose.ui.Modifier.padding(2.dp))
            }
        },
        state = rememberTooltipState(),
        content = content,
    )
}
