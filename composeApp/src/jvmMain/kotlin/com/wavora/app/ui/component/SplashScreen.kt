package com.wavora.app.ui.component

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.runtime.State
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wavora.app.ui.theme.wavoraBackground
import com.wavora.app.ui.theme.wavoraGradientEnd
import com.wavora.app.ui.theme.wavoraGradientMid
import com.wavora.app.ui.theme.wavoraPrimary
import org.jetbrains.compose.resources.painterResource
import wavora.composeapp.generated.resources.Res
import wavora.composeapp.generated.resources.app_icon
import kotlin.math.cos
import kotlin.math.sin

/** Tamaño del bloque del splash (logo + halos + texto de estado). */
val SPLASH_CONTENT_SIZE = 528.dp

/**
 * true cuando Skia dibuja por CPU (en Windows se fuerza `skiko.renderApi=SOFTWARE`
 * en DesktopApp.kt). Ahí un `Modifier.blur(64.dp)` sobre 480 dp se recalcula
 * entero en cada frame y el splash va a saltos justo mientras el arranque
 * compite por CPU. En ese modo dibujamos los halos sin blur: los degradados
 * radiales ya terminan en alpha 0 antes del borde, así que se ven igual de
 * suaves por una fracción del costo.
 */
val isSoftwareRendering: Boolean
    get() = System.getProperty("skiko.renderApi", "").equals("SOFTWARE", ignoreCase = true)

/**
 * Splash Screen de Wavora Desktop ("Breathing Blobs").
 *
 * Ahora vive DENTRO de la ventana principal (ver `WavoraMainWindow` en
 * DesktopApp.kt): mientras arranca, la ventana es transparente y solo se ve
 * este bloque centrado. Cuando el arranque real termina, la ventana se "abre"
 * desde el blob (clip redondeado que crece) y este composable corre su salida
 * guiado por [exitProgress]: los halos se expanden y se desvanecen, y el logo
 * vuela hasta el punto de la barra de título ([logoTargetOffset]).
 *
 * Igual que antes, NO hay ningún timer ligado al arranque: [exitProgress] lo
 * maneja el caller en base al estado real de Koin/DataStore/VLC.
 *
 * Rendimiento: todas las animaciones infinitas se leen en la fase de dibujo
 * (lambdas de `graphicsLayer {}` / `Canvas {}`), no en composición, así que
 * no hay recomposición por frame.
 *
 * @param exitProgress 0 = splash normal, 1 = salida terminada.
 * @param logoTargetOffset destino del logo, en px, relativo al centro del splash.
 * @param statusText mensaje de la etapa actual del arranque (null = ninguno).
 * @param loadProgress progreso aproximado del arranque (0..1), por etapas.
 */
