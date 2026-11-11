package com.wavora.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import coil3.ImageLoader
import coil3.compose.LocalPlatformContext
import coil3.compose.setSingletonImageLoaderFactory
import coil3.disk.DiskCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.CachePolicy
import coil3.request.crossfade
import com.kdroid.composetray.tray.api.Tray
import com.kdroid.composetray.utils.SingleInstanceManager
import com.wavora.appdata.di.loader.loadAllModules
import com.wavora.domain.manager.DataStoreManager
import com.wavora.domain.mediaservice.handler.MediaPlayerHandler
import com.wavora.domain.mediaservice.handler.ToastType
import com.wavora.domain.mediaservice.session.PlayerSessionAdapter
import com.wavora.app.di.viewModelModule
import com.wavora.app.ui.component.CustomTitleBar
import com.wavora.app.ui.component.SplashScreen
import com.wavora.app.ui.mini_player.MiniPlayerManager
import com.wavora.app.ui.mini_player.MiniPlayerWindow
import com.wavora.app.utils.VersionManager
import com.wavora.app.viewModel.SharedViewModel
import com.wavora.app.viewModel.changeLanguageNative
import com.wavora.logger.Logger
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import multiplatform.network.cmptoast.ToastHost
import multiplatform.network.cmptoast.showToast
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import okio.FileSystem
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.core.context.loadKoinModules
import org.koin.core.context.startKoin
import org.koin.mp.KoinPlatform.getKoin
import wavora.composeapp.generated.resources.Res
import wavora.composeapp.generated.resources.app_name
import wavora.composeapp.generated.resources.circle_app_icon
import wavora.composeapp.generated.resources.close_miniplayer
import wavora.composeapp.generated.resources.explicit_content_blocked
import wavora.composeapp.generated.resources.open_app
import wavora.composeapp.generated.resources.open_miniplayer
import wavora.composeapp.generated.resources.hw_accel_auto_disabled_toast
import wavora.composeapp.generated.resources.quit_app
import wavora.composeapp.generated.resources.tray_compat_mode
import wavora.composeapp.generated.resources.splash_stage_library
import wavora.composeapp.generated.resources.splash_stage_player
import wavora.composeapp.generated.resources.splash_stage_starting
import wavora.composeapp.generated.resources.time_out_check_internet_connection_or_change_piped_instance_in_settings

private const val TAG = "DesktopApp"

/**
 * Todo lo que necesita la composición principal una vez que el arranque
 * "pesado" (Koin, DataStore, VLC, etc.) terminó de verdad. Mientras esto sea
 * `null`, se muestra el Splash Screen; en cuanto existe, se muestra la ventana
 * principal. Ver [SplashScreen] y el comentario "WAVORA STARTUP+SPLASH FIX"
 * más abajo.
 */
private class AppReady(
    val mediaPlayerHandler: MediaPlayerHandler,
    val sharedViewModel: SharedViewModel,
)

/**
 * Etapa real del arranque, para que el splash muestre qué está pasando en vez
 * de un logo mudo. Se actualiza desde el coroutine de arranque (Dispatchers.Default)
 * y la UI la observa con collectAsState. `progress` es aproximado, por etapa.
 */
private enum class StartupStage(
    val progress: Float,
) {
    STARTING(0.12f),
    LIBRARY(0.45f),
    PLAYER(0.8f),
    READY(1f),
}

private val startupStage = MutableStateFlow(StartupStage.STARTING)

