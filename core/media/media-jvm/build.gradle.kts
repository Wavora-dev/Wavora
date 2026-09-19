plugins {
    id("java-library")
    alias(libs.plugins.jetbrains.kotlin.jvm)
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
    dependencies {
        implementation(projects.common)
        implementation(projects.domain)
        // AUDIT NOTE: agregado para el scrobbling a Last.fm (VlcPlayerAdapter
        // delega la parte de auth a LastFmApi, que vive en lyricsService).
        implementation(projects.lyricsService)
        // AUDIT NOTE: agregado para breadcrumbs de Sentry en CrossfadeAudit.log
        // (ver comentario ahí). Sentry.init() ya se llama una sola vez desde
        // composeApp - acá solo usamos la API estática, no reinicializamos nada.
        implementation(libs.sentry.jvm)
        implementation(platform(libs.koin.bom))
        implementation(libs.koin.jvm)
        implementation(libs.kotlinx.serialization.json)
        implementation(libs.kotlinx.coroutinesSwing)

        // VLC
        implementation(libs.vlcj)
    }
}