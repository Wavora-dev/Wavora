@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import com.codingfeline.buildkonfig.compiler.FieldSpec.Type.INT
import com.codingfeline.buildkonfig.compiler.FieldSpec.Type.STRING
import org.gradle.api.file.RelativePath
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.net.URI
import java.util.Properties

val isFullBuild: Boolean =
    try {
        extra["isFullBuild"] == "true"
    } catch (e: Exception) {
        false
    }

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.aboutlibraries.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.build.config)
    alias(libs.plugins.osdetector)
    alias(libs.plugins.packagedeps)
    alias(libs.plugins.vlc.setup)
}

// composeApp uses the `android.kotlin.multiplatform.library` plugin, so with the
// default `generateResClass = auto` Compose skips generating the `Res` class
// (it treats a KMP *library* module as not owning the public resource class).
// Force it to `always` so `Res` is generated for this app module.
compose.resources {
    generateResClass = always
}

// See desktopApp/build.gradle.kts for the full explanation: libs.native.tray
// transitively pulls an older org.jetbrains.compose.ui, which can shadow the
// project's own compose-ui jar on the runtime classpath and crash with
// NoSuchMethodError inside NativeTray's composable icon rendering. Pin it
// here too since native-tray is also declared in this module.
configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.compose.ui" ||
            requested.group == "org.jetbrains.compose.runtime" ||
            requested.group == "org.jetbrains.compose.foundation" ||
            requested.group == "org.jetbrains.compose.animation"
        ) {
            useVersion(libs.versions.composeMultiplatform.get())
            because("Pin compose-ui transitively pulled in by libs.native.tray to the project's Compose Multiplatform version to avoid duplicate compose-ui jars on the runtime classpath.")
        }
        // NOTE: org.jetbrains.compose.material and org.jetbrains.compose.material3 are
        // intentionally excluded above. Unlike ui/runtime/foundation/animation, those two
        // artifact families do NOT share Compose Multiplatform's version number (e.g. CMP
        // 1.11.1 pairs with material3 1.11.0-alpha07). Forcing them to
        // composeMultiplatform's version resolves to an unpublished artifact and breaks
        // the build. They're pinned to their correct, independent versions via the
        // root build.gradle.kts `force()` block instead.
    }
}

