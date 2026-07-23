package com.wavora.app.expect.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Launch
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Launch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wavora.app.expect.openUrl
import com.wavora.app.ui.theme.typo
import com.wavora.app.ui.theme.wavoraBorder
import com.wavora.app.ui.theme.wavoraPrimary
import com.wavora.app.ui.theme.wavoraSurface
import com.wavora.app.viewModel.LogInViewModel
import multiplatform.network.cmptoast.showToast
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import wavora.composeapp.generated.resources.Res
import wavora.composeapp.generated.resources.discord_setup_command
import wavora.composeapp.generated.resources.discord_setup_connect
import wavora.composeapp.generated.resources.discord_setup_connecting
import wavora.composeapp.generated.resources.discord_setup_error
import wavora.composeapp.generated.resources.discord_setup_step1_button
import wavora.composeapp.generated.resources.discord_setup_step1_desc
import wavora.composeapp.generated.resources.discord_setup_step1_title
import wavora.composeapp.generated.resources.discord_setup_step2_desc
import wavora.composeapp.generated.resources.discord_setup_step2_title
import wavora.composeapp.generated.resources.discord_setup_step3_copy_command
import wavora.composeapp.generated.resources.discord_setup_step3_desc
import wavora.composeapp.generated.resources.discord_setup_step3_title
import wavora.composeapp.generated.resources.discord_setup_step4_placeholder
import wavora.composeapp.generated.resources.discord_setup_step4_title
import wavora.composeapp.generated.resources.discord_setup_subtitle
import wavora.composeapp.generated.resources.discord_setup_title
import wavora.composeapp.generated.resources.youtube_setup_connect
import wavora.composeapp.generated.resources.youtube_setup_connecting
import wavora.composeapp.generated.resources.youtube_setup_error
import wavora.composeapp.generated.resources.youtube_setup_step1_button
import wavora.composeapp.generated.resources.youtube_setup_step1_desc
import wavora.composeapp.generated.resources.youtube_setup_step1_title
import wavora.composeapp.generated.resources.youtube_setup_step2_desc
import wavora.composeapp.generated.resources.youtube_setup_step2_title
import wavora.composeapp.generated.resources.youtube_setup_step3_desc
import wavora.composeapp.generated.resources.youtube_setup_step3_title
import wavora.composeapp.generated.resources.youtube_setup_step4_placeholder
import wavora.composeapp.generated.resources.youtube_setup_step4_title
import wavora.composeapp.generated.resources.youtube_setup_subtitle
import wavora.composeapp.generated.resources.youtube_setup_title
import wavora.composeapp.generated.resources.spotify_setup_connect
import wavora.composeapp.generated.resources.spotify_setup_error
import wavora.composeapp.generated.resources.spotify_setup_step1_button
import wavora.composeapp.generated.resources.spotify_setup_step1_desc
import wavora.composeapp.generated.resources.spotify_setup_step1_title
import wavora.composeapp.generated.resources.spotify_setup_step2_desc
import wavora.composeapp.generated.resources.spotify_setup_step2_title
import wavora.composeapp.generated.resources.spotify_setup_step3_desc
import wavora.composeapp.generated.resources.spotify_setup_step3_title
import wavora.composeapp.generated.resources.spotify_setup_step4_placeholder
import wavora.composeapp.generated.resources.spotify_setup_step4_title
import wavora.composeapp.generated.resources.spotify_setup_subtitle
import wavora.composeapp.generated.resources.spotify_setup_title
import com.wavora.app.viewModel.SettingsViewModel
import com.wavora.common.Config
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
// VERIFICAR: paquete inferido de la documentación pública de
// io.github.kevinnzou:compose-webview-multiplatform (no pude
// compilarlo en este entorno para confirmarlo con el IDE). Si el IDE
// no encuentra esta clase en este paquete exacto, buscá "Cookie" con
// autocompletado - debería estar muy cerca de acá dado que
// WebViewState.cookieManager.getCookies() la devuelve.
import com.multiplatform.webview.cookie.Cookie
import com.multiplatform.webview.web.LoadingState
import com.multiplatform.webview.web.WebView
import com.multiplatform.webview.web.rememberWebViewNavigator
import com.multiplatform.webview.web.rememberWebViewState
import com.wavora.app.expect.ui.kcef.KcefBootstrap
import com.wavora.logger.Logger
import kotlinx.coroutines.launch
// TEST A v2 (sin evaluateJavaScript): estas 4 son la API PUBLICA de JCEF
// para leer cookies y el request-context de un browser desde el lado
// Java/Kotlin - no son parte de compose-webview-multiplatform ni de su
// bridge JS<->Java, así que no dependen de MutableSharedFlow<NavigationEvent>
// ni de IWebView.handleNavigationEvents(). Confirmado contra el código
// fuente real de JCEF (JetBrains/jcef y chromiumembedded/java-cef):
// CefBrowser.getRequestContext() y CefCookieManager.visitAllCookies()/
// visitUrlCookies() son ambos public/abstract en esas interfaces.
// VERIFICAR solamente: que dev.datlag:kcef 2024.04.20.4 reexporte estos
// mismos paquetes org.cef.* sin renombrarlos (todas las versiones de KCEF
// que están en Maven Central lo hacen así hasta donde se pudo confirmar,
// pero no se pudo compilar en este entorno para verificarlo con el IDE).
import org.cef.browser.CefBrowser
import org.cef.callback.CefCookieVisitor
import org.cef.misc.BoolRef
import org.cef.network.CefCookie
import org.cef.network.CefCookieManager
import java.util.concurrent.atomic.AtomicBoolean

// AUDIT NOTE (login de YouTube Music en Desktop, ver documento de
// Fases 1-4): antes, esto leía de java.net.CookieHandler.getDefault(),
// que en la práctica siempre estaba vacío en Desktop porque nada acá
// hacía requests HTTP con ese CookieHandler configurado - era
// efectivamente un no-op. Ahora que PlatformWebView usa un motor de
// WebView real (KCEF) para YouTube Music, el contrato sigue siendo el
// mismo (createWebViewCookieManager() como factory sin estado, tal
// como ya lo usa Android con su CookieManager global), pero la
// implementación lee de JvmWebViewCookieBridge, que PlatformWebView
// llena en el momento exacto del login exitoso - ver el comentario en
// JvmWebViewCookieBridge para el porqué de este diseño en vez de
// exponer la API de cookies (asíncrona) de KCEF directamente acá.
actual fun createWebViewCookieManager(): WebViewCookieManager =
    object : WebViewCookieManager {
        override fun getCookie(url: String): String = JvmWebViewCookieBridge.getCookie(url)

        override fun removeAllCookies() {
            JvmWebViewCookieBridge.clear()
        }
    }

