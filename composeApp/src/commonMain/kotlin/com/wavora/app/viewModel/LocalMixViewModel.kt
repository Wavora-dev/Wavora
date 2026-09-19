package com.wavora.app.viewModel

import androidx.lifecycle.viewModelScope
import com.wavora.app.viewModel.base.BaseViewModel
import com.wavora.domain.model.entities.SongEntity
import com.wavora.domain.repository.AnalyticsRepository
import com.wavora.domain.repository.SongRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * "Mix para vos": una lista armada 100% local, sin ningún pedido de red, a
 * partir de tu propio historial de reproducción (analíticas locales que ya
 * se venían guardando para la pantalla de Analíticas). No es un algoritmo de
 * recomendación real - es "tus canciones más escuchadas últimamente,
 * mezcladas con peso" para que se sienta distinta cada vez que la abrís sin
 * ser 100% al azar (las que más escuchaste tienen más chance de aparecer,
 * pero no están garantizadas).
 */
class LocalMixViewModel(
    private val analyticsRepository: AnalyticsRepository,
    private val songRepository: SongRepository,
) : BaseViewModel() {
    private val _mix = MutableStateFlow<List<SongEntity>>(emptyList())
    val mix: StateFlow<List<SongEntity>> = _mix.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** true cuando ya se generó al menos una vez y no hay suficiente historial (< [MIN_TRACKS_FOR_MIX] canciones). */
    private val _notEnoughHistory = MutableStateFlow(false)
    val notEnoughHistory: StateFlow<Boolean> = _notEnoughHistory.asStateFlow()

    init {
        generateMix()
    }

    /** Se puede llamar de nuevo (ej. botón "Regenerar") para barajar otra combinación distinta. */
    fun generateMix() {
        viewModelScope.launch {
            _isLoading.value = true

            // 60 días de ventana: bastante para juntar variedad, no tanto
            // como para arrastrar canciones que ya no te interesan.
            var topTracks = analyticsRepository.queryTopPlayedSongsLastXDays(60).first()
            // Si hay poco historial reciente, ampliamos a una ventana enorme (~100
            // años, no Int.MAX_VALUE - beforeXDays() hace aritmética de fechas real
            // y un valor tan extremo puede desbordar) antes de rendirnos con la
            // pantalla vacía.
            if (topTracks.size < MIN_TRACKS_FOR_MIX) {
                topTracks = analyticsRepository.queryTopPlayedSongsLastXDays(EFFECTIVELY_ALL_TIME_DAYS).first()
            }

            if (topTracks.isEmpty()) {
                _mix.value = emptyList()
                _notEnoughHistory.value = true
                _isLoading.value = false
                return@launch
            }

            val candidateIds = topTracks.map { it.videoId }
            val songsById =
                songRepository
                    .getSongsByListVideoId(candidateIds)
                    .first()
                    .associateBy { it.videoId }

            // Peso = playCount (canciones que escuchaste más tienen más chance de
            // entrar), pero con una raíz cuadrada para achatar la curva: sin esto,
            // una canción escuchada 50 veces monopoliza el mix y las demás nunca
            // aparecen. sqrt es un ablandado simple y barato, no una fórmula mágica.
            val weighted =
                topTracks
                    .mapNotNull { top -> songsById[top.videoId]?.let { it to kotlin.math.sqrt(top.playCount.toDouble()).coerceAtLeast(1.0) } }

            _mix.value = weightedShuffleTake(weighted, MIX_SIZE)
            _notEnoughHistory.value = false
            _isLoading.value = false
        }
    }

    companion object {
        private const val MIN_TRACKS_FOR_MIX = 10
        private const val MIX_SIZE = 25
        private const val EFFECTIVELY_ALL_TIME_DAYS = 36_500 // ~100 años

        /**
         * Muestreo aleatorio ponderado sin reposición (algoritmo de Efraimidis-Spirakis):
         * a cada item se le asigna key = random()^(1/peso), y nos quedamos con los N de
         * mayor key. Con esto, un peso más alto sube la PROBABILIDAD de entrar, sin
         * hacerlo determinístico - cada llamada a generateMix() puede dar un orden y
         * subconjunto distintos.
         */
        internal fun <T> weightedShuffleTake(
            items: List<Pair<T, Double>>,
            count: Int,
        ): List<T> =
            items
                .map { (item, weight) -> item to kotlin.random.Random.nextDouble().pow(1.0 / weight) }
                .sortedByDescending { it.second }
                .take(count)
                .map { it.first }
    }
}