@OptIn(ExperimentalMaterial3Api::class)
fun runDesktopApp(args: Array<String> = emptyArray()) {
    // Install crash dialog handler first — catches all uncaught exceptions
    CrashDialog.install()

    // Instrumentación persistente (pedido explícito de devgodot para poder
    // encontrar la causa raíz de los Problemas 1/2/4, que todavía no tienen
    // evidencia concluyente). Se instala lo primero posible, antes de
    // Koin/VLC/Sentry/nada, para no perderse ni un segundo del arranque.
    // Agrega un LogWriter de archivo a Kermit y arranca el censo de procesos
    // + sampler de CPU en un scope propio de vida larga (no atado a la
    // composición, que todavía ni existe en este punto).
    val auditScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.Default)
    // OPTIMIZACIÓN (v2.3): la instrumentación detallada (logs Debug a disco +
    // samplers de CPU/procesos) ahora solo corre en modo diagnóstico. En uso
    // normal se guardan solo Warn/Error. Ver DiagnosticsConfig.
    val diagnosticsActive = com.wavora.app.diagnostics.DiagnosticsConfig.resolve(args)
    co.touchlab.kermit.Logger.setMinSeverity(
        if (diagnosticsActive) co.touchlab.kermit.Severity.Debug else co.touchlab.kermit.Severity.Warn,
    )
    try {
        // VERIFICAR al compilar: Logger.setLogWriters(vararg LogWriter) es la
        // API pública estable de kermit 2.1.0 para reemplazar los writers
        // activos - no se pudo compilar en el entorno de auditoría (sin
        // Windows/GUI) para confirmarlo con el IDE. Nota: esto REEMPLAZA el
        // writer de consola por defecto, no lo suma - si el IDE ofrece un
        // método para agregar en vez de reemplazar (p. ej. addLogWriter),
        // usar ese en su lugar para no perder la salida a consola en modo
        // desarrollo.
        co.touchlab.kermit.Logger.setLogWriters(com.wavora.app.diagnostics.AuditFileLogWriter())
        Logger.d(TAG, "===== Log de archivo instalado en ${com.wavora.app.diagnostics.AuditFileLogWriter.logFile.absolutePath} =====")
    } catch (e: Throwable) {
        System.err.println("No se pudo instalar el file log writer: ${e.message}")
    }

    // AUDIT INSTRUMENTATION (pedido explícito - NO es un fix, solo evidencia):
    // antes de decidir si la causa raíz de los Wavora.exe hijos ejecutando la
    // app completa es (1) KCEF los relanza con flags de subproceso de CEF
    // (--type=renderer, --type=gpu-process, etc.) que este main() nunca revisa,
    // o (2) alguna otra cosa los relanza sin ningún flag de CEF de por medio,
    // logueamos todo lo que el proceso puede ver de sí mismo apenas arranca -
    // antes de Koin/VLC/KCEF, para no perdernos nada. Se pone inmediatamente
    // después de instalar el log writer (necesario para que esto se escriba a
    // disco), no antes.
    try {
        val handle = ProcessHandle.current()
        val info = handle.info()
        val pid = handle.pid()
        val ppid = handle.parent().map { it.pid() }.orElse(-1L)
        val execPath = info.command().orElse("(no disponible)")
        val fullCommandLine = info.commandLine().orElse("(no disponible)")
        val jvmArguments = info.arguments().map { it.joinToString(" ") }.orElse("(no disponible)")
        val mainArgs = args.joinToString(" ")
        val allArgsForFlagCheck = "$fullCommandLine $jvmArguments $mainArgs"
        val cefFlagsToCheck = listOf(
            "--type=", "--no-zygote", "--lang", "--no-sandbox", "--field-trial-handle",
            "--enable-features", "--disable-features", "--utility-sub-type",
            "--service-sandbox-type", "--renderer-client-id", "--channel=",
        )
        val cefFlagsFound = cefFlagsToCheck.filter { allArgsForFlagCheck.contains(it) }
        Logger.d(TAG, "===== DIAGNOSTICO ARRANQUE DE PROCESO - PUNTO 2: comienzo de runDesktopApp() =====")
        Logger.d(TAG, "PID=$pid PPID=$ppid")
        Logger.d(TAG, "Ejecutable (ProcessHandle.info().command())=$execPath")
        Logger.d(TAG, "Command line completa (ProcessHandle.info().commandLine())=$fullCommandLine")
        Logger.d(TAG, "Arguments (ProcessHandle.info().arguments())=$jvmArguments")
        Logger.d(TAG, "args recibidos por main()/runDesktopApp()=$mainArgs")
        Logger.d(TAG, "Flags de CEF/Chromium detectados=${if (cefFlagsFound.isEmpty()) "NINGUNO" else cefFlagsFound.joinToString(", ")}")
        Logger.d(TAG, "===== FIN DIAGNOSTICO ARRANQUE DE PROCESO =====")
    } catch (e: Throwable) {
        System.err.println("No se pudo loguear el diagnostico de arranque de proceso: ${e.message}")
    }
    if (diagnosticsActive) {
        com.wavora.app.diagnostics.ProcessCensus.snapshot(label = "arranque (antes de Koin)")
        com.wavora.app.diagnostics.ProcessCensus.startPeriodicSnapshots(auditScope)
        com.wavora.app.diagnostics.CpuThreadSampler.startSampling(auditScope)
    }

    System.setProperty("compose.swing.render.on.graphics", "true")
    System.setProperty("compose.interop.blending", "true")
    // WAVORA FIX: COMPONENT layers cause invisible/blank windows on Windows 10/11
    // with integrated Intel, older AMD, and some NVIDIA GPUs. SWING layers +
    // SOFTWARE rendering bypass all GPU driver issues entirely.
    val isWindows = System.getProperty("os.name", "").contains("Windows", ignoreCase = true)
    if (isWindows) {
        System.setProperty("compose.layers.type", "SWING")
        // v2.3: ya no se fuerza SOFTWARE para todos. RenderConfig decide
        // (ajuste del usuario, historial de fallos y placa de video) y setea
        // skiko.renderApi en DIRECT3D o SOFTWARE. Ante cualquier duda: SOFTWARE.
        com.wavora.app.render.RenderConfig.decideAndApply(args)
    } else {
        System.setProperty("compose.layers.type", "COMPONENT")
    }

    // Handle deep link URIs
    // macOS: receives URI via Desktop open URI handler (app already running or launched via scheme)
    // Windows/Linux: receives URI as command-line argument
    val isMacOS = System.getProperty("os.name", "").contains("Mac", ignoreCase = true)
    if (isMacOS && java.awt.Desktop.isDesktopSupported()) {
        try {
            java.awt.Desktop.getDesktop().setOpenURIHandler { event ->
                DesktopDeepLinkHandler.onNewUri(event.uri.toString())
            }
        } catch (_: UnsupportedOperationException) {
            // Shouldn't happen on macOS, but handle gracefully
        }
    }
    // Handle URI passed as command-line argument (Windows/Linux, or explicit invocation)
    // Note: macOS does NOT pass URI as args — it uses Apple Events via setOpenURIHandler
    val deepLinkArg =
        args.firstOrNull()?.takeIf { arg ->
            arg.startsWith("wavora://") || arg.startsWith("http://") || arg.startsWith("https://")
        }
    // Single-instance guard — MUST run before startKoin. The DataStore Koin
    // singleton is `createdAtStart`, so a second Windows instance would touch
    // ~/.wavora/settings.preferences_pb and crash with an "Unable to rename
    // ...tmp" IOException (#2044) before it ever reached the old in-Compose check.
    // Bail out here, before Koin/DataStore initialize.
    val isSingleInstance =
        SingleInstanceManager.isSingleInstance(
            onRestoreRequest = { DesktopRestoreSignal.request() },
        )
    if (!isSingleInstance) {
        // Second instance: forward the deep link (if any) to the running instance,
        // then exit. Nothing has touched the DataStore file yet.
        deepLinkArg?.let { DesktopDeepLinkHandler.writePendingUri(it) }
        return
    }

    // First instance only: deliver our own deep link (non-macOS passes URI via args).
    // Safe to call even before Koin/SharedViewModel exist: DesktopDeepLinkHandler
    // caches the URI internally until `listener` is assigned (see below).
    if (!isMacOS) {
        deepLinkArg?.let { DesktopDeepLinkHandler.onNewUri(it) }
    }

    // WAVORA STARTUP+SPLASH FIX (Objetivos 1 y 2): todo lo que antes corría acá,
    // de forma síncrona y bloqueante — `startKoin` (que construye Room, todos los
    // repos `createdAtStart`, y el `MediaPlayerHandler` que a su vez carga las
    // librerías nativas de VLC), la lectura de idioma, Sentry, el registro del
    // protocolo de Windows y la resolución de `SharedViewModel` — se movió DENTRO
    // de `application { }`, a un coroutine en `Dispatchers.Default`. Mientras ese
    // coroutine corre, se muestra el Splash Screen (`SplashScreen.kt`); apenas
    // termina, se reemplaza por la ventana principal. No hay ningún timer ni
    // delay artificial: el splash desaparece exactamente cuando `appReady` deja
    // de ser null, es decir, cuando el init real terminó.
    //
    // El orden relativo de cada paso es idéntico al que había antes (Koin →
    // idioma → Sentry → MediaPlayerHandler → protocolo Windows → SharedViewModel
    // → deep link listener); lo único que cambió es que ahora corre en un hilo
    // de fondo en vez de bloquear el hilo que lanza la UI.
    // AUDIT INSTRUMENTATION - PUNTO 3 (pedido explícito, solo evidencia): si un
    // proceso que debería ser un subproceso liviano de CEF llega hasta acá,
    // está a punto de crear una ventana Compose Desktop completa (Skia/AWT) -
    // relevante para explicar el crash "SkiaLayer is disposed" reportado al
    // cerrar, si el proceso muere mientras esto está en marcha.
    Logger.d(TAG, "===== DIAGNOSTICO ARRANQUE DE PROCESO - PUNTO 3: justo antes de application{} / crear ventana principal =====")
    application {
        var appReady by remember { mutableStateOf<AppReady?>(null) }

        // FIX (Problema 1 - "el splash queda atrapado, ni la X ni minimizar
        // funcionan"): antes onCloseRequest era {} incondicional y
        // alwaysOnTop=true no distinguía "arranque normal, todavía corriendo"
        // de "algo se colgó, el usuario necesita poder salir". Ahora, si el
        // arranque real (LaunchedEffect de arriba) no terminó en
        // SPLASH_FORCE_CLOSE_TIMEOUT_MS, canForceClose pasa a true: recién
        // ahí la X cierra la app de verdad y se suelta alwaysOnTop (para que
        // minimizar funcione). Antes de ese timeout el comportamiento es
        // IDÉNTICO al de antes — no cambia nada del arranque normal, que hoy
        // tarda muchísimo menos que este umbral.
        var canForceClose by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(SPLASH_FORCE_CLOSE_TIMEOUT_MS)
            if (appReady == null) {
                Logger.w(
                    TAG,
                    "===== Splash: arranque supera ${SPLASH_FORCE_CLOSE_TIMEOUT_MS}ms sin terminar - " +
                        "habilitando cierre manual (X / minimizar) =====",
                )
                canForceClose = true
            }
        }

        LaunchedEffect(Unit) {
            com.wavora.app.diagnostics.StartupTiming.begin("LaunchedEffect total (splash -> appReady)")
            val ready =
                withContext(Dispatchers.Default) {
                    // AUDIT FIX (crash "Unable to rename ...settings.preferences_pb.tmp":
                    // reproducido por el usuario UNA sola vez, justo en el primer arranque
                    // después de que el instalador de MSIX pisara 2.1.2 -> 2.2.0, y NUNCA en
                    // un arranque normal - el segundo intento manual anduvo bien sin tocar
                    // nada). Encaja con una condición transitoria propia de Windows: el
                    // proceso de la versión vieja vive en OTRA carpeta versionada de
                    // WindowsApps, pero comparte el mismo ~/.wavora/settings.preferences_pb
                    // con la nueva, y puede seguir soltando su handle del archivo justo
                    // cuando la nueva versión se autolanza tras instalarse. A diferencia de
                    // Linux/macOS, Windows no permite renombrar un archivo mientras otro
                    // proceso lo tiene abierto - de ahí el IOException. En vez de crashear
                    // de una, reintentamos el arranque completo hasta 2 veces con una
                    // pequeña espera: si el handle viejo se libera en ese lapso (que es
                    // justo lo que pasó acá), la app arranca normal sin que el usuario note
                    // nada. Cualquier otro error (no esta condición puntual, o ya sin
                    // reintentos) se comporta exactamente igual que antes: se loguea y se
                    // relanza para terminar en CrashDialog.
                    var lastError: Throwable? = null
                    var result: AppReady? = null
                    for (attempt in 1..STARTUP_RETRY_MAX_ATTEMPTS) {
                    try {
                        startupStage.value = StartupStage.LIBRARY
                        // Initialize Koin ONCE before application starts
                        com.wavora.app.diagnostics.StartupTiming.begin("startKoin + loadAllModules")
                        startKoin {
                            loadAllModules()
                            loadKoinModules(viewModelModule)
                        }
                        com.wavora.app.diagnostics.StartupTiming.end("startKoin + loadAllModules")

                        com.wavora.app.diagnostics.StartupTiming.begin("language + VersionManager + Sentry")
                        val language =
                            getKoin()
                                .get<DataStoreManager>()
                                .language
                                .first()
                                .substring(0..1)
                        changeLanguageNative(language)

                        VersionManager.initialize()
                        if (BuildKonfig.sentryDsn.isNotEmpty()) {
                            Sentry.init { options ->
                                options.dsn = BuildKonfig.sentryDsn
                                options.release = "wavora-desktop@${VersionManager.getVersionName()}"
                                options.setDiagnosticLevel(SentryLevel.ERROR)
                            }
                        }
                        // AUDIT NOTE: mismo criterio que Sentry arriba - credenciales DE LA
                        // APP horneadas en el build (ver composeApp/build.gradle.kts), no
                        // algo que configure cada usuario. Sin esto, LastFmApi.isConfigured
                        // queda false y la sección de Last.fm en Ajustes avisa que no está
                        // configurado, sin romper nada más.
                        org.app.lyrics.scrobbler.LastFmApi.configure(BuildKonfig.lastFmApiKey, BuildKonfig.lastFmApiSecret)
                        com.wavora.app.diagnostics.StartupTiming.end("language + VersionManager + Sentry")

                        startupStage.value = StartupStage.PLAYER
                        com.wavora.app.diagnostics.StartupTiming.begin("MediaPlayerHandler (Koin get + VLC init)")
                        val mediaPlayerHandler = getKoin().get<MediaPlayerHandler>()
                        com.wavora.app.diagnostics.StartupTiming.end("MediaPlayerHandler (Koin get + VLC init)")
                        mediaPlayerHandler.showToast = { type ->
                            showToast(
                                when (type) {
                                    ToastType.ExplicitContent -> {
                                        runBlocking { getString(Res.string.explicit_content_blocked) }
                                    }

                                    is ToastType.PlayerError -> {
                                        runBlocking {
                                            getString(
                                                Res.string.time_out_check_internet_connection_or_change_piped_instance_in_settings,
                                                type.error,
                                            )
                                        }
                                    }
                                },
                            )
                        }
                        mediaPlayerHandler.pushPlayerError = { error ->
                            Sentry.withScope { scope ->
                                Sentry.captureMessage("Player Error: ${error.message}, code: ${error.errorCode}, code name: ${error.errorCodeName}")
                            }
                            getKoin().get<PlayerSessionAdapter>().reportError(error)
                        }

                        // Register wavora:// protocol handler on Windows (HKCU, no admin needed)
                        com.wavora.app.diagnostics.StartupTiming.begin("WindowsProtocolRegistrar.register()")
                        WindowsProtocolRegistrar.register()
                        com.wavora.app.diagnostics.StartupTiming.end("WindowsProtocolRegistrar.register()")

                        com.wavora.app.diagnostics.StartupTiming.begin("SharedViewModel (Koin get + checkForUpdateIfEnabled)")
                        val sharedViewModel = getKoin().get<SharedViewModel>()
                        sharedViewModel.checkForUpdateIfEnabled()
                        com.wavora.app.diagnostics.StartupTiming.end("SharedViewModel (Koin get + checkForUpdateIfEnabled)")

                        // Connect deep link handler to SharedViewModel
                        DesktopDeepLinkHandler.listener = { intent ->
                            sharedViewModel.setIntent(intent)
                        }

                        result = AppReady(mediaPlayerHandler, sharedViewModel)
                        break
                    } catch (e: Throwable) {
                        lastError = e
                        // Deshacemos el Koin a medio inicializar antes de reintentar: si no,
                        // el segundo startKoin{} de arriba tira "A Koin Application has
                        // already been started" en vez de darle una segunda oportunidad real.
                        runCatching { org.koin.core.context.stopKoin() }
                        val isTransientDataStoreRace =
                            e is java.io.IOException &&
                                e.message?.contains("multiple instances of DataStore", ignoreCase = true) == true
                        if (isTransientDataStoreRace && attempt < STARTUP_RETRY_MAX_ATTEMPTS) {
                            Logger.w(
                                TAG,
                                "Arranque intento $attempt falló por una carrera transitoria de " +
                                    "DataStore (probable proceso viejo de un update de MSIX " +
                                    "todavía cerrando) - reintentando en ${STARTUP_RETRY_DELAY_MS}ms: ${e.message}",
                            )
                            kotlinx.coroutines.delay(STARTUP_RETRY_DELAY_MS)
                        } else {
                            // No es el caso conocido de arriba, o ya se acabaron los
                            // reintentos - mismo comportamiento que había antes de este fix:
                            // no tragamos el error silenciosamente, lo logueamos y lo
                            // relanzamos para que termine en CrashDialog en vez de dejar el
                            // splash colgado para siempre en silencio.
                            Logger.e(TAG, "Fallo durante la inicialización de la app: ${e.message}", e)
                            throw e
                        }
                    }
                    }
                    result ?: throw (lastError ?: IllegalStateException("Startup terminó sin resultado y sin error"))
                }
            com.wavora.app.diagnostics.StartupTiming.end("LaunchedEffect total (splash -> appReady)")
            startupStage.value = StartupStage.READY
            appReady = ready
        }

        // WAVORA APERTURA (morph): ya no hay dos ventanas (splash cuadrado que
        // muere y recién después la principal). Hay UNA sola ventana, creada desde
        // el primer frame con su tamaño/posición finales y transparente: mientras
        // arranca solo se ve el splash centrado; cuando el arranque termina, la
        // UI real se compone debajo y la ventana se "abre" desde el blob. Sin
        // hueco en pantalla y sin que todo aparezca de golpe.
        WavoraMainWindow(
            appReady = appReady,
            canForceClose = canForceClose,
        )
    }
}

