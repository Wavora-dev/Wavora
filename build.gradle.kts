import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.parcelize) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.aboutlibraries) apply false
    alias(libs.plugins.aboutlibraries.multiplatform) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.sentry.gradle) apply false
    alias(libs.plugins.android.lint) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.jetbrains.kotlin.jvm) apply false
    alias(libs.plugins.build.config) apply false
    alias(libs.plugins.osdetector) apply false
    alias(libs.plugins.conveyor) apply false
}

tasks.register<Delete>("Clean") {
    delete(rootProject.layout.buildDirectory)
}

subprojects {
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            if (project.findProperty("enableComposeCompilerReports") == "true") {
                arrayOf("reports", "metrics").forEach {
                    freeCompilerArgs.addAll(
                        listOf(
                            "-P",
                            "plugin:androidx.compose.compiler.plugins.kotlin:${it}Destination=${layout.buildDirectory.asFile.get().absolutePath}/compose_metrics",
                        ),
                    )
                }
            }
        }
    }

    // PipePipe and Brave both depend on com.github.TeamNewPipe:nanojson with different commit
    // hashes. Gradle's default resolver picks PipePipe's older 1d9e1aea... commit which lacks
    // JsonArray.streamAsJsonObjects(), causing NoSuchMethodError when Brave's fallback runs at
    // runtime. Force the latest upstream commit (newer than both libs ship) across every module
    // so the merged APK/JAR carries a nanojson with the API both extractors expect.
    configurations.all {
        resolutionStrategy {
            force("com.github.TeamNewPipe:nanojson:c7a6c1c08d16b6d5ecded34758e6415e07be2166")
            // Skiko 0.144.6 is the version that actually ships paired with Compose
            // Multiplatform 1.11.1 (Skia Milestone 144) -- see ui-desktop, foundation-desktop
            // and compottie-core-desktop below, which all request 0.144.6 transitively.
            // Forcing a newer Skiko (0.148.1) here caused a NoClassDefFoundError on
            // org.jetbrains.skia.GradientStyle at runtime, since CMP 1.11.1's compiled
            // ui-graphics-desktop bytecode expects the M144 Skia class layout for
            // gradients/shaders and 0.148.1 is a later milestone with a reworked Shader API.
            force("org.jetbrains.skiko:skiko:0.144.6")
            // Pin Compose Multiplatform runtime to match the plugin version (1.11.1).
            // Without this, transitive dependencies can pull 1.12.0-alpha01 which causes
            // the "versions don't match" warning and potential runtime instability.
            force("org.jetbrains.compose.foundation:foundation:1.11.1")
            force("org.jetbrains.compose.foundation:foundation-desktop:1.11.1")
            force("org.jetbrains.compose.foundation:foundation-android:1.11.1")
            force("org.jetbrains.compose.ui:ui:1.11.1")
            force("org.jetbrains.compose.ui:ui-desktop:1.11.1")
            force("org.jetbrains.compose.ui:ui-android:1.11.1")
            force("org.jetbrains.compose.runtime:runtime:1.11.1")
            force("org.jetbrains.compose.runtime:runtime-desktop:1.11.1")
            force("org.jetbrains.compose.runtime:runtime-android:1.11.1")
            // Material3 (and its adaptive companion) are versioned independently from
            // Compose Multiplatform core and do NOT follow the same number. The pairing
            // published for Compose Multiplatform 1.11.1 is material3 1.11.0-alpha07 /
            // material3-adaptive 1.3.0-alpha07 (see JetBrains/compose-multiplatform release
            // notes). Forcing them here stops a transitive dependency from silently
            // upgrading to an unpublished 1.12.x alpha that only exists on the (unreliable)
            // oss.sonatype.org snapshot repository and broke the build.
            force("org.jetbrains.compose.material3:material3:1.11.0-alpha07")
            force("org.jetbrains.compose.material3:material3-desktop:1.11.0-alpha07")
            force("org.jetbrains.compose.material3:material3-android:1.11.0-alpha07")
            force("org.jetbrains.compose.material3.adaptive:adaptive:1.3.0-alpha07")
            force("org.jetbrains.compose.material:material-ripple:1.11.1")
        }
    }
}