kotlin {
    compilerOptions {
        // -Xwhen-guards, -Xcontext-parameters, -Xmulti-dollar-interpolation are now stable
        // in Kotlin 2.4 — the compiler flags them as redundant. Removed to suppress warnings
        // and marginally speed up incremental compilation metadata processing.
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
    android {
        namespace = "com.wavora.app.composeapp"
        compileSdk = 37
        minSdk = 26
        withJava()
        androidResources {
            enable = true
        }
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

//    listOf(
//        iosArm64(),
//        iosSimulatorArm64()
//    ).forEach { iosTarget ->
//        iosTarget.binaries.framework {
//            baseName = "ComposeApp"
//            isStatic = true
//        }
//    }

    jvm {
        // AUDIT NOTE: este target jvm() (usado directamente por
        // :desktopApp) queda explícito en bytecode 21, igual que el resto
        // del proyecto. Ver desktopApp/build.gradle.kts para el detalle
        // completo de por qué el runtime terminó siendo JBR 21 y no JBR 17
        // (una dependencia de terceros - cmptoast - viene precompilada a
        // Java 21).
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_21)
                }
            }
        }
    }

    sourceSets {
        dependencies {
            val composeBom = project.dependencies.platform(libs.compose.bom)
            val koinBom = project.dependencies.platform(libs.koin.bom)
            implementation(composeBom)
            implementation(koinBom)
            implementation(libs.commons.io)
        }
        androidMain.dependencies {
            api(project.dependencies.platform(libs.koin.bom))
            api(libs.koin.android)
            implementation(libs.koin.androidx.compose)

            implementation(libs.jetbrains.ui.tooling.preview)
            implementation(libs.constraintlayout.compose)

            api(libs.work.runtime.ktx)

            // Runtime
            api(libs.startup.runtime)

            api(projects.media3)
            api(projects.media3Ui)
        }
        commonMain.dependencies {
            implementation(libs.runtime)
            implementation(libs.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.components.resources)
            implementation(libs.jetbrains.ui.tooling.preview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)

            // Compose
            implementation(libs.compose.material3.adaptive)
            implementation(libs.compose.material.ripple)
            implementation(libs.compose.material.icons.core)
            implementation(libs.compose.material.icons.extended)

            implementation(libs.ui.tooling.preview)

            // Other module
            api(projects.common)
            api(projects.domain)
            implementation(projects.data)
            implementation(projects.kizzy)

            // Navigation Compose
            implementation(libs.navigation.compose)

            // Kotlin Serialization
            implementation(libs.kotlinx.serialization.json)

            // Coil
            api(libs.coil.compose)
            api(libs.coil.network.okhttp)
            api(libs.kmpalette.core)
            api(libs.kmpalette.network)
            implementation(libs.ktor.client.cio)

            // DataStore
            implementation(libs.datastore.preferences)

            // Lottie
            implementation(libs.compottie)
            implementation(libs.compottie.dot)
            implementation(libs.compottie.network)
            implementation(libs.compottie.resources)

            // Paging 3
            implementation(libs.androidx.paging.common)
            implementation(libs.paging.compose)

            implementation(libs.aboutlibraries)
            implementation(libs.aboutlibraries.compose.m3)

            // Koin
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)

            // Jetbrains Markdown
            api(libs.markdown)

            // Blur Haze
            implementation(libs.haze)
            implementation(libs.haze.material)

            api(libs.cmptoast)
            implementation(libs.file.picker)

            // Liquid glass
            implementation(libs.liquid.glass)
            implementation(libs.liquid.glass.shape)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            // Desktop app entry (main.kt), VLC setup, jpackage/Conveyor
            // packaging, and tray icon live in :desktopApp per the
            // JetBrains 2026 KMP default structure. This module keeps the
            // shared JVM UI + expect/actuals and their direct dependencies.
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
            implementation(libs.sentry.jvm)
            implementation(libs.native.tray)
            implementation(projects.mediaJvmUi)
            // Conveyor's official Control API (see AppUpdate.jvm.kt) — lets
            // us genuinely ask "is Conveyor's own update mechanism usable
            // right now, and does it see a newer version at the configured
            // site?" instead of guessing. Verified real coordinates/API,
            // not a placeholder: https://conveyor.hydraulic.dev/16.0/control-api-jvm/
            implementation("dev.hydraulic.conveyor:conveyor-control:1.1")
            // JNA (already used elsewhere in this project, e.g.
            // core/data's macOS media integration) - here specifically for
            // Shell32.ShellExecuteEx("runas") to elevate WavoraUpdater.exe
            // without going through PowerShell (see AppUpdate.jvm.kt).
            implementation(libs.jna)
            implementation(libs.jna.platform)
            // WebView real (KCEF/Chromium embebido) para el login de
            // YouTube Music en Desktop - ver
            // Login_YTMusic_Desktop_Fases1-4.md para la justificación
            // completa de por qué esta librería y no KCEF directo ni
            // un WebView nativo por SO. El repositorio jogamp que
            // necesita ya estaba declarado en settings.gradle.kts.
            // VERIFICAR versión vigente al momento de agregar esto -
            // 1.8.4 es la última confirmada en la documentación oficial
            // al momento de esta investigación (jul. 2026).
            // AUDIT NOTE (causa raíz real, confirmada con evidencia real):
            // compose-webview-multiplatform:1.8.4 trae por transitividad
            // dev.datlag:kcef:2024.01.07.1 (enero 2024), compilado contra
            // Ktor 2.x. En Ktor 2.x, ContentNegotiation era una clase/objeto
            // real; en Ktor 3.x (que el resto de este proyecto usa, 3.5.0)
            // pasó a ser una propiedad de nivel superior, no una clase - por
            // eso KCEF 2024.01.07.1 tira NoClassDefFoundError buscando una
            // clase que ya no existe con esa forma en Ktor 3.5.0.
            // Confirmado abriendo el jar de content-negotiation-jvm-3.5.0.jar
            // real: no contiene ContentNegotiation.class, solo
            // ContentNegotiationConfig.class/ContentNegotiationKt.class. No
            // es un problema de "falta la dependencia" (ya estaba presente,
            // con ruta correcta, confirmado con jar tf) sino de
            // incompatibilidad binaria entre versiones. Forzamos KCEF
            // 2025.03.23 en su momento (la más nueva en Maven Central en esa
            // fecha), compilada contra Ktor 3.x compatible con 3.5.0 - eso
            // resolvió el NoClassDefFoundError.
            // AUDIT NOTE (Ronda 17 - downgrade real, no relacionado a Ktor):
            // 2025.03.23 crasheaba de forma 100% reproducible en el
            // hardware real de Sebastián (i5-6200U + GTX 940MX, Optimus,
            // Windows 10 22H2) con STATUS_BREAKPOINT dentro de libcef.dll,
            // siempre en el mismo offset exacto, confirmado con Visor de
            // Eventos - independiente de JVM/bytecode/sandbox/banderas de
            // GPU/antivirus (todos descartados con evidencia real en
            // rondas anteriores). El propio changelog de 2025.03.23 dice
            // "Bumped jcef version" - un salto real de versión de Chromium.
            // Se bajó a v2024.04.20.4 (release previo, confirmado por
            // fuente clonada de KCEF que sigue usando Ktor 3.0.0 - mismo
            // major que 3.5.0, no reintroduce el bug de ContentNegotiation)
            // con la esperanza de que un Chromium más viejo sea compatible
            // con este hardware específico. Pendiente confirmar con el
            // próximo log si esto evita el crash.
            implementation("dev.datlag:kcef:2024.04.20.4")
            // WAVORA FORK (Escenario B, confirmado contra el código fuente
            // real de la librería/KCEF/jcef): la 1.8.4 stock crea un
            // CefRequestContext privado (con su propio cookie store,
            // DISTINTO del global que lee webViewState.cookieManager /
            // DesktopCookieManager) para poder aplicar el User-Agent
            // custom. Reemplazada por :webview-fork, un módulo local con
            // el mismo código de la 1.8.4 pero con el override de
            // User-Agent movido a CefRequestHandler a nivel de CefClient
            // (mismo mecanismo público que ya se usa acá mismo para
            // addLifeSpanHandler) — el browser queda en el contexto por
            // defecto del client, que es el global. Ver
            // webview-fork/build.gradle.kts y los comentarios "WAVORA
            // FORK" dentro de CefRequestExt.kt / WebView.desktop.kt para
            // el detalle y la verificación completa. Mismo groupId de
            // paquete (com.multiplatform.webview.*), así que no hace
            // falta tocar ningún import en el resto de Wavora.
            implementation(projects.webviewFork)
            // Ver nota arriba: esto sigue siendo necesario para que KCEF
            // pueda descargar su bundle de Chromium, independientemente de
            // qué versión de KCEF termine resolviendo.
            implementation(libs.ktor.client.content.negotiation)
            // Agregado de forma preventiva junto con ContentNegotiation
            // (mismo patrón que ya usa kotlinYtmusicScraper para su
            // propio HttpClient) - no tengo forma de confirmar sin
            // decompilar KCEF si su llamada interna realmente negocia
            // JSON, pero es el emparejamiento estándar de Ktor y el
            // costo de agregarlo de más es mínimo comparado con otro
            // ida y vuelta de NoClassDefFoundError.
            implementation(libs.ktor.serialization.kotlinx.json)
        }
    }
}