/** Intentos (cada ~1,5 s) para verificar que la ventana se ve con Direct3D. */
private const val RENDER_CHECK_MAX_ATTEMPTS = 20

/** Ejecuta la acción de un atajo de teclado (ver input/KeyboardShortcuts.kt). */
private fun runShortcut(
    action: com.wavora.app.input.KeyboardShortcuts.Action,
    sharedViewModel: SharedViewModel,
) {
    when (action) {
        com.wavora.app.input.KeyboardShortcuts.Action.PLAY_PAUSE ->
            sharedViewModel.onUIEvent(com.wavora.app.viewModel.UIEvent.PlayPause)
        com.wavora.app.input.KeyboardShortcuts.Action.NEXT ->
            sharedViewModel.onUIEvent(com.wavora.app.viewModel.UIEvent.Next)
        com.wavora.app.input.KeyboardShortcuts.Action.PREVIOUS ->
            sharedViewModel.onUIEvent(com.wavora.app.viewModel.UIEvent.Previous)
        com.wavora.app.input.KeyboardShortcuts.Action.VOLUME_UP,
        com.wavora.app.input.KeyboardShortcuts.Action.VOLUME_DOWN -> {
            val step = if (action == com.wavora.app.input.KeyboardShortcuts.Action.VOLUME_UP) 0.05f else -0.05f
            val current = sharedViewModel.controllerState.value.volume
            sharedViewModel.onUIEvent(com.wavora.app.viewModel.UIEvent.UpdateVolume((current + step).coerceIn(0f, 1f)))
        }
        com.wavora.app.input.KeyboardShortcuts.Action.LIKE ->
            sharedViewModel.onUIEvent(com.wavora.app.viewModel.UIEvent.ToggleLike)
        com.wavora.app.input.KeyboardShortcuts.Action.MINI_PLAYER ->
            MiniPlayerManager.isOpen = !MiniPlayerManager.isOpen
    }
}

