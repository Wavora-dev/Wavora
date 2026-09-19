package com.wavora.app.ui.screen.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.wavora.app.ui.component.EndOfPage
import com.wavora.app.ui.component.MoodAndGenresContentItem
import com.wavora.app.ui.component.NormalAppBar
import com.wavora.app.ui.theme.typo
import com.wavora.app.viewModel.MoodViewModel
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import wavora.composeapp.generated.resources.*
import com.wavora.app.ui.theme.LocalAppTypography

@Composable
fun MoodScreen(
    navController: NavController,
    viewModel: MoodViewModel = koinViewModel(),
    params: String?,
) {
    val moodData by viewModel.moodsMomentObject.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val genreMixLoading by viewModel.genreMixLoading.collectAsStateWithLifecycle()

    LaunchedEffect(key1 = params) {
        if (params != null) {
            viewModel.getMood(params)
        }
    }

    Column {
        NormalAppBar(
            title = {
                Text(
                    text = moodData?.header ?: "",
                    style = LocalAppTypography.current.labelMedium,
                )
            },
            leftIcon = {
                IconButton(onClick = { navController.navigateUp() }) {
                    Icon(
                        painterResource(Res.drawable.baseline_arrow_back_ios_new_24),
                        contentDescription = stringResource(Res.string.back),
                    )
                }
            },
            // AUDIT (Mix por género, Desktop + Android): arma una cola para
            // reproducir con canciones reales de varias de las playlists
            // curadas que YouTube Music trae para esta categoría, en vez de
            // tener que entrar a una sola playlist a mano. No depende de la
            // plataforma - toda la lógica vive en MoodViewModel (común).
            rightIcon =
                if (!moodData?.items.isNullOrEmpty()) {
                    {
                        if (genreMixLoading) {
                            Box(
                                modifier = Modifier.size(48.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            }
                        } else {
                            IconButton(onClick = { viewModel.playGenreMix() }) {
                                Icon(
                                    painterResource(Res.drawable.baseline_shuffle_24),
                                    contentDescription = stringResource(Res.string.genre_mix),
                                )
                            }
                        }
                    }
                } else {
                    null
                },
        )
        AnimatedVisibility(visible = !loading) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
            ) {
                items(moodData?.items ?: emptyList(), key = { it.header.ifEmpty { it.hashCode() } }) { item ->
                    MoodAndGenresContentItem(
                        data = item,
                        navController = navController,
                    )
                }
                item {
                    EndOfPage()
                }
            }
        }
        AnimatedVisibility(visible = loading) {
            Box(
                modifier = Modifier.fillMaxSize(),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}