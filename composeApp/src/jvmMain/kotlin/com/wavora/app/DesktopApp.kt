package com.wavora.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import wavora.composeapp.generated.resources.quit_app
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
    com.wavora.app.diagnostics.ProcessCensus.snapshot(label = "arranque (antes de Koin)")
    com.wavora.app.diagnostics.ProcessCensus.startPeriodicSnapshots(auditScope)
    com.wavora.app.diagnostics.CpuThreadSampler.startSampling(auditScope)

    System.setProperty("compose.swing.render.on.graphics", "true")
    System.setProperty("compose.interop.blending", "true")
    // WAVORA FIX: COMPONENT layers cause invisible/blank windows on Windows 10/11
    // with integrated Intel, older AMD, and some NVIDIA GPUs. SWING layers +
    // SOFTWARE rendering bypass all GPU driver issues entirely.
    val isWindows = System.getProperty("os.name", "").contains("Windows", ignoreCase = true)
    if (isWindows) {
        System.setProperty("compose.layers.type", "SWING")
        System.setProperty("skiko.renderApi", "SOFTWARE")
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
        // WAVORA SPLASH FIX (Objetivo 1, Problema 4): antes, apenas `appReady`
        // dejaba de ser null, `SplashWindow` se desmontaba en el mismo frame que
        // `MainAppWindow` aparecía — un corte seco. Ahora `SplashScreen` corre su
        // propia animación de salida (fade-out + scale-down, ~320ms) cuando
        // `visible = false`, y solo al terminar esa animación (`onExitFinished`)
        // marcamos `splashExited = true` para recién ahí desmontar la ventana de
        // splash y dejar la principal sola. La espera es sobre una animación real,
        // no un timer de arranque: el splash sigue desapareciendo exactamente
        // cuando el init real terminó, solo que ahora con una transición.
        var splashExited by remember { mutableStateOf(false) }

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
                    try {
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
                        com.wavora.app.diagnostics.StartupTiming.end("language + VersionManager + Sentry")

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

                        AppReady(mediaPlayerHandler, sharedViewModel)
                    } catch (e: Throwable) {
                        // No tragamos el error silenciosamente: antes, cualquier excepción acá
                        // tumbaba `main()` de entrada (y CrashDialog la mostraba). Ahora corre en
                        // un coroutine — la logueamos explícitamente y la relanzamos para que siga
                        // teniendo el mismo destino (CrashDialog / crash visible) en vez de dejar
                        // el splash colgado para siempre en silencio.
                        Logger.e(TAG, "Fallo durante la inicialización de la app: ${e.message}", e)
                        throw e
                    }
                }
            com.wavora.app.diagnostics.StartupTiming.end("LaunchedEffect total (splash -> appReady)")
            appReady = ready
        }

        val currentAppReady = appReady
        if (!splashExited) {
            SplashWindow(
                visible = currentAppReady == null,
                onExitFinished = { splashExited = true },
                canForceClose = canForceClose,
            )
        }
        if (currentAppReady != null && splashExited) {
            MainAppWindow(
                mediaPlayerHandler = currentAppReady.mediaPlayerHandler,
                sharedViewModel = currentAppReady.sharedViewModel,
            )
        }
    }
}

/**
 * Tamaño fijo de la ventana de splash (cuadrada, con margen para los halos).
 * +20% sobre el tamaño anterior (440dp -> 528dp) a pedido, en línea con el
 * logo (176dp -> 211dp) y el aura exterior del nuevo diseño de dos halos en
 * [SplashScreen].
 */
private val SPLASH_WINDOW_SIZE = 528.dp

// FIX (Problema 1). 30s es generoso frente a lo que tarda un arranque normal
// (segundos, según los propios StartupTiming agregados arriba) — solo se
// activa si algo realmente se colgó.
private const val SPLASH_FORCE_CLOSE_TIMEOUT_MS = 30_000L

/**
 * Calcula la posición para que una ventana de [windowSize] quede centrada en
 * el monitor donde el usuario realmente abrió la aplicación — no en un
 * "monitor por defecto" fijo, y no simplemente centrada respecto de sí misma.
 * Se usa la posición actual del mouse para determinar el `GraphicsDevice`
 * activo (igual que hacen los launchers tipo Spotify/Discord), con fallback
 * al monitor por defecto si por algún motivo no se puede leer el puntero.
 */
private fun centeredOnActiveScreen(windowSize: androidx.compose.ui.unit.Dp): androidx.compose.ui.window.WindowPosition {
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
        val windowSizePx = (windowSize.value * scale).toInt()
        val centerX = bounds.x + (bounds.width - windowSizePx) / 2
        val centerY = bounds.y + (bounds.height - windowSizePx) / 2
        androidx.compose.ui.window.WindowPosition(
            x = (centerX / scale).toFloat().dp,
            y = (centerY / scale).toFloat().dp,
        )
    } catch (e: Throwable) {
        // Si por lo que sea no se puede leer la config gráfica (headless, etc.),
        // dejamos que el SO decida en vez de crashear el arranque.
        Logger.e(TAG, "No se pudo centrar el splash en el monitor activo: ${e.message}", e)
        androidx.compose.ui.window.WindowPosition.PlatformDefault
    }
}

