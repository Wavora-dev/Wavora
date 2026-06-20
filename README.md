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
- Floating **miniplayer window** that stays on top and can be dragged anywhere on screen
- **macOS Now Playing Center** and Remote Command Center (media keys, lock screen widget)
- **Windows protocol handler** (`wavora://`) for deep link support
- Custom title bar on desktop

### 🎛️ Architecture & Code Quality
- **SharedViewModel split** — the original 2000-line monolithic SharedViewModel was broken into four focused ViewModels (`PlayerViewModel`, `NowPlayingViewModel`, `AppViewModel`) each with a single responsibility, improving maintainability and reducing unintended state coupling
- All sub-ViewModels are Koin singletons; existing screens required zero changes thanks to full backward-compatible delegation
- Removed `runBlocking` from hot paths inside coroutines (replaced with proper `suspend` + `first()`)
- Startup download-reset converted from permanent `collect {}` observers to one-shot `first()` calls — eliminates permanent DB observers that re-triggered on every write
- Per-song like status collector now properly cancels its predecessor job, preventing multiple open Room flows accumulating on rapid song changes

### 🎵 Crossfade (Android)
- Two ExoPlayer instances with a per-player `CrossfadeFilterAudioProcessor`
- **Equal-power cos/sin volume curve** instead of linear — preserves perceived loudness throughout the blend
- **DJ Mode** — biquad filter sweep (LPF on outgoing, HPF on incoming) with sigmoid S-curve on time axis and exponential interpolation on frequency axis, mimicking a real DJ mixer crossfader
- **AutoMix** — reads BPM and musical key from track metadata, adjusts crossfade duration and applies a front-loaded BPM/pitch ramp so both tracks share the same effective tempo during the audible overlap
- Next-track precaching pool (2 tracks ahead) so crossfade starts with audio already buffered
- Configurable duration (1–30 s) or fully automatic mode

### 🎵 Crossfade (Desktop)
- Two VLC instances (current + secondary) instead of one
- Same equal-power cos/sin volume curve as Android, 50 steps
- AutoMix BPM detection with the same metadata-driven duration resolution
- Precaching of the next track to minimize start latency

### 🎤 Lyrics
- **Wavora community lyrics database** — a new synced-lyrics provider built by the community, separate from all third-party services
- **AI translation** — translate any synced lyric set to any language via OpenAI, Gemini, or a custom model endpoint
- Voting system (upvote / downvote) for both original and translated lyrics — bad translations are auto-removed and reported
- Contributor tracking for the community database
- **Binary search** for the current lyric line (O(log n) instead of iterating all lines every 100 ms) — measurably reduces CPU usage while lyrics are displayed

### 🟣 Discord Rich Presence
- Fixed the core authentication race condition: Rich Presence was being sent before Discord's READY packet was received (WebSocket connected but IDENTIFY not yet acknowledged), causing all status updates to be silently dropped
- `isSocketConnectedToAccount()` now correctly gates on `sessionId != null` (set only after READY), not just TCP connection state
- Throttled updates to once every 5 seconds (was every 100 ms = 600 coroutine launches per minute)
- Null guard: Discord coroutines are skipped entirely when Rich Presence is disabled
- Built-in step-by-step setup guide for all platforms (no external instructions needed)

### 🟢 Spotify Integration
- Step-by-step `sp_dc` cookie setup guide built directly into the app
- Synced lyrics and Spotify Canvas work without any manual browser configuration on desktop

### 🔴 YouTube Account
<<<<<<< HEAD
- Fixed desktop login flow: replaced the dead wavora.org blog-post redirect with a built-in step-by-step cookie guide including a one-click copy of the `document.cookie` command
=======
- Built-in step-by-step desktop login guide, including a one-click copy of the `document.cookie` command
>>>>>>> 56d2aea (fix)
- Cookie validation confirms account info before saving
- Multiple accounts supported