/**
 * Puente mínimo entre el WebView real de YouTube Music (con cookies
 * scoped a su propia WebViewState/CookieManager de KCEF, asíncrono) y
 * el contrato WebViewCookieManager existente (síncrono, sin estado,
 * usado por LogInScreen.kt igual que en Android). YouTubeLoginWebView
 * llena esto justo antes de invocar onPageFinished(url), así que para
 * cuando LogInScreen.kt llama a getCookie(url) el valor ya está
 * disponible - no hace falta bloquear ni usar visitors asíncronos acá.
 *
 * Netscape también se guarda acá porque settingsViewModel.addAccount()
 * acepta un netscapeCookie opcional (ya usado por Android) - esto de
 * paso corrige un bug latente: getCookies() en
 * CommonRepositoryImpl.jvm.kt es un stub que siempre devolvía una
 * lista vacía en Desktop, por lo que el archivo ytdlp-cookie.txt se
 * generaba vacío. No tocamos ese archivo (fuera de alcance de este
 * cambio) - en su lugar, YouTubeLoginWebView arma el string netscape
 * directamente acá, con las cookies reales que sí tenemos disponibles.
 */
private object JvmWebViewCookieBridge {
    private var lastUrl: String? = null
    private var lastCookieHeader: String = ""
    private var lastNetscapeCookie: String = ""

    fun set(
        url: String,
        cookieHeader: String,
        netscapeCookie: String,
    ) {
        lastUrl = url
        lastCookieHeader = cookieHeader
        lastNetscapeCookie = netscapeCookie
    }

    fun getCookie(url: String): String = if (url == lastUrl) lastCookieHeader else ""

    fun getNetscapeCookie(url: String): String = if (url == lastUrl) lastNetscapeCookie else ""

    fun clear() {
        lastUrl = null
        lastCookieHeader = ""
        lastNetscapeCookie = ""
    }
}

@Composable
actual fun PlatformWebView(
    state: MutableState<WebViewState>,
    initUrl: String,
    aboveContent: @Composable (BoxScope.() -> Unit),
    onPageFinished: (String) -> Unit,
) {
    val isSpotify = initUrl.contains("spotify", ignoreCase = true)

    if (isSpotify) {
        // Sin cambios: Spotify sigue con la guía manual de sp_dc.
        // Fuera de alcance de este cambio - ver nota de seguimiento en
        // el documento de Fases 1-4.
        SpotifyDesktopSetup(aboveContent = aboveContent, onLoginDone = { onPageFinished(it) })
    } else {
        YouTubeLoginWebView(state = state, initUrl = initUrl, aboveContent = aboveContent, onPageFinished = onPageFinished)
    }
}

/**
 * WebView real (KCEF/Chromium embebido) para el login de YouTube Music
 * en Desktop - reemplaza la guía manual de "abrí devtools y pegá la
 * cookie" por el mismo flujo que ya tiene Android: el usuario inicia
 * sesión en la página real de Google/YouTube dentro de la app, y en
 * cuanto la navegación llega a YOUTUBE_MUSIC_MAIN_URL se extraen las
 * cookies automáticamente. Ver el documento de Fases 1-4 para la
 * justificación completa de por qué KCEF (vía
 * io.github.kevinnzou:compose-webview-multiplatform) y no otra
 * alternativa.
 */
