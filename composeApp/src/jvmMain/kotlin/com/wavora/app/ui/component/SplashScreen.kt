package com.wavora.app.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.wavora.app.ui.theme.wavoraBackground
import com.wavora.app.ui.theme.wavoraGradientEnd
import com.wavora.app.ui.theme.wavoraGradientMid
import com.wavora.app.ui.theme.wavoraPrimary
import org.jetbrains.compose.resources.painterResource
import wavora.composeapp.generated.resources.Res
import wavora.composeapp.generated.resources.app_icon

/**
 * Splash Screen de Wavora Desktop ("Breathing Halo").
 *
 * Diseño: un halo radial violeta -> cian, con blur real (glow), que "respira"
 * detrás del logo (escala + opacidad en un ciclo suave de ~1.8s), mientras el
 * logo permanece nítido y casi estático en el centro. Nada de spinners, nada
 * de texto de carga, nada de porcentajes — la idea es que se sienta premium y
 * silencioso (Spotify/Arc/Nothing OS), nunca infantil.
 *
 * IMPORTANTE: esta pantalla NO tiene ningún timer ni delay ligado a la
 * inicialización real. Quien la usa (`runDesktopApp` en DesktopApp.kt) decide
 * cuándo empezar a ocultarla, en base al estado real de Koin/DataStore/VLC —
 * nunca en base a un tiempo fijo. La única animación por tiempo que tiene esta
 * pantalla es la de entrada/salida (fade + scale), puramente cosmética, que se
 * dispara sobre un evento real (`visible` pasando a `false`), no sobre un
 * temporizador de arranque.
 *
 * @param visible Controla la fase de salida. Mientras es `true` la pantalla
 *   respira en loop normalmente. Al pasar a `false` (init real ya terminó)
 *   se dispara un fade-out + scale-down y, al terminar, se invoca
 *   [onExitFinished] para que el caller recién ahí desmonte la ventana.
 * @param onExitFinished Invocado una única vez, cuando la animación de salida
 *   terminó por completo.
 */
@Composable
fun SplashScreen(
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    onExitFinished: () -> Unit = {},
) {
    // --- Entrada: fade-in + scale-up suave al montar la pantalla ---
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        entrance.animateTo(1f, tween(durationMillis = 420, easing = EaseOutCubic))
    }

    // --- Salida: fade-out + scale-down cuando el init real terminó ---
    val exit = remember { Animatable(1f) }
    LaunchedEffect(visible) {
        if (!visible) {
            exit.animateTo(0f, tween(durationMillis = 320, easing = FastOutSlowInEasing))
            onExitFinished()
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "splash_breathing")

    // Ciclo de "respiración": 0f -> 1f -> 0f cada 2200ms, easing suave (sin picos).
    val breath by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 2200, easing = EaseInOutSine),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "breath",
    )

    // El halo crece/se desvanece con el ciclo de respiración.
    val haloScale = 0.90f + breath * 0.20f // 0.90 -> 1.10
    val haloAlpha = 0.26f + breath * 0.32f // 0.26 -> 0.58

    // El logo mismo respira de forma mucho más sutil que el halo (2.5% de escala).
    val logoScale = 0.985f + breath * 0.025f

    // Combina entrada + salida en un único factor de escala/alpha para el
    // conjunto completo (halo + anillo + logo), sin afectar el loop de breathing.
    val presenceAlpha = entrance.value * exit.value
    val presenceScale = (0.94f + entrance.value * 0.06f) * (0.92f + exit.value * 0.08f)

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = presenceAlpha
                    scaleX = presenceScale
                    scaleY = presenceScale
                    transformOrigin = TransformOrigin.Center
                },
        contentAlignment = Alignment.Center,
    ) {
        // Halo radial pulsante detrás del logo, con blur real para un glow suave
        // (Spotify/Arc-style), en vez de solo jugar con el alpha del gradiente.
        Canvas(
            modifier =
                Modifier
                    .size(360.dp)
                    .graphicsLayer {
                        scaleX = haloScale
                        scaleY = haloScale
                        transformOrigin = TransformOrigin.Center
                    }.blur(28.dp),
        ) {
            val radius = size.minDimension / 2f
            drawCircle(
                brush =
                    Brush.radialGradient(
                        colors =
                            listOf(
                                wavoraPrimary.copy(alpha = haloAlpha),
                                wavoraGradientMid.copy(alpha = haloAlpha * 0.6f),
                                wavoraGradientEnd.copy(alpha = 0f),
                            ),
                        center = Offset(size.width / 2f, size.height / 2f),
                        radius = radius,
                    ),
                radius = radius,
            )
        }

        // Anillo fino, muy sutil, que también respira (un toque "Nothing OS").
        // Sin blur (queda nítido) para que contraste con el halo difuso de atrás.
        Canvas(
            modifier =
                Modifier
                    .size(210.dp)
                    .graphicsLayer {
                        scaleX = haloScale
                        scaleY = haloScale
                        transformOrigin = TransformOrigin.Center
                    },
        ) {
            drawCircle(
                color = wavoraGradientEnd.copy(alpha = haloAlpha * 0.5f),
                radius = size.minDimension / 2f,
                style = Stroke(width = 1.5.dp.toPx()),
            )
        }

        // Logo Wavora a resolución completa (512x512 nativo), mostrado grande
        // y nítido; respiración mucho más sutil que el halo.
        Image(
            painter = painterResource(Res.drawable.app_icon),
            contentDescription = null,
            modifier =
                Modifier
                    .size(176.dp)
                    .graphicsLayer {
                        scaleX = logoScale
                        scaleY = logoScale
                        transformOrigin = TransformOrigin.Center
                    },
        )
    }
}

/** Color de fondo sólido para la ventana de splash — mismo fondo que el resto de la app. */
val splashBackgroundColor: Color get() = wavoraBackground
