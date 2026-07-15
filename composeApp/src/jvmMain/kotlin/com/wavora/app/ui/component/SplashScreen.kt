package com.wavora.app.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
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
import kotlin.math.cos
import kotlin.math.sin

/**
 * Splash Screen de Wavora Desktop ("Breathing Blobs").
 *
 * Mismos colores/gradiente reales de marca que el resto de la app
 * (`wavoraPrimary` -> `wavoraGradientMid` para el núcleo, `wavoraGradientEnd`
 * para el aura exterior — el mismo trío que ya se usa en `FullscreenPlayer.kt`
 * y compañía, no un celeste inventado). Lo que cambia respecto a la versión
 * anterior es la FORMA: en vez de círculos perfectos, cada halo es un blob
 * orgánico — el contorno se arma con una suma de un par de armónicos
 * senoidales sobre el radio (`blobPath`, más abajo) cuya fase avanza sin
 * parar, así el borde "vibra"/se deforma en loop en vez de quedar plano y
 * geométrico. Cada halo vibra a su propia velocidad y con su propio patrón
 * (frecuencias distintas), para que no se sientan sincronizados entre sí.
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
 *   respira/vibra en loop normalmente. Al pasar a `false` (init real ya
 *   terminó) se dispara un fade-out + scale-down y, al terminar, se invoca
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

    // Núcleo interior: respiración lenta (3400ms) — la más "presente" de las
    // dos, ya que este halo es el que lleva casi todo el peso visual.
    val innerBreath by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 3400, easing = EaseInOutSine),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "inner_breath",
    )

    // Aura exterior: respiración más lenta todavía (5200ms) y sin sincronizar
    // con el interior a propósito — al no compartir período, con el tiempo
    // los dos halos quedan en fases distintas entre sí, lo que rompe la
    // sensación de "pulso mecánico" de un único ciclo repetido.
    val outerBreath by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 5200, easing = EaseInOutSine),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "outer_breath",
    )

    // Desplazamiento sutil del centro del gradiente de cada halo — el brillo
    // "flota" levemente dentro del blob en vez de quedar fijo en el centro.
    val innerDriftX by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 6100, easing = EaseInOutSine),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "inner_drift_x",
    )
    val innerDriftY by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 7300, easing = EaseInOutSine),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "inner_drift_y",
    )
    val outerDriftX by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 9400, easing = EaseInOutSine),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "outer_drift_x",
    )
    val outerDriftY by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 8200, easing = EaseInOutSine),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "outer_drift_y",
    )

    // --- "Vibración" del contorno: fases que avanzan sin parar (0 -> 2π en
    // loop, LinearEasing, sin ping-pong) — esto es lo que hace que el borde
    // del blob se deforme continuamente en vez de quedar en un círculo plano.
    // Dos fases por halo con velocidades distintas entre sí (y respecto al
    // otro halo) para que la deformación combinada no se sienta como un
    // patrón repetitivo/mecánico a simple vista.
    val innerWobblePhase1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = 4200, easing = LinearEasing)),
        label = "inner_wobble_1",
    )
    val innerWobblePhase2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = 6700, easing = LinearEasing)),
        label = "inner_wobble_2",
    )
    val outerWobblePhase1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = 5800, easing = LinearEasing)),
        label = "outer_wobble_1",
    )
    val outerWobblePhase2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = 9100, easing = LinearEasing)),
        label = "outer_wobble_2",
    )

    // --- Núcleo interior: violeta -> mid, blur bajo, opacidad alta ---
    val innerScale = 0.94f + innerBreath * 0.10f // 0.94 -> 1.04, sutil
    val innerAlpha = 0.55f + innerBreath * 0.20f // 0.55 -> 0.75

    // --- Aura exterior: cian, blur muy grande, opacidad baja ---
    val outerScale = 0.90f + outerBreath * 0.16f // 0.90 -> 1.06
    val outerAlpha = 0.15f + outerBreath * 0.10f // 0.15 -> 0.25

    // El logo mismo respira de forma mucho más sutil que los halos.
    val logoScale = 0.985f + innerBreath * 0.025f

    // Combina entrada + salida en un único factor de escala/alpha para el
    // conjunto completo (halos + anillo + logo), sin afectar los loops propios.
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
        // Aura exterior — grande, cian, blur muy fuerte, opacidad baja, blob
        // orgánico. Va primero (más atrás) para que el núcleo quede encima.
        Canvas(
            modifier =
                Modifier
                    .size(480.dp)
                    .graphicsLayer {
                        scaleX = outerScale
                        scaleY = outerScale
                        transformOrigin = TransformOrigin.Center
                    }.blur(64.dp),
        ) {
            val radius = size.minDimension / 2f
            val driftOffset = Offset(outerDriftX * radius * 0.08f, outerDriftY * radius * 0.08f)
            val center = Offset(size.width / 2f, size.height / 2f)
            drawPath(
                path =
                    blobPath(
                        center = center,
                        baseRadius = radius,
                        // Amplitud moderada: el aura exterior es la que más
                        // "respira" de forma visible en el borde.
                        amplitude1 = 0.09f,
                        frequency1 = 3,
                        phase1 = outerWobblePhase1,
                        amplitude2 = 0.05f,
                        frequency2 = 5,
                        phase2 = outerWobblePhase2,
                    ),
                brush =
                    Brush.radialGradient(
                        colors =
                            listOf(
                                wavoraGradientEnd.copy(alpha = outerAlpha),
                                wavoraGradientEnd.copy(alpha = outerAlpha * 0.4f),
                                wavoraGradientEnd.copy(alpha = 0f),
                            ),
                        center = center + driftOffset,
                        radius = radius * 1.15f,
                    ),
            )
        }

        // Núcleo interior — compacto, violeta -> mid, blur bajo, opacidad
        // alta, blob orgánico con una vibración más sutil que el aura (es el
        // elemento con más definición del conjunto, no debe leerse ruidoso).
        Canvas(
            modifier =
                Modifier
                    .size(312.dp)
                    .graphicsLayer {
                        scaleX = innerScale
                        scaleY = innerScale
                        transformOrigin = TransformOrigin.Center
                    }.blur(10.dp),
        ) {
            val radius = size.minDimension / 2f
            val driftOffset = Offset(innerDriftX * radius * 0.06f, innerDriftY * radius * 0.06f)
            val center = Offset(size.width / 2f, size.height / 2f)
            drawPath(
                path =
                    blobPath(
                        center = center,
                        baseRadius = radius,
                        amplitude1 = 0.06f,
                        frequency1 = 4,
                        phase1 = innerWobblePhase1,
                        amplitude2 = 0.035f,
                        frequency2 = 6,
                        phase2 = innerWobblePhase2,
                    ),
                brush =
                    Brush.radialGradient(
                        colors =
                            listOf(
                                wavoraPrimary.copy(alpha = innerAlpha),
                                wavoraGradientMid.copy(alpha = innerAlpha * 0.85f),
                                wavoraGradientMid.copy(alpha = 0f),
                            ),
                        center = center + driftOffset,
                        radius = radius * 1.15f,
                    ),
            )
        }

        // Anillo fino, muy sutil, que respira junto con el aura exterior.
        // Sin blur (queda nítido) y sin blob — es un acento geométrico
        // deliberado que contrasta contra los dos halos orgánicos de atrás,
        // no otro "círculo plano" compitiendo con ellos.
        Canvas(
            modifier =
                Modifier
                    .size(252.dp)
                    .graphicsLayer {
                        scaleX = outerScale
                        scaleY = outerScale
                        transformOrigin = TransformOrigin.Center
                    },
        ) {
            drawCircle(
                color = wavoraGradientEnd.copy(alpha = outerAlpha * 0.9f),
                radius = size.minDimension / 2f,
                style = Stroke(width = 1.5.dp.toPx()),
            )
        }

        // Logo Wavora a resolución completa (512x512 nativo), mostrado grande
        // y nítido; respiración mucho más sutil que los halos. +20% de tamaño
        // respecto a la versión anterior (176dp -> 211dp).
        Image(
            painter = painterResource(Res.drawable.app_icon),
            contentDescription = null,
            modifier =
                Modifier
                    .size(211.dp)
                    .graphicsLayer {
                        scaleX = logoScale
                        scaleY = logoScale
                        transformOrigin = TransformOrigin.Center
                    },
        )
    }
}

/**
 * Genera un contorno orgánico ("blob") en vez de un círculo perfecto: el
 * radio en cada ángulo se perturba con la suma de dos armónicos senoidales
 * (`amplitudeN` como fracción del radio base, `frequencyN` como cantidad de
 * "lóbulos" alrededor del círculo, `phaseN` como el offset que avanza en el
 * tiempo para que el contorno vibre en loop). Los puntos resultantes se unen
 * con una spline Catmull-Rom cerrada (convertida a curvas cúbicas) para que
 * el borde quede suave — sin vértices angulosos — en vez de un polígono.
 */