@Composable
private fun YouTubeLoginWebView(
    state: MutableState<WebViewState>,
    initUrl: String,
    aboveContent: @Composable BoxScope.() -> Unit,
    onPageFinished: (String) -> Unit,
) {
    val kcefState by KcefBootstrap.state.collectAsState()

    LaunchedEffect(Unit) {
        KcefBootstrap.ensureInitialized()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (val current = kcefState) {
            KcefBootstrap.State.NotStarted, KcefBootstrap.State.Initializing -> {
                YouTubeWebViewStatus(youtube_setup_connecting_label = true)
            }

            is KcefBootstrap.State.Downloading -> {
                YouTubeWebViewStatus(downloadingPercent = current.progressPercent)
            }

            KcefBootstrap.State.RestartRequired -> {
                YouTubeWebViewStatus(restartRequired = true)
            }

            is KcefBootstrap.State.Failed -> {
                YouTubeWebViewStatus(errorMessage = current.message)
            }

            KcefBootstrap.State.Ready -> {
                val webViewState = rememberWebViewState(initUrl)

                // ==========================================================
                // DIAGNOSTICO TEMPORAL - Escenario B (SACAR una vez confirmada
                // o descartada la hipotesis, esto no es el fix, es solo para
                // decidir si vale la pena hacer el fork).
                //
                // TEST B: antes de que el usuario haga nada, escribimos una
                // cookie sintetica y la releemos de inmediato con el MISMO
                // manager (webViewState.cookieManager -> DesktopCookieManager
                // -> KCEFCookieManager.instance -> CefCookieManager.getGlobalManager()).
                // Si esto funciona, confirma que la lectura del contexto
                // global NO esta rota en general - que SI sabe leer cookies
                // que realmente estan ahi. Esto aisla la falla real (mas
                // abajo, cuando el login real no aparece) a "las cookies del
                // login no estan llegando a ese store", no a "el manager esta
                // roto".
                //
                // BUG REAL ENCONTRADO Y CORREGIDO (log de Sebastian,
                // pantalla en blanco + "el navegador ya se cerro"):
                // wavora_diag_test_cookie quedaba viva en
                // webViewState.cookieManager despues de este test - y ese es
                // EL MISMO manager que el codigo de produccion mas abajo lee
                // con `val cookies = webViewState.cookieManager.getCookies(loaded)`
                // para decidir si el login termino (`if (cookies.isNotEmpty())`).
                // Como wavora_diag_test_cookie SIEMPRE esta ahi (la escribimos
                // nosotros, sin login), esa condicion daba true de entrada,
                // aunque el login real todavia no habia pasado ninguna cookie -
                // eso disparaba onPageFinished()+KcefBootstrap.disposeAfterSuccessfulLogin()
                // (confirmado en el log: "Login exitoso - liberando KCEF..."
                // aparece justo despues de un COOKIE MANAGER que solo tenia
                // wavora_diag_test_cookie, sin SAPISID) - KCEF.dispose() corria
                // mientras el WebView todavia estaba en pantalla (de ahi el
                // blanco), y como disposeAfterSuccessfulLogin() es de una sola
                // via, la proxima vez que se entraba a la pantalla de login
                // ensureInitialized() devolvia directo el Failed con el
                // mensaje "el navegador ya se cerro" (exactamente la imagen
                // que mandaste). Fix: borrar la cookie sintetica apenas
                // terminamos de leerla, para no contaminar la deteccion real
                // de login mas abajo.
                LaunchedEffect(Unit) {
                    val testUrl = "https://music.youtube.com/"
                    val testCookieName = "wavora_diag_test_cookie"
                    val testCookieValue = System.currentTimeMillis().toString()
                    try {
                        webViewState.cookieManager.setCookie(
                            testUrl,
                            Cookie(
                                name = testCookieName,
                                value = testCookieValue,
                                domain = "music.youtube.com",
                                path = "/",
                            ),
                        )
                        val readBack = webViewState.cookieManager.getCookies(testUrl)
                        val found = readBack.firstOrNull { it.name == testCookieName }
                        Logger.d(
                            "WavoraDiag",
                            "===== TEST B =====\n" +
                                "Set cookie ${if (found != null) "OK" else "FALLO (no referencia el manager, revisar antes de seguir)"}\n" +
                                "Cookie escrita: $testCookieName=$testCookieValue\n" +
                                "Cookies leidas de vuelta (${readBack.size}): " +
                                readBack.joinToString(", ") { "${it.name}=${it.value}" } + "\n" +
                                "===================",
                        )
                    } catch (e: Exception) {
                        Logger.e("WavoraDiag", "===== TEST B ===== \nException haciendo el roundtrip: ${e.message}\n===================", e)
                    } finally {
                        // CRITICO: sin este cleanup, wavora_diag_test_cookie
                        // queda viva y contamina el `cookies.isNotEmpty()`
                        // real de mas abajo - ver el bug real documentado
                        // arriba. removeAllCookies() es seguro aca porque
                        // este LaunchedEffect(Unit) corre apenas la pantalla
                        // entra en Ready, antes de que el usuario haga nada -
                        // no deberia haber ninguna otra cookie real todavia.
                        try {
                            webViewState.cookieManager.removeAllCookies()
                        } catch (e: Exception) {
                            Logger.e("WavoraDiag", "===== TEST B (cleanup) ===== \nException limpiando la cookie sintetica: ${e.message}\n===================", e)
                        }
                    }
                }
                // Fin bloque TEST B.
                // ==========================================================

                // Chrome de escritorio real, no el UA por defecto de
                // CEF/Chromium embebido: Google bloquea activamente
                // logins desde user-agents que detecta como
                // "navegador inseguro"/automatizado. Android ya
                // necesita este mismo workaround (ver
                // SAMSUNG_USER_AGENT en Cookies.android.kt) - acá
                // usamos un UA de Chrome de Windows en vez de uno de
                // Android porque este SÍ es un navegador de
                // escritorio real.
                //
                // VERIFICAR: el nombre exacto de esta propiedad
                // (customUserAgentString en WebSettings de primer
                // nivel) es lo más probable según la documentación
                // pública de la librería, pero no lo pude confirmar
                // compilando en este entorno. Si el IDE no la
                // encuentra ahí, buscala bajo
                // webViewState.webSettings.desktopWebSettings - la
                // librería separa configuración común de
                // configuración específica de Desktop en algunas
                // versiones.
                LaunchedEffect(Unit) {
                    webViewState.webSettings.customUserAgentString =
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                            "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
                }

                val navigator = rememberWebViewNavigator()

                // TEST A v2 (CORREGIDO tras el error real de compilacion:
                // "Argument type mismatch... '() -> Unit' was expected").
                // Confirmado con el compilador de Sebastian: en
                // io.github.kevinnzou:compose-webview-multiplatform 1.8.4 el
                // onCreated del WebView(...) de mas abajo TODAVIA es
                // () -> Unit (sin el NativeWebView como parametro) - esa
                // variante con el browser real se agrego en una version
                // posterior de la libreria, no estaba en 1.8.4. Por eso NO
                // podemos capturar el CefBrowser real via onCreated en esta
                // version.
                //
                // Fallback: findCefBrowserViaReflection() (definida debajo
                // de este composable) - lee por reflection el primer field
                // de WebViewState cuyo tipo sea asignable a
                // org.cef.browser.CefBrowser (la interfaz PUBLICA y estable
                // de JCEF, no una clase interna de compose-webview-multiplatform).
                // AVISO HONESTO: esto ya no es "solo API publica" en sentido
                // estricto - es reflection de solo lectura sobre un objeto
                // que ya tenemos en memoria (no modifica la libreria, no es
                // un fork, no escribe nada). Si preferis mantenerte 100%
                // dentro de superficie publica, la alternativa es actualizar
                // compose-webview-multiplatform a la version donde
                // onCreated empezo a exponer NativeWebView y usar ese
                // mecanismo en vez de este fallback - lo dejo como reflection
                // ahora porque pediste explicitamente no bumpear/forkear
                // todavia nada, solo diagnosticar.
                var kcefBrowserRef by remember { mutableStateOf<CefBrowser?>(null) }

                // AUDIT NOTE (Ronda 20 - investigación a fondo, pedida
                // explícitamente por Sebastián, sobre los carteles de
                // seguridad de Windows/Windows Hello que le aparecían
                // durante el login): confirmado leyendo el código fuente
                // real de Chromium (device/fido/win/webauthn_api.cc) que
                // el puente a la API nativa de Windows Hello/llaves de
                // seguridad está compilado directo adentro de libcef.dll,
                // sin ningún --disable-features que lo desactive - ni el
                // propio Chrome/Brave/Edge reales pueden suprimirlo de
                // forma confiable (múltiples reportes de usuarios de
                // 2025-2026 sin solución). La pantalla "Sign in faster"
                // (screenshot real de Sebastián) es una página web común
                // de Google, NO el cartel nativo en sí - el cartel nativo
                // solo se dispara si el usuario clickea "Continue" en esa
                // página, iniciando la ceremonia real de WebAuthn. En vez
                // de pelear contra Chromium, la app misma le clickea "Not
                // now" automáticamente vía JS antes de que el usuario
                // llegue a ver la pantalla - evita el cartel nativo por
                // completo sin tocar ninguna configuración del motor.
                // Es un best-effort: si Google cambia el texto/DOM de este
                // botón en el futuro, esto simplemente no encuentra nada y
                // no hace nada (no puede romper el login real, solo hace
                // click si encuentra un elemento con ese texto exacto).
                LaunchedEffect(webViewState.loadingState, webViewState.lastLoadedUrl) {
                    if (webViewState.lastLoadedUrl?.contains("accounts.google.com") == true) {
                        navigator.evaluateJavaScript(
                            """
                            (function() {
                                var targets = ['not now', 'ahora no'];
                                var els = document.querySelectorAll('button, [role="button"], a, span, div');
                                for (var i = 0; i < els.length; i++) {
                                    var t = (els[i].innerText || els[i].textContent || '').trim().toLowerCase();
                                    if (targets.indexOf(t) !== -1) {
                                        els[i].click();
                                        return 'dismissed-passkey-offer';
                                    }
                                }
                                return 'not-found';
                            })();
                            """.trimIndent(),
                        ) { result ->
                            Logger.d("YouTubeLoginWebView", "Passkey auto-dismiss JS result: $result")
                        }
                    }
                }

                LaunchedEffect(webViewState.loadingState, webViewState.lastLoadedUrl) {
                    // FIX (confirmado por el compilador real de Sebastián:
                    // "Only safe (?.) or non-null asserted (!!.) calls are
                    // allowed on a nullable receiver of type 'String?'" y
                    // varios "Argument type mismatch: actual type is
                    // 'String?', but 'String' was expected"):
                    // `lastLoadedUrl` es `String?` en esta versión de la
                    // librería - salir temprano si todavía es null (la
                    // página no cargó nada todavía) deja a `loaded`
                    // smart-cast a `String` no-nulo para el resto del
                    // bloque, arreglando los 3 errores de una.
                    val loaded = webViewState.lastLoadedUrl ?: return@LaunchedEffect
                    // AUDIT NOTE (Ronda 18 - bug real reportado por
                    // Sebastián: se loguea en el WebView, ve la interfaz de
                    // YouTube Music perfectamente, pero la cuenta nunca
                    // queda guardada en la app - hay que loguearse de
                    // nuevo cada vez): la comparación anterior exigía
                    // `loaded == Config.YOUTUBE_MUSIC_MAIN_URL` (igualdad
                    // EXACTA contra "https://music.youtube.com/"). YouTube
                    // Music es una SPA - después de la cadena de redirects
                    // de Google, la URL real casi seguro trae algo de más
                    // (query params de sesión, sin la barra final, etc.),
                    // así que esa igualdad exacta nunca se cumplía, aunque
                    // la página cargara perfectamente - la app nunca se
                    // enteraba de que el login había terminado. Fix:
                    // `startsWith` sobre la base del dominio+path, sin
                    // exigir coincidencia exacta de query string/barra
                    // final - la forma estándar y robusta de detectar
                    // "llegamos al sitio de destino" después de una cadena
                    // de redirects de login.
                    if (webViewState.loadingState is LoadingState.Finished &&
                        loaded.startsWith("https://music.youtube.com")
                    ) {
                        // ==================================================
                        // DIAGNOSTICO TEMPORAL - Escenario B (SACAR junto con
                        // el bloque de TEST B de arriba una vez resuelto).
                        //
                        // TEST A v2 (reemplaza al TEST A viejo basado en
                        // evaluateJavaScript - descartado porque su callback
                        // nunca llegaba, ya que depende del
                        // MutableSharedFlow<NavigationEvent> + el collector
                        // de IWebView.handleNavigationEvents() + el bridge
                        // JS<->Java de la libreria, que en esta combinacion
                        // de versiones no es confiable).
                        //
                        // Este test es 100% nativo: usa unicamente la API
                        // publica de JCEF (org.cef.browser.CefBrowser /
                        // org.cef.network.CefCookieManager), sin JS, sin
                        // callbacks JS, sin pasar por el bridge JS<->Java de
                        // compose-webview-multiplatform en absoluto.
                        //
                        // Parte 1 (SINCRONICA, la prueba mas fuerte): le
                        // preguntamos directamente al CefBrowser real por su
                        // propio CefRequestContext.isGlobal(). Esto es una
                        // llamada nativa sincronica, sin hilos ni callbacks
                        // de por medio. Por diseno de CEF (confirmado en el
                        // changelog oficial: "Cookie managers are now
                        // per-request-context by default"), si isGlobal()
                        // da false, el cookie store que usa ESTE browser
                        // para el login NO PUEDE ser el mismo que devuelve
                        // CefCookieManager.getGlobalManager() (que es lo que
                        // lee DesktopCookieManager/webViewState.cookieManager
                        // mas abajo) - son, por definicion de la propia
                        // libreria, dos cookie stores distintos. Esto solo
                        // confirma (o descarta) el Escenario B a nivel
                        // arquitectura, sin necesitar leer ninguna cookie
                        // todavia.
                        val browser = kcefBrowserRef ?: findCefBrowserViaReflection(webViewState).also { kcefBrowserRef = it }
                        if (browser == null) {
                            Logger.w(
                                "WavoraDiag",
                                "===== TEST A v2 =====\nkcefBrowserRef es null - " +
                                    "findCefBrowserViaReflection() no encontro ningun field de " +
                                    "tipo CefBrowser en WebViewState (o el WebView todavia no se " +
                                    "termino de crear del lado nativo) - revisar antes de confiar " +
                                    "en este test\n" +
                                    "===================",
                            )
                        } else {
                            try {
                                // VERIFICAR: browser.requestContext es la
                                // property Kotlin generada a partir de
                                // CefBrowser.getRequestContext() (confirmado
                                // publico en CefBrowser.java del repo real de
                                // JCEF). Si KCEFBrowser sobreescribe este
                                // metodo con otro nombre, el IDE lo va a
                                // marcar - no debería, porque es un metodo de
                                // interfaz heredado, pero como no pude
                                // compilar esto en este entorno lo dejo
                                // marcado igual que el resto de VERIFICAR de
                                // este archivo.
                                val ctx = browser.requestContext
                                Logger.d(
                                    "WavoraDiag",
                                    "===== TEST A v2 (parte 1: sincronica, sin JS) =====\n" +
                                        "requestContext != null: ${ctx != null}\n" +
                                        "requestContext.isGlobal(): ${ctx?.isGlobal}\n" +
                                        "requestContext.handler != null: ${ctx?.handler != null}\n" +
                                        "(si isGlobal=false, esto YA confirma Escenario B a nivel " +
                                        "arquitectura - el browser esta corriendo bajo un contexto " +
                                        "que la propia CEF documenta como duenio de su PROPIO cookie " +
                                        "manager, distinto del global)\n" +
                                        "===================",
                                )
                            } catch (e: Throwable) {
                                Logger.e(
                                    "WavoraDiag",
                                    "===== TEST A v2 (parte 1) ===== \nException leyendo requestContext: ${e.message}\n===================",
                                    e,
                                )
                            }

                            // Parte 2 (ASINCRONICA, pero NO es el bridge
                            // JS<->Java - es el mecanismo nativo propio de
                            // CEF para visitar cookies, documentado en
                            // CefCookieManager.visitUrlCookies(): el
                            // callback lo invoca directo el hilo IO de CEF,
                            // nada pasa por MutableSharedFlow<NavigationEvent>
                            // ni por IWebView.handleNavigationEvents()).
                            // Complementa al bloque "COOKIE MANAGER" de mas
                            // abajo: ese usa webViewState.cookieManager
                            // .getCookies() (el wrapper de la libreria KMP),
                            // esto usa la API nativa de JCEF directo -
                            // si ambos coinciden en 0 cookies, descarta que
                            // el wrapper de la libreria KMP sea el que esta
                            // fallando (no es un bug de conversion en su
                            // getCookies(), es que el manager global
                            // realmente no tiene las cookies).
                            try {
                                val visited = AtomicBoolean(false)
                                val names = mutableListOf<String>()
                                val visitor =
                                    object : CefCookieVisitor {
                                        override fun visit(
                                            cookie: CefCookie,
                                            count: Int,
                                            total: Int,
                                            delete: BoolRef,
                                        ): Boolean {
                                            visited.set(true)
                                            names += cookie.name
                                            if (count == total - 1) {
                                                Logger.d(
                                                    "WavoraDiag",
                                                    "===== TEST A v2 (parte 2: CefCookieManager.getGlobalManager(), nativo) =====\n" +
                                                        "Cookies visitadas ($total): ${names.joinToString(", ")}\n" +
                                                        "tiene SAPISID=${names.contains("SAPISID")}\n" +
                                                        "tiene __Secure-3PAPISID=${names.contains("__Secure-3PAPISID")}\n" +
                                                        "===================",
                                                )
                                            }
                                            return true
                                        }
                                    }
                                val accepted = CefCookieManager.getGlobalManager().visitUrlCookies(loaded, true, visitor)
                                if (!accepted) {
                                    Logger.w("WavoraDiag", "===== TEST A v2 (parte 2) =====\nvisitUrlCookies devolvio false (no se pudo acceder al manager)\n===================")
                                }
                                // visit() nunca se llama si hay 0 cookies -
                                // sin este chequeo con delay, ese caso
                                // (que es justo el que estamos tratando de
                                // confirmar) no dejaria ningun log.
                                launch {
                                    kotlinx.coroutines.delay(1500)
                                    if (!visited.get()) {
                                        Logger.d(
                                            "WavoraDiag",
                                            "===== TEST A v2 (parte 2) =====\n" +
                                                "visit() nunca se invoco en 1.5s -> 0 cookies en el " +
                                                "manager global para $loaded (via API nativa, sin pasar " +
                                                "por el wrapper de la libreria KMP)\n" +
                                                "===================",
                                        )
                                    }
                                }
                            } catch (e: Throwable) {
                                Logger.e(
                                    "WavoraDiag",
                                    "===== TEST A v2 (parte 2) ===== \nException en visitUrlCookies: ${e.message}\n===================",
                                    e,
                                )
                            }
                        }
                        // Fin bloque TEST A v2.
                        // ==================================================

                        val cookies: List<Cookie> = webViewState.cookieManager.getCookies(loaded)
                        // DIAGNOSTICO (auditoria persistencia de sesion Desktop, pedido
                        // explicito de Sebastian): esto muestra EXACTAMENTE que cookies
                        // devuelve el cookie manager de KCEF en el momento de la captura,
                        // antes de que nada mas las toque. La hipotesis a confirmar/descartar
                        // es que "SAPISID" y "__Secure-3PAPISID" (cookies HttpOnly de Google)
                        // no aparezcan aca - si no aparecen aca, el problema es 100% la
                        // captura en KCEF, no el guardado ni la llamada de red de mas abajo.
                        Logger.d(
                            "WavoraDiag",
                            "===== COOKIE MANAGER =====\n" +
                                "Cookies del manager (${cookies.size}): " +
                                cookies.joinToString(", ") { it.name } + "\n" +
                                "tiene SAPISID=${cookies.any { it.name == "SAPISID" }}\n" +
                                "tiene __Secure-3PAPISID=${cookies.any { it.name == "__Secure-3PAPISID" }}\n" +
                                "===========================",
                        )
                        if (cookies.isNotEmpty()) {
                            // VERIFICAR: nombres de campos de Cookie según
                            // la versión exacta de
                            // io.github.kevinnzou:compose-webview-multiplatform
                            // que termines usando (confirmado en su
                            // documentación pública: name, value, domain,
                            // expiresDate - path/secure no confirmados acá,
                            // el compilador te va a marcar si algo cambió).
                            val cookieHeader = cookies.joinToString("; ") { "${it.name}=${it.value}" }
                            val netscapeCookie =
                                cookies.joinToString("\n") { c ->
                                    // FIX (confirmado por el compilador real de
                                    // Sebastián: error en esta misma línea,
                                    // "Only safe call allowed on nullable
                                    // receiver"): Cookie.domain es String? en
                                    // esta versión de la librería, no String -
                                    // justo lo que dejé marcado como
                                    // VERIFICAR. Iba con safe call + fallback.
                                    val domain = c.domain?.ifBlank { "music.youtube.com" } ?: "music.youtube.com"
                                    val includeSubdomains = if (domain.startsWith(".")) "TRUE" else "FALSE"
                                    val path = "/"
                                    val secure = "TRUE"
                                    val expiry = (c.expiresDate ?: 0L).toString()
                                    listOf(domain, includeSubdomains, path, secure, expiry, c.name, c.value)
                                        .joinToString("\t")
                                }
                            JvmWebViewCookieBridge.set(loaded, cookieHeader, netscapeCookie)
                            webViewState.cookieManager.removeAllCookies()
                            onPageFinished(loaded)
                            // Decisión explícita de Sebastián (Ronda 19):
                            // preferir consumo casi nulo en segundo plano
                            // por sobre poder re-loguear sin reiniciar la
                            // app - ver el comentario completo en
                            // disposeAfterSuccessfulLogin().
                            KcefBootstrap.disposeAfterSuccessfulLogin()
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    WebView(
                        state = webViewState,
                        navigator = navigator,
                        modifier = Modifier.fillMaxSize(),
                        // CORREGIDO: en 1.8.4 onCreated es () -> Unit, sin el
                        // NativeWebView como parametro (confirmado por el
                        // compilador real de Sebastian - ver el comentario
                        // largo mas arriba, junto a la declaracion de
                        // kcefBrowserRef). No podemos capturar el browser
                        // real desde aca en esta version de la libreria; lo
                        // hacemos via findCefBrowserViaReflection() mas
                        // abajo, en el momento en que TEST A v2 lo necesita.
                        onCreated = {
                            Logger.d("WavoraDiag", "WebView onCreated (sin referencia al browser en esta version de la libreria)")
                        },
                    )

                    // AUDIT NOTE (Ronda 23 - problema real reportado por
                    // Sebastián: el WebView queda en blanco bastante
                    // tiempo la primera vez - el log confirmó ~27+
                    // segundos entre "CefApp: set state INITIALIZING" y
                    // "CefApp: set state INITIALIZED", más el tiempo real
                    // de carga de la página de Google ENCIMA de eso - y
                    // como no había NINGÚN indicador cubriendo este hueco
                    // específico (KcefBootstrap ya cubre
                    // Downloading/Initializing ANTES de esto, pero acá
                    // KCEF ya está Ready y sin embargo la página real
                    // todavía no pintó nada), el usuario salía de la
                    // pantalla de login pensando que se había colgado -
                    // 3 veces seguidas en el log más reciente, sin que
                    // NINGÚN intento llegara a completar el login de
                    // verdad (ni la detección de cookie ni el
                    // auto-dismiss del passkey llegaron a dispararse ni
                    // una vez, porque nunca se les daba tiempo). Fix: un
                    // overlay semitransparente sobre el WebView mientras
                    // `loadingState` no sea `Finished`, para que SIEMPRE
                    // haya feedback visual de que algo está pasando,
                    // sin importar en qué sub-etapa exacta del arranque
                    // de Chromium/carga de página esté.
                    if (webViewState.loadingState !is LoadingState.Finished) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.85f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            YouTubeWebViewStatus(youtube_setup_connecting_label = true)
                        }
                    }
                }
            }
        }
        aboveContent()
    }
}