/** Duración de la apertura (el blob se vuelve ventana). */
private const val REVEAL_DURATION_MS = 760

/** Diámetro del blob desde el que se abre la ventana. */
private val REVEAL_START_SIZE = 260.dp

// FIX (Problema 1). 30s es generoso frente a lo que tarda un arranque normal
// (segundos, según los propios StartupTiming agregados arriba) — solo se
// activa si algo realmente se colgó.
private const val SPLASH_FORCE_CLOSE_TIMEOUT_MS = 30_000L

// AUDIT FIX (carrera transitoria de DataStore post-update MSIX): ver comentario
// junto al retry loop en LaunchedEffect, más arriba en este archivo.
private const val STARTUP_RETRY_MAX_ATTEMPTS = 3
private const val STARTUP_RETRY_DELAY_MS = 1500L

/**
 * Calcula la posición para que una ventana de [windowSize] quede centrada en
 * el monitor donde el usuario realmente abrió la aplicación — no en un
 * "monitor por defecto" fijo, y no simplemente centrada respecto de sí misma.
 * Se usa la posición actual del mouse para determinar el `GraphicsDevice`
 * activo (igual que hacen los launchers tipo Spotify/Discord), con fallback
 * al monitor por defecto si por algún motivo no se puede leer el puntero.
 */
