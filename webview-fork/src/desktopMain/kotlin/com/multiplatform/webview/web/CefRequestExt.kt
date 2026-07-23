package com.multiplatform.webview.web

import com.multiplatform.webview.setting.WebSettings
import dev.datlag.kcef.KCEFResourceRequestHandler
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefRequestHandlerAdapter
import org.cef.handler.CefResourceRequestHandler
import org.cef.misc.BoolRef
import org.cef.network.CefRequest

// ============================================================================
// WAVORA FORK — compose-webview-multiplatform 1.8.4, Escenario B
// ----------------------------------------------------------------------------
// ORIGINAL: este archivo creaba un CefRequestContext PRIVADO vía
// CefRequestContext.createContext(...), pasado luego a
// KCEFClient.createBrowser(url, rendering, transparent, context) en
// WebView.desktop.kt. Un CefRequestContext no-global (isGlobal() == false)
// tiene su propio cookie store, DISTINTO del que devuelve
// CefCookieManager.getGlobalManager() — que es el que siempre lee
// DesktopCookieManager (KCEFCookieManager.instance), o sea
// webViewState.cookieManager. Resultado: el browser real nunca guardaba sus
// cookies donde el resto de la app las leía (Escenario B, confirmado contra
// el código fuente real de compose-webview-multiplatform 1.8.4,
// dev.datlag:kcef 2024.04.20.4 y su submódulo jcef — ningún método público
// existe para leer el cookie manager de un CefRequestContext privado, así
// que "arreglar" el manager en vez de sacar el contexto privado no era
// viable).
//
// FIX: se verificó que CefRequestContextHandler (la interfaz que
// implementaba el context privado) es de UN SOLO método —
// getResourceRequestHandler — y que ese único método solo se usaba acá para
// setear el header User-Agent custom. No hay ningún otro efecto colateral
// que se pierda al sacar el context (confirmado: ningún otro archivo del
// módulo desktop referencia CefRequestContext).
//
// La propia doc de CefRequestContextHandler.getResourceRequestHandler
// (jcef, org/cef/handler/CefRequestContextHandler.java) dice textualmente:
// "This method will not be called if the client associated with |browser|
// returns a non-null value from CefRequestHandler.getResourceRequestHandler
// for the same request." — es decir, el handler a nivel de CefClient tiene
// prioridad y es completamente independiente del RequestContext del
// browser. Por eso acá reemplazamos createModifiedRequestContext(settings):
// CefRequestContext por createUserAgentRequestHandler(settings):
// CefRequestHandlerAdapter, para registrar en el CLIENT (vía
// client.addRequestHandler(...), ya público en KCEFClient) el mismo
// override de User-Agent, sin crear ningún CefRequestContext nuevo.
//
// Con esto el browser se crea sin context propio (ver WebView.desktop.kt,
// overload de 3 argumentos de createBrowser) y usa el contexto POR DEFECTO
// del CefClient, que es el global — el mismo que ya lee
// webViewState.cookieManager.
// ============================================================================
internal fun createUserAgentRequestHandler(settings: WebSettings): CefRequestHandlerAdapter {
    return object : CefRequestHandlerAdapter() {
        override fun getResourceRequestHandler(
            browser: CefBrowser?,
            frame: CefFrame?,
            request: CefRequest?,
            isNavigation: Boolean,
            isDownload: Boolean,
            requestInitiator: String?,
            disableDefaultHandling: BoolRef?,
        ): CefResourceRequestHandler {
            return object : KCEFResourceRequestHandler(
                KCEFResourceRequestHandler.getGlobalDefaultHandler(
                    browser,
                    frame,
                    request,
                    isNavigation,
                    isDownload,
                    requestInitiator,
                    disableDefaultHandling,
                ),
            ) {
                override fun onBeforeResourceLoad(
                    browser: CefBrowser?,
                    frame: CefFrame?,
                    request: CefRequest?,
                ): Boolean {
                    if (request != null) {
                        settings.customUserAgentString?.let(request::setUserAgentString)
                    }
                    return super.onBeforeResourceLoad(browser, frame, request)
                }
            }
        }
    }
}

internal fun CefRequest.setUserAgentString(userAgent: String) {
    setHeaderByName("User-Agent", userAgent, true)
}
