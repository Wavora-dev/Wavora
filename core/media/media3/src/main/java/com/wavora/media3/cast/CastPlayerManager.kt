package com.wavora.media3.cast

import android.content.Context
import androidx.media3.cast.CastPlayer
import androidx.media3.common.util.UnstableApi
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.common.GoogleApiAvailability
import com.google.common.util.concurrent.MoreExecutors
import com.wavora.logger.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "CastPlayerManager"

/**
 * Inicialización del Cast SDK y punto único de acceso al [CastPlayer].
 *
 * Responsabilidades:
 * - Confirmar que Google Play Services está disponible en el dispositivo.
 * - Obtener el [CastContext] compartido y construir el [CastPlayer].
 * - Exponer [castPlayerFlow] para que quien lo necesite (hoy,
 *   `com.wavora.media3.exoplayer.CrossfadeExoPlayerAdapter`) reaccione
 *   cuando el [CastPlayer] queda listo — la inicialización es asíncrona
 *   (`Task` de Play Services), así que no alcanza con un simple `var`.
 *
 * Esta clase deliberadamente NO le pone ningún `SessionAvailabilityListener`
 * al [CastPlayer] ella misma — quien lo consume es responsable de eso, porque
 * reaccionar a "apareció/desapareció una sesión Cast" requiere contexto que
 * este manager no tiene (el player local activo, el estado del crossfade,
 * etc.). Ver `CrossfadeExoPlayerAdapter.init{}`.
 *
 * Aislado 100% a Android — no existe equivalente ni referencia desde el
 * código común (`MediaPlayerInterface`, `MediaPlayerHandler`) ni desde
 * Desktop.
 */
@UnstableApi
class CastPlayerManager(
    private val context: Context,
) {
    /** true si el dispositivo tiene Google Play Services disponible/actualizado. */
    val isCastAvailable: Boolean by lazy {
        val result = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)
        val available = result == com.google.android.gms.common.ConnectionResult.SUCCESS
        Logger.d(TAG, "Google Play Services available: $available (code=$result)")
        available
    }

    private var castContext: CastContext? = null

    private val _castPlayerFlow = MutableStateFlow<CastPlayer?>(null)

    /**
     * El [CastPlayer] de Media3, una vez que [initialize] resuelve. `null`
     * hasta entonces, o si el dispositivo no tiene Google Play Services, o
     * después de [release].
     */
    val castPlayerFlow: StateFlow<CastPlayer?> = _castPlayerFlow.asStateFlow()

    /**
     * Inicializa el [CastContext] y el [CastPlayer] de forma asíncrona.
     * Seguro de llamar aunque el dispositivo no tenga Google Play Services
     * (queda todo en `null`, no revienta nada).
     *
     * Llamarlo una sola vez, temprano en el ciclo de vida
     * (`SimpleMediaService.onCreate()`).
     */
    fun initialize() {
        if (!isCastAvailable) {
            Logger.d(TAG, "Skipping Cast init: Google Play Services not available")
            return
        }
        CastContext
            .getSharedInstance(context, MoreExecutors.directExecutor())
            .addOnSuccessListener { ctx ->
                castContext = ctx
                _castPlayerFlow.value = CastPlayer(ctx)
                Logger.d(TAG, "CastContext + CastPlayer initialized OK")
            }.addOnFailureListener { e ->
                Logger.e(TAG, "CastContext init failed: ${e.message}", e)
            }
    }

    /** Libera el CastPlayer. Llamar desde `SimpleMediaService.onDestroy()`. */
    fun release() {
        _castPlayerFlow.value?.let { player ->
            player.setSessionAvailabilityListener(null)
            player.release()
        }
        _castPlayerFlow.value = null
    }
}
