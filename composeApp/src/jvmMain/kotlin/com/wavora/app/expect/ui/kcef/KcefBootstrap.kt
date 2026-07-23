package com.wavora.app.expect.ui.kcef

import com.wavora.appdata.io.getHomeFolderPath
import com.wavora.logger.Logger
import dev.datlag.kcef.KCEF
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "KcefBootstrap"

/**
 * Lazily bootstraps KCEF (Kotlin CEF - a real embedded Chromium) so Desktop
 * can show a proper in-app WebView for logins that require a real browser
 * (YouTube Music today; Spotify potentially later - see the audit doc).
 *
 * WHY LAZY, NOT AT APP STARTUP:
 * KCEF downloads/initializes a full Chromium runtime (100+ MB the first
 * time, ~300-400 MB of RAM while running). Initializing it in Main.kt would
 * add that cost to every single app launch, even for the overwhelming
 * majority of sessions where the user is already logged in and never opens
 * the login screen. Instead, [ensureInitialized] is only called from
 * PlatformWebView the moment the user actually opens a login screen that
 * needs it - so the cost is paid once, only by users who need it, only when
 * they need it.
 *
 * WHY A SEPARATE OBJECT INSTEAD OF INLINING INTO Cookies.jvm.kt:
 * KCEF's lifecycle (init progress, restart-required, disposal) is a
 * standalone concern independent of any single WebView composable - keeping
 * it here means Cookies.jvm.kt only has to observe [state], not manage a
 * Chromium subprocess's lifecycle inline with UI code.
 */
object KcefBootstrap {
    sealed class State {
        data object NotStarted : State()

        data class Downloading(
            val progressPercent: Float,
        ) : State()

        data object Initializing : State()

        data object Ready : State()

        data object RestartRequired : State()

        data class Failed(
            val message: String,
        ) : State()
    }

    private val _state = MutableStateFlow<State>(State.NotStarted)
    val state: StateFlow<State> = _state.asStateFlow()

    // Guards against two composables racing to call ensureInitialized()
    // concurrently (e.g. fast navigation in/out of the login screen).
    private val initMutex = Mutex()

    // AUDIT NOTE (Ronda 16 - bug real confirmado por log:
    // "androidx.compose.runtime.ForgottenCoroutineScopeException:
    // rememberCoroutineScope left the composition"): la firma anterior de
    // `ensureInitialized(scope: CoroutineScope)` lanzaba TODO el trabajo de
    // descarga+init de KCEF dentro del `rememberCoroutineScope()` de
    // LogInScreen (ver el call site en Cookies.jvm.kt) - ese scope muere en
    // cuanto el composable sale de composición (cualquier navegación fuera
    // de la pantalla de login, incluso momentánea), matando la descarga a
    // mitad de camino con esta excepción. Esto contradecía la intención ya
    // documentada arriba ("the cost is paid once") - el trabajo de KCEF es
    // un singleton de nivel de app, no debería depender del ciclo de vida
    // de una sola pantalla. Fix: KcefBootstrap ahora es dueño de su propio
    // scope de larga vida (SupervisorJob para que un error en una
    // corrutina hija no cancele las demás), independiente de qué pantalla
    // lo llamó.
    private val bootstrapScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // AUDIT NOTE (Ronda 19 - decisión explícita de Sebastián: prioriza
    // consumo de recursos casi nulo por sobre poder volver a loguear en la
    // MISMA ejecución de la app): `KCEF.dispose()` es de una sola vía - la
    // propia librería lanza `KCEFException.Disposed` si se llama a
    // `KCEF.init()` de nuevo después de haber sido disposed (ver
    // `disposeAfterSuccessfulLogin()` más abajo). Este flag evita ese
    // crash: si el usuario necesita volver a loguearse en la misma sesión
    // (agregar otra cuenta, cookie vencida), `ensureInitialized()` lo
    // detecta y va directo a un estado de error claro en vez de dejar que
    // la excepción real de la librería se propague sin manejar.
    private var disposedForSession = false

