package com.wavora.app.di

import com.wavora.app.viewModel.AlbumViewModel
import com.wavora.app.viewModel.AnalyticsViewModel
import com.wavora.app.viewModel.AppViewModel
import com.wavora.app.viewModel.ArtistViewModel
import com.wavora.app.viewModel.HomeViewModel
import com.wavora.app.viewModel.LibraryDynamicPlaylistViewModel
import com.wavora.app.viewModel.LibraryViewModel
import com.wavora.app.viewModel.LocalPlaylistViewModel
import com.wavora.app.viewModel.LogInViewModel
import com.wavora.app.viewModel.MoodViewModel
import com.wavora.app.viewModel.MoreAlbumsViewModel
import com.wavora.app.viewModel.NotificationViewModel
import com.wavora.app.viewModel.NowPlayingBottomSheetViewModel
import com.wavora.app.viewModel.NowPlayingViewModel
import com.wavora.app.viewModel.PlayerViewModel
import com.wavora.app.viewModel.PlaylistViewModel
import com.wavora.app.viewModel.PodcastViewModel
import com.wavora.app.viewModel.RecentlySongsViewModel
import com.wavora.app.viewModel.SearchViewModel
import com.wavora.app.viewModel.SettingsViewModel
import com.wavora.app.viewModel.SharedViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val viewModelModule =
    module {
        // ── Sub-ViewModels (singletons so they survive across screens) ────
        single {
            PlayerViewModel(
                get(), // DataStoreManager
                get(), // SongRepository
                get(), // StreamRepository
            )
        }
        single {
            NowPlayingViewModel(
                get(), // DataStoreManager
                get(), // SongRepository
                get(), // LyricsCanvasRepository
                get(), // StreamRepository
            )
        }
        single {
            AppViewModel(
                get(), // DataStoreManager
                get(), // UpdateRepository
            )
        }

        // ── SharedViewModel: thin coordinator, depends on sub-VMs ─────────
        single {
            SharedViewModel(
                get(), // DataStoreManager
                get(), // SongRepository
                get(), // AlbumRepository
                get(), // LocalPlaylistRepository
                get(), // PlaylistRepository
            )
        }

        // ── Screen ViewModels ─────────────────────────────────────────────
        single {
            SearchViewModel(
                get(),
                get(),
            )
        }
        viewModel {
            NowPlayingBottomSheetViewModel(
                get(),
                get(),
                get(),
                get(),
            )
        }
        viewModel {
            LibraryViewModel(
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
            )
        }
        viewModel {
            LibraryDynamicPlaylistViewModel(
                get(),
                get(),
            )
        }
        viewModel {
            AlbumViewModel(
                get(),
                get(),
            )
        }
        viewModel {
            HomeViewModel(
                get(),
                get(),
            )
        }
        viewModel {
            SettingsViewModel(
                get(),
                get(),
                get(),
                get(),
                get(),
            )
        }
        viewModel {
            ArtistViewModel(
                get(),
                get(),
            )
        }
        viewModel {
            PlaylistViewModel(
                get(),
                get(),
                get(),
            )
        }
        viewModel {
            LogInViewModel(
                get(),
            )
        }
        viewModel {
            PodcastViewModel(
                get(),
            )
        }
        viewModel {
            MoreAlbumsViewModel(
                get(),
            )
        }
        viewModel {
            RecentlySongsViewModel(
                get(),
            )
        }
        viewModel {
            LocalPlaylistViewModel(
                get(),
                get(),
                get(),
            )
        }
        viewModel {
            NotificationViewModel(
                get(),
            )
        }
        viewModel {
            MoodViewModel(
                get(),
                get(),
            )
        }
        viewModel {
            AnalyticsViewModel(
                get(),
                get(),
                get(),
                get(),
                get(),
            )
        }
    }
