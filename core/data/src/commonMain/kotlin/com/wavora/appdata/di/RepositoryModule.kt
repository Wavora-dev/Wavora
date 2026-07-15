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

// WAVORA STARTUP FIX (Objetivo 1): estos 12 repositorios estaban todos marcados
// `createdAtStart = true`, lo que fuerza a Koin a construirlos de forma SÍNCRONA
// dentro de `startKoin { }`, antes de que exista ventana o composición alguna.
// Solo `AlbumRepository`, `LocalPlaylistRepository`, `PlaylistRepository` y
// `SongRepository` son realmente necesarios de entrada: los usa `SharedViewModel`,
// resuelto explícitamente justo después de `startKoin` (DesktopApp.kt/MainActivity.kt).
// El resto (Account, Artist, Home, LyricsCanvas, Podcast, Search, Stream,
// Update, Analytics) no se tocan hasta que el usuario navega a una pantalla que
// los usa. Quitar `createdAtStart` NO cambia el comportamiento observable para
// esos 9 — Koin sigue garantizando una única instancia y la construye en el
// primer `get()`/inyección real, solo se difiere el MOMENTO.
//
// AUDIT NOTE (bug real, encontrado y corregido): `CommonRepository` SÍ es un
// caso distinto de esos otros 9, y quedó mal incluido en esa lista original.
// Su `.init(...)` arranca la sincronización reactiva
// `dataStoreManager.cookie.collectLatest { youTube.cookie = cookie }` — el
// ÚNICO lugar que restaura, al arrancar la app, la cookie de YouTube Music
// persistida hacia el cliente compartido `YouTube`/`Ytmusic`. Ni HomeViewModel
// ni LibraryViewModel inyectan `CommonRepository` (usan Home/Library/Playlist
// repository), así que con la construcción diferida esa sincronización nunca
// se disparaba en un arranque en frío: Home/Library pedían datos personalizados
// con `youTube.cookie == null` — se veían como "sesión cerrada", historial
// vacío, aunque la cookie estuviera perfecta en disco — hasta que el usuario
// visitaba Configuración > Cuenta, que sí inyecta `CommonRepository`
// indirectamente (a través de `AccountRepositoryImpl.getAccountInfo`, que
// reasigna `youTube.cookie` como efecto secundario de refrescar la info de
// cuenta) y recién ahí "aparecía" todo. Fix: `CommonRepository` vuelve a
// `createdAtStart = true` — es el único de los 12 cuya construcción diferida
// tiene un efecto secundario del que dependen otras pantallas sin saberlo.
val repositoryModule =
    module {
        single<AccountRepository> {
            AccountRepositoryImpl(get(), get())
        }

        single<AlbumRepository>(createdAtStart = true) {
            AlbumRepositoryImpl(get(), get())
        }

        single<ArtistRepository> {
            ArtistRepositoryImpl(get(), get())
        }

        single<CommonRepository>(createdAtStart = true) {
            CommonRepositoryImpl(get(named(SERVICE_SCOPE)), get(), get(), get(), get(), get()).apply {
                this.init("${fileDir()}/ytdlp-cookie.txt", get())
            }
        }

        single<HomeRepository> {
            HomeRepositoryImpl(get(), get())
        }

        single<LocalPlaylistRepository>(createdAtStart = true) {
            LocalPlaylistRepositoryImpl(get(), get())
        }

        single<LyricsCanvasRepository> {
            LyricsCanvasRepositoryImpl(get(), get(), get(), get(), get())
        }

        single<PlaylistRepository>(createdAtStart = true) {
            PlaylistRepositoryImpl(get(), get(), get())
        }

        single<PodcastRepository> {
            PodcastRepositoryImpl(get(), get())
        }

        single<SearchRepository> {
            SearchRepositoryImpl(get(), get())
        }

        single<SongRepository>(createdAtStart = true) {
            SongRepositoryImpl(get(), get(), get())
        }

        single<StreamRepository> {
            StreamRepositoryImpl(get(), get())
        }

        single<UpdateRepository> {
            UpdateRepositoryImpl(get())
        }

        single<AnalyticsRepository> {
            AnalyticsRepositoryImpl(get())
        }
    }