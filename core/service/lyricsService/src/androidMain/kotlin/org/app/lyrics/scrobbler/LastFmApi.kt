package org.app.lyrics.scrobbler

import com.wavora.ktorext.getEngine
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.MessageDigest

/**
 * Cliente mínimo de la API REST de Last.fm (https://www.last.fm/api).
 *
 * Requiere una API key + shared secret DE LA APLICACIÓN (se consiguen gratis
 * en last.fm/api/account/create) - no son credenciales del usuario. El
 * usuario solo pone su usuario/contraseña UNA vez para obtener una
 * "session key" permanente (auth.getMobileSession, el mismo flujo que usan
 * los scrobblers de escritorio/móvil clásicos); esa session key es lo único
 * que se guarda - la contraseña nunca se persiste.
 *
 * Sin LASTFM_API_KEY/LASTFM_API_SECRET configuradas (variables de entorno,
 * ver README), todas las funciones devuelven Result.failure sin romper nada
 * - Last.fm queda deshabilitado hasta que alguien las configure.
 */
object LastFmApi {
    private const val API_ROOT = "https://ws.audioscrobbler.com/2.0/"

    @Volatile private var apiKey: String = ""

    @Volatile private var apiSecret: String = ""

    /**
     * Se llama UNA vez al arrancar la app (ver DesktopApp.kt), pasándole
     * BuildKonfig.lastFmApiKey/lastFmApiSecret. No podíamos leer estos
     * valores acá adentro con System.getenv() en runtime como se hizo en un
     * primer intento: esas env vars solo existen en la máquina de quien
     * corre `gradlew`, no en la PC de cada usuario final que instala el
     * .exe empaquetado. BuildKonfig horni­a el valor como constante en tiempo
     * de compilación (mismo mecanismo que sentryDsn), así que hay que
     * pasárselo explícitamente desde composeApp, que es el único módulo
     * donde BuildKonfig es visible.
     */
    fun configure(
        apiKey: String,
        apiSecret: String,
    ) {
        this.apiKey = apiKey
        this.apiSecret = apiSecret
    }

    /** true solo si alguien llamó a [configure] con valores no vacíos. */
    val isConfigured: Boolean get() = apiKey.isNotBlank() && apiSecret.isNotBlank()

    private val json = Json { ignoreUnknownKeys = true }

    private val httpClient by lazy {
        HttpClient(getEngine()) {
            expectSuccess = false
            install(HttpTimeout) {
                connectTimeoutMillis = 8_000
                requestTimeoutMillis = 15_000
                socketTimeoutMillis = 15_000
            }
            install(HttpRequestRetry) {
                retryOnExceptionOrServerErrors(maxRetries = 2)
                exponentialDelay()
            }
        }
    }

    /**
     * Firma de Last.fm: todos los params (menos "format"), ordenados
     * alfabéticamente por key, concatenados como "clave1valor1clave2valor2...",
     * más el secret al final, todo pasado por MD5. Ver
     * https://www.last.fm/api/authspec#_8-signing-calls
     */
    private fun sign(params: Map<String, String>): String {
        val base =
            params
                .filterKeys { it != "format" }
                .toSortedMap()
                .entries
                .joinToString("") { (k, v) -> "$k$v" } + apiSecret
        val digest = MessageDigest.getInstance("MD5").digest(base.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Intercambia usuario/contraseña por una session key permanente. La
     * contraseña solo se usa acá, en esta única llamada - nunca se guarda.
     */
    suspend fun getMobileSession(
        username: String,
        password: String,
    ): Result<String> {
        if (!isConfigured) return Result.failure(IllegalStateException("Last.fm no configurado (falta LASTFM_API_KEY/SECRET)"))
        return runCatching {
            val params =
                mapOf(
                    "method" to "auth.getMobileSession",
                    "api_key" to apiKey,
                    "username" to username,
                    "password" to password,
                )
            val response =
                httpClient.post(API_ROOT) {
                    setBody(
                        FormDataContent(
                            Parameters.build {
                                params.forEach { (k, v) -> append(k, v) }
                                append("api_sig", sign(params))
                                append("format", "json")
                            },
                        ),
                    )
                }
            val body = response.bodyAsText()
            if (!response.status.isSuccess()) {
                error("Last.fm auth.getMobileSession HTTP ${response.status.value}: $body")
            }
            val sessionKey =
                json
                    .parseToJsonElement(body)
                    .jsonObject["session"]
                    ?.jsonObject
                    ?.get("key")
                    ?.jsonPrimitive
                    ?.content
            sessionKey ?: error("Last.fm no devolvió session.key: $body")
        }
    }

    /** Registra un scrobble. [timestampSeconds] es el momento en que EMPEZÓ a sonar, en epoch seconds UTC. */
    suspend fun scrobble(
        sessionKey: String,
        artist: String,
        track: String,
        album: String?,
        timestampSeconds: Long,
    ): Result<Unit> {
        if (!isConfigured) return Result.failure(IllegalStateException("Last.fm no configurado (falta LASTFM_API_KEY/SECRET)"))
        return runCatching {
            val params =
                buildMap {
                    put("method", "track.scrobble")
                    put("api_key", apiKey)
                    put("sk", sessionKey)
                    put("artist", artist)
                    put("track", track)
                    put("timestamp", timestampSeconds.toString())
                    if (!album.isNullOrBlank()) put("album", album)
                }
            val response =
                httpClient.post(API_ROOT) {
                    setBody(
                        FormDataContent(
                            Parameters.build {
                                params.forEach { (k, v) -> append(k, v) }
                                append("api_sig", sign(params))
                                append("format", "json")
                            },
                        ),
                    )
                }
            val body = response.bodyAsText()
            if (!response.status.isSuccess()) {
                error("Last.fm track.scrobble HTTP ${response.status.value}: $body")
            }
        }
    }
}
