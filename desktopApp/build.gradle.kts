@file:Suppress("UnstableApiUsage")

import org.apache.commons.io.FileUtils
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JvmVendorSpec
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask
import java.net.URI
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// desktopApp — JVM application module for Wavora Desktop.
//
// Per JetBrains 2026 KMP guidance (AGP 9 + new default structure), the
// platform-app entry points live in dedicated modules separate from the
// shared KMP library. This module owns:
//
//   * the JVM main() entry
//   * compose.desktop.application packaging (jpackage path, still used
//     until Conveyor cutover lands in Task 14)
//   * VLC native bundling (vlc-setup)
//   * desktop-only UI (CustomTitleBar, MiniPlayerWindow, CrashDialog, etc.)
//
// composeApp remains a pure KMP library — its src/jvmMain only carries the
// expect/actual implementations the shared code needs on the JVM.
//
// Refs:
//   https://blog.jetbrains.com/kotlin/2026/05/new-kmp-default-structure/
//   https://github.com/HaroonBsf/kmp-conveyor-template
plugins {
    // Order matters: per the Bifrost reference (a known-good Conveyor 2.0
    // + KMP setup), `conveyor` must apply BEFORE `kotlin.multiplatform`.
    // Conveyor's task creation runs at apply time and only succeeds when
    // a `jar` task already exists — the compose plugin (applied first)
    // provides that, while kotlin.multiplatform would override it later
    // with `jvmJar` if applied before conveyor.
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.conveyor)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.multiplatform)
    // AUDIT NOTE (login de YouTube Music en Desktop / KCEF): agrega
    // ktor-client-content-negotiation al proyecto empujó el classpath
    // por encima del límite de línea de comandos de Windows, y el
    // mecanismo por default de Gradle para eso ("pathing jar" con
    // Class-Path en el manifest) tiene un bug conocido y documentado
    // donde algunas clases no se resuelven aunque el jar exista en
    // disco (confirmado con diagnóstico real: la ruta del jar de
    // content-negotiation existe, pero Class.forName no la encuentra -
    // ver https://github.com/gradle/gradle/issues/10114 y #34003). Este
    // plugin reemplaza ese mecanismo por un archivo de argumentos
    // (@file), soportado nativamente desde Java 9, que no tiene este
    // problema. OJO: solo funciona con la configuration cache
    // desactivada (ver el comentario en gradle.properties).
    id("com.redock.classpathtofile") version "0.1.0"
    // NOTE: `vlc.setup` lives in :composeApp (not here) because its eager
    // task iteration at apply time triggers Conveyor's writeConveyorConfig
    // creation, which then fails with "Task with name 'jar' not found" —
    // jvmJar isn't created until after the script body's `kotlin {}` block
    // runs. Plugin order tricks (vlc.setup last, Bifrost ordering) don't
    // help because vlc.setup's iteration force-realizes EVERY existing
    // task, including the lazily-registered Conveyor ones. Confirmed by
    // retry on 2026-05-21: same error reproduced. Run vlcSetup via
    // `./gradlew :composeApp:vlcSetup --no-configuration-cache`.
}

version = libs.versions.version.name.get().removeSuffix("-hf")

