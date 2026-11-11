package com.wavora.common

/**
 * Helpers para URLs de stream (googlevideo) usadas para REPRODUCCIÓN.
 *
 * FIX (cortes a mitad de canción / error de red al reconectar):
 * StreamRepositoryImpl.getStream() devuelve la URL con `&range=0-<contentLength>`
 * agregado en la query (o `0-10000000` si el contentLength era desconocido), mientras
 * que la URL guardada en NewFormatEntity (la que se usa en todas las reproducciones
 * siguientes) NO lo lleva. Los players (ExoPlayer y VLC) piden los bytes con la
 * cabecera HTTP `Range: bytes=X-` al reanudar, reconectar o hacer seek. Con el rango
 * fijado en la query, googlevideo responde según la QUERY (desde el byte 0, o
 * truncado a 10 MB), no según la cabecera: VLC rechaza la respuesta de una reconexión
 * (espera 206 desde X) y la canción se corta; ExoPlayer tiene que re-descargar y
 * descartar todo lo anterior a X en cada reapertura.
 *
 * Para reproducir se usa siempre la forma sin `range=` (la misma que ya se usa en
 * todas las reproducciones desde la base de datos). Las descargas no pasan por acá.
 */
object StreamUrlUtils {
    fun stripRangeQueryParam(url: String): String {
        val queryStart = url.indexOf('?')
        if (queryStart < 0) return url
        val fragmentStart = url.indexOf('#', queryStart).let { if (it < 0) url.length else it }
        val base = url.substring(0, queryStart)
        val query = url.substring(queryStart + 1, fragmentStart)
        val fragment = url.substring(fragmentStart)
        val kept =
            query
                .split('&')
                .filter { it.isNotEmpty() && !it.startsWith("range=") }
        if (kept.size == query.split('&').count { it.isNotEmpty() }) return url
        return if (kept.isEmpty()) base + fragment else base + "?" + kept.joinToString("&") + fragment
    }
}