// NOTE: compose.desktop{} application block, ProGuard config,
// linuxDebConfig{}, the custom AppImage tooling, and Conveyor packaging
// live in :desktopApp per the JetBrains 2026 KMP default structure.
//
// vlcSetup{} stays in :composeApp — moving it to :desktopApp fails
// because vlc.setup eagerly iterates tasks at apply time, which force-
// realizes Conveyor's lazily-registered writeConveyorConfig task before
// kotlin.multiplatform has created jvmJar → "Task with name 'jar' not
// found" (reproduced 2026-05-21 with vlc.setup placed last in the
// plugins block — plugin order does not help). composeApp has no
// Conveyor so the conflict cannot occur here.
//
// Run `./gradlew :composeApp:vlcSetup --no-configuration-cache` to
// populate vlc-natives/{linux-x64,macos-<hostArch>,windows-x64}/. Layout
// is per-arch so Conveyor can bundle the right native slice into each
// per-machine installer (universal Mac dylibs almost doubled artifact
// size pre-split — see commit message for context).
val hostMacArchDir = if (System.getProperty("os.arch").lowercase().contains("aarch64")) {
    "macos-arm64"
} else {
    "macos-x64"
}
val hostWinArchDir = if (System.getProperty("os.arch").lowercase().contains("aarch64")) {
    "windows-arm64"
} else {
    "windows-x64"
}
vlcSetup {
    vlcVersion = libs.versions.vlc.get()
    shouldCompressVlcFiles = false
    shouldIncludeAllVlcFiles = true
    pathToCopyVlcLinuxFilesTo   = rootDir.resolve("vlc-natives/linux-x64/")
    pathToCopyVlcMacosFilesTo   = rootDir.resolve("vlc-natives/$hostMacArchDir/")
    pathToCopyVlcWindowsFilesTo = rootDir.resolve("vlc-natives/$hostWinArchDir/")
}

