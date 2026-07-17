package com.wavora.updater

import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import org.jetbrains.skia.Image as SkiaImage
import java.awt.Dimension

// ===== Brand tokens (copied, not imported: wavoraUpdater deliberately does
// not depend on :composeApp's theme module - see build.gradle.kts's doc on
// why this module stays standalone). Keep in sync by eye if the palette in
// composeApp/src/commonMain/kotlin/com/wavora/app/ui/theme/Color.kt changes. =====
private val wavoraBackground = Color(0xFF0B0B0F)
private val wavoraPrimary = Color(0xFFA259FF)
private val wavoraGradientMid = Color(0xFF6A5CFF)
private val wavoraGradientEnd = Color(0xFF00D4FF)
private val wavoraTextSecondary = Color(0xFFB3B3C6)
private val wavoraBorder = Color(0xFF1F1F2E)

private fun Stage.label(): String =
    when (this) {
        Stage.WAITING_FOR_WAVORA -> "Checking updates..."
        Stage.DOWNLOADING -> "Downloading..."
        Stage.VERIFYING -> "Verifying..."
        Stage.EXTRACTING -> "Extracting..."
        Stage.INSTALLING -> "Installing..."
        Stage.CLEANING -> "Cleaning temporary files..."
        Stage.LAUNCHING -> "Launching Wavora..."
    }

private fun parseArgs(argv: Array<String>): UpdaterArgs {
    fun value(flag: String): String? {
        val i = argv.indexOf(flag)
        return if (i >= 0 && i + 1 < argv.size) argv[i + 1] else null
    }
    val zipUrl = value("--zip-url") ?: error("Falta --zip-url")
    val version = value("--version") ?: error("Falta --version")
    val sha256 = value("--sha256")?.takeIf { it.isNotBlank() && !it.equals("none", ignoreCase = true) }
    val pid = value("--wavora-pid")?.toLongOrNull() ?: error("Falta o es inválido --wavora-pid")
    return UpdaterArgs(zipUrl = zipUrl, targetVersion = version, expectedSha256 = sha256, wavoraPid = pid)
}

fun main(args: Array<String>) {
    // Single-instance protection FIRST, before any Compose/AWT
    // initialization - a second instance should do nothing visible at
    // all, not even flash a window. See acquireSingleInstanceLock()'s doc.
    val instanceLock =
        acquireSingleInstanceLock() ?: run {
            System.err.println("WavoraUpdater: another instance is already running - exiting")
            kotlin.system.exitProcess(0)
        }
    // Deliberately never released/closed explicitly - held for this
    // process's entire lifetime and released automatically by the OS
    // when it exits (normally or via crash), which is exactly the
    // lifetime we want it to cover.
    Runtime.getRuntime().addShutdownHook(Thread { runCatching { instanceLock.release() } })

    runUpdaterApplication(args)
}

private fun runUpdaterApplication(args: Array<String>) =
    application {
        val updaterArgs =
            try {
                parseArgs(args)
            } catch (e: Exception) {
                // No hay una UI a la que atarse todavía si faltan argumentos -
                // esto solo puede pasar por un bug en quien nos invoca
                // (AppUpdate.jvm.kt), no por una condición del usuario.
                System.err.println("WavoraUpdater: argumentos inválidos: ${e.message}")
                kotlin.system.exitProcess(1)
            }

        val windowState =
            rememberWindowState(
                position = WindowPosition.Aligned(Alignment.Center),
                width = 380.dp,
                height = 220.dp,
            )

        Window(
            onCloseRequest = ::exitApplication,
            state = windowState,
            title = "Wavora",
            undecorated = true,
            transparent = true,
            resizable = false,
            alwaysOnTop = true,
        ) {
            window.minimumSize = Dimension(380, 220)
            UpdaterApp(updaterArgs, onFinished = ::exitApplication)
        }
    }

@Composable
private fun UpdaterApp(
    args: UpdaterArgs,
    onFinished: () -> Unit,
) {
    var stage by remember { mutableStateOf<Stage?>(null) }
    var downloadProgress by remember { mutableStateOf<Float?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableStateOf(0) }

    LaunchedEffect(attempt) {
        errorMessage = null
        stage = null
        downloadProgress = null
        try {
            UpdaterLogic.run(args) { newStage, progress ->
                stage = newStage
                downloadProgress = progress
            }
            onFinished()
        } catch (e: Exception) {
            errorMessage = e.message ?: "Error desconocido durante la actualización."
        }
    }

    MaterialTheme {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(wavoraBackground),
            contentAlignment = Alignment.Center,
        ) {
            BreathingGlow()

            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Logo()

                if (errorMessage == null) {
                    Text(
                        text = stage?.label() ?: "Checking updates...",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    BrandProgressBar(progress = downloadProgress)
                } else {
                    Text(
                        text = "No se pudo completar la actualización",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = errorMessage ?: "",
                        color = wavoraTextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = { attempt++ },
                            colors = ButtonDefaults.buttonColors(containerColor = wavoraPrimary),
                        ) { Text("Reintentar") }
                        Button(
                            onClick = onFinished,
                            colors = ButtonDefaults.buttonColors(containerColor = wavoraBorder),
                        ) { Text("Cerrar") }
                    }
                }
            }
        }
    }
}

/** Un único halo respirando, mucho más simple que el del Splash completo a
 * propósito (ver el pedido de "interfaz mínima") - misma paleta e idea de
 * "aliento" continuo, sin el sistema de blobs/wobble del Splash real. */
@Composable
private fun BreathingGlow() {
    val infiniteTransition = rememberInfiniteTransition(label = "updater_glow")
    val breath by infiniteTransition.animateFloat(
        initialValue = 0.75f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 3400, easing = EaseInOutSine),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "breath",
    )
    Canvas(modifier = Modifier.size(260.dp).blur(48.dp)) {
        val radius = (size.minDimension / 2f) * breath
        drawCircle(
            brush =
                Brush.radialGradient(
                    colors =
                        listOf(
                            wavoraPrimary.copy(alpha = 0.55f),
                            wavoraGradientMid.copy(alpha = 0.25f),
                            wavoraGradientEnd.copy(alpha = 0f),
                        ),
                    center = Offset(size.width / 2f, size.height / 2f),
                    radius = radius * 1.2f,
                ),
            radius = radius,
            center = Offset(size.width / 2f, size.height / 2f),
        )
    }
}

@Composable
private fun Logo() {
    val bitmap =
        remember {
            val bytes =
                requireNotNull(object {}.javaClass.getResourceAsStream("/wavora-logo.png")) {
                    "Bundled resource /wavora-logo.png not found"
                }.readBytes()
            SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
        }
    Image(
        bitmap = bitmap,
        contentDescription = null,
        modifier = Modifier.size(64.dp),
    )
}

@Composable
private fun BrandProgressBar(progress: Float?) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(wavoraBorder),
    ) {
        if (progress != null) {
            // Progreso real (solo la etapa de descarga lo tiene) - ancho
            // proporcional, sin animación indeterminada fingiendo actividad.
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            Brush.horizontalGradient(colors = listOf(wavoraPrimary, wavoraGradientMid, wavoraGradientEnd)),
                        ),
            )
        } else {
            // Etapas sin porcentaje real (verificar/extraer/instalar/limpiar):
            // barra llena con el gradiente de marca, sin fingir un progreso
            // que no existe.
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            Brush.horizontalGradient(colors = listOf(wavoraBorder, wavoraGradientMid, wavoraBorder)),
                        ),
            )
        }
    }
}


