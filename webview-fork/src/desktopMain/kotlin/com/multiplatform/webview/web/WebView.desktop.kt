package com.multiplatform.webview.web

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import com.multiplatform.webview.jsbridge.WebViewJsBridge
import dev.datlag.kcef.KCEF
import dev.datlag.kcef.KCEFBrowser
import org.cef.browser.CefRendering

/**
 * Desktop WebView implementation.
 */
// WAVORA FORK: sin `actual` (ver nota en getPlatform.kt) — mismo
// comportamiento que el original de la 1.8.4.
@Composable
fun ActualWebView(
    state: WebViewState,
    modifier: Modifier,
    captureBackPresses: Boolean,
    navigator: WebViewNavigator,
    webViewJsBridge: WebViewJsBridge?,
    onCreated: () -> Unit,
    onDispose: () -> Unit,
) {
    DesktopWebView(
        state,
        modifier,
        navigator,
        webViewJsBridge,
        onCreated = onCreated,
        onDispose = onDispose,
    )
}

/**
 * Desktop WebView implementation.
 */
@Composable
fun DesktopWebView(
    state: WebViewState,
    modifier: Modifier,
    navigator: WebViewNavigator,
    webViewJsBridge: WebViewJsBridge?,
    onCreated: () -> Unit,
    onDispose: () -> Unit,
) {
    val currentOnDispose by rememberUpdatedState(onDispose)
    val client =
        remember(state.webSettings.desktopWebSettings.disablePopupWindows) {
            KCEF.newClientOrNullBlocking()?.also {
                if (state.webSettings.desktopWebSettings.disablePopupWindows) {
                    it.addLifeSpanHandler(DisablePopupWindowsLifeSpanHandler())
                } else {
                    if (it.getLifeSpanHandler() is DisablePopupWindowsLifeSpanHandler) {
                        it.removeLifeSpanHandler()
                    }
                }
                // WAVORA FORK (Escenario B) — ver CefRequestExt.kt para el detalle
                // completo. Reemplaza a createModifiedRequestContext(...): en vez
                // de crear un CefRequestContext privado (con su propio cookie
                // store, distinto del global que lee webViewState.cookieManager),
                // el override de User-Agent se registra acá, a nivel de CefClient,
                // vía la misma API pública addXHandler que ya se usa arriba para
                // el LifeSpanHandler.
                //
                // OJO — verificado contra el código fuente real de CefClient.java:
                // requestHandler_ es UN SOLO slot, no una lista, y
                // addRequestHandler(handler) es un no-op SILENCIOSO si ya hay un
                // handler seteado (if (requestHandler_ == null) requestHandler_ =
                // handler). Confirmado que ni KCEF ni el resto de Wavora llaman a
                // addRequestHandler/CefRequestHandler en ningún otro lado, así que
                // hoy esta es la única llamada sobre este client (que además es
                // nuevo en cada KCEF.newClientOrNullBlocking() — ver
                // KCEF.newClient(): KCEFClient(cefApp.createClient()) — por lo que
                // requestHandler_ arranca en null siempre). Si en el futuro se
                // necesita otro CefRequestHandler (auth credentials, certificate
                // errors, etc.) para este mismo client, hay que combinarlo ACÁ
                // ADENTRO de este mismo objeto — no agregar un segundo
                // addRequestHandler, porque el segundo llamado se ignoraría sin
                // ningún error.
                it.addRequestHandler(createUserAgentRequestHandler(state.webSettings))
            }
        }
    val scope = rememberCoroutineScope()
    val fileContent by produceState("", state.content) {
        value =
            if (state.content is WebContent.File) {
                // WAVORA FORK: la 1.8.4 original leía este archivo con
                // org.jetbrains.compose.resources.resource(...), API que ya no
                // existe en la versión de Compose Multiplatform que usa este
                // proyecto (fue removida/reemplazada por el sistema de recursos
                // generado por módulo). Como acá no hay multiplatform real (un
                // solo target, JVM), alcanza con leer el recurso directamente
                // del classpath — mismo resultado, sin esa API.
                val fileName = "assets/${(state.content as WebContent.File).fileName}"
                Thread.currentThread().contextClassLoader
                    .getResourceAsStream(fileName)
                    ?.use { it.readBytes() }
                    ?.decodeToString()
                    ?.trimIndent()
                    ?: ""
            } else {
                ""
            }
    }

    val browser: KCEFBrowser? =
        remember(
            client,
            state.webSettings.desktopWebSettings.offScreenRendering,
            state.webSettings.desktopWebSettings.transparent,
            state.webSettings,
            fileContent,
        ) {
            val rendering =
                if (state.webSettings.desktopWebSettings.offScreenRendering) {
                    CefRendering.OFFSCREEN
                } else {
                    CefRendering.DEFAULT
                }

            when (val current = state.content) {
                is WebContent.Url ->
                    // WAVORA FORK (Escenario B): sin el 4to argumento (context).
                    // Antes: createModifiedRequestContext(state.webSettings) acá
                    // forzaba un CefRequestContext privado. Ahora, sin context,
                    // KCEFClient.createBrowser(url, rendering, transparent) usa el
                    // overload de 3 argumentos (KCEFClient.kt) → CefClient usa su
                    // contexto por defecto → el global → el mismo que
                    // CefCookieManager.getGlobalManager() / webViewState.cookieManager.
                    // El User-Agent custom lo sigue aplicando el
                    // CefRequestHandler agregado arriba, a nivel de client.
                    client?.createBrowser(
                        current.url,
                        rendering,
                        state.webSettings.desktopWebSettings.transparent,
                    )

                is WebContent.Data ->
                    client?.createBrowserWithHtml(
                        current.data,
                        current.baseUrl ?: KCEFBrowser.BLANK_URI,
                        rendering,
                        state.webSettings.desktopWebSettings.transparent,
                    )

                is WebContent.File ->
                    client?.createBrowserWithHtml(
                        fileContent,
                        KCEFBrowser.BLANK_URI,
                        rendering,
                        state.webSettings.desktopWebSettings.transparent,
                    )

                else -> {
                    // WAVORA FORK (Escenario B): mismo cambio que arriba, sin
                    // context — ver comentario en la rama WebContent.Url.
                    client?.createBrowser(
                        KCEFBrowser.BLANK_URI,
                        rendering,
                        state.webSettings.desktopWebSettings.transparent,
                    )
                }
            }
        }
    val desktopWebView =
        remember(browser) {
            if (browser != null) {
                DesktopWebView(browser, scope, webViewJsBridge)
            } else {
                null
            }
        }

    browser?.let {
        SwingPanel(
            factory = {
                state.webView = desktopWebView
                webViewJsBridge?.webView = desktopWebView
                browser.apply {
                    addDisplayHandler(state)
                    addLoadListener(state, navigator)
                }
                onCreated()
                browser.uiComponent
            },
            modifier = modifier,
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            client?.dispose()
            currentOnDispose()
        }
    }
}