kotlin {
    // AUDIT NOTE (login de YouTube Music en Desktop / KCEF - crash nativo
    // persistente con exit code -2147483645 / STATUS_BREAKPOINT en
    // libcef.dll justo al inicializar CefApp): la release de KCEF
    // 2025.03.23 dice ser compatible con jbr-release-17.0.14b1367.22
    // (JBR basado en JDK 17). Bajar el TOOLCHAIN DE COMPILACIÓN de todo
    // :desktopApp a 17 resultó ser el camino equivocado - Gradle exige
    // entonces que TODA la cadena de dependencias (incluidos
    // core/common, core/domain, core/data, compartidos con Android)
    // también sea compatible con 17, un efecto cascada demasiado
    // riesgoso para lo que se busca acá. En su lugar, dejamos el
    // toolchain de COMPILACIÓN en 21 (sin cambios).
    // 2ª vuelta: el plan original era ejecutar con un `javaLauncher`/
    // `executable` de JBR 17 (compilando igual en 21), asumiendo que
    // bytecode 21 corre sin problema sobre un runtime 17 - ESO ERA
    // INCORRECTO, una JVM nunca carga class files de una versión más
    // nueva que la suya (confirmado con un crash real,
    // UnsupportedClassVersionError, apenas se cargó la primera clase de
    // la app). Además, `network.chaintech:cmptoast` (dependencia de
    // terceros) viene precompilada a Java 21, así que ni bajando TODO
    // nuestro propio bytecode a 17 se hubiera solucionado del todo. Se
    // optó entonces por ejecutar con JBR 21 en vez de JBR 17 (ver
    // `setExecutable` más abajo) - JetBrains también publica JBR en la
    // línea 21, con soporte JCEF, así que sigue siendo un runtime válido
    // para probar si arregla el crash de libcef.dll.
    jvmToolchain(21)

    // KMP jvm() target — Conveyor 2.0's writeConveyorConfig task looks up
    // `jvmRuntimeClasspath` (KMP convention), so we use the KMP plugin here
    // even though desktopApp only has a single JVM target. Pattern adapted
    // from https://github.com/zacharee/Bifrost desktop module.
    jvm {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    // AUDIT NOTE (2ª vuelta): la vuelta anterior había bajado
                    // esto a 17 para matchear el runtime JBR 17 que se
                    // probaba en ese momento (ver el comentario grande más
                    // abajo, junto a `setExecutable`, sobre por qué se
                    // volvió a subir el runtime a JBR 21). Bajar solo NUESTRO
                    // bytecode no alcanza cuando una dependencia de terceros
                    // (cmptoast) viene precompilada a Java 21 - por eso se
                    // revierte este valor a 21, para matchear otra vez el
                    // `jvmToolchain(21)` de arriba y el runtime JBR 21.
                    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
                }
            }
        }
    }

    sourceSets {
        val jvmMain by getting {
            dependencies {
                // Shared KMP library — pulls App.kt, expect/actual impls,
                // view-models, MiniPlayer state object, etc.
                implementation(project(":composeApp"))

                // Compose Desktop runtime for the current OS.
                implementation(compose.desktop.currentOs)

                // Swing dispatcher for kotlinx.coroutines on the JVM.
                implementation(libs.kotlinx.coroutinesSwing)

                // Sentry crash reporting (full builds only via BuildKonfig).
                implementation(libs.sentry.jvm)

                // System tray icon for desktop builds.
                implementation(libs.native.tray)

                // Media player JVM UI primitives consumed by jvmMain expect/actuals.
                implementation(projects.mediaJvmUi)

                // Commons-IO drives the custom AppImage packaging task below.
                implementation(libs.commons.io)
                // org.json is bundled in the Android SDK but missing on JVM desktop; PipePipeExtractor
                // references org.json.* so it must be provided explicitly here.
                implementation(libs.org.json)

                // AUDIT NOTE (login de YouTube Music en Desktop / KCEF):
                // ya está declarado en composeApp/build.gradle.kts, pero
                // el classpath REAL con el que corre `:desktopApp:run`
                // no viene de jvmRuntimeClasspath sino de la config
                // paralela `desktopRuntimeClasspath` de más abajo en
                // este mismo archivo (ver el comentario ahí sobre
                // "breaking the lock chain") - esa config no estaba
                // reflejando el agregado hecho solo del lado de
                // composeApp. Confirmado con diagnóstico real:
                // java.class.path del proceso de Sebastián no contenía
                // 'content-negotiation' pese a que
                // :desktopApp:dependencies --configuration
                // jvmRuntimeClasspath sí la mostraba resuelta.
                // Declarándola acá también, directo en las dependencias
                // propias de :desktopApp, evita depender de esa
                // propagación rota.
                // AUDIT NOTE: ver el comentario completo en
                // composeApp/build.gradle.kts sobre por qué se fuerza esta
                // versión - kcef 2024.01.07.1 (la que trae
                // compose-webview-multiplatform por transitividad) es
                // binariamente incompatible con Ktor 3.5.0.
                implementation("dev.datlag:kcef:2024.04.20.4")
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.kotlinx.json)
            }
        }
    }
}

// Workaround the Gradle "Cannot mutate configuration after observation" error
// hit when Conveyor 2.0's per-arch deps mix with VLC-setup / compose plugins
// that resolve runtimeClasspath at configuration time. Creating a sibling
// `desktopRuntimeClasspath` configuration shifts Conveyor's resolution off
// the primary jvmRuntimeClasspath, breaking the lock chain.
// Source: https://github.com/zacharee/Bifrost/blob/main/desktop/build.gradle.kts
project.configurations.create("desktopRuntimeClasspath") {
    extendsFrom(project.configurations.getByName("jvmRuntimeClasspath"))
}

