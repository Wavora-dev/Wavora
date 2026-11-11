package com.wavora.app.ui.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.wavora.app.ui.component.CenterLoadingBox
import com.wavora.app.ui.component.Chip
import com.wavora.app.ui.component.EndOfPage
import com.wavora.app.ui.component.RippleIconButton
import com.wavora.app.ui.component.SongFullWidthItems
import com.wavora.app.ui.theme.LocalAppTypography
import com.wavora.app.viewModel.LocalMixViewModel
import com.wavora.app.viewModel.SharedViewModel
import com.wavora.common.Config
import com.wavora.domain.mediaservice.handler.PlaylistType
import com.wavora.domain.mediaservice.handler.QueueData
import com.wavora.domain.utils.toTrack
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.flow.map
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import wavora.composeapp.generated.resources.Res
import wavora.composeapp.generated.resources.baseline_arrow_back_ios_new_24
import wavora.composeapp.generated.resources.local_mix
import wavora.composeapp.generated.resources.local_mix_enable_tracking
import wavora.composeapp.generated.resources.local_mix_mode_for_you
import wavora.composeapp.generated.resources.local_mix_mode_rediscover
import wavora.composeapp.generated.resources.local_mix_mode_time_of_day
import wavora.composeapp.generated.resources.local_mix_radio
import wavora.composeapp.generated.resources.local_mix_regenerate
import wavora.composeapp.generated.resources.local_mix_rediscover_empty
import wavora.composeapp.generated.resources.local_mix_not_enough_history
import wavora.composeapp.generated.resources.local_mix_tracking_off

@OptIn(ExperimentalMaterial3Api::class, ExperimentalHazeMaterialsApi::class)
@Composable
fun LocalMixScreen(
    innerPadding: PaddingValues,
    navController: NavController,
    viewModel: LocalMixViewModel = koinViewModel(),
    sharedViewModel: SharedViewModel = koinInject(),
) {
    val hazeState = rememberHazeState()

    val mix by viewModel.mix.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val notEnoughHistory by viewModel.notEnoughHistory.collectAsStateWithLifecycle()
    val trackingEnabled by viewModel.trackingEnabled.collectAsStateWithLifecycle()
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val mixPlaylistName = stringResource(Res.string.local_mix)
    val playingTrack by sharedViewModel.nowPlayingState.map { it?.songEntity }.collectAsState(initial = null)
    val isPlaying by sharedViewModel.controllerState.map { it.isPlaying }.collectAsState(initial = false)

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            isLoading -> {
                CenterLoadingBox(modifier = Modifier.fillMaxSize().padding(top = innerPadding.calculateTopPadding() + 112.dp).padding(16.dp))
            }

            notEnoughHistory -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(top = innerPadding.calculateTopPadding() + 112.dp).padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text =
                            when (mode) {
                                LocalMixViewModel.MixMode.REDISCOVER -> stringResource(Res.string.local_mix_rediscover_empty)
                                else -> stringResource(Res.string.local_mix_not_enough_history)
                            },
                        style = LocalAppTypography.current.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                    // Sin historial local activado, el mix solo cuenta con el
                    // contador de reproducciones: ofrecemos activarlo acá mismo.
                    if (!trackingEnabled) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = stringResource(Res.string.local_mix_tracking_off),
                            style = LocalAppTypography.current.bodySmall,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = {
                            viewModel.enableTracking()
                            viewModel.generateMix()
                        }) {
                            Text(text = stringResource(Res.string.local_mix_enable_tracking))
                        }
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().hazeSource(state = hazeState),
                ) {
                    item {
                        // 64dp de la barra superior + 48dp de los chips de modo.
                        Spacer(Modifier.size(innerPadding.calculateTopPadding() + 112.dp))
                    }

                    items(mix, key = { it.videoId }) { song ->
                        SongFullWidthItems(
                            songEntity = song,
                            isPlaying = playingTrack?.videoId == song.videoId && isPlaying,
                            onClickListener = { _ ->
                                val firstQueue = song.toTrack()
                                viewModel.setQueueData(
                                    QueueData.Data(
                                        listTracks = ArrayList(mix.map { it.toTrack() }),
                                        firstPlayedTrack = firstQueue,
                                        playlistId = "local-mix",
                                        playlistName = mixPlaylistName,
                                        playlistType = PlaylistType.LOCAL_PLAYLIST,
                                        continuation = null,
                                    ),
                                )
                                viewModel.loadMediaItem(firstQueue, Config.SONG_CLICK, 0)
                            },
                            onAddToQueue = {
                                sharedViewModel.addListToQueue(arrayListOf(song.toTrack()))
                            },
                            modifier = Modifier.animateItem(),
                        )
                    }

                    item { EndOfPage() }
                }
            }
        }

        Column(
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .hazeEffect(state = hazeState, style = HazeMaterials.ultraThin()) {
                        blurEnabled = true
                    },
        ) {
            TopAppBar(
                title = {
                    Text(text = stringResource(Res.string.local_mix), style = LocalAppTypography.current.titleMedium)
                },
                navigationIcon = {
                    Box(Modifier.padding(horizontal = 5.dp)) {
                        RippleIconButton(
                            Res.drawable.baseline_arrow_back_ios_new_24,
                            Modifier.size(32.dp),
                            true,
                        ) {
                            navController.navigateUp()
                        }
                    }
                },
                actions = {
                    // Radio del mix (v2.3): arranca una radio de YouTube Music a partir
                    // de una canción del mix (la primera, que ya salió al azar con peso).
                    IconButton(
                        enabled = mix.isNotEmpty(),
                        onClick = {
                            val seed = mix.firstOrNull()?.toTrack() ?: return@IconButton
                            viewModel.setQueueData(
                                QueueData.Data(
                                    listTracks = arrayListOf(seed),
                                    firstPlayedTrack = seed,
                                    playlistId = "RDAMVM${seed.videoId}",
                                    playlistName = mixPlaylistName,
                                    playlistType = PlaylistType.RADIO,
                                    continuation = null,
                                ),
                            )
                            viewModel.loadMediaItem(seed, Config.SONG_CLICK, 0)
                        },
                    ) {
                        Icon(imageVector = Icons.Filled.Radio, contentDescription = stringResource(Res.string.local_mix_radio))
                    }
                    IconButton(onClick = { viewModel.generateMix() }) {
                        Icon(imageVector = Icons.Filled.Refresh, contentDescription = stringResource(Res.string.local_mix_regenerate))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
            // Modos del mix (v2.3). Fuera de la lista a propósito: siguen visibles
            // aunque un modo no tenga canciones, para poder cambiar a otro.
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    LocalMixViewModel.MixMode.FOR_YOU to stringResource(Res.string.local_mix_mode_for_you),
                    LocalMixViewModel.MixMode.TIME_OF_DAY to stringResource(Res.string.local_mix_mode_time_of_day),
                    LocalMixViewModel.MixMode.REDISCOVER to stringResource(Res.string.local_mix_mode_rediscover),
                ).forEach { (option, label) ->
                    Chip(
                        isSelected = mode == option,
                        text = label,
                        onClick = { viewModel.setMode(option) },
                    )
                }
            }
        }
    }
}
