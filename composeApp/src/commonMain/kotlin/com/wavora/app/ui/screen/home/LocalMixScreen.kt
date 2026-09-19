package com.wavora.app.ui.screen.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.wavora.app.ui.component.CenterLoadingBox
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
import wavora.composeapp.generated.resources.local_mix_not_enough_history

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
    val playingTrack by sharedViewModel.nowPlayingState.map { it?.songEntity }.collectAsState(initial = null)
    val isPlaying by sharedViewModel.controllerState.map { it.isPlaying }.collectAsState(initial = false)

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            isLoading -> {
                CenterLoadingBox(modifier = Modifier.fillMaxSize().padding(16.dp))
            }

            notEnoughHistory -> {
                Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(Res.string.local_mix_not_enough_history),
                        style = LocalAppTypography.current.bodyMedium,
                    )
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().hazeSource(state = hazeState),
                ) {
                    item {
                        Spacer(Modifier.size(innerPadding.calculateTopPadding() + 64.dp))
                    }

                    items(mix, key = { it.videoId }) { song ->
                        val mixPlaylistName = stringResource(Res.string.local_mix)
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

        TopAppBar(
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .hazeEffect(state = hazeState, style = HazeMaterials.ultraThin()) {
                        blurEnabled = true
                    },
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
                IconButton(onClick = { viewModel.generateMix() }) {
                    Icon(imageVector = Icons.Filled.Refresh, contentDescription = null)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        )
    }
}