@Composable
private fun YouTubeWebViewStatus(
    youtube_setup_connecting_label: Boolean = false,
    downloadingPercent: Float? = null,
    restartRequired: Boolean = false,
    errorMessage: String? = null,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when {
            errorMessage != null -> {
                Text(
                    "No se pudo iniciar el navegador integrado",
                    style = typo().titleMedium,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    errorMessage,
                    style = typo().bodySmall,
                    color = Color.White.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                )
            }

            restartRequired -> {
                Text(
                    "Wavora necesita reiniciarse para completar la configuración",
                    style = typo().titleMedium,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
            }

            downloadingPercent != null -> {
                CircularProgressIndicator(color = wavoraPrimary)
                Spacer(Modifier.height(16.dp))
                Text(
                    "Preparando el navegador integrado (${downloadingPercent.toInt()}%)",
                    style = typo().bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Esto solo pasa la primera vez",
                    style = typo().bodySmall,
                    color = Color.White.copy(alpha = 0.4f),
                    textAlign = TextAlign.Center,
                )
            }

            else -> {
                CircularProgressIndicator(color = wavoraPrimary)
                Spacer(Modifier.height(16.dp))
                Text(
                    "Preparando el navegador integrado…",
                    style = typo().bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

// AUDIT NOTE (login de YouTube Music en Desktop, ver documento de
// Fases 1-4): esta guía manual ("abrí devtools y pegá la cookie") ya
// NO se invoca desde PlatformWebView - la reemplaza YouTubeLoginWebView
// (WebView real vía KCEF), definida más arriba en este mismo archivo.
// Se deja el código intacto, sin borrar, como plan de rollback: si el
// WebView real da algún problema en producción, revertir es cambiar
// una sola línea en PlatformWebView para volver a llamar a esta
// función en vez de YouTubeLoginWebView. El compilador puede marcar
// esta función como "never used" - es esperado y no es un error.
@Composable
private fun YouTubeDesktopSetup(
    aboveContent: @Composable BoxScope.() -> Unit,
    onLoginDone: (String) -> Unit,
) {
    val viewModel: LogInViewModel = koinInject()
    val settingsViewModel: SettingsViewModel = koinInject()
    val validationState by viewModel.cookieValidation.collectAsState()
    var cookie by remember { mutableStateOf("") }

    LaunchedEffect(validationState) {
        if (validationState is LogInViewModel.CookieValidationState.Success) {
            onLoginDone(Config.YOUTUBE_MUSIC_MAIN_URL)
            showToast("Connected to YouTube Music!")
            viewModel.resetCookieValidation()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Header
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(Res.string.youtube_setup_title),
                    style = typo().titleMedium,
                    color = Color.White,
                )
                Text(
                    text = stringResource(Res.string.youtube_setup_subtitle),
                    style = typo().bodySmall,
                    color = Color.White.copy(alpha = 0.6f),
                )
            }

            // Step 1
            StepCard(number = 1) {
                Text(stringResource(Res.string.youtube_setup_step1_title), style = typo().labelMedium, color = Color.White)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(Res.string.youtube_setup_step1_desc), style = typo().bodySmall, color = Color.White.copy(alpha = 0.7f))
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { openUrl("https://music.youtube.com") },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF0000)),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Launch, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(Res.string.youtube_setup_step1_button), style = typo().labelMedium)
                }
            }

            // Step 2
            StepCard(number = 2) {
                Text(stringResource(Res.string.youtube_setup_step2_title), style = typo().labelMedium, color = Color.White)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(Res.string.youtube_setup_step2_desc), style = typo().bodySmall, color = Color.White.copy(alpha = 0.7f))
            }

            // Step 3
            StepCard(number = 3) {
                Text(stringResource(Res.string.youtube_setup_step3_title), style = typo().labelMedium, color = Color.White)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(Res.string.youtube_setup_step3_desc), style = typo().bodySmall, color = Color.White.copy(alpha = 0.7f))
            }

            // Step 4
            StepCard(number = 4) {
                Text(stringResource(Res.string.youtube_setup_step4_title), style = typo().labelMedium, color = Color.White)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = cookie,
                    onValueChange = {
                        cookie = it
                        if (validationState is LogInViewModel.CookieValidationState.Error) {
                            viewModel.resetCookieValidation()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            stringResource(Res.string.youtube_setup_step4_placeholder),
                            style = typo().bodySmall,
                            color = Color.White.copy(alpha = 0.4f),
                        )
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = validationState is LogInViewModel.CookieValidationState.Error,
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFFF0000),
                        unfocusedBorderColor = wavoraBorder,
                        errorBorderColor = Color(0xFFCF6679),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                    ),
                )
                AnimatedVisibility(
                    visible = validationState is LogInViewModel.CookieValidationState.Error,
                    enter = fadeIn() + expandVertically(),
                ) {
                    Text(
                        stringResource(Res.string.youtube_setup_error),
                        style = typo().bodySmall,
                        color = Color(0xFFCF6679),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Spacer(Modifier.height(10.dp))
                val isLoading = validationState is LogInViewModel.CookieValidationState.Loading
                Button(
                    onClick = {
                        if (cookie.isNotBlank()) {
                            viewModel.validateAndSaveYouTubeCookie(cookie, settingsViewModel)
                        }
                    },
                    enabled = cookie.isNotBlank() && !isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF0000)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(Res.string.youtube_setup_connecting), style = typo().labelMedium)
                    } else {
                        Text(stringResource(Res.string.youtube_setup_connect), style = typo().labelMedium)
                    }
                }
            }
        }
        aboveContent()
    }
}