// Force a single, consistent Compose Multiplatform UI version across every
// configuration (including the desktopRuntimeClasspath sibling above, whose
// comment already notes it breaks the normal version "lock chain").
//
// Root cause of the runtime crash this fixes:
//   java.lang.NoSuchMethodError: ...BackHandler_jbKt.getLocalCompatNavigationEventDispatcherOwner()
// `libs.native.tray` (io.github.kdroidfilter:composenativetray) transitively
// pulls its own, older org.jetbrains.compose.ui artifacts. Because
// native-tray is declared in BOTH :composeApp and :desktopApp, and the
// custom desktopRuntimeClasspath configuration above doesn't get the same
// version alignment as the default jvmRuntimeClasspath, the packaged app
// ends up with two different compose-ui jars on its runtime classpath. The
// JVM silently loads whichever copy comes first, which can be the older one
// — missing newer APIs like the predictive-back compositionlocal above,
// hence the NoSuchMethodError when NativeTray() renders its composable icon.
// Forcing every configuration to the project's own Compose Multiplatform
// version guarantees only one (correct) compose-ui jar is ever present.
configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.compose.ui" ||
            requested.group == "org.jetbrains.compose.runtime" ||
            requested.group == "org.jetbrains.compose.foundation" ||
            requested.group == "org.jetbrains.compose.animation"
        ) {
            useVersion(libs.versions.composeMultiplatform.get())
            because("Pin compose-ui transitively pulled in by libs.native.tray to the project's Compose Multiplatform version to avoid duplicate compose-ui jars on the runtime classpath (NoSuchMethodError in NativeTray's composable icon rendering).")
        }
        // NOTE: org.jetbrains.compose.material and org.jetbrains.compose.material3 are
        // intentionally excluded above — see composeApp/build.gradle.kts for the full
        // explanation. They don't share Compose Multiplatform's version number, and
        // forcing them to it resolves to an unpublished artifact and breaks the build.
    }
}

// Conveyor per-arch artifacts. These configurations are created by the
// Conveyor plugin's `apply()`; we just feed them the right native compose
// binaries for cross-build from any host OS.
dependencies {
    linuxAarch64(libs.compose.linux.arm64)
    linuxAmd64(libs.compose.linux.x64)
    macAarch64(libs.compose.macos.arm64)
    macAmd64(libs.compose.macos.x64)
    windowsAarch64(libs.compose.windows.arm64)
    windowsAmd64(libs.compose.windows.x64)
}

// Append Wavora-specific keys to Conveyor's generated config file and
// — crucially — replace the auto-detected `app.inputs` classpath with
// ProGuard's shrunk jar directory so the packaged AppImage carries
// obfuscated + size-reduced bytecode instead of raw Gradle output.
// `dependsOn(proguardReleaseJars)` builds the shrunk jars first.
tasks.named<hydraulic.conveyor.gradle.WriteConveyorConfigTask>("writeConveyorConfig") {
    dependsOn(tasks.named("proguardReleaseJars"))
    val proguardJarsDir = layout.buildDirectory.dir("compose/tmp/main-release/proguard")
    doLast {
        // On Windows, absolutePath uses backslashes (e.g. "C:\Users\...").
        // HOCON strings use JSON-style escaping, so a lone "\U" (as in
        // "\Users") is parsed as an invalid \u-unicode escape and blows up
        // the parser. Forward slashes are valid path separators on Windows
        // too (both the JVM and Conveyor accept them), so normalize instead
        // of trying to escape - it's simpler and matches what Linux/Mac
        // already produce.
        val proguardJarsPath = proguardJarsDir.get().asFile.absolutePath.replace("\\", "/")
        destination.get().asFile.appendText(
            """
            |app.fsname = wavora
            |app.display-name = Wavora
            |app.rdns-name = com.wavora.app
            |
            |// Override the Gradle-detected classpath with the ProGuard'd
            |// jar directory. Conveyor expands a directory entry to every
            |// file inside it — saves ~750 MB raw / ~100 MB compressed in
            |// the resulting AppImage by replacing 221 raw jars with the
            |// shrunk equivalents from compose.desktop's proguard task.
            |app.inputs = [
            |    "$proguardJarsPath"
            |]
            """.trimMargin() + "\n",
        )
    }
}

// vlcSetup block disabled with the plugin above. VLC natives in
// vlc-natives/{linux,macos,windows}/ are already on disk from prior runs.
// TODO: replace with a simple Gradle download task that doesn't iterate
// tasks at apply time, so Conveyor + vlc-setup can coexist.

