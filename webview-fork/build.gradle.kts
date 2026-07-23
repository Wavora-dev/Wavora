// WAVORA FORK — reemplaza a la dependencia externa
// io.github.kevinnzou:compose-webview-multiplatform:1.8.4 en composeApp.
//
// Es el mismo código fuente que esa versión 1.8.4 (tag 1.8.4 del repo
// KevinnZou/compose-webview-multiplatform), pero solo con los targets que
// Wavora realmente usa (desktop/JVM — no android/iOS, que la lib original
// sí publica pero que composeApp nunca consume), y con los dos archivos
// parcheados de CefRequestExt.kt / WebView.desktop.kt (Escenario B: saca el
// CefRequestContext privado, mueve el override de User-Agent a
// CefRequestHandler a nivel de CefClient).
//
// Estructura: mismo split commonMain/desktopMain que la librería original,
// compilado como un módulo Kotlin/JVM plano (no kotlin-multiplatform) —
// mismo patrón que ya usan :media-jvm y :media-jvm-ui en este proyecto.
plugins {
    alias(libs.plugins.jetbrains.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

sourceSets {
    main {
        kotlin.srcDirs("src/commonMain/kotlin", "src/desktopMain/kotlin")
    }
}

dependencies {
    implementation(compose.desktop.common)
    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.material)
    @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
    implementation(compose.components.resources)

    // Misma versión que composeApp fuerza explícitamente (ver AUDIT NOTE
    // ahí sobre Ktor/hardware) — se declara acá también como `api` para
    // que las clases dev.datlag.kcef.* usadas en las firmas públicas de
    // este módulo (KCEF, KCEFBrowser, KCEFCookieManager, etc.) queden
    // expuestas a quien lo consuma, igual que en la librería original.
    api("dev.datlag:kcef:2024.04.20.4")

    implementation(libs.kermit.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutinesSwing)
}
