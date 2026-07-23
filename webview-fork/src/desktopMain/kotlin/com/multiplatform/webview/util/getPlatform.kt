package com.multiplatform.webview.util

// WAVORA FORK: sin `actual` — este módulo es Kotlin/JVM plano (un solo
// target), no kotlin-multiplatform, así que no hay declaración `expect`
// correspondiente (se sacó de util/Platform.kt). Mismo comportamiento que
// el original de la 1.8.4, solo sin el par expect/actual.
internal fun getPlatform(): Platform {
    return Platform.Desktop
}
