package com.wavora.app.ui.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.wavora.app.ui.component.CenterLoadingBox
import com.wavora.app.ui.component.EndOfPage
import com.wavora.app.ui.component.RippleIconButton
import com.wavora.app.ui.component.SongFullWidthItems
import com.wavora.app.ui.navigation.destination.list.ArtistDestination
import com.wavora.app.ui.theme.LocalAppTypography
import com.wavora.app.ui.theme.wavoraBorder
import com.wavora.app.ui.theme.wavoraGradientEnd
import com.wavora.app.ui.theme.wavoraPrimary
import com.wavora.app.ui.theme.wavoraSurface
import com.wavora.app.ui.theme.wavoraTextSecondary
import com.wavora.app.viewModel.SharedViewModel
import com.wavora.app.viewModel.WrappedViewModel
import com.wavora.common.Config
import com.wavora.domain.mediaservice.handler.PlaylistType
import com.wavora.domain.mediaservice.handler.QueueData
import com.wavora.domain.utils.toTrack
import kotlinx.coroutines.flow.map
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import wavora.composeapp.generated.resources.Res
import wavora.composeapp.generated.resources.baseline_arrow_back_ios_new_24
import wavora.composeapp.generated.resources.wrapped_days_listened
import wavora.composeapp.generated.resources.wrapped_distinct_songs
import wavora.composeapp.generated.resources.wrapped_empty
import wavora.composeapp.generated.resources.wrapped_longest_streak
import wavora.composeapp.generated.resources.wrapped_minutes
import wavora.composeapp.generated.resources.wrapped_plays
import wavora.composeapp.generated.resources.wrapped_plays_count
import wavora.composeapp.generated.resources.wrapped_title
import wavora.composeapp.generated.resources.wrapped_top_artists
import wavora.composeapp.generated.resources.wrapped_top_hour
import wavora.composeapp.generated.resources.wrapped_top_songs

/**
 * "Tu año en Wavora" (v2.3). Se abre desde Analíticas (botón de la barra
 * superior). Todo sale del historial local; ver [WrappedViewModel].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WrappedScreen(
    innerPadding: PaddingValues,
    navController: NavController,
    viewModel: WrappedViewModel = koinViewModel(),
    sharedViewModel: SharedViewModel = koinInject(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val playingTrack by sharedViewModel.nowPlayingState.map { it?.songEntity }.collectAsState(initial = null)
    val isPlaying by sharedViewModel.controllerState.map { it.isPlaying }.collectAsState(initial = false)

    Box(modifier = Modifier.fillMaxSize()) {
        when (val current = state) {
            WrappedViewModel.State.Loading -> {
                CenterLoadingBox(modifier = Modifier.fillMaxSize().padding(16.dp))
            }

            WrappedViewModel.State.Empty -> {
                Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(Res.string.wrapped_empty),
                        style = LocalAppTypography.current.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            is WrappedViewModel.State.Ready -> {
                val summary = current.summary
                val playlistName = stringResource(Res.string.wrapped_title, summary.year.toString())
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item { Spacer(Modifier.size(innerPadding.calculateTopPadding() + 64.dp)) }

                    item {
                        HeroCard(
                            title = playlistName,
                            big = summary.minutesListened.toString(),
                            caption = stringResource(Res.string.wrapped_minutes),
                        )
                    }

                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            StatCard(summary.plays.toString(), stringResource(Res.string.wrapped_plays), Modifier.weight(1f))
                            StatCard(summary.distinctSongs.toString(), stringResource(Res.string.wrapped_distinct_songs), Modifier.weight(1f))
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            StatCard(summary.daysListened.toString(), stringResource(Res.string.wrapped_days_listened), Modifier.weight(1f))
                            StatCard(summary.longestStreakDays.toString(), stringResource(Res.string.wrapped_longest_streak), Modifier.weight(1f))
                        }
                    }
                    summary.topHour?.let { hour ->
                        item {
                            StatCard(
                                value = "${hour.toString().padStart(2, '0')}:00",
                                label = stringResource(Res.string.wrapped_top_hour),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    if (summary.topSongs.isNotEmpty()) {
                        item { SectionTitle(stringResource(Res.string.wrapped_top_songs)) }
                        items(summary.topSongs, key = { "song_" + it.first.videoId }) { (song, _) ->
                            SongFullWidthItems(
                                songEntity = song,
                                isPlaying = playingTrack?.videoId == song.videoId && isPlaying,
                                onClickListener = { _ ->
                                    val first = song.toTrack()
                                    viewModel.setQueueData(
                                        QueueData.Data(
                                            listTracks = ArrayList(summary.topSongs.map { it.first.toTrack() }),
                                            firstPlayedTrack = first,
                                            playlistId = "local-wrapped-${summary.year}",
                                            playlistName = playlistName,
                                            playlistType = PlaylistType.LOCAL_PLAYLIST,
                                            continuation = null,
                                        ),
                                    )
                                    viewModel.loadMediaItem(first, Config.SONG_CLICK, 0)
                                },
                                onAddToQueue = {
                                    sharedViewModel.addListToQueue(arrayListOf(song.toTrack()))
                                },
                                modifier = Modifier,
                            )
                        }
                    }

                    if (summary.topArtists.isNotEmpty()) {
                        item { SectionTitle(stringResource(Res.string.wrapped_top_artists)) }
                        items(summary.topArtists, key = { "artist_" + it.first.channelId }) { (artist, plays) ->
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { navController.navigate(ArtistDestination(channelId = artist.channelId)) }
                                        .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AsyncImage(
                                    model = artist.thumbnails,
                                    contentDescription = artist.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(48.dp).clip(CircleShape).background(wavoraSurface),
                                )
                                Column(modifier = Modifier.padding(start = 12.dp)) {
                                    Text(text = artist.name, style = LocalAppTypography.current.bodyMedium, maxLines = 1)
                                    Text(
                                        text = stringResource(Res.string.wrapped_plays_count, plays.toString()),
                                        style = LocalAppTypography.current.bodySmall,
                                        color = wavoraTextSecondary,
                                    )
                                }
                            }
                        }
                    }

                    item { EndOfPage() }
                }
            }
        }

        TopAppBar(
            modifier = Modifier.align(Alignment.TopCenter),
            title = {},
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
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        )
    }
}

@Composable
private fun HeroCard(
    title: String,
    big: String,
    caption: String,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.linearGradient(
                        listOf(wavoraPrimary.copy(alpha = 0.55f), wavoraGradientEnd.copy(alpha = 0.35f)),
                    ),
                ).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text = title, style = LocalAppTypography.current.titleMedium, color = Color.White)
        Text(text = big, style = LocalAppTypography.current.headlineLarge, color = Color.White)
        Text(text = caption, style = LocalAppTypography.current.bodyMedium, color = Color.White.copy(alpha = 0.85f))
    }
}

@Composable
private fun StatCard(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(16.dp))
                .background(wavoraSurface)
                .background(wavoraBorder.copy(alpha = 0.25f))
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text = value, style = LocalAppTypography.current.titleLarge, color = Color.White)
        Text(text = label, style = LocalAppTypography.current.bodySmall, color = wavoraTextSecondary)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = LocalAppTypography.current.titleMedium,
        color = Color.White,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}