private fun centeredOnActiveScreen(windowSize: DpSize): androidx.compose.ui.window.WindowPosition {
    return try {
        val ge = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
        val pointerLocation = java.awt.MouseInfo.getPointerInfo()?.location
        val targetDevice =
            if (pointerLocation != null) {
                ge.screenDevices.firstOrNull { device ->
                    device.defaultConfiguration.bounds.contains(pointerLocation)
                } ?: ge.defaultScreenDevice
            } else {
                ge.defaultScreenDevice
            }
        val bounds = targetDevice.defaultConfiguration.bounds
        // bounds están en px físicos de AWT; convertimos el tamaño de la ventana
        // (dp) a la misma unidad usando el scale factor de esa pantalla, para que
        // el centrado sea exacto también en monitores con distinto DPI.
        val scale = targetDevice.defaultConfiguration.defaultTransform.scaleX
        val windowWidthPx = (windowSize.width.value * scale).toInt()
        val windowHeightPx = (windowSize.height.value * scale).toInt()
        val centerX = bounds.x + (bounds.width - windowWidthPx) / 2
        val centerY = bounds.y + (bounds.height - windowHeightPx) / 2
        androidx.compose.ui.window.WindowPosition(
            x = (centerX / scale).toFloat().dp,
            y = (centerY / scale).toFloat().dp,
        )
    } catch (e: Throwable) {
        // Si por lo que sea no se puede leer la config gráfica (headless, etc.),
        // dejamos que el SO decida en vez de crashear el arranque.
        Logger.e(TAG, "No se pudo centrar la ventana en el monitor activo: ${e.message}", e)
        androidx.compose.ui.window.WindowPosition.PlatformDefault
    }
}