// Flatten vlc-natives/<arch>/vlc/plugins → vlc-natives/<arch>/plugins after
// vlc-setup copies files. The plugin ships a nested vlc/ subdir for VLC's
// own path resolution, but Conveyor then copies both the nested tree AND
// extracts the .so files flat at the parent → 2× duplication (~348 MB
// extra) in the packaged AppImage. Flat layout keeps Conveyor lean while
// VLCJ still resolves libs via DefaultVlcDiscoverer.
tasks.named("vlcSetup").configure {
    doLast {
        listOf("linux-x64", hostMacArchDir, hostWinArchDir).forEach { archDir ->
            val root = rootDir.resolve("vlc-natives/$archDir")
            val nested = root.resolve("vlc")
            if (nested.isDirectory) {
                nested.listFiles()?.forEach { child ->
                    val target = root.resolve(child.name)
                    if (target.exists()) target.deleteRecursively()
                    child.renameTo(target)
                }
                nested.deleteRecursively()
            }
        }
    }
}

// ============================================================================
// Cross-OS VLC natives downloader (single-runner CI)
// ============================================================================
// The `vlc-setup` plugin above only registers `vlcSetup` for the HOST OS
// (LinuxTasksConfigure / MacTasksConfigure / WindowsTasksConfigure each
// gate on `getCurrentOs() == OS.X`). To package multi-OS artifacts from a
// single CI runner (Linux), we replicate the plugin's download + filter +
// copy logic for the OTHER two OSes here.
//
// Resulting layout matches the upstream plugin so VLCJ's
// DefaultVlcDiscoverer keeps working unchanged.
//
// Local dev: keep using `./gradlew :composeApp:vlcSetup` (host-OS only).
// Cross-OS CI: use `./gradlew :composeApp:vlcSetupAll`.
//
// Mac DMG extraction needs the 7z tool:
//   Ubuntu CI:   sudo apt-get install -y p7zip-full
//   macOS local: brew install p7zip
val vlcCacheDir = layout.buildDirectory.dir("vlc-cache")

fun downloadIfMissing(url: String, target: java.io.File) {
    if (target.exists() && target.length() > 0) {
        logger.lifecycle("[vlc-multi] Cached: ${target.name}")
        return
    }
    logger.lifecycle("[vlc-multi] Downloading $url")
    target.parentFile.mkdirs()
    // Use curl instead of Java's URL.openStream() because get.videolan.org
    // returns a 302 redirect to a random mirror per request, and Java's
    // default HttpURLConnection redirect handling is fragile — if the
    // mirror lands on a cross-protocol redirect or returns an HTML error
    // page, openStream() silently saves the HTML response as the target
    // file, producing a "Cannot expand ZIP" downstream. curl's `-L`
    // follows redirects robustly across protocols + mirrors, `--fail`
    // exits non-zero on HTTP errors instead of saving error bodies, and
    // `-o` writes atomically via tmp file. The downloaded artifact is
    // also size-verified (must match Content-Length).
    val curlExit = ProcessBuilder(
        "curl",
        "-fsSL",
        "--retry", "3",
        "--retry-delay", "2",
        "-o", target.absolutePath,
        url,
    ).inheritIO().start().waitFor()
    check(curlExit == 0 && target.exists() && target.length() > 0) {
        // Delete partial/empty file so the next run can retry cleanly.
        if (target.exists()) target.delete()
        "curl failed (exit $curlExit) downloading $url to $target"
    }
}

