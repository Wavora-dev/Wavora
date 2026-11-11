package com.wavora.media3.datasource

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import java.io.IOException

/**
 * FIX (canciones que se cortan a la mitad / "error de red" a mitad de canción en Android).
 *
 * Antes, el "chunking" de 5 MB se hacía en el ResolvingDataSource con
 * `dataSpec.subrange(..., chunkLength)`: el DataSource entero (por encima de los cachés)
 * quedaba acotado a 5 MB, así que cada 5 MB el extractor recibía un FIN DE ARCHIVO falso:
 *  - si el corte caía a mitad de un elemento → EOFException → error de carga, reintento
 *    del Loader, y una NUEVA resolución de URL (con HEAD de is403Url por red, y si ese
 *    HEAD fallaba, una extracción completa con YouTube + Tidal) en medio de la canción;
 *    con la red inestable esos errores se acumulaban y terminaban en un error fatal
 *    cuando el buffer se vaciaba.
 *  - si el corte caía justo en el borde de un elemento (WebM/Opus: cada bloque de ~20 ms
 *    es un elemento) → fin LIMPIO: el extractor daba la pista por terminada y la canción
 *    se cortaba antes de tiempo.
 *
 * Este DataSource se pone por DEBAJO de los cachés, delante del HTTP: sigue pidiendo al
 * servidor bloques de [chunkLength] (mismo tamaño de petición que antes), pero los
 * empalma de forma transparente. Hacia arriba es un único stream continuo: sin EOF
 * falsos, sin errores por bloque y sin re-resolver la URL cada 5 MB.
 *
 * Solo trocea URIs http/https; el resto (archivos, content://) pasa directo.
 */
@UnstableApi
class ChunkedDataSource(
    private val upstream: DataSource,
    private val chunkLength: Long,
) : DataSource {
    class Factory(
        private val upstreamFactory: DataSource.Factory,
        private val chunkLength: Long,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource = ChunkedDataSource(upstreamFactory.createDataSource(), chunkLength)
    }

    private companion object {
        // C.LENGTH_UNSET es Int en Media3; acá trabajamos todo en Long.
        val LENGTH_UNSET: Long = C.LENGTH_UNSET.toLong()
    }

    private var dataSpec: DataSpec? = null
    private var chunking = false
    private var finished = false

    // Posición absoluta del próximo byte a pedir.
    private var nextChunkPosition = 0L

    // Bytes que todavía pidió el caller (LENGTH_UNSET = hasta el final).
    private var bytesRemaining = LENGTH_UNSET

    // Tamaño pedido y bytes leídos de la sub-petición actual.
    private var subRequestedLength = 0L
    private var subBytesRead = 0L

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    @Throws(IOException::class)
    override fun open(dataSpec: DataSpec): Long {
        this.dataSpec = dataSpec
        finished = false
        val scheme = dataSpec.uri.scheme?.lowercase()
        chunking = chunkLength > 0 && (scheme == "http" || scheme == "https")
        if (!chunking) {
            return upstream.open(dataSpec)
        }

        nextChunkPosition = dataSpec.position
        bytesRemaining = dataSpec.length
        val firstChunkLength = openNextChunk()

        if (dataSpec.length != LENGTH_UNSET) return dataSpec.length
        if (firstChunkLength == 0L) return 0L
        // Si el servidor informó el tamaño total (Content-Range: bytes a-b/TOTAL), se
        // devuelve el largo real; si no, desconocido (el caché lo fija al llegar al EOF).
        val total = totalLengthFromContentRange()
        return if (total != null && total >= dataSpec.position) total - dataSpec.position else LENGTH_UNSET
    }

    /** Abre la siguiente sub-petición. Devuelve el largo informado por el upstream. */
    @Throws(IOException::class)
    private fun openNextChunk(): Long {
        val spec = checkNotNull(dataSpec)
        val requested =
            if (bytesRemaining == LENGTH_UNSET) chunkLength else minOf(chunkLength, bytesRemaining)
        subRequestedLength = requested
        subBytesRead = 0L
        val subSpec =
            spec
                .buildUpon()
                .setPosition(nextChunkPosition)
                .setLength(requested)
                .build()
        val reported =
            try {
                upstream.open(subSpec)
            } catch (e: HttpDataSource.InvalidResponseCodeException) {
                // 416 en un bloque POSTERIOR al primero = pedimos justo pasado el final
                // del documento: es el fin real del stream, no un error.
                if (e.responseCode == 416 && nextChunkPosition > spec.position) {
                    closeUpstreamQuietly()
                    finished = true
                    return 0L
                }
                throw e
            }
        if (reported == 0L) finished = true
        return reported
    }

    @Throws(IOException::class)
    override fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int {
        if (length == 0) return 0
        if (!chunking) return upstream.read(buffer, offset, length)

        while (true) {
            if (finished || bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

            val toRead =
                if (bytesRemaining == LENGTH_UNSET) length else minOf(length.toLong(), bytesRemaining).toInt()
            val read = upstream.read(buffer, offset, toRead)
            if (read != C.RESULT_END_OF_INPUT) {
                subBytesRead += read
                nextChunkPosition += read
                if (bytesRemaining != LENGTH_UNSET) bytesRemaining -= read
                return read
            }

            // Se terminó la sub-petición actual.
            val hitChunkLimit = subBytesRead >= subRequestedLength
            closeUpstreamQuietly()
            if (!hitChunkLimit || bytesRemaining == 0L) {
                // El servidor devolvió menos de lo pedido: fin real del documento.
                finished = true
                return C.RESULT_END_OF_INPUT
            }
            // Bloque completo: pedir el siguiente y seguir leyendo sin cortar el stream.
            openNextChunk()
        }
    }

    override fun getUri(): Uri? = upstream.uri ?: dataSpec?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    @Throws(IOException::class)
    override fun close() {
        dataSpec = null
        finished = false
        upstream.close()
    }

    private fun closeUpstreamQuietly() {
        try {
            upstream.close()
        } catch (_: IOException) {
        }
    }

    private fun totalLengthFromContentRange(): Long? {
        val value =
            upstream.responseHeaders.entries
                .firstOrNull { it.key?.equals("Content-Range", ignoreCase = true) == true }
                ?.value
                ?.firstOrNull()
                ?: return null
        val slash = value.lastIndexOf('/')
        if (slash < 0) return null
        return value.substring(slash + 1).trim().toLongOrNull()
    }
}