@Composable
private fun SpotifyDesktopSetup(
    aboveContent: @Composable BoxScope.() -> Unit,
    onLoginDone: (String) -> Unit,
) {
    val viewModel: LogInViewModel = koinInject()
    val validationState by viewModel.spdcValidation.collectAsState()
    var spdc by remember { mutableStateOf("") }

    LaunchedEffect(validationState) {
        if (validationState is LogInViewModel.SpdcValidationState.Success) {
            onLoginDone("https://accounts.spotify.com/status")
            showToast("Connected to Spotify!")
            viewModel.resetSpdcValidation()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(Res.string.spotify_setup_title),
                    style = typo().titleMedium,
                    color = Color.White,
                )
                Text(
                    text = stringResource(Res.string.spotify_setup_subtitle),
                    style = typo().bodySmall,
                    color = Color.White.copy(alpha = 0.6f),
                )
            }

            StepCard(number = 1) {
                Text(stringResource(Res.string.spotify_setup_step1_title), style = typo().labelMedium, color = Color.White)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(Res.string.spotify_setup_step1_desc), style = typo().bodySmall, color = Color.White.copy(alpha = 0.7f))
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { openUrl("https://open.spotify.com") },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1DB954)),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Launch, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(Res.string.spotify_setup_step1_button), style = typo().labelMedium)
                }
            }

            StepCard(number = 2) {
                Text(stringResource(Res.string.spotify_setup_step2_title), style = typo().labelMedium, color = Color.White)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(Res.string.spotify_setup_step2_desc), style = typo().bodySmall, color = Color.White.copy(alpha = 0.7f))
            }

            StepCard(number = 3) {
                Text(stringResource(Res.string.spotify_setup_step3_title), style = typo().labelMedium, color = Color.White)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(Res.string.spotify_setup_step3_desc), style = typo().bodySmall, color = Color.White.copy(alpha = 0.7f))
            }

            StepCard(number = 4) {
                Text(stringResource(Res.string.spotify_setup_step4_title), style = typo().labelMedium, color = Color.White)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = spdc,
                    onValueChange = {
                        spdc = it
                        if (validationState is LogInViewModel.SpdcValidationState.Error) {
                            viewModel.resetSpdcValidation()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            stringResource(Res.string.spotify_setup_step4_placeholder),
                            style = typo().bodySmall,
                            color = Color.White.copy(alpha = 0.4f),
                        )
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = validationState is LogInViewModel.SpdcValidationState.Error,
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF1DB954),
                        unfocusedBorderColor = wavoraBorder,
                        errorBorderColor = Color(0xFFCF6679),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                    ),
                )
                AnimatedVisibility(
                    visible = validationState is LogInViewModel.SpdcValidationState.Error,
                    enter = fadeIn() + expandVertically(),
                ) {
                    Text(
                        stringResource(Res.string.spotify_setup_error),
                        style = typo().bodySmall,
                        color = Color(0xFFCF6679),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Spacer(Modifier.height(10.dp))
                val isLoading = validationState is LogInViewModel.SpdcValidationState.Loading
                Button(
                    onClick = { if (spdc.isNotBlank()) viewModel.validateAndSaveSpotifySpdc(spdc) },
                    enabled = spdc.isNotBlank() && !isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1DB954)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(Res.string.discord_setup_connecting), style = typo().labelMedium)
                    } else {
                        Text(stringResource(Res.string.spotify_setup_connect), style = typo().labelMedium)
                    }
                }
            }
        }
        aboveContent()
    }
}

