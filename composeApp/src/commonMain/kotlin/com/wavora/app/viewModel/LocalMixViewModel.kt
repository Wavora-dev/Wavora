package com.wavora.app.viewModel

import androidx.lifecycle.viewModelScope
import com.wavora.app.viewModel.base.BaseViewModel
import com.wavora.domain.model.entities.SongEntity
import com.wavora.domain.repository.AnalyticsRepository
import com.wavora.domain.repository.SongRepository
import com.wavora.domain.extension.beforeXDays
import com.wavora.domain.extension.now
import com.wavora.domain.manager.DataStoreManager
import com.wavora.logger.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.component.inject
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

    /**
     * Variantes del mix (v2.3):
     * - [MixMode.FOR_YOU]: lo de siempre, tus más escuchadas con peso.
     * - [MixMode.TIME_OF_DAY]: lo que más escuchás a esta hora del día (±2 h).
     * - [MixMode.REDISCOVER]: canciones que escuchabas mucho y hace 60+ días
     *   que no aparecen en tu historial.
     */
    enum class MixMode { FOR_YOU, TIME_OF_DAY, REDISCOVER }

    private val _mode = MutableStateFlow(MixMode.FOR_YOU)
    val mode: StateFlow<MixMode> = _mode.asStateFlow()

    fun setMode(newMode: MixMode) {
        if (_mode.value == newMode) return
        _mode.value = newMode
        generateMix()
    }

    private val dataStoreManager: DataStoreManager by inject()
    private var generateJob: Job? = null

    init {
        generateMix()
    }

    /**
     * true si el historial local de escuchas (playback_event) está activado.
     * Se expone para que la pantalla vacía pueda ofrecer activarlo en un toque
     * en vez de solo decir "escuchá más canciones" (antes venía apagado por
     * defecto y el mix nunca tenía datos, sin que el usuario supiera por qué).
     */
    val trackingEnabled: StateFlow<Boolean> =
        dataStoreManager.localTrackingEnabled
            .map { it == DataStoreManager.TRUE }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun enableTracking() {
        viewModelScope.launch {
            dataStoreManager.setLocalTrackingEnabled(true)
        }
    }

    /** Se puede llamar de nuevo (ej. botón "Regenerar") para barajar otra combinación distinta. */
    fun generateMix() {
        generateJob?.cancel()
        generateJob =
            viewModelScope.launch {
                _isLoading.value = true
                try {
                    val weighted =
                        when (_mode.value) {
                            MixMode.FOR_YOU -> buildFromPlaybackEvents().ifTooSmall { buildFromPlayCount() }
                            MixMode.TIME_OF_DAY -> buildForTimeOfDay().ifTooSmall { buildFromPlaybackEvents() }
                            // Sin relleno a propósito: rellenar con "tus más escuchadas"
                            // dejaría de ser "redescubrir".
                            MixMode.REDISCOVER -> buildRediscover()
                        }
                    val mix = weightedShuffleTake(weighted, MIX_SIZE)
                    _mix.value = mix
                    // Si después de cruzar con la tabla `song` no quedó nada, hay que
                    // mostrar el mensaje de "poco historial" y no una lista vacía muda.
                    _notEnoughHistory.value = mix.isEmpty()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Logger.e(TAG, "generateMix falló: ${e.message}", e)
                    _mix.value = emptyList()
                    _notEnoughHistory.value = true
                } finally {
                    // Siempre se apaga el loading: antes una excepción dejaba el
                    // spinner girando para siempre.
                    _isLoading.value = false
                }
            }
    }

    /** Fuente principal: analíticas locales (requiere el historial local activado). */
    private suspend fun buildFromPlaybackEvents(): List<Pair<SongEntity, Double>> {
        // 60 días de ventana: bastante para juntar variedad, no tanto
        // como para arrastrar canciones que ya no te interesan.
        var topTracks = analyticsRepository.queryTopPlayedSongsLastXDays(60).first()
        // Si hay poco historial reciente, ampliamos a una ventana enorme (~100
        // años, no Int.MAX_VALUE - beforeXDays() hace aritmética de fechas real
        // y un valor tan extremo puede desbordar).
        if (topTracks.size < MIN_TRACKS_FOR_MIX) {
            topTracks = analyticsRepository.queryTopPlayedSongsLastXDays(EFFECTIVELY_ALL_TIME_DAYS).first()
        }
        if (topTracks.isEmpty()) return emptyList()

        val songsById =
            songRepository
                .getSongsByListVideoId(topTracks.map { it.videoId })
                .first()
                .associateBy { it.videoId }

        // Peso = playCount con raíz cuadrada para achatar la curva: sin esto,
        // una canción escuchada 50 veces monopoliza el mix.
        return topTracks.mapNotNull { top ->
            songsById[top.videoId]?.let { it to sqrt(top.playCount.toDouble()).coerceAtLeast(1.0) }
        }
    }

    /**
     * Lo que más escuchás a esta hora: eventos de los últimos [TIME_OF_DAY_WINDOW_DAYS]
     * días cuya hora cae a ±[TIME_OF_DAY_HOUR_RADIUS] h de la hora actual.
     * Se leen paginados con la consulta que ya existe (sin SQL nuevo).
     */
    private suspend fun buildForTimeOfDay(): List<Pair<SongEntity, Double>> {
        val currentHour = now().hour
        val cutoff = now().beforeXDays(TIME_OF_DAY_WINDOW_DAYS)
        val counts = HashMap<String, Int>()
        var offset = 0
        while (offset < TIME_OF_DAY_MAX_EVENTS) {
            val page = analyticsRepository.getPlaybackEventsByOffsetAndTimestamp(offset, PAGE_SIZE, cutoff).first()
            page.forEach { event ->
                val diff = kotlin.math.abs(event.timestamp.hour - currentHour)
                val circularDiff = minOf(diff, 24 - diff)
                if (circularDiff <= TIME_OF_DAY_HOUR_RADIUS) {
                    counts[event.videoId] = (counts[event.videoId] ?: 0) + 1
                }
            }
            if (page.size < PAGE_SIZE) break
            offset += PAGE_SIZE
        }
        if (counts.isEmpty()) return emptyList()
        val songs = songRepository.getSongsByListVideoId(counts.keys.toList()).first()
        return songs.map { it to sqrt((counts[it.videoId] ?: 1).toDouble()).coerceAtLeast(1.0) }
    }

    /**
     * Redescubrir: canciones con historial ANTERIOR a [REDISCOVER_AFTER_DAYS]
     * días que no se escucharon desde entonces. Si el historial local es nuevo,
     * se usa `song.inLibrary` (fecha de última reproducción) como respaldo.
     */
    private suspend fun buildRediscover(): List<Pair<SongEntity, Double>> {
        val cutoff = now().beforeXDays(REDISCOVER_AFTER_DAYS)
        val recentIds =
            analyticsRepository
                .queryTopPlayedSongsLastXDays(REDISCOVER_AFTER_DAYS)
                .first()
                .map { it.videoId }
                .toHashSet()
        val older =
            analyticsRepository
                .queryTopPlayedSongsInRange(now().beforeXDays(EFFECTIVELY_ALL_TIME_DAYS), cutoff)
                .first()
                .filter { it.videoId !in recentIds }
        val fromEvents =
            if (older.isEmpty()) {
                emptyList()
            } else {
                val byId = songRepository.getSongsByListVideoId(older.map { it.videoId }).first().associateBy { it.videoId }
                older.mapNotNull { top -> byId[top.videoId]?.let { it to sqrt(top.playCount.toDouble()).coerceAtLeast(1.0) } }
            }
        if (fromEvents.size >= MIN_TRACKS_FOR_MIX) return fromEvents
        val seen = fromEvents.map { it.first.videoId }.toHashSet()
        val fromPlayCount =
            songRepository
                .getMostPlayedSongs()
                .first()
                .filter { it.inLibrary < cutoff && it.videoId !in recentIds && seen.add(it.videoId) }
                .map { it to sqrt(it.totalPlayTime.toDouble()).coerceAtLeast(1.0) }
        return fromEvents + fromPlayCount
    }

    /**
     * Respaldo: `song.totalPlayTime` se incrementa en cada reproducción SIEMPRE,
     * esté o no activado el historial local. Así el mix funciona desde el primer
     * día, incluso para quien tiene el historial apagado.
     */
    private suspend fun buildFromPlayCount(): List<Pair<SongEntity, Double>> =
        songRepository
            .getMostPlayedSongs()
            .first()
            .map { it to sqrt(it.totalPlayTime.toDouble()).coerceAtLeast(1.0) }

    private suspend inline fun List<Pair<SongEntity, Double>>.ifTooSmall(
        fallback: () -> List<Pair<SongEntity, Double>>,
    ): List<Pair<SongEntity, Double>> {
        if (size >= MIN_TRACKS_FOR_MIX) return this
        // Combinamos ambas fuentes (sin duplicados) en vez de descartar lo poco
        // que haya en playback_event.
        val seen = map { it.first.videoId }.toHashSet()
        return this + fallback().filter { seen.add(it.first.videoId) }
    }

    companion object {
        private const val MIN_TRACKS_FOR_MIX = 10
        private const val MIX_SIZE = 25
        private const val EFFECTIVELY_ALL_TIME_DAYS = 36_500 // ~100 años
        private const val TAG = "LocalMixViewModel"
        private const val PAGE_SIZE = 500
        private const val TIME_OF_DAY_WINDOW_DAYS = 120
        private const val TIME_OF_DAY_MAX_EVENTS = 6_000
        private const val TIME_OF_DAY_HOUR_RADIUS = 2
        private const val REDISCOVER_AFTER_DAYS = 60

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