compose.desktop {
    application {
        mainClass = "com.wavora.app.MainKt"
        jvmArgs += "--add-opens=java.base/java.nio=ALL-UNNAMED"

        nativeDistributions {
            appResourcesRootDir = rootDir.resolve("vlc-natives/")
            val listTarget = mutableListOf<TargetFormat>()
            if (org.gradle.internal.os.OperatingSystem
                    .current()
                    .isMacOsX
            ) {
                listTarget.addAll(
                    listOf(TargetFormat.Dmg, TargetFormat.Msi),
                )
            } else {
                listTarget.addAll(
                    listOf(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.AppImage),
                )
            }
            targetFormats(*listTarget.toTypedArray())
            modules("jdk.unsupported")
            packageName = "Wavora"
            macOS {
                val formatedDate =
                    Instant.now().let {
                        DateTimeFormatter
                            .ofPattern("yyyy.MM.dd")
                            .withZone(ZoneId.of("UTC"))
                            .format(it)
                    }
                includeAllModules = true
                packageVersion = formatedDate
                iconFile.set(rootDir.resolve("composeApp/icon/circle_app_icon.icns"))
                val macExtraPlistKeys =
                    """
                    <key>LSApplicationCategoryType</key>
                    <string>public.app-category.music</string>
                    <key>UIBackgroundModes</key>
                    <array>
                        <string>audio</string>
                        <string>fetch</string>
                        <string>processing</string>
                    </array>
                    <key>CFBundleURLTypes</key>
                    <array>
                        <dict>
                            <key>CFBundleTypeRole</key>
                            <string>Viewer</string>
                            <key>CFBundleURLName</key>
                            <string>com.wavora.app.deeplink</string>
                            <key>CFBundleURLSchemes</key>
                            <array>
                                <string>wavora</string>
                            </array>
                        </dict>
                    </array>
                    """.trimIndent()
                infoPlist {
                    extraKeysRawXml = macExtraPlistKeys
                }
            }
            windows {
                includeAllModules = true
                packageVersion =
                    libs.versions.version.name
                        .get()
                        .removeSuffix("-hf")
                iconFile.set(rootDir.resolve("composeApp/icon/circle_app_icon.ico"))
                // NOTE (audit): this `compose.desktop.application.nativeDistributions`
                // block is JetBrains' own jpackage-based packaging path, which the
                // release pipeline no longer invokes — release.yml only runs
                // `conveyor make site` (see conveyor.conf). This `shortcut = true`
                // therefore has ZERO effect on the .msix Windows actually ships;
                // Conveyor's MSIX packaging has no equivalent "create a desktop
                // shortcut" key, which is why installs never got one. The desktop
                // shortcut is now created explicitly in scripts/windows/install.ps1
                // (resolved dynamically via Get-StartApps, not hardcoded). Left here
                // (rather than deleted) only in case this module is ever revived as
                // a non-Conveyor packaging fallback — do not rely on it for Wavora's
                // actual Windows distribution.
                shortcut = true
                menu = false
                upgradeUuid = "A1B2C3D4-E5F6-7890-ABCD-EF1234567890"
            }
            linux {
                includeAllModules = true
                packageVersion =
                    libs.versions.version.name
                        .get()
                        .removeSuffix("-hf")
                iconFile.set(rootDir.resolve("composeApp/icon/circle_app_icon.png"))
            }
        }

        buildTypes.release.proguard {
            // ProGuard 7.7.0 (Compose's default) can't read Java 25 bytecode (class v69) now shipped
            // by PipePipeExtractor, which broke :desktopApp:proguardReleaseJars. 7.8.x supports Java 25.
            version.set("7.8.1")
            // AUDIT NOTE (2026-07-23, integración KCEF/JCEF para el WebView del
            // login de YouTube Music): JCEF y JOGL (su dependencia de bajo nivel)
            // traen soporte opcional para un montón de toolkits/protocolos que
            // Wavora nunca usa — remote-CEF-process vía Apache Thrift, SWT,
            // JavaFX, Pack200. Ya agregamos -dontwarn/-keep para todos esos
            // paquetes en proguard-desktop-rules.pro, lo cual alcanza para las
            // fases de shrink/obfuscate. Pero optimize.set(true) hace análisis
            // de flujo de tipos (partial evaluation) que necesita la jerarquía
            // de clases COMPLETA para cualquier código que referencie esas
            // clases opcionales, sin importar si están "kept" — y como esas
            // superclases (org.eclipse.swt.widgets.Canvas, etc.) están
            // genuinamente ausentes del classpath (a propósito, no las
            // necesitamos), el optimizador tira
            // IncompleteClassHierarchyException y aborta el build entero cada
            // vez que topa con una cadena de tipos nueva involucrando alguna de
            // ellas (confirmado con 2 rondas distintas: primero Thrift, después
            // JOGL-SWT — patrón que iba a seguir apareciendo de a una).
            // Shrink y obfuscate no tienen este problema (no hacen análisis de
            // flujo), así que apagamos solo optimize: seguimos con el jar
            // reducido/ofuscado (que es lo que realmente ahorra los ~750MB de
            // Conveyor), perdiendo únicamente micro-optimizaciones de bytecode
            // en código que de todos modos nunca se ejecuta.
            optimize.set(false)
            obfuscate.set(true)
            configurationFiles.from(rootDir.resolve("composeApp/proguard-desktop-rules.pro"))
        }
    }
}