private fun DrawScope.blobPath(
    center: Offset,
    baseRadius: Float,
    amplitude1: Float,
    frequency1: Int,
    phase1: Float,
    amplitude2: Float,
    frequency2: Int,
    phase2: Float,
    pointCount: Int = 14,
): Path {
    val angleStep = (2 * Math.PI / pointCount).toFloat()
    val points =
        (0 until pointCount).map { i ->
            val angle = i * angleStep
            val wobble =
                amplitude1 * sin(angle * frequency1 + phase1) +
                    amplitude2 * sin(angle * frequency2 + phase2)
            val r = baseRadius * (1f + wobble)
            Offset(center.x + r * cos(angle), center.y + r * sin(angle))
        }

    return Path().apply {
        moveTo(points[0].x, points[0].y)
        for (i in points.indices) {
            val p0 = points[(i - 1 + pointCount) % pointCount]
            val p1 = points[i]
            val p2 = points[(i + 1) % pointCount]
            val p3 = points[(i + 2) % pointCount]
            // Catmull-Rom -> Bezier cúbico estándar (tensión 1/6).
            val c1 = Offset(p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f)
            val c2 = Offset(p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f)
            cubicTo(c1.x, c1.y, c2.x, c2.y, p2.x, p2.y)
        }
        close()
    }
}

/** Color de fondo sólido para la ventana de splash — mismo fondo que el resto de la app. */
val splashBackgroundColor: Color get() = wavoraBackground
