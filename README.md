<div align="center">
  <img src="asset/screenshot/01.png" width="120" style="border-radius: 20px"/>
  <h1>Wavora</h1>
  A modern YouTube Music client for Android and Desktop.<br>
  Futuristic UI · Immersive · Emotional
  <br><br>
</div>

## Features ✨

- Play music from YouTube Music and YouTube — free, ad-free, in the background
- High quality up to 256kbps stream
- Browse Home, Charts, Podcasts, Moods & Genres
- Search everything on YouTube
- Spotify Canvas support
- Play 1080p video with subtitles
- AI song suggestions
- Synced lyrics (LRCLIB, Spotify, YouTube Transcript) with AI translation
- Crossfade with DJ-style transitions
- SponsorBlock and Return YouTube Dislike
- Discord Rich Presence
- Offline playback and caching
- Sleep Timer
- Android Auto
- And much more!

## Screenshots

<p align="center">
  <img src="asset/screenshot/01.png" width="200"/>
  <img src="asset/screenshot/02.png" width="200"/>
  <img src="asset/screenshot/03.png" width="200"/>
  <img src="asset/screenshot/04.png" width="200"/>
</p>

## Build

See `WAVORA-INSTRUCCIONES.md` for full build instructions.

Requires JDK 21 and Android command line tools.

```bat
gradlew.bat :composeApp:vlcSetup --no-configuration-cache
gradlew.bat :desktopApp:packageMsi --no-configuration-cache
```