afterEvaluate {
    // AUDIT NOTE (ronda de "Toolchain from `executable` property does not
    // match toolchain from `javaLauncher` property" - build real de
    // Sebastián, :desktopApp:run FAILED): la ronda anterior usaba
    // `javaLauncher.set(jbr17Launcher)` sobre TODOS los `JavaExec`, pero el
    // plugin de Compose Desktop (org.jetbrains.compose) YA configura su
    // propia tarea `run`/`runDistributable` con un `executable` explícito
    // (resuelto a partir del toolchain de COMPILACIÓN, JDK 21, vía
    // `jvmToolchain(21)` de más arriba) ANTES de que este bloque
    // `afterEvaluate` corra. Gradle valida en tiempo de ejecución que
    // `executable` y `javaLauncher`, si ambos están seteados
    // explícitamente, apunten al MISMO toolchain - si no, tira
    // exactamente este error y la tarea nunca llega a ejecutarse (por lo
    // tanto nunca pudimos ni empezar a probar si JBR 17 arregla el crash
    // de libcef.dll). Fix: en vez de agregar un SEGUNDO valor en
    // conflicto (`javaLauncher`), resolvemos el ejecutable real de JBR 17
    // nosotros mismos y lo asignamos directamente a `executable` -
    // pisando el valor que puso Compose - así solo queda UNA fuente de
    // verdad sobre qué JVM corre la tarea, sin nada con qué pueda chocar.
    // AUDIT NOTE (2ª vuelta - crash real UnsupportedClassVersionError, esta
    // vez en `multiplatform/network/cmptoast/ToastHostKt`, un ARTEFACTO
    // PRECOMPILADO de una librería de terceros - network.chaintech:cmptoast):
    // bajar el bytecode de NUESTROS módulos a 17 (ronda anterior) no alcanza,
    // porque cualquier dependencia de terceros compilada a Java 21 (cosa muy
    // común y fuera de nuestro control - no podemos recompilar un .jar
    // externo) va a crashear con el mismo error apenas se cargue su primera
    // clase, sin importar cuántos módulos propios bajemos. Es una pelea sin
    // fin. Cambio de estrategia: en vez de bajar TODO a 17 para matchear
    // JBR 17, se sube el runtime a JBR 21 (JetBrains también publica JBR en
    // la línea 21, con soporte JCEF) para matchear lo que Kotlin/Gradle ya
    // compila por defecto (jvmToolchain(21) de arriba). La nota de KCEF
    // sobre "compatible con JBR 17.0.14" es sobre la versión del binario
    // nativo de Chromium que el propio KCEF descarga (independiente del
    // JVM host), no sobre el lenguaje Java del runtime - así que no hay
    // motivo real para pensar que JBR 21 rompa esa parte, y si el crash de
    // libcef.dll vuelve a aparecer bajo JBR 21, lo vamos a ver clarito en
    // el próximo log y volvemos a evaluar desde ahí con evidencia real.
    val jbrExecutablePath =
        javaToolchains
            .launcherFor {
                languageVersion.set(JavaLanguageVersion.of(21))
                vendor.set(JvmVendorSpec.JETBRAINS)
            }.get()
            .executablePath
            .asFile
            .absolutePath

    tasks.withType<JavaExec> {
        // AUDIT NOTE (Ronda 12 - test de aislamiento): bajo JBR 21 resuelto
        // acá, KCEF.init() se queda mudo para siempre - ni un solo % de
        // descarga, ni error, nada (log real, 3 corridas seguidas
        // idénticas) - y el Administrador de Tareas confirma que ni
        // siquiera llega a lanzar el proceso hijo de Chromium (nada tipo
        // jcef_helper.exe bajo java.exe). Dato clave de Sebastián: ANTES,
        // bajo el JDK de Microsoft que Gradle usaba por defecto (sin este
        // override), sí aparecía el % de descarga - solo crasheaba
        // DESPUÉS, ya con Chromium corriendo, con STATUS_BREAKPOINT en
        // libcef.dll. Eso apunta a que el JBR 21 que Gradle resuelve acá
        // (probablemente un build "genérico", no necesariamente el
        // mismo empaquetado con soporte JCEF completo que trae la IDE de
        // JetBrains) es justamente lo que ahora impide que el subproceso
        // de Chromium llegue siquiera a arrancar. Se comenta esta línea
        // para volver al JDK por defecto de la máquina (Microsoft, JDK 21)
        // como test de aislamiento - si con esto vuelve el % de descarga
        // (aunque termine crasheando de nuevo), confirma que el problema
        // está en ESTE JBR 21 específico, no en el bytecode/toolchain de
        // compilación (que sigue sin tocarse, en 21). NO BORRAR el código
        // de abajo - dejarlo comentado para poder volver a probarlo con
        // otro build de JBR 21 (p.ej. uno con -jcef explícito) si hace
        // falta más adelante.
        // setExecutable(jbrExecutablePath)

        jvmArgs("--add-opens", "java.desktop/sun.awt=ALL-UNNAMED")
        jvmArgs("--add-opens", "java.desktop/java.awt.peer=ALL-UNNAMED")
        jvmArgs("--add-opens", "java.base/java.nio=ALL-UNNAMED")

        // Pass bundled VLC natives path to the runtime for `./gradlew desktopApp:run`.
        val osArch = System.getProperty("os.arch").lowercase()
        val osSubDir =
            when {
                System.getProperty("os.name").contains("Mac") ->
                    if (osArch.contains("aarch64")) "macos-arm64" else "macos-x64"
                System.getProperty("os.name").contains("Win") ->
                    if (osArch.contains("aarch64")) "windows-arm64" else "windows-x64"
                else -> "linux-x64"
            }
        val vlcNativesPath = rootDir.resolve("vlc-natives/$osSubDir").absolutePath
        systemProperty("vlc.bundled.path", vlcNativesPath)

        if (System.getProperty("os.name").contains("Mac")) {
            jvmArgs("--add-opens", "java.desktop/sun.awt=ALL-UNNAMED")
            jvmArgs("--add-opens", "java.desktop/sun.lwawt=ALL-UNNAMED")
            jvmArgs("--add-opens", "java.desktop/sun.lwawt.macosx=ALL-UNNAMED")
        }

        // AUDIT NOTE (login de YouTube Music en Desktop / KCEF - crash
        // nativo persistente con exit code -2147483645 / STATUS_BREAKPOINT
        // en libcef.dll justo al inicializar CefApp): encontrado un reporte
        // real de otro usuario con la MISMA versión exacta de KCEF
        // (2025.03.23) y el mismo patrón de log (CefApp: set state
        // INITIALIZING seguido de crash) en
        // https://github.com/KevinnZou/compose-webview-multiplatform/issues/384
        // - la solución que usa esa persona incluye estos add-opens
        // específicos de Windows que este proyecto no tenía (solo tenía
        // los de macOS arriba). sun.awt.windows/sun.java2d son APIs
        // internas de AWT en Windows que JCEF necesita acceder
        // reflectivamente para su integración de ventanas/renderizado -
        // sin abrirlas, el puente nativo puede fallar un CHECK() interno
        // al arrancar.
        if (System.getProperty("os.name").contains("Win")) {
            jvmArgs("--add-opens", "java.desktop/sun.awt.windows=ALL-UNNAMED")
            jvmArgs("--add-opens", "java.desktop/sun.java2d=ALL-UNNAMED")
        }
    }

}

