<div align="center">

<img src="composeApp/appimage/wavora.png" alt="Wavora" width="200"/>

# Wavora

**A modern, cross-platform music player powered by YouTube Music.**

[![Android](https://img.shields.io/badge/Android-3DDC84?style=flat&logo=android&logoColor=white)](https://github.com/Wavora-dev/Wavora/releases)
[![Windows](https://img.shields.io/badge/Windows-0078D4?style=flat&logo=windows&logoColor=white)](https://github.com/Wavora-dev/Wavora/releases)
[![macOS](https://img.shields.io/badge/macOS-000000?style=flat&logo=apple&logoColor=white)](https://github.com/Wavora-dev/Wavora/releases)
[![Linux](https://img.shields.io/badge/Linux-FCC624?style=flat&logo=linux&logoColor=black)](https://github.com/Wavora-dev/Wavora/releases)
[![License: GPL-3.0](https://img.shields.io/badge/License-GPL%203.0-blue.svg)](LICENSE)

[Download](https://github.com/Wavora-dev/Wavora/releases) · [Report a bug](https://github.com/Wavora-dev/Wavora/issues) · [Discord](https://discord.gg/wavora)

</div>

---

## About

Wavora is a free, open-source music player built on top of [SimpMusic](https://github.com/maxrave-dev/SimpMusic). It streams from YouTube Music without ads or a subscription, extends the original with a full desktop app for Windows, macOS and Linux, and adds a significant set of new features focused on playback quality, lyrics, personalization, and stability.

No account is required for basic playback. Log in with your Google account to unlock personalized recommendations, your YouTube Music library, and listening history sync.

---

## What's new compared to SimpMusic

### 🖥️ Platform
- **Full desktop app** for Windows, macOS, and Linux built with Kotlin Multiplatform + Compose Desktop + VLC — the first multiplatform build of SimpMusic's codebase
- Floating **miniplayer window** that stays on top and can be dragged anywhere on screen (with visual drag handle)
- **macOS Now Playing Center** and Remote Command Center (media keys, lock screen widget)
- **Windows protocol handler** (`wavora://`) for deep link support
- Custom title bar on desktop

### 🎛️ Architecture & Code Quality
- **SharedViewModel split** — the original 2000-line monolithic SharedViewModel was broken into four focused ViewModels (`PlayerViewModel`, `NowPlayingViewModel`, `AppViewModel`) each with a single responsibility
- All sub-ViewModels are Koin singletons; existing screens required zero changes thanks to full backward-compatible delegation
- Removed `runBlocking` from all hot paths in composables and onClick handlers — replaced with `stringResource` / hoisted vals
- Database operations moved off the main thread (`Dispatchers.IO`) across all repositories
- Startup download-reset converted from permanent `collect {}` observers to one-shot `first()` calls
- Per-song like status collector now properly cancels its predecessor job, preventing multiple open Room flows accumulating on rapid song changes
- `LocalAppTypography` CompositionLocal — `Typography` is now computed once in `AppTheme` and distributed via composition, instead of being recreated on every call to `typo()` (was called 426× across the codebase)
- Coil `MemoryCache` capped at 10% of heap (was unlimited at the OS default of 25%, ~64 MB on a 256 MB device)

### 🎵 Crossfade (Android)
- Two ExoPlayer instances with a per-player `CrossfadeFilterAudioProcessor`
- **Equal-power cos/sin volume curve** instead of linear — preserves perceived loudness throughout the blend
- **DJ Mode** — biquad filter sweep (LPF on outgoing, HPF on incoming) with sigmoid S-curve on time axis and exponential interpolation on frequency axis
- **AutoMix** — reads BPM and musical key from track metadata, adjusts crossfade duration and applies a front-loaded BPM/pitch ramp
- Next-track precaching pool (2 tracks ahead) so crossfade starts with audio already buffered
- Configurable duration (1–30 s) or fully automatic mode

### 🎵 Crossfade (Desktop)
- Two VLC instances (current + secondary) instead of one
- Same equal-power cos/sin volume curve as Android, 50 steps
- AutoMix BPM detection with the same metadata-driven duration resolution
- Precaching of the next track to minimize start latency

### 🎤 Lyrics
- **Wavora community lyrics database** — a new synced-lyrics provider built by the community
- **AI translation** — translate any synced lyric set to any language via OpenAI, Gemini, or a custom model endpoint
- Voting system (upvote / downvote) for both original and translated lyrics
- **Binary search** for the current lyric line (O(log n) instead of iterating all lines every 100 ms)

### 🟣 Discord Rich Presence
- Fixed the core authentication race condition (READY packet vs TCP connection state)
- `isSocketConnectedToAccount()` now correctly gates on `sessionId != null`
- Throttled updates to once every 5 seconds (was every 100 ms)
- Built-in step-by-step setup guide for all platforms

### 🟢 Spotify Integration
- Step-by-step `sp_dc` cookie setup guide built directly into the app
- Synced lyrics and Spotify Canvas work without any manual browser configuration on desktop

### 🔴 YouTube Account
- Built-in step-by-step desktop login guide, including a one-click copy of the `document.cookie` command
- Cookie validation confirms account info before saving
- Multiple accounts supported
- Granular logging in the login flow to diagnose cookie/API issues when they arise

### ⚡ Performance & Resource Usage
- **ExoPlayer buffer reduced from 4× to 2× the default** — with up to 4 simultaneous ExoPlayer instances, the previous setting was downloading up to 800 seconds of audio in parallel
- **`startForeground()` loop removed** — was being called every 30 seconds in an infinite coroutine on Android
- **Discord RPC throttled** on Android from every 100 ms to every 5 s
- **Rainbow crossfade animation** changed from always-running `rememberInfiniteTransition` to a `LaunchedEffect` only active during active crossfades
- **Chip animations** (HomeScreen) — infinite transitions now use `Int.MAX_VALUE` duration when the chip is not selected, effectively pausing 10 out of 11 always-running animations
- **Palette color extraction cached** per `videoId` in the Now Playing screen — re-extraction is skipped when swiping back to a previously seen track
- **`ImageLoader` in bulk downloads** — `DownloadUtils` now reuses a single lazy `ImageLoader` instance instead of creating one per downloaded track (direct contributor to OOM during large playlist downloads)
- **`HttpClient(CIO)` in HomeScreen** wrapped in `remember {}` — was leaking a new connection pool on every recomposition
- `MutableInteractionSource()` instances wrapped in `remember {}` in 8 places (NowPlaying, MiniPlayer, LyricsView, FullscreenPlayer)
- `derivedStateOf` usage verified correct across the codebase
- **Network request logging disabled in production** — `CurlLogger` and `LogLevel.ALL` were active in release builds
- **Kermit log level gated by build type** — release builds now use `Severity.Warn`
- **Room indices added** for `liked`, `downloadState`, `totalPlayTime`, and `inLibrary` on song, album, and playlist tables
- **Widget `ImageLoader` leak fixed** — the Glance widget was creating a new `ImageLoader` instance on every thumbnail change; now reuses a single cached instance per widget lifecycle

### 🐛 Bug Fixes
- **Android crash** (`Key "" was already used`) in `LazyColumn`/`LazyRow` fixed across all affected screens
- **Desktop crash** on search screen scroll: playlist and album results with the same `browseId` produced duplicate keys
- **ModalBottomSheet playlist list** (`Key "PLhNE7..." was already used`) — `key = { it.browseId }` replaced with `key = { "${it.browseId}_${it.hashCode()}" }` to handle API duplicates
- **parseCookieString crash** — `split("=")` without a limit crashed silently when cookie values contained `=` padding (common in base64 tokens); fixed with `split("=", limit = 2)`
- **Crossfade state flag** (`isCrossfading`) was being set on TCP connect instead of after Discord READY
- **`checkAllDownloadingSongs`** used `collect {}` (permanent observer) instead of `first()`
- **JVM buffered update** was hardcoded to emit 100% buffer level regardless of actual VLC buffer state
- **`isVideo` heuristic** in download — tracks with rectangular thumbnails were misclassified as videos based on image aspect ratio instead of the `videoType` field, causing double downloads and the "VIDEO" prefix appearing during downloads
- **Share URLs** pointing to the non-existent `wavora.org/app/...` domain replaced with valid `music.youtube.com` links across all share entry points (player, bottom sheet, model layer, Discord RPC)
- **`chart.wavora.org`** endpoint removed — the service does not exist; the Charts tab now returns an empty list gracefully instead of making failing network requests
- **`SettingScreen` `LaunchedEffect(true)` duplicate** — two effects with the same key caused `getAllGoogleAccount()` to be cancelled immediately by the second effect; merged into a single `LaunchedEffect(Unit)`
- **Greeting hour** on HomeScreen no longer freezes at the hour of app launch — updates dynamically each hour via a `LaunchedEffect` loop
- **`LimitedBorderAnimationView`** `LaunchedEffect(true)` changed to `LaunchedEffect(isAnimated)` so animation re-runs correctly when the flag changes after initial composition

### 🎨 Visual & UI
- **Onboarding flow** — 3-page animated introduction shown on first launch with gradient backgrounds, page indicators, and a "Get Started" CTA; never shown again after completion
- **Artwork crossfade** — the Now Playing screen now animates between album artworks when the track changes (`fadeIn 380ms / fadeOut 280ms`) via `AnimatedContent` instead of a hard cut
- **Search history as chips** — recent searches are now displayed as compact horizontal chip pills in a `FlowRow` instead of a full-width list, giving instant visual access to more history items at once
- **Skeleton loading screens** for Artist, Album, and Library — `ArtistScreenShimmer`, `AlbumScreenShimmer`, and `LibraryGridShimmer` composables replace the generic spinner while content loads
- **Error states with retry** — Artist, Album, and Playlist screens now show `OfflineErrorState` with a "Retry" button instead of silently navigating back when a network error occurs
- **Haptic feedback** on Android for all playback controls (shuffle, previous, play/pause, next, repeat) and the like/unlike button — provides tactile confirmation on every action
- **Empty state with icon** in Library grid — replaced the bare text fallback with a centered `MusicOff` icon + dimmed subtitle, consistent with the existing `OfflineErrorState` style
- **Typography semantic colors fixed** — `headlineMedium` and `headlineLarge` were defined as gray (`#A8A8A8`) but used for white section titles, requiring ~15 manual color overrides; now correctly defined as `Color.White`
- **Color system consolidated** — `wavora*` tokens are now the single source of truth; `md_theme_dark_*` values are aliases that reference them, eliminating duplicate definitions
- **Accent color corrected** — `seed` (active state for shuffle, repeat, like) was `#8ECAE6` (off-brand light blue); now `wavoraSecondary` (`#00D4FF`, the brand cyan)
- **Liquid Glass effect** on Android 16+ (blur + refraction on navigation bar and player)
- **Animated blur background** on the Now Playing screen driven by album art luminance
- **Spotify Canvas** — artist-provided animated video backgrounds on the Now Playing screen
- Translucent bottom navigation bar (configurable)
- Miniplayer on desktop with album art, playback controls, seek bar, and volume
- Smooth animated transitions on all navigation actions
- Scrollbars on all scrollable desktop lists

### 🔒 Privacy & Compatibility
- No analytics, no telemetry
- Proxy support (HTTP / SOCKS5 with auth) for restricted network environments
- Optional Piped instance as streaming data provider
- Community opt-in to help build the lyrics database (disabled by default)
- Backward-compatible with existing SimpMusic local databases

---

## Features

### Playback
- Stream any song, album, playlist or podcast from YouTube Music
- Background playback with media session notification (Android) and system tray (desktop)
- **Crossfade** — smooth transition between tracks with configurable duration (1–30 s) or Auto mode
  - **DJ Mode** — biquad filter sweep alongside volume fade (Android)
  - **AutoMix** — BPM-aware crossfade duration and tempo blending
- Gapless-style playback via next-track precaching
- **Sleep timer** — stop playback after N minutes or at the end of the current song
- Repeat (off / one / all) and shuffle
- Endless queue (auto-radio after last track)
- Skip silent sections
- SponsorBlock — automatically skip sponsor segments in music videos
- Balance loudness across tracks
- Configurable audio quality (low / medium / high / max)
- Download songs and videos for offline playback
- Save playback state across restarts (last track, queue, shuffle/repeat mode)

### Lyrics
- **Wavora community lyrics** — synced (rich) lyrics from the Wavora database
- **LRCLIB** — open lyrics database fallback
- **Spotify** — synced lyrics via Spotify `sp_dc` cookie
- **YouTube** — auto-generated captions / subtitles in any available language
- **BetterLyrics** — additional provider fallback
- **AI translation** — translate lyrics to any language using OpenAI, Gemini, or a custom model
- Word-level sync highlight (rich synced)
- Vote lyrics up or down to improve the community database
- Blurred fullscreen lyrics mode
- Offline lyrics (cached)

### Library
- **Favorites** — liked songs
- **Downloaded** — offline songs and videos
- **Followed artists**
- **Albums and playlists** — local and synced from YouTube Music
- **Recently played**
- **Most played**
- **Top tracks / artists / albums** — listening analytics with date range filter
- **Podcasts**
- Create, edit, delete and reorder local playlists
- Sync playlists to and from YouTube Music
- Add / remove songs from YouTube liked music
- Offline keep: cache your YouTube playlists for offline browsing

### Search
- Search songs, albums, artists, playlists, videos, and podcasts
- **Search history as chips** — recent searches displayed as quick-tap pills
- Suggested queries and YouTube Music suggestions
- Filter results by type (songs / videos / artists / albums / playlists / podcasts)
- Moods & Genres browser

### YouTube Music Account
- Log in with your YouTube / Google account (cookie-based, no OAuth required)
- Multiple accounts
- Upload listening history to YouTube Music (improves recommendations)
- Personalized home feed
- YouTube liked music sync

### Discord Rich Presence
- Shows current song, artist, album art, and elapsed time in your Discord status
- WebSocket gateway connection (not a bot — uses your personal user token)
- Built-in setup guide for all platforms

### Spotify Integration
- Synced lyrics via Spotify `sp_dc` cookie
- Spotify Canvas — animated backgrounds on the Now Playing screen

### Desktop (Windows / macOS / Linux)
- Standalone windowed app built with Kotlin Multiplatform + Compose Desktop
- Floating miniplayer window — stays on top, draggable anywhere on screen, with visual drag indicator
- Full-screen player
- macOS Now Playing Center and Remote Command Center (media keys, lock screen widget)
- Windows protocol handler (`wavora://`) for deep links
- VLC-based playback engine (libvlc)
- Scrollbars on all scrollable lists
- Custom title bar
- All three login flows (YouTube, Discord, Spotify) built into the app — step-by-step, no external browser DevTools needed

### Personalization
- Liquid Glass UI effect (Android 16+)
- Animated blur background on the Now Playing screen
- Translucent bottom navigation bar
- Themes follow system dark/light mode

### Privacy & Network
- No analytics, no telemetry
- Optional proxy support (HTTP / SOCKS5 with authentication)
- Optional Piped instance as streaming data provider
- Community opt-in to help build the Wavora lyrics database

### Data
- Backup and restore all app data (library, playlists, settings)
- Auto backup with configurable frequency (daily / weekly)
- Import / export local playlists
- Cache management (player cache, thumbnail cache, Canvas cache, downloaded files)

---

## Installation

### Android
Download the latest `.apk` from [Releases](https://github.com/Wavora-dev/Wavora/releases) and install it. Android 8.0+ required.

### Windows
Download the `.msi` installer from [Releases](https://github.com/Wavora-dev/Wavora/releases). Requires [VLC](https://www.videolan.org/vlc/) installed (64-bit).

### macOS
Download the `.dmg` from [Releases](https://github.com/Wavora-dev/Wavora/releases). Requires VLC installed.

### Linux
Download the `.deb` or `.rpm` from [Releases](https://github.com/Wavora-dev/Wavora/releases). Requires VLC (`libvlc-dev`).

---

## Building from source

Requirements: JDK 17+, Android SDK (for the Android target), VLC (for desktop tests).

```bash
git clone https://github.com/Wavora-dev/Wavora
cd Wavora

# VLC natives
gradlew.bat :composeApp:vlcSetup 

# Android APK
gradlew.bat :composeApp:assembleRelease

# Windows installer
gradlew.bat :desktopApp:packageMsi

# macOS DMG
gradlew.bat :desktopApp:packageDmg

# Linux DEB
gradlew.bat :desktopApp:packageDeb
```

---

## Credits

Wavora is built on top of [SimpMusic](https://github.com/maxrave-dev/SimpMusic) by maxrave-dev, licensed under GPL-3.0.

Additional libraries: ExoPlayer (Media3), VLC (vlcj), Koin, Ktor, Room, Jetpack Compose, Compose Multiplatform, KizzyRPC, Coil, and others listed in **Settings → Third-party libraries**.

---

## License

GPL-3.0 — see [LICENSE](LICENSE).

---

---

<div align="center">

# 🇦🇷 Wavora en español

</div>

---

## ¿Qué es Wavora?

Wavora es un reproductor de música gratuito y de código abierto que transmite desde YouTube Music sin publicidad ni suscripción. Está disponible para Android, Windows, macOS y Linux. No necesitás cuenta para reproducir música — si iniciás sesión con tu cuenta de Google, desbloqueás recomendaciones personalizadas, tu biblioteca de YouTube Music y el historial de escucha.

---

## Funciones principales

### 🎵 Reproducción
- Reproducí cualquier canción, álbum, playlist o podcast de YouTube Music
- Reproducción en segundo plano con notificación de medios (Android) y bandeja del sistema (escritorio)
- **Crossfade** — transición suave entre canciones con duración configurable (1–30 s) o modo automático
  - **Modo DJ** — barrido de filtro biquad junto al fade de volumen
  - **AutoMix** — ajuste automático de duración y tempo basado en el BPM de cada canción
- **Timer de sueño** — detiene la reproducción después de N minutos o al terminar la canción actual
- Repetir (desactivado / una / todas) y aleatorio
- Cola infinita (radio automática al terminar la cola)
- Omitir secciones silenciosas
- SponsorBlock — saltá automáticamente los segmentos de patrocinadores en videos musicales
- Descargá canciones y videos para escuchar sin conexión
- Guardado del estado de reproducción entre reinicios

### 🎤 Letras
- **Letras de la comunidad Wavora** — letras sincronizadas creadas por la comunidad
- **Spotify** — letras sincronizadas vía cookie `sp_dc`
- **YouTube** — subtítulos generados automáticamente
- **Traducción con IA** — traducí letras a cualquier idioma con OpenAI, Gemini o un modelo propio
- Resaltado palabra por palabra (letras sincronizadas enriquecidas)
- Votá letras para mejorar la base de datos comunitaria
- Letras en modo pantalla completa con fondo difuminado
- Letras sin conexión (guardadas en caché)

### 📚 Biblioteca
- Canciones favoritas, descargas, artistas seguidos, álbumes y playlists
- Reproducidas recientemente y más reproducidas
- Estadísticas de escucha con filtro por rango de fechas
- Sincronización con tu biblioteca de YouTube Music

### 🔍 Búsqueda
- Buscá canciones, álbumes, artistas, playlists, videos y podcasts
- **Historial de búsqueda como chips** — tus búsquedas recientes aparecen como pastillas de acceso rápido
- Sugerencias de YouTube Music en tiempo real
- Filtro por tipo de contenido
- Explorador de estados de ánimo y géneros

### 🟣 Discord Rich Presence
- Mostrá en tu estado de Discord la canción que estás escuchando con portada, artista y tiempo transcurrido
- Guía de configuración paso a paso incluida en la app

### 🟢 Integración con Spotify
- Letras sincronizadas vía cookie `sp_dc`
- Spotify Canvas — fondos animados en la pantalla del reproductor

### 🖥️ Escritorio (Windows / macOS / Linux)
- App de escritorio nativa con Kotlin Multiplatform + Compose Desktop
- Miniplayer flotante — se mantiene encima de todas las ventanas, se arrastra libremente, con indicador visual de arrastre
- Reproductor en pantalla completa
- Soporte para teclas multimedia y widget de pantalla de bloqueo en macOS
- Protocolo `wavora://` para deep links en Windows
- Motor de reproducción VLC

---

## Novedades recientes

### ✨ Nuevas funciones
- **Pantalla de bienvenida (Onboarding)** — presentación de 3 páginas animadas que se muestra solo la primera vez que abrís la app
- **Crossfade visual de portadas** — cuando cambia la canción, la imagen de portada hace un fade suave en lugar de un corte brusco
- **Historial de búsqueda como chips** — reemplaza la lista vertical por pastillas horizontales compactas en `FlowRow`
- **Skeletons de carga** — las pantallas de Artista, Álbum y Biblioteca muestran placeholders animados mientras cargan en lugar del spinner genérico
- **Estados de error con reintento** — las pantallas de Artista, Álbum y Playlist ahora muestran un botón "Reintentar" en lugar de volver hacia atrás silenciosamente ante un error de red
- **Feedback háptico** en Android — vibración en los botones de reproducción (shuffle, anterior, play/pause, siguiente, repetir) y en el botón de like/unlike
- **Widget de Android mejorado** — se corrigió una fuga de memoria por creación de `ImageLoader` duplicado en cada cambio de thumbnail
- **Timer de sueño** — configuralo desde el bottom sheet del reproductor (ícono de luna). Podés elegir minutos exactos o "al terminar la canción actual"

### 🐛 Fixes importantes
- Crash al hacer scroll en listas de playlists con IDs duplicados
- Links de "Compartir" que apuntaban a `wavora.org/app/...` (dominio inexistente) — ahora generan links válidos a `music.youtube.com`
- Crash silencioso en parseo de cookies con valores que contienen `=` (tokens base64)
- Base de datos accedida desde el hilo principal — movido a `Dispatchers.IO`
- El heurístico de detección de videos en descargas usaba el aspect ratio de la miniatura en vez del campo real del modelo, causando descargas dobles y el prefijo "VIDEO" visible durante la descarga

### ⚡ Mejoras de rendimiento
- La tipografía se recalcula una sola vez al iniciar la app (antes se recreaba 426 veces por recomposición)
- El caché de imágenes de Coil se limita al 10% del heap (antes era el 25% por defecto, ~64 MB en un Motorola moto g85)
- La paleta de colores del reproductor se cachea por canción — no se vuelve a extraer al volver a una canción ya vista
- Las animaciones de los chips de Home se pausan cuando no están visibles (antes 11 animaciones infinitas corrían simultáneamente)

---

## Instalación

### Android
Descargá el `.apk` más reciente desde [Releases](https://github.com/Wavora-dev/Wavora/releases). Requiere Android 8.0 o superior.

### Windows
Descargá el instalador `.msi` desde [Releases](https://github.com/Wavora-dev/Wavora/releases). Requiere [VLC](https://www.videolan.org/vlc/) instalado (64-bit).

### macOS
Descargá el `.dmg` desde [Releases](https://github.com/Wavora-dev/Wavora/releases). Requiere VLC instalado.

### Linux
Descargá el `.deb` o `.rpm` desde [Releases](https://github.com/Wavora-dev/Wavora/releases). Requiere VLC (`libvlc-dev`).

---

## Créditos

Wavora está construido sobre [SimpMusic](https://github.com/maxrave-dev/SimpMusic) de maxrave-dev, bajo licencia GPL-3.0.

Librerías adicionales: ExoPlayer (Media3), VLC (vlcj), Koin, Ktor, Room, Jetpack Compose, Compose Multiplatform, KizzyRPC, Coil, y otras listadas en **Ajustes → Librerías de terceros**.

---

## Licencia

GPL-3.0 — ver [LICENSE](LICENSE).