@Composable
actual fun DiscordWebView(
    state: MutableState<WebViewState>,
    aboveContent: @Composable (BoxScope.() -> Unit),
    onLoginDone: (@ParameterName("token") String) -> Unit,
) {
    val viewModel: LogInViewModel = koinInject()
    val validationState by viewModel.tokenValidation.collectAsState()
    var token by remember { mutableStateOf("") }
    val jsCommand = stringResource(Res.string.discord_setup_command)

    // When validation succeeds, forward the token upward and reset state
    LaunchedEffect(validationState) {
        if (validationState is LogInViewModel.TokenValidationState.Success) {
            val info = (validationState as LogInViewModel.TokenValidationState.Success).userInfo
            onLoginDone(token.trim())
            showToast("Connected as ${info.name}")
            viewModel.resetTokenValidation()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Header
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(Res.string.discord_setup_title),
                    style = typo().titleMedium,
                    color = Color.White,
                )
                Text(
                    text = stringResource(Res.string.discord_setup_subtitle),
                    style = typo().bodySmall,
                    color = Color.White.copy(alpha = 0.6f),
                )
            }

            // Step 1
            StepCard(number = 1) {
                Text(
                    stringResource(Res.string.discord_setup_step1_title),
                    style = typo().labelMedium,
                    color = Color.White,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(Res.string.discord_setup_step1_desc),
                    style = typo().bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { openUrl("https://discord.com/login") },
                    colors = ButtonDefaults.buttonColors(containerColor = wavoraPrimary),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.Launch,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(Res.string.discord_setup_step1_button),
                        style = typo().labelMedium,
                    )
                }
            }

            // Step 2
            StepCard(number = 2) {
                Text(
                    stringResource(Res.string.discord_setup_step2_title),
                    style = typo().labelMedium,
                    color = Color.White,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(Res.string.discord_setup_step2_desc),
                    style = typo().bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                )
            }

            // Step 3
            StepCard(number = 3) {
                Text(
                    stringResource(Res.string.discord_setup_step3_title),
                    style = typo().labelMedium,
                    color = Color.White,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(Res.string.discord_setup_step3_desc),
                    style = typo().bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                )
                Spacer(Modifier.height(10.dp))
                // Code block
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF0D1117))
                            .border(1.dp, wavoraBorder, RoundedCornerShape(6.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = jsCommand,
                        style = typo().bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                        ),
                        color = Color(0xFF58A6FF),
                        modifier = Modifier.weight(1f),
                        maxLines = 3,
                    )
                    IconButton(
                        onClick = {
                            val clip = StringSelection(jsCommand)
                            Toolkit.getDefaultToolkit().systemClipboard.setContents(clip, null)
                            showToast("Command copied!")
                        },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            Icons.Rounded.ContentCopy,
                            contentDescription = stringResource(Res.string.discord_setup_step3_copy_command),
                            tint = wavoraPrimary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            // Step 4
            StepCard(number = 4) {
                Text(
                    stringResource(Res.string.discord_setup_step4_title),
                    style = typo().labelMedium,
                    color = Color.White,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = token,
                    onValueChange = {
                        token = it
                        if (validationState is LogInViewModel.TokenValidationState.Error) {
                            viewModel.resetTokenValidation()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            stringResource(Res.string.discord_setup_step4_placeholder),
                            style = typo().bodySmall,
                            color = Color.White.copy(alpha = 0.4f),
                        )
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = validationState is LogInViewModel.TokenValidationState.Error,
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = wavoraPrimary,
                        unfocusedBorderColor = wavoraBorder,
                        errorBorderColor = Color(0xFFCF6679),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                    ),
                )

                // Error message
                AnimatedVisibility(
                    visible = validationState is LogInViewModel.TokenValidationState.Error,
                    enter = fadeIn() + expandVertically(),
                ) {
                    Text(
                        stringResource(Res.string.discord_setup_error),
                        style = typo().bodySmall,
                        color = Color(0xFFCF6679),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                Spacer(Modifier.height(10.dp))
                val isLoading = validationState is LogInViewModel.TokenValidationState.Loading
                Button(
                    onClick = { if (token.isNotBlank()) viewModel.validateAndSaveDiscordToken(token) },
                    enabled = token.isNotBlank() && !isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = wavoraPrimary),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(Res.string.discord_setup_connecting), style = typo().labelMedium)
                    } else {
                        Text(stringResource(Res.string.discord_setup_connect), style = typo().labelMedium)
                    }
                }
            }
        }
        aboveContent()
    }
}