/**
 * Ventana principal + splash + tray + mini player.
 *
 * Mientras [appReady] es null la ventana ya existe (con su tamaño/posición
 * finales) pero es transparente y solo muestra el [SplashScreen] centrado.
 * Cuando el arranque termina:
 *  1. se compone la UI real (App) debajo del splash, todavía recortada al blob;
 *  2. se esperan dos frames para que ese primer frame pesado ya esté pintado;
 *  3. la ventana se abre desde el blob ([RevealShape]) mientras el splash se
 *     expande/desvanece y el logo vuela al punto de la barra de título.
 *
 * El tray y el mini player solo existen una vez que la app está lista, igual
 * que antes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@androidx.compose.runtime.Composable
private fun androidx.compose.ui.window.ApplicationScope.WavoraMainWindow(
    appReady: AppReady?,
    canForceClose: Boolean,
) {
    // Main Window - restore saved size, position and placement
    val windowPrefs = remember { java.util.prefs.Preferences.userRoot().node("Wavora/MainWindow") }
    val windowState =
        remember {
            val size =
                DpSize(
                    windowPrefs.getFloat("width", 1280f).dp,
                    windowPrefs.getFloat("height", 780f).dp,
                )
            val wasMaximized = windowPrefs.getBoolean("maximized", false)
            androidx.compose.ui.window.WindowState(
                placement =
                    if (wasMaximized) {
                        androidx.compose.ui.window.WindowPlacement.Maximized
                    } else {
                        androidx.compose.ui.window.WindowPlacement.Floating
                    },
                // FIX: antes x/y se leían pero nunca se usaban.
                position = restoredWindowPosition(windowPrefs, size) ?: centeredOnActiveScreen(size),
                size = size,
            )
        }
    val awtWindowRef = remember { java.util.concurrent.atomic.AtomicReference<java.awt.Window?>(null) }

    // FIX: antes el tamaño solo se guardaba en el onCloseRequest del sistema, que
    // con ventana sin bordes casi nunca se dispara (la X propia de CustomTitleBar
    // solo ocultaba). Ahora se guarda en todos los caminos de cierre/ocultado.
    fun saveWindowPrefs() {
        try {
            if (windowState.placement == androidx.compose.ui.window.WindowPlacement.Floating) {
                windowPrefs.putFloat("width", windowState.size.width.value)
                windowPrefs.putFloat("height", windowState.size.height.value)
                val awtWindow = awtWindowRef.get()
                // Minimizada, Windows reporta x/y = -32000: no guardar eso.
                if (awtWindow != null && !windowState.isMinimized) {
                    windowPrefs.putInt("x", awtWindow.x)
                    windowPrefs.putInt("y", awtWindow.y)
                }
                windowPrefs.putBoolean("maximized", false)
            } else {
                windowPrefs.putBoolean("maximized", true)
            }
            windowPrefs.flush()
        } catch (e: Exception) {
            Logger.e(TAG, "No se pudo guardar el tamaño/posición de la ventana: ${e.message}", e)
        }
    }

    var isVisible by remember { mutableStateOf(true) }
    // The single-instance guard now runs before startKoin (top of
    // runDesktopApp). Here we only react to a restore request raised when a
    // second instance launches: bring the window back to the foreground and
    // consume any deep link the second instance forwarded.
    LaunchedEffect(Unit) {
        DesktopRestoreSignal.requests.collect {
            isVisible = true
            windowState.isMinimized = false
            DesktopDeepLinkHandler.consumePendingUri()
        }
    }

    // Cierre ordenado (bandeja, reinicio por ajustes, diálogo de modo compatible).
    fun quitApp() {
        saveWindowPrefs()
        com.wavora.app.input.GlobalMediaKeys.stop()
        appReady?.mediaPlayerHandler?.release()
        exitApplication()
    }
    androidx.compose.runtime.SideEffect {
        com.wavora.app.render.AppRestart.exitHandler = { quitApp() }
    }

    // Teclas multimedia globales (Windows): funcionan con la app minimizada o en la bandeja.
    LaunchedEffect(appReady) {
        val vm = appReady?.sharedViewModel ?: return@LaunchedEffect
        com.wavora.app.input.GlobalMediaKeys.start { action ->
            vm.onUIEvent(
                when (action) {
                    com.wavora.app.input.GlobalMediaKeys.Action.PLAY_PAUSE -> com.wavora.app.viewModel.UIEvent.PlayPause
                    com.wavora.app.input.GlobalMediaKeys.Action.NEXT -> com.wavora.app.viewModel.UIEvent.Next
                    com.wavora.app.input.GlobalMediaKeys.Action.PREVIOUS -> com.wavora.app.viewModel.UIEvent.Previous
                    com.wavora.app.input.GlobalMediaKeys.Action.STOP -> com.wavora.app.viewModel.UIEvent.Stop
                },
            )
        }
    }

    // Si el arranque anterior desactivó sola la aceleración, se avisa una vez.
    val fallbackNotice = com.wavora.app.render.RenderConfig.fallbackNotice
    if (appReady != null && fallbackNotice != null) {
        val noticeText = stringResource(Res.string.hw_accel_auto_disabled_toast)
        LaunchedEffect(Unit) {
            showToast(noticeText)
            com.wavora.app.render.RenderConfig.clearNotice()
        }
    }

    if (appReady != null) {
        val openAppString = stringResource(Res.string.open_app)
        val quitAppString = stringResource(Res.string.quit_app)
        val openMiniPlayer = stringResource(Res.string.open_miniplayer)
        val closeMiniPlayer = stringResource(Res.string.close_miniplayer)
        val compatModeString = stringResource(Res.string.tray_compat_mode)
        Tray(
            icon = painterResource(Res.drawable.circle_app_icon),
            tooltip = stringResource(Res.string.app_name),
            primaryAction = {
                isVisible = true
                windowState.isMinimized = false
            },
        ) {
            if (!isVisible) {
                Item(openAppString) {
                    isVisible = true
                    windowState.isMinimized = false
                }
            }
            if (MiniPlayerManager.isOpen) {
                Item(closeMiniPlayer) {
                    MiniPlayerManager.isOpen = false
                }
            } else {
                Item(openMiniPlayer) {
                    MiniPlayerManager.isOpen = true
                }
            }
            if (com.wavora.app.render.RenderConfig.current?.api == com.wavora.app.render.RenderConfig.Api.DIRECT3D) {
                // Salida de emergencia: el menú de la bandeja es nativo y se ve
                // aunque la ventana haya quedado en blanco.
                Item(compatModeString) {
                    com.wavora.app.render.RenderConfig.mode = com.wavora.app.render.RenderConfig.Mode.SOFTWARE
                    com.wavora.app.render.AppRestart.restartNow()
                }
            }
            Divider()
            Item(quitAppString) {
                quitApp()
            }
        }
    }

    val stage by startupStage.collectAsState()
    val stageText =
        when (stage) {
            StartupStage.STARTING -> stringResource(Res.string.splash_stage_starting)
            StartupStage.LIBRARY -> stringResource(Res.string.splash_stage_library)
            StartupStage.PLAYER, StartupStage.READY -> stringResource(Res.string.splash_stage_player)
        }

    // WAVORA: Use undecorated frameless window on ALL platforms.
    // The invisible window bug on Windows is handled by SWING layers + the
    // render API chosen by RenderConfig (Direct3D with verified fallback to
    // SOFTWARE, see render/RenderConfig.kt). This gives us the custom title bar
    // with buttons on the right instead of the ugly white Windows frame.
    Window(
        onCloseRequest = {
            if (appReady == null) {
                // Mismo criterio que tenía la ventana de splash: durante un arranque
                // normal no se cierra; si se colgó (timeout), la X sale de la app.
                if (canForceClose) {
                    Logger.w(TAG, "===== Splash: usuario cerró manualmente tras timeout de arranque =====")
                    exitApplication()
                }
            } else {
                saveWindowPrefs()
                isVisible = false
            }
        },
        title = stringResource(Res.string.app_name),
        icon = painterResource(Res.drawable.circle_app_icon),
        undecorated = true,
        transparent = true,
        state = windowState,
        visible = isVisible,
    ) {
        androidx.compose.runtime.SideEffect { awtWindowRef.set(window) }

        // Atajos de teclado configurables (v2.3.1). Se escuchan a nivel AWT: el
        // onKeyEvent de Compose solo recibe teclas si algo de la UI tiene foco,
        // y por eso en la v2.3.0 no andaban. Ver input/KeyboardShortcuts.kt.
        val shortcutViewModel = appReady?.sharedViewModel
        androidx.compose.runtime.DisposableEffect(shortcutViewModel) {
            if (shortcutViewModel != null) {
                com.wavora.app.input.KeyboardShortcuts.install(window) { action ->
                    runShortcut(action, shortcutViewModel)
                }
            }
            onDispose { com.wavora.app.input.KeyboardShortcuts.uninstall() }
        }

        val reveal = remember { androidx.compose.animation.core.Animatable(0f) }
        val isReady = appReady != null
        LaunchedEffect(isReady) {
            if (isReady) {
                // Dejamos que App() componga y pinte su primer frame (el más caro)
                // todavía oculto bajo el splash, y recién ahí abrimos la ventana:
                // así la animación no arranca trabada.
                androidx.compose.runtime.withFrameNanos { }
                androidx.compose.runtime.withFrameNanos { }
                reveal.animateTo(
                    1f,
                    androidx.compose.animation.core.tween(
                        durationMillis = REVEAL_DURATION_MS,
                        easing = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f),
                    ),
                )
            }
        }
        val splashGone by remember { androidx.compose.runtime.derivedStateOf { reveal.value >= 1f } }

        // Verificación de la aceleración por hardware (solo si este arranque usa
        // Direct3D): ¿la ventana realmente se ve? Ver render/WindowRenderCheck.kt.
        LaunchedEffect(splashGone) {
            if (!splashGone || !com.wavora.app.render.RenderConfig.awaitingConfirmation) return@LaunchedEffect
            val awtWindow = window
            var confirmed = false
            var failures = 0
            for (attempt in 1..RENDER_CHECK_MAX_ATTEMPTS) {
                kotlinx.coroutines.delay(if (attempt == 1) 800L else 1500L)
                val result =
                    withContext(Dispatchers.IO) { com.wavora.app.render.WindowRenderCheck.check(awtWindow) }
                if (result == true) {
                    confirmed = true
                    break
                }
                if (result == false) {
                    failures++
                    // Dos lecturas seguidas sin ver la app = falla real, no un parpadeo.
                    if (failures >= 2) break
                } else {
                    failures = 0
                }
            }
            if (failures >= 2) {
                com.wavora.app.render.RenderConfig.reportHardwareFailure("la ventana no se veía (pantalla en blanco)")
                com.wavora.app.render.WindowRenderCheck.showFallbackDialog {
                    com.wavora.app.render.AppRestart.exitHandler?.invoke()
                }
            } else {
                // Confirmada, o no se pudo verificar (sin foco todo el rato): se
                // da por buena. Quedan las salidas de emergencia (bandeja, Ajustes,
                // --software-render) por si acaso.
                if (!confirmed) Logger.w(TAG, "No se pudo verificar la ventana con Direct3D; se asume que se ve")
                com.wavora.app.render.RenderConfig.confirmHardwareWorks(
                    runCatching { awtWindow.renderApi.name }.getOrNull(),
                )
            }
        }
        val density = androidx.compose.ui.platform.LocalDensity.current
        val windowSizePx = remember { java.util.concurrent.atomic.AtomicReference(androidx.compose.ui.unit.IntSize.Zero) }

        androidx.compose.foundation.layout.Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { windowSizePx.set(it) },
            contentAlignment = androidx.compose.ui.Alignment.Center,
        ) {
            if (appReady != null) {
                val glowColor = com.wavora.app.ui.theme.wavoraPrimary
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                clip = true
                                shape = RevealShape(reveal.value, REVEAL_START_SIZE, 12.dp)
                            }.drawWithContent {
                                drawContent()
                                // Brillo violeta del blob que se apaga mientras la
                                // ventana se abre: une visualmente splash y app.
                                val glow = 1f - (reveal.value / 0.6f).coerceIn(0f, 1f)
                                if (glow > 0f) {
                                    drawRect(
                                        brush =
                                            androidx.compose.ui.graphics.Brush.radialGradient(
                                                colors =
                                                    listOf(
                                                        glowColor.copy(alpha = 0.55f * glow),
                                                        glowColor.copy(alpha = 0f),
                                                    ),
                                                center = center,
                                                radius = size.minDimension * 0.6f,
                                            ),
                                    )
                                }
                            },
                ) {
                    CustomTitleBar(
                        title = stringResource(Res.string.app_name),
                        windowState = windowState,
                        window = window,
                        onCloseRequest = {
                            saveWindowPrefs()
                            isVisible = false
                        },
                        modifier =
                            Modifier.graphicsLayer {
                                val a = ((reveal.value - 0.45f) / 0.4f).coerceIn(0f, 1f)
                                alpha = a
                                translationY = (1f - a) * -6.dp.toPx()
                            },
                    )

                    val context = LocalPlatformContext.current
                    setSingletonImageLoaderFactory {
                        ImageLoader
                            .Builder(context)
                            .components {
                                add(
                                    OkHttpNetworkFetcherFactory(
                                        // A single shared OkHttpClient with an explicit
                                        // connection pool instead of OkHttp's bare defaults.
                                        callFactory = {
                                            OkHttpClient.Builder()
                                                .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
                                                .build()
                                        },
                                    ),
                                )
                            }.diskCachePolicy(CachePolicy.ENABLED)
                            .networkCachePolicy(CachePolicy.ENABLED)
                            .diskCache(
                                DiskCache
                                    .Builder()
                                    .directory(FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "image_cache")
                                    .maxSizeBytes(512L * 1024 * 1024)
                                    .build(),
                            ).crossfade(true)
                            .build()
                    }
                    Column(
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .graphicsLayer {
                                    val a = ((reveal.value - 0.15f) / 0.5f).coerceIn(0f, 1f)
                                    alpha = a
                                    val s = 0.985f + 0.015f * a
                                    scaleX = s
                                    scaleY = s
                                },
                    ) {
                        App()
                        ToastHost()
                    }
                }
            }

            if (!splashGone) {
                SplashScreen(
                    exitProgress = { reveal.value },
                    logoTargetOffset = {
                        // Punto de color de CustomTitleBar: 16dp de padding + 3dp de
                        // radio en X, mitad de los 44dp de alto en Y.
                        val size = windowSizePx.get()
                        with(density) {
                            androidx.compose.ui.geometry.Offset(
                                19.dp.toPx() - size.width / 2f,
                                22.dp.toPx() - size.height / 2f,
                            )
                        }
                    },
                    statusText = stageText,
                    loadProgress = stage.progress,
                )
            }
        }
    }

    // Mini Player Window (separate window)
    if (appReady != null && MiniPlayerManager.isOpen) {
        MiniPlayerWindow(
            sharedViewModel = appReady.sharedViewModel,
            onCloseRequest = {
                MiniPlayerManager.isOpen = false
            },
        )
    }
}

/**
 * Recorte de la ventana durante la apertura: arranca como un círculo de
 * [startSize] en el centro (el blob del splash) y crece hasta ocupar toda la
 * ventana con esquinas de [endRadius] — el mismo 12dp que ya usaba la ventana.
 * En progress = 1 es idéntico al `clip(RoundedCornerShape(12.dp))` anterior.
 */