// ---------------------------------------------------------------------------
// packageConveyorAppImage — wraps Conveyor's `linux-app` directory tree
// (project root /output) into a single-file .AppImage. Conveyor 2.0 dropped
// native AppImage support (only .deb + .tar.gz remain), so we keep the
// "one-for-all-distros" .AppImage by piping its output through appimagetool
// — identical to the previous jpackage-based pipeline, just sourced from
// Conveyor instead.
//
// Usage: run AFTER `conveyor -Kapp.machines=linux.amd64.glibc make linux-app`
//        then `./gradlew :desktopApp:packageConveyorAppImage`
// ---------------------------------------------------------------------------
// Step 1 of the AppImage chain — invoke the external `conveyor` CLI to
// produce ./output (the relocatable linux-app directory tree).
// Conveyor 2.0 prompts once for a root-key passphrase on first run; press
// Enter to use no passphrase. Subsequent runs are fully non-interactive.
val conveyorMakeLinuxApp = tasks.register<Exec>("conveyorMakeLinuxApp") {
    group = "distribution"
    description = "Run `conveyor make linux-app` for Linux x86_64 (glibc)."
    dependsOn(":composeApp:vlcSetup")
    workingDir = rootDir
    commandLine(
        "conveyor",
        "--agree-to-license=1",
        "-Kapp.machines=linux.amd64.glibc",
        "make", "linux-app",
    )
    standardInput = System.`in`
}