@Composable
fun SplashScreen(
    modifier: Modifier = Modifier,
    exitProgress: () -> Float = { 0f },
    logoTargetOffset: () -> Offset = { Offset.Zero },
    statusText: String? = null,
    loadProgress: Float = 0f,
    lowPowerMode: Boolean = remember { isSoftwareRendering },
) {
    // --- Entrada: fade-in + scale-up suave al montar la pantalla ---
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        entrance.animateTo(1f, tween(durationMillis = 420, easing = EaseOutCubic))
    }
    val animatedLoad by animateFloatAsState(
        targetValue = loadProgress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 600, easing = EaseOutCubic),
        label = "splash_load",
    )

    val t = rememberInfiniteTransition(label = "splash_breathing")
    val innerBreath = t.breathing(3400, "inner_breath")
    val outerBreath = t.breathing(5200, "outer_breath")
    val innerDriftX = t.drift(6100, "inner_drift_x")
    val innerDriftY = t.drift(7300, "inner_drift_y")
    val outerDriftX = t.drift(9400, "outer_drift_x")
    val outerDriftY = t.drift(8200, "outer_drift_y")
    val innerWobble1 = t.phase(4200, "inner_wobble_1")
    val innerWobble2 = t.phase(6700, "inner_wobble_2")
    val outerWobble1 = t.phase(5800, "outer_wobble_1")
    val outerWobble2 = t.phase(9100, "outer_wobble_2")

    // Sin blur, el degradado tiene que llegar a alpha 0 antes del borde del
    // blob para que no se vea un corte duro.
    val gradientReach = if (lowPowerMode) 0.98f else 1.15f

    Box(
        modifier = modifier.size(SPLASH_CONTENT_SIZE),
        contentAlignment = Alignment.Center,
    ) {
        // Halos + anillo: se expanden y desvanecen durante la salida.
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val e = entrance.value
                        val x = easeOut((exitProgress() / 0.8f).coerceIn(0f, 1f))
                        alpha = e * (1f - x)
                        val s = (0.94f + e * 0.06f) * (1f + 1.6f * x)
                        scaleX = s
                        scaleY = s
                        transformOrigin = TransformOrigin.Center
                    },
            contentAlignment = Alignment.Center,
        ) {
            // Aura exterior (cian).
            Canvas(
                modifier =
                    Modifier
                        .size(480.dp)
                        .graphicsLayer {
                            val s = 0.90f + outerBreath.value * 0.16f
                            scaleX = s
                            scaleY = s
                        }.then(if (lowPowerMode) Modifier else Modifier.blur(64.dp)),
            ) {
                val radius = size.minDimension / 2f
                val alpha = 0.15f + outerBreath.value * 0.10f
                val center = Offset(size.width / 2f, size.height / 2f)
                val drift = Offset(outerDriftX.value * radius * 0.08f, outerDriftY.value * radius * 0.08f)
                drawPath(
                    path = blobPath(center, radius, 0.09f, 3, outerWobble1.value, 0.05f, 5, outerWobble2.value),
                    brush =
                        Brush.radialGradient(
                            colors =
                                listOf(
                                    wavoraGradientEnd.copy(alpha = alpha),
                                    wavoraGradientEnd.copy(alpha = alpha * 0.4f),
                                    wavoraGradientEnd.copy(alpha = 0f),
                                ),
                            center = center + drift,
                            radius = radius * gradientReach,
                        ),
                )
            }

            // Núcleo interior (violeta -> mid).
            Canvas(
                modifier =
                    Modifier
                        .size(312.dp)
                        .graphicsLayer {
                            val s = 0.94f + innerBreath.value * 0.10f
                            scaleX = s
                            scaleY = s
                        }.then(if (lowPowerMode) Modifier else Modifier.blur(10.dp)),
            ) {
                val radius = size.minDimension / 2f
                val alpha = 0.55f + innerBreath.value * 0.20f
                val center = Offset(size.width / 2f, size.height / 2f)
                val drift = Offset(innerDriftX.value * radius * 0.06f, innerDriftY.value * radius * 0.06f)
                drawPath(
                    path = blobPath(center, radius, 0.06f, 4, innerWobble1.value, 0.035f, 6, innerWobble2.value),
                    brush =
                        Brush.radialGradient(
                            colors =
                                listOf(
                                    wavoraPrimary.copy(alpha = alpha),
                                    wavoraGradientMid.copy(alpha = alpha * 0.85f),
                                    wavoraGradientMid.copy(alpha = 0f),
                                ),
                            center = center + drift,
                            radius = radius * gradientReach,
                        ),
                )
            }

            // Anillo fino.
            Canvas(
                modifier =
                    Modifier
                        .size(252.dp)
                        .graphicsLayer {
                            val s = 0.90f + outerBreath.value * 0.16f
                            scaleX = s
                            scaleY = s
                        },
            ) {
                drawCircle(
                    color = wavoraGradientEnd.copy(alpha = (0.15f + outerBreath.value * 0.10f) * 0.9f),
                    radius = size.minDimension / 2f,
                    style = Stroke(width = 1.5.dp.toPx()),
                )
            }
        }

        // Logo: respira sutil; en la salida vuela hasta la barra de título.
        Image(
            painter = painterResource(Res.drawable.app_icon),
            contentDescription = null,
            modifier =
                Modifier
                    .size(211.dp)
                    .graphicsLayer {
                        val e = entrance.value
                        val p = exitProgress()
                        val fly = easeInOut((p / 0.75f).coerceIn(0f, 1f))
                        val target = logoTargetOffset()
                        val breath = 0.985f + innerBreath.value * 0.025f
                        val s = (0.94f + e * 0.06f) * lerp(breath, 0.04f, fly)
                        scaleX = s
                        scaleY = s
                        translationX = target.x * fly
                        translationY = target.y * fly
                        alpha = e * (1f - ((p - 0.6f) / 0.25f).coerceIn(0f, 1f))
                        transformOrigin = TransformOrigin.Center
                    },
        )

        // Estado del arranque + barra de progreso fina, debajo del logo.
        Column(
            modifier =
                Modifier
                    .offset(y = 150.dp)
                    .graphicsLayer {
                        alpha = entrance.value * (1f - (exitProgress() / 0.25f).coerceIn(0f, 1f))
                    },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Crossfade(targetState = statusText, animationSpec = tween(250), label = "splash_status") { text ->
                Text(
                    text = text ?: "",
                    color = Color(0xFFB3B3C6),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(10.dp))
            Box(
                modifier =
                    Modifier
                        .width(160.dp)
                        .height(2.dp)
                        .drawBehind {
                            val r = CornerRadius(size.height / 2f)
                            drawRoundRect(color = Color(0xFF22222E), cornerRadius = r)
                            drawRoundRect(
                                brush = Brush.horizontalGradient(listOf(wavoraPrimary, wavoraGradientEnd)),
                                size = size.copy(width = size.width * animatedLoad),
                                cornerRadius = r,
                            )
                        },
            )
        }
    }
}

@Composable
private fun androidx.compose.animation.core.InfiniteTransition.breathing(
    durationMillis: Int,
    label: String,
): State<Float> =
    animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis, easing = EaseInOutSine), RepeatMode.Reverse),
        label = label,
    )

@Composable
private fun androidx.compose.animation.core.InfiniteTransition.drift(
    durationMillis: Int,
    label: String,
): State<Float> =
    animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis, easing = EaseInOutSine), RepeatMode.Reverse),
        label = label,
    )

@Composable
private fun androidx.compose.animation.core.InfiniteTransition.phase(
    durationMillis: Int,
    label: String,
): State<Float> =
    animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(durationMillis, easing = LinearEasing)),
        label = label,
    )

private fun lerp(a: Float, b: Float, f: Float): Float = a + (b - a) * f

private fun easeOut(x: Float): Float = 1f - (1f - x) * (1f - x) * (1f - x)

private fun easeInOut(x: Float): Float = if (x < 0.5f) 4f * x * x * x else 1f - ((-2f * x + 2f).let { it * it * it }) / 2f


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