/**
 * Ventana de splash: sin decoración, cuadrada, centrada en el monitor donde el
 * usuario abrió la app. Su contenido (`SplashScreen`) corre su propia
 * animación de salida cuando `visible` pasa a `false`; recién cuando esa
 * animación termina se invoca [onExitFinished], que en [runDesktopApp] dispara
 * el desmonte real de esta ventana. No tiene ningún timer de arranque propio.
 *
 * @param canForceClose Cuando es `false` (arranque normal en curso), el
 *   comportamiento es el de siempre: sin botones de cierre nativos, siempre
 *   encima. Cuando pasa a `true` (arranque colgado más de
 *   [SPLASH_FORCE_CLOSE_TIMEOUT_MS], ver `runDesktopApp`), la X cierra la
 *   aplicación de verdad y se suelta `alwaysOnTop` para que minimizar
 *   funcione — así el usuario nunca queda atrapado sin poder salir.
 */
@androidx.compose.runtime.Composable
private fun androidx.compose.ui.window.ApplicationScope.SplashWindow(
    visible: Boolean,
    onExitFinished: () -> Unit,
    canForceClose: Boolean,
) {
    val windowState =
        remember {
            androidx.compose.ui.window.WindowState(
                size = DpSize(SPLASH_WINDOW_SIZE, SPLASH_WINDOW_SIZE),
                position = centeredOnActiveScreen(SPLASH_WINDOW_SIZE),
            )
        }
    Window(
        onCloseRequest = {
            if (canForceClose) {
                Logger.w(TAG, "===== Splash: usuario cerró manualmente tras timeout de arranque =====")
                exitApplication()
            }
        },
        title = "Wavora",
        undecorated = true,
        transparent = true,
        resizable = false,
        alwaysOnTop = !canForceClose,
        state = windowState,
    ) {
        SplashScreen(visible = visible, onExitFinished = onExitFinished)
    }
}

/**
 * Ventana principal + tray + mini player — idéntico al contenido que antes
 * vivía directamente dentro de `application { }`, ahora parametrizado por el
 * `mediaPlayerHandler`/`sharedViewModel` ya construidos (ver [AppReady]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@androidx.compose.runtime.Composable
private fun androidx.compose.ui.window.ApplicationScope.MainAppWindow(
    mediaPlayerHandler: MediaPlayerHandler,
    sharedViewModel: SharedViewModel,
) {
    // Main Window - restore saved size and position
    val windowPrefs = java.util.prefs.Preferences.userRoot().node("Wavora/MainWindow")
    val savedWidth = windowPrefs.getFloat("width", 1280f)
    val savedHeight = windowPrefs.getFloat("height", 780f)
    val savedX = windowPrefs.getInt("x", -1)
    val savedY = windowPrefs.getInt("y", -1)
    val wasMaximized = windowPrefs.getBoolean("maximized", false)

    val windowState =
        rememberWindowState(
            size = DpSize(savedWidth.dp, savedHeight.dp),
            placement = if (wasMaximized) androidx.compose.ui.window.WindowPlacement.Maximized
                        else androidx.compose.ui.window.WindowPlacement.Floating,
        )
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
    val openAppString = stringResource(Res.string.open_app)
    val quitAppString = stringResource(Res.string.quit_app)
    val openMiniPlayer = stringResource(Res.string.open_miniplayer)
    val closeMiniPlayer = stringResource(Res.string.close_miniplayer)
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
        Divider()
        Item(quitAppString) {
            mediaPlayerHandler.release()
            exitApplication()
        }
    }
    // WAVORA: Use undecorated frameless window on ALL platforms.
    // The invisible window bug on Windows is fixed by SWING layers + SOFTWARE
    // rendering set at startup. This gives us the custom Wavora title bar
    // with buttons on the right instead of the ugly white Windows frame.
    val isUseDecorated = remember { false }
    Window(
        onCloseRequest = {
            // Save window size and position for next launch
            if (windowState.placement == androidx.compose.ui.window.WindowPlacement.Floating) {
                windowPrefs.putFloat("width", windowState.size.width.value)
                windowPrefs.putFloat("height", windowState.size.height.value)
                windowPrefs.putBoolean("maximized", false)
            } else {
                windowPrefs.putBoolean("maximized", true)
            }
            windowPrefs.flush()
            isVisible = false
        },
        title = stringResource(Res.string.app_name),
        icon = painterResource(Res.drawable.circle_app_icon),
        undecorated = true,
        transparent = true,
        state = windowState,
        visible = isVisible,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp)),
        ) {
            if (!isUseDecorated) {
                CustomTitleBar(
                    title = stringResource(Res.string.app_name),
                    windowState = windowState,
                    window = window,
                    onCloseRequest = {
                        isVisible = false
                    },
                )
            }

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
            App()
            ToastHost()
        }
    }

    // Mini Player Window (separate window)
    if (MiniPlayerManager.isOpen) {
        MiniPlayerWindow(
            sharedViewModel = sharedViewModel,
            onCloseRequest = {
                MiniPlayerManager.isOpen = false
            },
        )
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