tasks.register("packageConveyorAppImage") {
    group = "distribution"
    description = "Wrap Conveyor's linux-app output (./output) into a portable .AppImage."
    // Captures project references (rootDir, layout, libs catalog) inside
    // doLast — necessary for the per-build paths, incompatible with the
    // Gradle 9 configuration cache. This is a manual one-shot packaging
    // task invoked after `conveyor make linux-app`, not part of the
    // critical CI path, so opting out is acceptable.
    notCompatibleWithConfigurationCache(
        "Reads project/layout/libs from within doLast to compute appimage paths."
    )

    doLast {
        val appName = "Wavora"
        val conveyorOutput = rootDir.resolve("output")
        if (!conveyorOutput.exists()) {
            throw GradleException(
                "Conveyor output (./output) not found. Run `conveyor " +
                    "-Kapp.machines=linux.amd64.glibc make linux-app` first.",
            )
        }

        val appimagetool =
            layout.buildDirectory
                .dir("tmp")
                .get()
                .asFile
                .resolve("appimagetool-x86_64.AppImage")
        if (!appimagetool.exists()) {
            downloadFile(
                "https://github.com/AppImage/AppImageKit/releases/download/continuous/appimagetool-x86_64.AppImage",
                appimagetool,
            )
        }
        if (!appimagetool.canExecute()) {
            appimagetool.setExecutable(true)
        }

        val appDir =
            layout.buildDirectory
                .dir("appimage/conveyor/$appName.AppDir")
                .get()
                .asFile
        if (appDir.exists()) {
            appDir.deleteRecursively()
        }

        // Stage scaffold (icon source) + Conveyor binary tree.
        val appDirSrc = rootDir.resolve("composeApp/appimage")
        if (appDirSrc.exists()) {
            FileUtils.copyDirectory(appDirSrc, appDir)
        } else {
            appDir.mkdirs()
        }
        FileUtils.copyDirectory(conveyorOutput, appDir)

        // Ensure top-level PNG icon expected by appimagetool exists.
        val iconSrc = rootDir.resolve("composeApp/icon/circle_app_icon.png")
        val iconDst = appDir.resolve("wavora.png")
        if (!iconDst.exists() && iconSrc.exists()) {
            FileUtils.copyFile(iconSrc, iconDst)
        }

        val versionName = libs.versions.version.name.get()
        val desktopFile = appDir.resolve("wavora.desktop")
        desktopFile.writeText(
            """[Desktop Entry]
            |Type=Application
            |Version=1.0
            |Name=Wavora
            |Comment=Wavora v$versionName - FOSS YouTube Music Client
            |Exec=bin/wavora %u
            |Icon=wavora
            |Terminal=false
            |Categories=Audio;AudioVideo;
            |StartupWMClass=com-wavora-wavora-MainKt
            |MimeType=x-scheme-handler/wavora;
            |
            """.trimMargin(),
        )

        val appRun = appDir.resolve("AppRun")
        appRun.writeText(
            """#!/bin/sh
            |
            |SELF=${'$'}(readlink -f "${'$'}0")
            |HERE=${'$'}{SELF%/*}
            |
            |# Install icon into XDG dirs so GNOME/KDE pick it up the first time.
            |ICON_DIR="${'$'}HOME/.local/share/icons/hicolor/256x256/apps"
            |if [ ! -f "${'$'}ICON_DIR/wavora.png" ] || [ "${'$'}HERE/wavora.png" -nt "${'$'}ICON_DIR/wavora.png" ]; then
            |    mkdir -p "${'$'}ICON_DIR"
            |    cp "${'$'}HERE/wavora.png" "${'$'}ICON_DIR/wavora.png"
            |    gtk-update-icon-cache -f -t "${'$'}HOME/.local/share/icons/hicolor" 2>/dev/null || true
            |fi
            |
            |# Install .desktop file with absolute Exec path to the AppImage.
            |DESKTOP_DIR="${'$'}HOME/.local/share/applications"
            |mkdir -p "${'$'}DESKTOP_DIR"
            |APPIMAGE_PATH="${'$'}{APPIMAGE:-${'$'}SELF}"
            |sed "s|Exec=bin/wavora|Exec=${'$'}APPIMAGE_PATH|" "${'$'}HERE/wavora.desktop" > "${'$'}DESKTOP_DIR/com-wavora-wavora-MainKt.desktop"
            |update-desktop-database "${'$'}DESKTOP_DIR" 2>/dev/null || true
            |
            |cd "${'$'}HERE"
            |exec bin/wavora "${'$'}@"
            |
            """.trimMargin(),
        )
        appRun.setExecutable(true, false)

        // Conveyor's launcher lives at output/bin/wavora (lowercase).
        val appExecutable = appDir.resolve("bin/wavora")
        if (appExecutable.exists() && !appExecutable.canExecute()) {
            appExecutable.setExecutable(true)
        }

        val outputAppImage = appDir.parentFile.resolve("$appName-x86_64.AppImage")
        val process =
            ProcessBuilder(
                appimagetool.canonicalPath,
                "$appName.AppDir",
                outputAppImage.name,
            ).directory(appDir.parentFile)
                .apply { environment()["ARCH"] = "x86_64" }
                .inheritIO()
                .start()

        val exitCode = process.waitFor()
        if (exitCode != 0) {
            throw GradleException("appimagetool failed with exit code $exitCode")
        }
        println("[AppImage] Built: ${outputAppImage.absolutePath}")
    }
}

