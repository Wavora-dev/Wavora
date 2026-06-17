<div align="center">

<img src="composeApp/src/commonMain/composeResources/drawable/ic_launcher.xml" alt="Wavora" width="200"/>

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

Wavora is a free, open-source music player built on top of [SimpMusic](https://github.com/maxrave-dev/SimpMusic). It streams from YouTube Music without ads or a subscription, extends the original with a full desktop app for Windows, macOS and Linux, and adds a significant set of new features focused on playback quality, lyrics, and personalization.

No account is required for basic playback. Log in with your Google account to unlock personalized recommendations, your YouTube Music library, and listening history sync.

---

## What's new compared to SimpMusic

| Area | Wavora additions |
|---|---|
| **Platform** | Full desktop app (Windows, macOS, Linux) via Kotlin Multiplatform + VLC |
| **Crossfade (Android)** | Equal-power cos/sin fade, DJ biquad filter mode, AutoMix BPM detection, precaching of next track, per-step progress, configurable duration or Auto mode |
| **Crossfade (Desktop)** | Two-VLC-instance crossfade with cos/sin volume curve, AutoMix BPM detection, precaching |
| **Lyrics** | Wavora community lyrics database (SIMPMUSIC provider), AI translation, voting/rating system, contributor tracking |
| **Discord** | Rich Presence via WebSocket gateway (KizzyRPC), shows song + artist + elapsed time |
| **macOS** | Now Playing Center integration, Remote Command Center (media keys, lock screen) |
| **Windows** | Custom title bar, protocol handler (`wavora://`), system tray miniplayer |
| **Performance** | Binary search for lyrics sync (O(log n) vs O(n)), throttled Discord RPC updates, one-shot startup DB cleanup, per-song like status job cancellation |
| **UI** | Liquid Glass effect (Android 16+), animated blur background on NowPlaying, Spotify Canvas, configurable translucent bottom bar |
| **Analytics** | Top tracks/artists/albums, total listening time, date range filters |
| **Backup** | Auto backup with configurable frequency (daily/weekly), restore from file |

---

## Features

### Playback
- Stream any song, album, playlist or podcast from YouTube Music
- Background playback with media session notification (Android) and system tray (desktop)
- **Crossfade** — smooth transition between tracks with configurable duration (1–30 s) or Auto mode
  - **DJ Mode** — biquad filter sweep (low-pass on outgoing, high-pass on incoming) alongside volume fade
  - **AutoMix** — detects BPM of both tracks and adjusts crossfade length for tempo-aware blending
- Gapless-style playback via next-track precaching
- Sleep timer (by minutes or end of current song)
- Repeat (off / one / all) and shuffle
- Endless queue (auto-radio after last track)
- Skip silent sections
- SponsorBlock — automatically skip sponsor segments in music videos
- Balance loudness across tracks
- Configurable audio quality (low / medium / high / max)
- Download songs and videos for offline playback
- Save playback state across restarts (last track, queue, shuffle/repeat)

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
- WebSocket gateway connection (not Discord bot — uses your user token)
- Setup guide built into the app (no external steps needed)

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
- All three login flows (YouTube, Discord, Spotify) built into the app — step-by-step, no browser DevTools needed

### Personalization
- Liquid Glass UI effect (Android 16+)
- Animated blur background on the Now Playing screen
- Translucent bottom navigation bar
- Themes follow system dark/light mode

### Privacy & Network
- No analytics, no telemetry
- Optional proxy support (HTTP / SOCKS5 with authentication)
- Optional Piped instance as streaming data provider
- Send-back mode: opt-in to help build the community lyrics database

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

# Android APK
./gradlew :composeApp:assembleRelease

# Windows installer
./gradlew :desktopApp:packageMsi

# macOS DMG
./gradlew :desktopApp:packageDmg

# Linux DEB
./gradlew :desktopApp:packageDeb
```

---

## Credits

Wavora is built on top of [SimpMusic](https://github.com/maxrave-dev/SimpMusic) by maxrave-dev, licensed under GPL-3.0.

Additional libraries: ExoPlayer (Media3), VLC (vlcj), Koin, Ktor, Room, Jetpack Compose, Compose Multiplatform, KizzyRPC, Coil, and others listed in **Settings → Third-party libraries**.

---

## License

GPL-3.0 — see [LICENSE](LICENSE).