val vlcSetupLinuxCi by tasks.registering {
    group = "vlc-multi"
    description = "Cross-OS: populate vlc-natives/linux-x64/ with .so files."
    val outputDir = rootDir.resolve("vlc-natives/linux-x64/")
    outputs.dir(outputDir)
    doLast {
        // Pinned upstream — Linux artifact is a custom Maven package whose
        // version is independent of the desktop VLC release.
        val linuxVersion = "3.0.20-2"
        val cache = vlcCacheDir.get().asFile
        val jar = cache.resolve("vlc-plugins-linux-$linuxVersion.jar")
        downloadIfMissing(
            "https://repo1.maven.org/maven2/ir/mahozad/vlc-plugins-linux/$linuxVersion/vlc-plugins-linux-$linuxVersion.jar",
            jar,
        )
        outputDir.walk().filter { it.extension == "so" }.forEach { it.delete() }
        project.copy {
            from(zipTree(jar))
            into(outputDir)
            // Ship the full VLC plugin set (matches the v1.2.1 release).
            // A curated subset based on upstream vlc-setup defaults turned
            // out to be insufficient for Wavora — YT Music streaming
            // depends on HTTP/HTTPS access + MP4/WebM demuxers that the
            // upstream music-app preset doesn't cover. `**/` is needed
            // because include() evaluates against the original jar paths
            // (which include the `vlc-plugins-linux-<ver>/` top-level dir)
            // before the eachFile drop(1) transformation kicks in.
            include("**/*.so", "**/*.so.*")
            // Strip the top-level dir inside the jar (matches upstream plugin).
            eachFile {
                if (relativePath.segments.size > 1) {
                    relativePath = RelativePath(true, *relativePath.segments.drop(1).toTypedArray())
                }
            }
            includeEmptyDirs = false
        }
        // Flatten vlc-natives/linux-x64/vlc/* → vlc-natives/linux-x64/* (same
        // as the host-OS flatten task above) so Conveyor doesn't duplicate
        // plugins.
        val nested = outputDir.resolve("vlc")
        if (nested.isDirectory) {
            nested.listFiles()?.forEach { child ->
                val target = outputDir.resolve(child.name)
                if (target.exists()) target.deleteRecursively()
                child.renameTo(target)
            }
            nested.deleteRecursively()
        }
    }
}