// End-to-end: vlcSetup → conveyor make linux-app → wrap as .AppImage.
// Single command for users: `./gradlew :desktopApp:buildLinuxAppImage --no-configuration-cache`
tasks.register("buildLinuxAppImage") {
    group = "distribution"
    description = "Full Wavora Desktop Linux AppImage build pipeline (vlcSetup → conveyor → AppImage)."
    dependsOn(conveyorMakeLinuxApp)
    finalizedBy("packageConveyorAppImage")
}

// macOS — Conveyor 2.0 ships .zip wrapping the .app bundle (no native
// .dmg target). Run on a macOS host for proper code signing; cross-build
// from Linux works but the app won't be signed.
//
// Run via: `./gradlew :desktopApp:buildMacZipAmd64 --no-configuration-cache`
val conveyorMakeMacZipAmd64 = tasks.register<Exec>("conveyorMakeMacZipAmd64") {
    group = "distribution"
    description = "Run `conveyor make unnotarized-mac-zip` for macOS Intel."
    dependsOn(":composeApp:vlcSetup")
    workingDir = rootDir
    commandLine(
        "conveyor",
        "--agree-to-license=1",
        "-Kapp.machines=mac.amd64",
        "make", "unnotarized-mac-zip",
    )
    standardInput = System.`in`
}

val conveyorMakeMacZipAarch64 = tasks.register<Exec>("conveyorMakeMacZipAarch64") {
    group = "distribution"
    description = "Run `conveyor make unnotarized-mac-zip` for macOS Apple Silicon."
    dependsOn(":composeApp:vlcSetup")
    workingDir = rootDir
    commandLine(
        "conveyor",
        "--agree-to-license=1",
        "-Kapp.machines=mac.aarch64",
        "make", "unnotarized-mac-zip",
    )
    standardInput = System.`in`
}

tasks.register("buildMacZipAmd64") {
    group = "distribution"
    description = "Full Wavora Desktop macOS Intel .zip pipeline (vlcSetup → conveyor)."
    dependsOn(conveyorMakeMacZipAmd64)
}

tasks.register("buildMacZipAarch64") {
    group = "distribution"
    description = "Full Wavora Desktop macOS Apple Silicon .zip pipeline (vlcSetup → conveyor)."
    dependsOn(conveyorMakeMacZipAarch64)
}

// Windows — Conveyor 2.0 ships .msix (modern Windows 10+ app package).
// NOTE: Unsigned .msix has rough UX (users must enable sideloading +
// install certificate). Recommended path long-term: code-sign with an
// EV cert OR switch to Inno Setup `.exe` wrap if signing budget unavailable.
val conveyorMakeWindowsMsix = tasks.register<Exec>("conveyorMakeWindowsMsix") {
    group = "distribution"
    description = "Run `conveyor make windows-msix` for Windows x86_64."
    dependsOn(":composeApp:vlcSetup")
    workingDir = rootDir
    commandLine(
        "conveyor",
        "--agree-to-license=1",
        "-Kapp.machines=windows.amd64",
        "make", "windows-msix",
    )
    standardInput = System.`in`
}

tasks.register("buildWindowsMsix") {
    group = "distribution"
    description = "Full Wavora Desktop Windows .msix pipeline (vlcSetup → conveyor)."
    dependsOn(conveyorMakeWindowsMsix)
}

tasks.withType<AbstractJPackageTask>().configureEach {
    notCompatibleWithConfigurationCache("Compose Desktop JPackage tasks are not yet compatible with configuration cache")
}

listOf("vlcExtract", "vlcFilterPlugins", "vlcSetup", "clean").forEach { taskName ->
    tasks.findByName(taskName)?.let {
        it.notCompatibleWithConfigurationCache("vlc-setup plugin tasks are not yet compatible with configuration cache")
    }
}

private fun downloadFile(
    url: String,
    destFile: java.io.File,
) {
    val destParent = destFile.parentFile
    destParent.mkdirs()

    if (destFile.exists()) {
        destFile.delete()
    }

    println("Download $url")
    URI(url).toURL().openStream().use { input ->
        destFile.outputStream().use { output ->
            input.copyTo(output)
        }
    }
    println("Download finish")
}
