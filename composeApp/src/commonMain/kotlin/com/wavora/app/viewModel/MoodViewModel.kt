package com.wavora.app.viewModel

import androidx.lifecycle.viewModelScope
import com.wavora.common.Config
import com.wavora.domain.model.model.mood.moodmoments.MoodsMomentObject
import com.wavora.domain.manager.DataStoreManager
import com.wavora.domain.mediaservice.handler.PlaylistType
import com.wavora.domain.mediaservice.handler.QueueData
import com.wavora.domain.model.model.browse.album.Track
import com.wavora.domain.repository.HomeRepository
import com.wavora.domain.repository.PlaylistRepository
import com.wavora.domain.utils.Resource
import com.wavora.logger.Logger
import com.wavora.app.viewModel.base.BaseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import wavora.composeapp.generated.resources.Res
import wavora.composeapp.generated.resources.genre_mix_not_available
import wavora.composeapp.generated.resources.view_count

class MoodViewModel(
    // Kept for DI wiring parity with other ViewModels built from the same Koin module; no longer
    // read directly now that the dead regionCode/language fields below have been removed.
    dataStoreManager: DataStoreManager,
    private val homeRepository: HomeRepository,
    private val playlistRepository: PlaylistRepository,
) : BaseViewModel() {
    private val _moodsMomentObject: MutableStateFlow<MoodsMomentObject?> = MutableStateFlow(null)
    var moodsMomentObject: StateFlow<MoodsMomentObject?> = _moodsMomentObject
    var loading = MutableStateFlow<Boolean>(false)

    // AUDIT (Mix por género, Desktop + Android): estado del botón "Mix" de
    // esta pantalla - mientras arma la cola no queremos que el usuario lo
    // pueda tocar de nuevo y disparar dos mixes en simultáneo.
    private val _genreMixLoading = MutableStateFlow(false)
    val genreMixLoading: StateFlow<Boolean> = _genreMixLoading
    private var genreMixJob: Job? = null

    fun getMood(params: String) {
        loading.value = true
        viewModelScope.launch {
            homeRepository.getMoodData(params).collect { values ->
                Logger.w("MoodViewModel", "getMood: $values")
                when (values) {
                    is Resource.Success -> {
                        _moodsMomentObject.value = values.data
                    }

                    is Resource.Error -> {
                        _moodsMomentObject.value = null
                    }
                }
            }
            withContext(Dispatchers.Main) {
                loading.value = false
            }
        }
    }

    /**
     * Botón "Mix" de la pantalla de género/mood: en vez de navegar a una
     * sola de las playlists curadas que YouTube Music devuelve para esta
     * categoría (lo único que se podía hacer antes acá), junta canciones
     * REALES de varias de ellas al mismo tiempo y arranca a reproducir una
     * cola nueva armada con esa mezcla - un "mix por género" propio, no
     * una playlist curada más.
     */
    fun playGenreMix() {
        if (genreMixJob?.isActive == true) return

        val genreTitle = _moodsMomentObject.value?.header.orEmpty()
        val sourcePlaylistIds =
            _moodsMomentObject.value
                ?.items
                ?.flatMap { it.contents }
                ?.map { it.playlistBrowseId }
                ?.filter { it.isNotBlank() }
                ?.distinct()
                ?.shuffled()
                ?.take(MAX_SOURCE_PLAYLISTS)
                ?: emptyList()

        if (sourcePlaylistIds.isEmpty()) {
            makeToast(getString(Res.string.genre_mix_not_available))
            return
        }

        genreMixJob =
            viewModelScope.launch {
                _genreMixLoading.value = true
                val viewString = getString(Res.string.view_count)

                // Las playlists de origen son independientes entre sí, así que
                // se piden todas en paralelo en vez de una por una - con 5-6
                // requests esto es la diferencia entre <2s y >8s de espera.
                val tracksPerPlaylist =
                    sourcePlaylistIds
                        .map { playlistId ->
                            async(Dispatchers.IO) { fetchSomeRealTracks(playlistId, viewString) }
                        }.awaitAll()

                val mixedTracks =
                    tracksPerPlaylist
                        .flatten()
                        .distinctBy { it.videoId }
                        .shuffled()
                        .take(MAX_TOTAL_TRACKS)

                _genreMixLoading.value = false

                if (mixedTracks.isEmpty()) {
                    makeToast(getString(Res.string.genre_mix_not_available))
                    return@launch
                }

                val firstTrack = mixedTracks.first()
                setQueueData(
                    QueueData.Data(
                        listTracks = mixedTracks.toCollection(arrayListOf<Track>()),
                        firstPlayedTrack = firstTrack,
                        // Id sintético (no es un playlistId real de YouTube): identifica
                        // esta cola como "el mix de género de esta sesión", nada más.
                        playlistId = "GENRE_MIX_${_moodsMomentObject.value?.params}",
                        playlistName = "\"$genreTitle\" Mix",
                        playlistType = PlaylistType.RADIO,
                        continuation = null,
                    ),
                )
                loadMediaItem(firstTrack, Config.RADIO_CLICK, 0)
            }
    }

    /** Trae hasta [TRACKS_PER_SOURCE_PLAYLIST] canciones reales (al azar) de una playlist curada. */
    private suspend fun fetchSomeRealTracks(
        playlistId: String,
        viewString: String,
    ): List<Track> =
        runCatching {
            val result = playlistRepository.getPlaylistData(playlistId, viewString).firstOrNull()
            when (result) {
                is Resource.Success ->
                    result.data
                        ?.first
                        ?.tracks
                        ?.shuffled()
                        ?.take(TRACKS_PER_SOURCE_PLAYLIST)
                        ?: emptyList()
                else -> {
                    Logger.w("MoodViewModel", "fetchSomeRealTracks: sin datos para $playlistId (${result?.message})")
                    emptyList()
                }
            }
        }.getOrElse {
            Logger.w("MoodViewModel", "fetchSomeRealTracks: error en $playlistId: ${it.message}")
            emptyList()
        }

    companion object {
        private const val MAX_SOURCE_PLAYLISTS = 6
        private const val TRACKS_PER_SOURCE_PLAYLIST = 8
        private const val MAX_TOTAL_TRACKS = 40
    }
}