// Shared Mac DMG download + extraction logic. Each per-arch task calls
// this with its slice's DMG suffix ("arm64" or "intel64") and output
// folder. We pull per-arch DMGs (48-55 MB each) instead of the universal
// DMG (84.9 MB = arm64 + intel64 fat binary), so each per-machine zip
// only ships its own slice — saves ~25-40 MB per user download.
fun extractMacVlcSlice(
    archSuffix: String,
    outputDir: java.io.File,
) {
    val macVersion = libs.versions.vlc.get()
    val cache = vlcCacheDir.get().asFile
    val dmg = cache.resolve("vlc-$macVersion-$archSuffix.dmg")
    downloadIfMissing(
        "https://get.videolan.org/vlc/$macVersion/macosx/vlc-$macVersion-$archSuffix.dmg",
        dmg,
    )
    outputDir.walk().filter { it.extension == "dylib" }.forEach { it.delete() }
    outputDir.mkdirs()
    // Pick the extractor that's native to the host:
    //   • macOS  → hdiutil (built-in, no install required for local dev)
    //   • Linux/Windows CI → 7z (cross-platform HFS+ support, needs
    //     p7zip-full / official 7-Zip 23+ installed on the runner)
    // Both paths drop a directory containing the VLC.app payload at
    // `macOsDir`, ready for the curated copy step below.
    val isMacHost = System.getProperty("os.name").lowercase().contains("mac")
    val macOsDir: java.io.File
    val cleanupMount: (() -> Unit)?
    if (isMacHost) {
        val mountPoint = cache.resolve("vlc-mount-$macVersion-$archSuffix")
        mountPoint.deleteRecursively()
        mountPoint.mkdirs()
        val attachExit = ProcessBuilder(
            "hdiutil", "attach",
            "-mountpoint", mountPoint.absolutePath,
            "-nobrowse", "-quiet",
            dmg.absolutePath,
        ).inheritIO().start().waitFor()
        check(attachExit == 0) {
            "hdiutil attach failed with exit code $attachExit for $dmg"
        }
        macOsDir = mountPoint.resolve("VLC.app/Contents/MacOS")
        cleanupMount = {
            ProcessBuilder("hdiutil", "detach", "-quiet", mountPoint.absolutePath)
                .inheritIO().start().waitFor()
        }
    } else {
        val extractDir = cache.resolve("vlc-mac-$macVersion-$archSuffix-extract")
        extractDir.deleteRecursively()
        extractDir.mkdirs()
        // 7z returns exit code 2 because the DMG contains a "VLC media
        // player/Applications → /Applications" drag-to-install symlink
        // that 7z refuses to extract (dangerous absolute link). The
        // VLC.app payload extracts fine, so we verify by directory
        // presence below rather than trusting the exit code.
        val sevenZipExit = ProcessBuilder(
            "7z", "x", "-y", "-bso0", "-bsp0",
            "-o${extractDir.absolutePath}",
            dmg.absolutePath,
        ).inheritIO().start().waitFor()
        macOsDir = extractDir.walkTopDown()
            .firstOrNull {
                it.isDirectory &&
                    it.name == "MacOS" &&
                    it.parentFile?.name == "Contents" &&
                    it.parentFile?.parentFile?.name == "VLC.app"
            }
            ?: error(
                "VLC.app/Contents/MacOS/ not found inside extracted DMG at $extractDir " +
                        "(7z exit code $sevenZipExit)",
            )
        cleanupMount = null
    }
    check(macOsDir.isDirectory) {
        "VLC.app/Contents/MacOS not found at ${macOsDir.absolutePath}"
    }
    try {
        project.copy {
            from(macOsDir)
            into(outputDir)
            // Ship the full VLC plugin set (matches v1.2.1 release).
            // Curated music-app preset from upstream vlc-setup didn't
            // include HTTP/HTTPS access + MP4/WebM demuxers needed for
            // YT Music streaming.
            include("lib/libvlc.dylib", "lib/libvlccore.dylib", "plugins/**")
            // Flatten lib/ → root (matches upstream Mac VlcSetupTask).
            eachFile {
                if (relativePath.segments.firstOrNull() == "lib") {
                    relativePath = RelativePath(true, *relativePath.segments.drop(1).toTypedArray())
                }
            }
            includeEmptyDirs = false
        }
    } finally {
        cleanupMount?.invoke()
    }
}

val vlcSetupMacArmCi by tasks.registering {
    group = "vlc-multi"
    description = "Cross-OS: populate vlc-natives/macos-arm64/ with Apple Silicon .dylib files."
    val outputDir = rootDir.resolve("vlc-natives/macos-arm64/")
    outputs.dir(outputDir)
    doLast { extractMacVlcSlice("arm64", outputDir) }
}

val vlcSetupMacX64Ci by tasks.registering {
    group = "vlc-multi"
    description = "Cross-OS: populate vlc-natives/macos-x64/ with Intel .dylib files."
    val outputDir = rootDir.resolve("vlc-natives/macos-x64/")
    outputs.dir(outputDir)
    doLast { extractMacVlcSlice("intel64", outputDir) }
}