@Composable
private fun StepCard(
    number: Int,
    content: @Composable () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(wavoraSurface)
                .border(1.dp, wavoraBorder, RoundedCornerShape(12.dp))
                .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Step number badge
        Box(
            modifier =
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(wavoraPrimary.copy(alpha = 0.15f))
                    .border(1.dp, wavoraPrimary.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = number.toString(),
                style = typo().labelMedium,
                color = wavoraPrimary,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            content()
        }
    }
}

// ============================================================================
// DIAGNOSTICO TEMPORAL - Escenario B, TEST A v2 (SACAR una vez resuelto,
// junto con el resto de los bloques WavoraDiag de este archivo).
//
// Fallback para conseguir el CefBrowser real cuando la version de
// compose-webview-multiplatform en uso (1.8.4, confirmado por el
// compilador real de Sebastian - ver AUDIT NOTE junto a kcefBrowserRef
// mas arriba) todavia no exponia el NativeWebView a traves de onCreated.
//
// Que hace: recorre por reflection los declaredFields de WebViewState (y
// de sus superclases, por si el field vive mas arriba en la jerarquia) y
// devuelve el primer valor cuyo tipo sea asignable a
// org.cef.browser.CefBrowser - la interfaz PUBLICA y estable de JCEF
// (KCEFBrowser la implementa, asi que esto encuentra la instancia real sin
// necesitar saber el nombre exacto del field interno de la libreria, que
// puede cambiar entre versiones).
//
// AVISO HONESTO (para no vender esto como algo que no es): esto ya NO es
// "solo API publica" en sentido estricto - es reflection de solo lectura
// sobre un objeto que ya esta en memoria de la app. No modifica la
// libreria, no genera ningun fork, no escribe nada, solo lee un field
// privado ya existente para diagnostico. Si mas adelante se decide
// actualizar compose-webview-multiplatform a una version donde onCreated
// si expone NativeWebView directamente, este fallback deja de hacer falta
// y se puede volver al mecanismo 100% publico.
private fun findCefBrowserViaReflection(state: com.multiplatform.webview.web.WebViewState): CefBrowser? {
    var current: Class<*>? = state.javaClass
    while (current != null) {
        for (field in current.declaredFields) {
            if (CefBrowser::class.java.isAssignableFrom(field.type)) {
                return try {
                    field.isAccessible = true
                    field.get(state) as? CefBrowser
                } catch (e: Throwable) {
                    Logger.e("WavoraDiag", "findCefBrowserViaReflection: no se pudo leer el field ${field.name}: ${e.message}", e)
                    null
                }
            }
        }
        current = current.superclass
    }
    return null
}