private class RevealShape(
    private val progress: Float,
    private val startSize: androidx.compose.ui.unit.Dp,
    private val endRadius: androidx.compose.ui.unit.Dp,
) : androidx.compose.ui.graphics.Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        density: androidx.compose.ui.unit.Density,
    ): androidx.compose.ui.graphics.Outline {
        val p = progress.coerceIn(0f, 1f)
        val start = with(density) { startSize.toPx() }.coerceAtMost(minOf(size.width, size.height))
        val end = with(density) { endRadius.toPx() }
        val w = start + (size.width - start) * p
        val h = start + (size.height - start) * p
        val left = (size.width - w) / 2f
        val top = (size.height - h) / 2f
        val r = start / 2f + (end - start / 2f) * p
        return androidx.compose.ui.graphics.Outline.Rounded(
            androidx.compose.ui.geometry.RoundRect(
                left = left,
                top = top,
                right = left + w,
                bottom = top + h,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(r),
            ),
        )
    }
}

/**
 * Posición guardada de la ventana, solo si sigue cayendo en algún monitor
 * conectado (si desenchufaste el segundo monitor, no abrimos la ventana fuera
 * de la pantalla: devolvemos null y se centra en el monitor activo).
 */
private fun restoredWindowPosition(
    prefs: java.util.prefs.Preferences,
    size: DpSize,
): androidx.compose.ui.window.WindowPosition? {
    val x = prefs.getInt("x", Int.MIN_VALUE)
    val y = prefs.getInt("y", Int.MIN_VALUE)
    if (x == Int.MIN_VALUE || y == Int.MIN_VALUE) return null
    return try {
        val rect = java.awt.Rectangle(x, y, size.width.value.toInt(), size.height.value.toInt())
        val onScreen =
            java.awt.GraphicsEnvironment
                .getLocalGraphicsEnvironment()
                .screenDevices
                .any { device ->
                    val visible = device.defaultConfiguration.bounds.intersection(rect)
                    !visible.isEmpty && visible.width >= 120 && visible.height >= 60
                }
        if (onScreen) androidx.compose.ui.window.WindowPosition(x.dp, y.dp) else null
    } catch (e: Throwable) {
        null
    }
}

/**
 * Bridges a restore request from the single-instance guard (which runs outside
 * Compose, at the top of [runDesktopApp]) into the running window's composition.
 * The guard calls [request] when a second instance launches; the window collects
 * [requests] to bring itself back to the foreground and pick up any deep link.
 */
private object DesktopRestoreSignal {
    private val _requests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val requests: SharedFlow<Unit> = _requests.asSharedFlow()

    fun request() {
        _requests.tryEmit(Unit)
    }
}