// Shared Windows VLC zip extraction. VideoLAN ships separate per-arch
// zips (win64/ for x64, winarm64/ for ARM64) — we mirror that layout
// in vlc-natives/ so Conveyor bundles the right slice per msix.
fun extractWindowsVlcSlice(
    archSuffix: String,
    outputDir: java.io.File,
) {
    val winVersion = libs.versions.vlc.get()
    val cache = vlcCacheDir.get().asFile
    // VideoLAN URL layout for Windows:
    //   x64: /vlc/<ver>/win64/vlc-<ver>-win64.zip
    //   arm: /vlc/<ver>/winarm64/vlc-<ver>-winarm64.zip
    // Both zips share the same internal tree shape, so once downloaded
    // the rest of the pipeline is identical.
    val subDir = if (archSuffix == "winarm64") "winarm64" else "win64"
    val zip = cache.resolve("vlc-$winVersion-$archSuffix.zip")
    downloadIfMissing(
        "https://get.videolan.org/vlc/$winVersion/$subDir/vlc-$winVersion-$archSuffix.zip",
        zip,
    )
    outputDir.walk().filter { it.extension == "dll" }.forEach { it.delete() }
    project.copy {
        from(zipTree(zip))
        into(outputDir)
        // Ship the full VLC plugin set (matches v1.2.1 release). The
        // music-app preset from upstream vlc-setup turned out to be
        // missing HTTP/HTTPS access + MP4/WebM demuxers needed for YT
        // Music streaming. `**/` is required because include() runs
        // against the original `vlc-<ver>/...` paths inside the zip
        // before the eachFile drop(1) transformation.
        include("**/*.dll")
        // Strip top-level `vlc-<ver>/` prefix dir.
        eachFile {
            if (relativePath.segments.size > 1) {
                relativePath = RelativePath(true, *relativePath.segments.drop(1).toTypedArray())
            }
        }
        includeEmptyDirs = false
    }
}

val vlcSetupWindowsX64Ci by tasks.registering {
    group = "vlc-multi"
    description = "Cross-OS: populate vlc-natives/windows-x64/ with .dll files."
    val outputDir = rootDir.resolve("vlc-natives/windows-x64/")
    outputs.dir(outputDir)
    doLast { extractWindowsVlcSlice("win64", outputDir) }
}

val vlcSetupWindowsArmCi by tasks.registering {
    group = "vlc-multi"
    description = "Cross-OS: populate vlc-natives/windows-arm64/ with ARM64 .dll files."
    val outputDir = rootDir.resolve("vlc-natives/windows-arm64/")
    outputs.dir(outputDir)
    doLast { extractWindowsVlcSlice("winarm64", outputDir) }
}

val vlcSetupAll by tasks.registering {
    group = "vlc-multi"
    description = "Cross-OS: populate vlc-natives/{linux-x64,macos-arm64,macos-x64,windows-x64,windows-arm64}/ from any host. Use in CI."
    dependsOn(
        vlcSetupLinuxCi,
        vlcSetupMacArmCi,
        vlcSetupMacX64Ci,
        vlcSetupWindowsX64Ci,
        vlcSetupWindowsArmCi,
    )
}

buildkonfig {
    packageName = "com.wavora.app"
    exposeObjectWithName = "BuildKonfig"
    defaultConfigs {
        val versionName =
            libs.versions.version.name
                .get()
        val versionCode =
            libs.versions.version.code
                .get()
                .toInt()
        buildConfigField(STRING, "versionName", versionName)
        buildConfigField(INT, "versionCode", "$versionCode")

        if (isFullBuild) {
            // En CI, SENTRY_DSN llega como variable de entorno (Secret). En
            // desarrollo local, se sigue leyendo de local.properties como antes.
            val dsn =
                System.getenv("SENTRY_DSN")
                    ?: try {
                        val properties = Properties()
                        properties.load(rootProject.file("local.properties").inputStream())
                        properties.getProperty("SENTRY_DSN")
                    } catch (e: Exception) {
                        println("Failed to load SENTRY_DSN from local.properties: ${e.message}")
                        null
                    }
            if (dsn.isNullOrBlank()) {
                println("isFullBuild=true pero no hay SENTRY_DSN (ni env var ni local.properties) — Sentry queda deshabilitado.")
            } else {
                println("Full build detected, enabling Sentry DSN")
            }
            buildConfigField(STRING, "sentryDsn", dsn ?: "")
        } else {
            buildConfigField(STRING, "sentryDsn", "")
        }
    }
}

aboutLibraries {
    collect.configPath = file("../config")
    export {
        outputFile = file("src/commonMain/composeResources/files/aboutlibraries.json")
        prettyPrint = true
        excludeFields = listOf("generated")
    }
    library {
        // Enable the duplication mode, allows to merge, or link dependencies which relate
        duplicationMode = com.mikepenz.aboutlibraries.plugin.DuplicateMode.MERGE
        // Configure the duplication rule, to match "duplicates" with
        duplicationRule = com.mikepenz.aboutlibraries.plugin.DuplicateRule.SIMPLE
    }
}

