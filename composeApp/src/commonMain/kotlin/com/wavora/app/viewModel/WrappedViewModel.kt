package com.wavora.app.viewModel

import androidx.lifecycle.viewModelScope
import com.wavora.app.viewModel.base.BaseViewModel
import com.wavora.domain.extension.now
import com.wavora.domain.extension.startTimestampOfThisYear
import com.wavora.domain.model.entities.ArtistEntity
import com.wavora.domain.model.entities.SongEntity
import com.wavora.domain.repository.AnalyticsRepository
import com.wavora.domain.repository.ArtistRepository
import com.wavora.domain.repository.SongRepository
import com.wavora.logger.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * "Tu año en Wavora" (v2.3): resumen anual armado 100% con el historial local
 * de escuchas (la misma tabla playback_event que usan Analíticas y el Mix
 * local). No hace pedidos de red: los artistas que no están guardados
 * localmente simplemente no aparecen.
 */
class WrappedViewModel(
    private val analyticsRepository: AnalyticsRepository,
    private val songRepository: SongRepository,
    private val artistRepository: ArtistRepository,
) : BaseViewModel() {
    data class Summary(
        val year: Int,
        val minutesListened: Long,
        val plays: Int,
        val distinctSongs: Int,
        val daysListened: Int,
        val longestStreakDays: Int,
        /** Hora del día (0-23) con más reproducciones. */
        val topHour: Int?,
        val topSongs: List<Pair<SongEntity, Int>>,
        val topArtists: List<Pair<ArtistEntity, Int>>,
    )

    sealed interface State {
        data object Loading : State

        data object Empty : State

        data class Ready(
            val summary: Summary,
        ) : State
    }

    private val _state = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = State.Loading
            _state.value =
                try {
                    buildSummary()?.let { State.Ready(it) } ?: State.Empty
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Logger.e(TAG, "No se pudo armar el resumen: ${e.message}", e)
                    State.Empty
                }
        }
    }

    private suspend fun buildSummary(): Summary? {
        val start = startTimestampOfThisYear()
        val end = now()

        var listenedSeconds = 0L
        var plays = 0
        val songs = HashSet<String>()
        val days = HashSet<LocalDate>()
        val hours = IntArray(24)

        var offset = 0
        while (offset < MAX_EVENTS) {
            val page = analyticsRepository.getPlaybackEventsByOffsetAndTimestamp(offset, PAGE_SIZE, start).first()
            page.forEach { event ->
                plays++
                listenedSeconds += event.listenedSecond
                songs.add(event.videoId)
                days.add(event.timestamp.date)
                hours[event.timestamp.hour]++
            }
            if (page.size < PAGE_SIZE) break
            offset += PAGE_SIZE
        }
        if (plays == 0) return null

        val topTracks = analyticsRepository.queryTopPlayedSongsInRange(start, end).first().take(TOP_N)
        val songsById =
            songRepository
                .getSongsByListVideoId(topTracks.map { it.videoId })
                .first()
                .associateBy { it.videoId }
        val topSongs = topTracks.mapNotNull { top -> songsById[top.videoId]?.let { it to top.playCount } }

        val topArtists =
            analyticsRepository
                .queryTopArtistsInRange(start, end)
                .first()
                .take(TOP_N * 2)
                .mapNotNull { top ->
                    artistRepository.getArtistById(top.channelId).firstOrNull()?.let { it to top.playCount }
                }.take(TOP_N)

        return Summary(
            year = end.year,
            minutesListened = listenedSeconds / 60,
            plays = plays,
            distinctSongs = songs.size,
            daysListened = days.size,
            longestStreakDays = longestStreak(days),
            topHour = hours.indices.maxByOrNull { hours[it] }?.takeIf { hours[it] > 0 },
            topSongs = topSongs,
            topArtists = topArtists,
        )
    }

    companion object {
        private const val TAG = "WrappedViewModel"
        private const val PAGE_SIZE = 500
        private const val MAX_EVENTS = 100_000
        private const val TOP_N = 5

        /** Racha más larga de días seguidos con al menos una reproducción. */
        internal fun longestStreak(days: Set<LocalDate>): Int {
            if (days.isEmpty()) return 0
            var best = 0
            for (day in days) {
                // Solo se cuenta desde el primer día de cada racha.
                if (day.plus(-1, DateTimeUnit.DAY) in days) continue
                var length = 1
                var next = day.plus(1, DateTimeUnit.DAY)
                while (next in days) {
                    length++
                    next = next.plus(1, DateTimeUnit.DAY)
                }
                if (length > best) best = length
            }
            return best
        }
    }
}