### ⚡ Performance & Resource Usage
- **ExoPlayer buffer reduced from 4× to 2× the default** — with up to 4 simultaneous ExoPlayer instances (active + crossfade + 2 precached), the previous setting was downloading up to 800 seconds of audio in parallel, causing sustained high CPU and network usage
- **`startForeground()` loop removed** — was being called every 30 seconds in an infinite coroutine on Android, causing repeated OS wake-ups reported as "very high" energy usage
- **Discord RPC throttled** on Android from every 100 ms to every 5 s; only runs when Discord is actually connected
- **Rainbow crossfade animation** changed from `rememberInfiniteTransition` (always running) to a `LaunchedEffect` that only starts when a crossfade is actually in progress
- Lyrics timeline key changed from the full `TimeLine` data class (new object every 100 ms → unnecessary recomposition) to `timelineState.current` (Long, only changes when position actually changes)
- `updateLyrics()` made `suspend` — removes the `runBlocking` call that was blocking a coroutine dispatcher thread every time lyrics were updated
<<<<<<< HEAD
=======
- **Network request logging disabled in production** — `CurlLogger` and `LogLevel.ALL` were active on all 5 HTTP clients (YouTube Music, Spotify, Discord, Kizzy, lyrics service), formatting full request/response bodies — including auth cookies and tokens — on every single API call, in release builds too
- **Kermit log level gated by build type on Android** — logging defaulted to `Severity.Verbose` regardless of build type; release builds now use `Severity.Warn`, while debug builds keep full verbosity
- **Shared OkHttpClient with an explicit connection pool for Coil** (Android + Desktop) — image loading previously created a bare default `OkHttpClient()`, separate from the rest of the app's networking
- **Miniplayer drag (Desktop)** — dragging the floating window queried `MouseInfo.getPointerInfo()` (a JNI call to the OS) on every frame; now uses the drag gesture's own per-frame delta
- **AI Suggest button shimmer/rotation** — two `rememberInfiniteTransition` animations ran continuously for the entire lifetime of the Local Playlist screen; now only animate while the button is actually visible
- **Room indices added** for `liked`, `downloadState`, `totalPlayTime`, and `inLibrary` on `song`, `album`, and `playlist` tables — these back the Liked, Downloaded, and Most Played library filters, which previously did a full table scan on every query
>>>>>>> 56d2aea (fix)

### 🐛 Bug Fixes
- **Android crash** (`Key "" was already used`) in `LazyColumn`/`LazyRow`: fixed across all affected screens — home carousel, mood items, playlist lists, artist videos, library analytics, now playing screen — by treating empty strings the same as null and always including a uniqueness-guaranteed fallback
- **Desktop crash** on search screen scroll: playlist and album results with the same `browseId` produced duplicate keys; fixed by combining `browseId` with `hashCode()`; also fixed `suggestQueries` and `searchHistory` lists that could produce duplicate string keys
- **Crossfade state flag** (`isCrossfading`) was being set on TCP connect instead of after Discord READY — caused the UI rainbow animation to show incorrectly
- **`checkAllDownloadingSongs`** used `collect {}` (permanent observer) instead of `first()` — caused the startup cleanup to re-run every time any song's download state changed
- **JVM buffered update** was hardcoded to emit 100% buffer level regardless of actual VLC buffer state — now reads `player.bufferedPercentage`
<<<<<<< HEAD
- **Update redirect** pointed to `wavora.org/download` (does not exist) — fixed to redirect to the GitHub Releases page
=======
>>>>>>> 56d2aea (fix)

### 🎨 Visual & UI
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
- Sleep timer (by minutes or end of current song)
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
- Search history
- Suggested queries and YouTube Music suggestions
- Filter results by type (songs / videos / artists / albums / playlists / podcasts)
- Charts per country (Wavora Charts)
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
- Floating miniplayer window — stays on top, draggable anywhere on screen
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
<<<<<<< HEAD

=======
>>>>>>> 56d2aea (fix)
gradlew.bat :composeApp:vlcSetup 

# Android APK
gradlew.bat :composeApp:assembleRelease

# Windows installer
gradlew.bat :desktopApp:packageMsi

# macOS DMG
gradlew.bat :desktopApp:packageDmg

# Linux DEB
gradlew.bat :desktopApp:packageDeb

<<<<<<< HEAD


=======
>>>>>>> 56d2aea (fix)
```

---

## Credits

Wavora is built on top of [SimpMusic](https://github.com/maxrave-dev/SimpMusic) by maxrave-dev, licensed under GPL-3.0.

Additional libraries: ExoPlayer (Media3), VLC (vlcj), Koin, Ktor, Room, Jetpack Compose, Compose Multiplatform, KizzyRPC, Coil, and others listed in **Settings → Third-party libraries**.

---

## License

GPL-3.0 — see [LICENSE](LICENSE).