// Wire BuildKonfig output as input to AGP ArtProfile prepare tasks.
// Required by Gradle 9 strict task dependency validation. BuildKonfig 0.21.0
// migrated to AGP 9.2.1 + Gradle 9.4.1 but doesn't auto-wire
// generateBuildKonfig output to AGP's prepare*ArtProfile tasks.
// Refs: moko-resources#421, AboutLibraries#936.
afterEvaluate {
    tasks.matching { it.name.startsWith("prepare") && it.name.endsWith("ArtProfile") }
        .configureEach {
            dependsOn("generateBuildKonfig")
        }
}


// ---------------------------------------------------------------------------
// Bundle :wavoraUpdater (WavoraUpdater.exe + its own small runtime) as a
// classpath resource inside composeApp's own jar, so AppUpdate.jvm.kt can
// extract it to %LOCALAPPDATA%\Wavora\Updater\ without needing to know
// anything about where/how it was built (see ensureUpdaterInstalled()'s
// doc). Deliberately NOT distributed via Conveyor or as a separate
// standalone download — it rides along inside the main app's own install,
// the same way :wavoraUpdater's own resources (logo, etc.) are bundled -
// see AppUpdate.jvm.kt's ensureUpdaterInstalled().
//
// NOTE (verify on a real build): `createDistributable` is the Compose
// Desktop Gradle plugin's standard task name for a portable jpackage
// "app image" (no installer wrapper) as of the version this project pins
// in gradle/libs.versions.toml. If a future plugin version renames it,
// this reference needs updating - `./gradlew :wavoraUpdater:tasks --all`
// will show the actual name if this fails to resolve.
// NOTE: this used to eagerly resolve `:wavoraUpdater:createDistributable`
// via tasks.getByPath(...) and read its .outputs.files.singleFile at
// CONFIGURATION time - both risky (forces cross-project task realization
// during configuration, the same class of problem this project's own
// desktopApp/build.gradle.kts already has a comment about re: Conveyor's
// writeConveyorConfig; and .outputs.files isn't reliably populated before
// the task has actually run). Rewritten to be fully lazy:
//   - dependsOn(":wavoraUpdater:createDistributable") by task PATH
//     (a String, not a resolved Task) - Gradle resolves this during task
//     graph construction, not during script evaluation, so it never
//     forces :wavoraUpdater's configuration ahead of when it would
//     already happen.
//   - the source directory is a Provider<Directory> built from
//     :wavoraUpdater's own `layout.buildDirectory`, using the exact
//     path Compose Desktop's jpackage app-image task is CONFIRMED to
//     write to (see the real build log in the chat: "The distribution
//     is written to ...\wavoraUpdater\build\compose\binaries\main\app")
//     - not introspected from task outputs at all, just the known,
//     stable convention plus the module's own `packageName`.
val wavoraUpdaterProject = project(":wavoraUpdater")
val wavoraUpdaterDistDir =
    wavoraUpdaterProject.layout.buildDirectory.dir("compose/binaries/main/app/WavoraUpdater")

val wavoraUpdaterResourcesDir = layout.buildDirectory.dir("generated/wavoraUpdaterResources")

val bundleWavoraUpdater =
    tasks.register<Zip>("bundleWavoraUpdater") {
        group = "build"
        description = "Zips :wavoraUpdater's jpackage app-image into a classpath resource for composeApp."
        dependsOn(":wavoraUpdater:createDistributable")

        // Zips the app-image folder's CONTENTS at the zip's root (not
        // nested under another "WavoraUpdater/" level), since
        // ensureUpdaterInstalled() (AppUpdate.jvm.kt) extracts straight
        // into %LOCALAPPDATA%\Wavora\Updater\ expecting WavoraUpdater.exe
        // at the top.
        from(wavoraUpdaterDistDir)
        archiveFileName.set("wavora-updater.zip")
        destinationDirectory.set(wavoraUpdaterResourcesDir)
    }

kotlin.sourceSets.getByName("jvmMain").resources.srcDir(wavoraUpdaterResourcesDir)

// KMP's JVM target resource-processing task - verify with
// `./gradlew :composeApp:tasks --all` if this exact name ever changes
// across Kotlin Gradle plugin versions.
tasks.matching { it.name == "jvmProcessResources" }.configureEach {
    dependsOn(bundleWavoraUpdater)
}
