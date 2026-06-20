package com.wavora.appdata.di

import com.wavora.common.Config.SERVICE_SCOPE
import com.wavora.appdata.io.fileDir
import com.wavora.appdata.repository.AccountRepositoryImpl
import com.wavora.appdata.repository.AlbumRepositoryImpl
import com.wavora.appdata.repository.AnalyticsRepositoryImpl
import com.wavora.appdata.repository.ArtistRepositoryImpl
import com.wavora.appdata.repository.CommonRepositoryImpl
import com.wavora.appdata.repository.HomeRepositoryImpl
import com.wavora.appdata.repository.LocalPlaylistRepositoryImpl
import com.wavora.appdata.repository.LyricsCanvasRepositoryImpl
import com.wavora.appdata.repository.PlaylistRepositoryImpl
import com.wavora.appdata.repository.PodcastRepositoryImpl
import com.wavora.appdata.repository.SearchRepositoryImpl
import com.wavora.appdata.repository.SongRepositoryImpl
import com.wavora.appdata.repository.StreamRepositoryImpl
import com.wavora.appdata.repository.UpdateRepositoryImpl
import com.wavora.domain.repository.AccountRepository
import com.wavora.domain.repository.AlbumRepository
import com.wavora.domain.repository.AnalyticsRepository
import com.wavora.domain.repository.ArtistRepository
import com.wavora.domain.repository.CommonRepository
import com.wavora.domain.repository.HomeRepository
import com.wavora.domain.repository.LocalPlaylistRepository
import com.wavora.domain.repository.LyricsCanvasRepository
import com.wavora.domain.repository.PlaylistRepository
import com.wavora.domain.repository.PodcastRepository
import com.wavora.domain.repository.SearchRepository
import com.wavora.domain.repository.SongRepository
import com.wavora.domain.repository.StreamRepository
import com.wavora.domain.repository.UpdateRepository
import org.koin.core.qualifier.named
import org.koin.dsl.module

val repositoryModule =
    module {
        single<AccountRepository>(createdAtStart = true) {
            AccountRepositoryImpl(get(), get())
        }

        single<AlbumRepository>(createdAtStart = true) {
            AlbumRepositoryImpl(get(), get())
        }

        single<ArtistRepository>(createdAtStart = true) {
            ArtistRepositoryImpl(get(), get())
        }

        single<CommonRepository>(createdAtStart = true) {
            CommonRepositoryImpl(get(named(SERVICE_SCOPE)), get(), get(), get(), get(), get()).apply {
                this.init("${fileDir()}/ytdlp-cookie.txt", get())
            }
        }

        single<HomeRepository>(createdAtStart = true) {
            HomeRepositoryImpl(get(), get())
        }

        single<LocalPlaylistRepository>(createdAtStart = true) {
            LocalPlaylistRepositoryImpl(get(), get())
        }

        single<LyricsCanvasRepository>(createdAtStart = true) {
            LyricsCanvasRepositoryImpl(get(), get(), get(), get(), get())
        }

        single<PlaylistRepository>(createdAtStart = true) {
            PlaylistRepositoryImpl(get(), get(), get())
        }

        single<PodcastRepository>(createdAtStart = true) {
            PodcastRepositoryImpl(get(), get())
        }

        single<SearchRepository>(createdAtStart = true) {
            SearchRepositoryImpl(get(), get())
        }

        single<SongRepository>(createdAtStart = true) {
            SongRepositoryImpl(get(), get(), get())
        }

        single<StreamRepository>(createdAtStart = true) {
            StreamRepositoryImpl(get(), get())
        }

        single<UpdateRepository>(createdAtStart = true) {
            UpdateRepositoryImpl(get())
        }

        single<AnalyticsRepository>(createdAtStart = true) {
            AnalyticsRepositoryImpl(get())
        }
    }