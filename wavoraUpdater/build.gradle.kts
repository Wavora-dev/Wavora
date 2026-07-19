import org.jetbrains.compose.desktop.application.dsl.TargetFormat

// wavoraUpdater — WavoraUpdater.exe, the native fallback updater used only
// when Conveyor's own update mechanism isn't available (see
// composeApp/src/jvmMain/kotlin/com/wavora/app/expect/AppUpdate.jvm.kt).
//
// Deliberately its OWN small module, NOT a dependency of :composeApp or
// :desktopApp, and deliberately NOT reusing either of those modules'
// dependencies:
//   - no VLC / media-jvm(-ui)
//   - no Sentry
//   - no native-tray
//   - no networking/scraping/lyrics/spotify services
//   - no shared Wavora ViewModels or DI graph (Koin)
// It only needs: a tiny Compose UI (reusing the Splash's *look*, not its
// code, since the Splash composable itself pulls in the shared theme
// module and everything that drags in) and plain JDK APIs for
// networking/zip/hashing. This keeps its own jlink'd runtime as small as
// jpackage allows instead of inheriting the ~150-200MB footprint of the
// full app.
//
// Packaged as a plain jpackage "app image" (portable folder, no
// installer wrapper) via the Compose Desktop Gradle plugin -
// deliberately NOT through Conveyor: Conveyor is for distributing/
// updating the *main* app with its own signing and update-site
// machinery; this is a small auxiliary tool that :composeApp bundles as
// a resource and copies out to %LOCALAPPDATA%\Wavora\Updater\ itself
// (see AppUpdate.jvm.kt) - it never needs its own installer, signing, or
// update mechanism.
plugins {
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.jetbrains.kotlin.jvm)
}

version = libs.versions.version.name.get().removeSuffix("-hf")

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(libs.foundation)
    implementation(libs.compose.material3)
    implementation(libs.runtime)
    implementation(libs.kotlinx.coroutinesSwing)
    // Only for launching Wavora at the end via its AppUserModelID - no
    // COM/WinRT interop needed for that specific call (shell:AppsFolder
    // is a plain shell path Windows resolves for us).
}

compose.desktop {
    application {
        mainClass = "com.wavora.updater.MainKt"

        // AUDIT NOTE ("El nombre de archivo, el nombre de directorio o la
        // sintaxis de la etiqueta del volumen no son correctos" / Windows
        // ERROR_INVALID_NAME 123 durante la actualización): sin esto, la JVM
        // embebida por jpackage hereda el codepage ANSI del sistema para
        // sun.jnu.encoding en vez de UTF-8. runInstallScript() en
        // UpdaterLogic.kt arma un ProcessBuilder("powershell.exe", ...,
        // "-File", installScript.absolutePath) donde ese path vive bajo
        // java.io.tmpdir (típicamente
        // C:\Users\<usuario>\AppData\Local\Temp\...). Si el nombre de usuario
        // de Windows tiene un caracter no-ASCII (ej. una tilde), un
        // sun.jnu.encoding mal configurado hace que ProcessBuilder lo mangle
        // a "?" al construir el argumento de línea de comandos - y "?" es un
        // caracter inválido en rutas/nombres de Windows, lo que dispara
        // exactamente ese error nativo al intentar ejecutar el script.
        // Forzar UTF-8 explícitamente evita el problema independientemente
        // del codepage del sistema.
        jvmArgs += listOf(
            "-Dfile.encoding=UTF-8",
            "-Dsun.jnu.encoding=UTF-8",
        )

        nativeDistributions {
            // AppImage here is the Compose Desktop Gradle plugin's generic
            // "portable app image" target (a runnable folder, no OS
            // installer wrapper) - available for every OS the plugin
            // targets, not just Linux. This is intentionally NOT Msi/Exe
            // (that would try to build a Windows *installer*, which is
            // not what we want: we just need a folder we can zip, bundle,
            // and xcopy to %LOCALAPPDATA%).
            targetFormats(TargetFormat.AppImage)
            packageName = "WavoraUpdater"
            packageVersion = libs.versions.version.name.get().removeSuffix("-hf")
            description = "Wavora - actualizador de respaldo (solo se usa si Conveyor no está disponible)"
            copyright = "Wavora"
        }
    }
}