    /**
     * Kicks off KCEF initialization if it hasn't started yet. Safe to call
     * multiple times / from multiple composables - only the first call
     * actually does anything; subsequent calls just observe [state].
     */
    fun ensureInitialized() {
        if (disposedForSession) {
            _state.value =
                State.Failed(
                    "El navegador integrado ya se cerró tras el último login para " +
                        "no consumir recursos en segundo plano. Reiniciá la app para " +
                        "poder iniciar sesión de nuevo.",
                )
            return
        }
        if (_state.value !is State.NotStarted) return
        bootstrapScope.launch {
            initMutex.withLock {
                // Re-check inside the lock: another caller may have already
                // started (or finished) initialization while we were
                // waiting for the mutex.
                if (_state.value !is State.NotStarted) return@withLock

                _state.value = State.Initializing
                Logger.d(TAG, "State -> Initializing (a punto de llamar a KCEF.init)")
                withContext(Dispatchers.IO) {
                    try {
                        // Reuses the SAME data-directory convention the rest
                        // of the app already uses for Desktop
                        // (DataStoreManagerImpl.jvm.kt uses
                        // getHomeFolderPath(listOf(".wavora")) for the
                        // preferences file) instead of inventing a new
                        // location or using a relative path (which would
                        // break depending on the process's working
                        // directory - a real risk for an app launched via a
                        // desktop shortcut/MSIX, not a terminal).
                        val kcefHome = File(getHomeFolderPath(listOf(".wavora", "kcef")))
                        val installDir = File(kcefHome, "bundle")
                        val cacheDir = File(kcefHome, "cache")

                        // DIAGNÓSTICO TEMPORAL (sacar una vez confirmado que
                        // el fix de versión de KCEF funciona en producción):
                        // chequeamos que el classpath real (ya sin el jar
                        // "wrapper" de Windows, gracias al plugin
                        // com.redock.classpathtofile en desktopApp) tenga lo
                        // que necesitamos, y que una clase real de
                        // ContentNegotiation cargue bien.
                        //
                        // NOTA: originalmente este chequeo probaba
                        // "io.ktor.client.plugins.contentnegotiation.ContentNegotiation"
                        // a secas - resultó ser un chequeo mal armado de mi
                        // parte: en Ktor 3.x ContentNegotiation es una
                        // PROPIEDAD de nivel superior (un ClientPlugin), no
                        // una clase, así que ese FQCN nunca iba a existir
                        // como archivo .class, independientemente de si el
                        // classpath estaba bien o mal. Confirmado abriendo
                        // el jar real con `jar tf`: existe
                        // ContentNegotiationConfig.class y
                        // ContentNegotiationKt.class, pero no
                        // ContentNegotiation.class. Este chequeo ahora prueba
                        // la clase real.
                        val classLoaderUsedHere = Thread.currentThread().contextClassLoader
                        val fullClasspath = System.getProperty("java.class.path") ?: ""
                        Logger.d(
                            TAG,
                            "DIAGNOSTICO: java.class.path contiene 'content-negotiation'? " +
                                fullClasspath.contains("content-negotiation"),
                        )
                        Logger.d(
                            TAG,
                            "DIAGNOSTICO: java.class.path contiene 'kcef-2025.03.23'? " +
                                fullClasspath.contains("kcef-2025.03.23"),
                        )
                        try {
                            val loadedFrom =
                                Class
                                    .forName(
                                        "io.ktor.client.plugins.contentnegotiation.ContentNegotiationConfig",
                                        false,
                                        classLoaderUsedHere,
                                    ).protectionDomain
                                    ?.codeSource
                                    ?.location
                            Logger.d(TAG, "DIAGNOSTICO: ContentNegotiationConfig cargó OK, viene de: $loadedFrom")
                        } catch (e: ClassNotFoundException) {
                            Logger.e(TAG, "DIAGNOSTICO: ContentNegotiationConfig NO carga", e)
                        }

                        Logger.d(TAG, "Llamando a KCEF.init() ahora - installDir=$installDir cacheDir=$cacheDir")
                        Logger.d(
                            TAG,
                            "DIAGNOSTICO: JVM que ejecuta esto ahora -> " +
                                "vendor=${System.getProperty("java.vendor")} " +
                                "version=${System.getProperty("java.version")} " +
                                "home=${System.getProperty("java.home")}",
                        )
                        KCEF.init(
                            builder = {
                                installDir(installDir)
                                progress {
                                    onDownloading { percent ->
                                        Logger.d(TAG, "State -> Downloading ${percent.coerceIn(0f, 100f)}%")
                                        _state.value = State.Downloading(percent.coerceIn(0f, 100f))
                                    }
                                    onInitialized {
                                        Logger.d(TAG, "State -> Ready (KCEF.onInitialized)")
                                        _state.value = State.Ready
                                    }
                                }
                                // AUDIT NOTE (Ronda 13 - confirmado leyendo el
                                // código fuente real de KCEFBuilder.kt, no
                                // adivinado): el crash real de Sebastián pasa
                                // SIEMPRE en el mismo punto exacto - justo
                                // después de "CefApp: set state INITIALIZING",
                                // con exit code -2147483645 (STATUS_BREAKPOINT)
                                // - confirmado independiente de qué JVM lo
                                // ejecuta (probado con JBR 17, JBR 21, y el
                                // JDK de Microsoft por defecto: mismo crash en
                                // los tres, siempre en ese punto). Esto aísla
                                // el problema a la inicialización nativa de
                                // Chromium/CEF en sí, no a la JVM. En el
                                // código fuente de KCEFBuilder.kt
                                // (initFromRuntime()) se encontró: si se le
                                // pasa el argumento "--disable-gpu"
                                // explícitamente, KCEF se SALTEA la carga de
                                // las librerías nativas de gráficos (EGL,
                                // GLESv2, vk_swiftshader - el stack de
                                // aceleración por GPU de Chromium/ANGLE) antes
                                // de arrancar CefApp. Dado que la máquina de
                                // prueba tiene una GPU integrada vieja (dato
                                // ya confirmado en rondas anteriores) y el
                                // propio mantenedor de KCEF, ante un log
                                // idéntico a este en otro SO, respondió
                                // "probablemente un problema del servidor
                                // gráfico" (issue #7 del repo), forzar
                                // --disable-gpu es el workaround más
                                // directamente respaldado por evidencia que
                                // tenemos hasta ahora para este crash. El
                                // método `.args(vararg args: String)` está
                                // confirmado en el código fuente descargado
                                // de KCEFBuilder.kt (línea 112), no es una
                                // suposición.
                                // AUDIT NOTE (Ronda 15 - confirmado el
                                // hardware real de Sebastián: i5-6200U +
                                // GTX 940MX, un laptop Nvidia Optimus con
                                // GPU dedicada débil y vieja + HDD + 6GB
                                // RAM): `--disable-gpu` solo no evitó el
                                // crash porque Chromium hace una detección
                                // temprana del adaptador de video (para
                                // decidir bloquear o no la GPU) ANTES de
                                // aplicar esa bandera - en setups Optimus
                                // con drivers Intel/Nvidia desparejos, esa
                                // detección en sí misma puede crashear.
                                // `--use-gl=swiftshader` fuerza a Chromium a
                                // usar su implementación de GL 100% por
                                // software (SwiftShader) en vez de tocar el
                                // driver real - a diferencia de
                                // `--use-gl=disabled`, esto todavía permite
                                // que el WebView renderice contenido visible
                                // (disabled directamente no dibuja nada). NO
                                // se agregó `--disable-software-rasterizer`:
                                // esa bandera hace lo contrario de lo que
                                // buscamos acá (desactiva el fallback por
                                // software, forzando a fallar en vez de usar
                                // software rendering).
                                args(
                                    "--disable-gpu",
                                    "--disable-gpu-compositing",
                                    "--use-gl=swiftshader",
                                    "--disable-gpu-sandbox",
                                )
                                settings {
                                    cachePath = cacheDir.absolutePath
                                    noSandbox = true
                                }
                            },
                            onError = { error ->
                                Logger.e(TAG, "KCEF init failed", error)
                                _state.value = State.Failed(error?.message ?: "Unknown KCEF error")
                            },
                            onRestartRequired = {
                                Logger.w(TAG, "KCEF reports a restart is required to finish setup")
                                _state.value = State.RestartRequired
                            },
                        )
                    } catch (e: Exception) {
                        // Defensive: KCEF.init's own onError callback covers
                        // the documented failure path, but we don't want an
                        // unexpected exception (e.g. disk full while
                        // extracting the bundle) to leave [state] stuck on
                        // Initializing forever.
                        Logger.e(TAG, "Unexpected error during KCEF init", e)
                        _state.value = State.Failed(e.message ?: "Unknown error")
                    }
                }
            }
        }
    }

    /**
     * Releases the Chromium subprocess right after a successful login, so
     * Wavora doesn't keep a whole embedded browser resident in memory for
     * the rest of the app session when all it needed was to grab one
     * session cookie. This is a deliberate trade-off Sebastián asked for
     * explicitly (Ronda 19): near-zero background footprint after login,
     * in exchange for needing to restart the app if YouTube Music needs to
     * be logged into again (add another account, or a re-login after the
     * saved cookie expires) within that same run - KCEF's own `dispose()`
     * is one-way; re-calling `init()` afterwards throws
     * `KCEFException.Disposed` (confirmed in KCEF's own source, not a
     * guess). [ensureInitialized] checks [disposedForSession] to turn that
     * into a clear message instead of an uncaught crash.
     */
    fun disposeAfterSuccessfulLogin() {
        if (_state.value !is State.Ready) return
        bootstrapScope.launch {
            Logger.d(TAG, "Login exitoso - liberando KCEF para minimizar consumo en segundo plano")
            KCEF.dispose()
            disposedForSession = true
        }
    }
}
