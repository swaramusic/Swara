<div align="center">

# Swara

**Native Android Music Player — Fast, Fluid, Pure Kotlin & Jetpack Compose**

[![Download APK](https://img.shields.io/badge/Download-Latest%20APK-2563EB?style=for-the-badge&logo=android&logoColor=white)](https://github.com/swaramusic/Swara/releases/latest)
[![Website](https://img.shields.io/badge/Website-Swara%20Pages-1C9B90?style=for-the-badge)](https://swaramusic.github.io/Swara/)

[![Android CI](https://github.com/swaramusic/Swara/actions/workflows/android-build.yml/badge.svg)](https://github.com/swaramusic/Swara/actions/workflows/android-build.yml)
[![License](https://img.shields.io/badge/License-GPL--3.0-1C9B90.svg)](#license)

</div>

---

## Features

### Android Native (`apps/android-native`)
- **100% Pure Kotlin + Jetpack Compose + Material Design 3** — zero WebView overhead, smooth 60fps animations, and **Liquid Glass** floating miniplayer & navigation bars.
- **Media3 (ExoPlayer + MediaSessionService)** — background playback, lock-screen controls, Dynamic Island overlay, and Bluetooth/headset media controls.
- **Home, Explore & Charts** — region-aware Top Charts, Trending Playlists, Top Artists, New Releases, Browse All (18+ genres), and persistent Search History.
- **Audio Customization** — 5-band Equalizer, Reverb, Speed & Pitch, Sound Styles (*Nightcore*, *Lo-fi*, *8D*, *Konser*, *Bass Heavy*, etc.), and Sleep Timer.
- **Library & Offline** — Liked Songs, Recently Played, Offline Downloads, Local Audio Import (`FLAC`, `MP3`, `WAV`, `M4A`, `OGG`), and Spotify / YouTube Music playlist importer.
- **Synced Lyrics & Now Playing** — tap circular artwork to switch to a spinning Vinyl Record, plus live synced Lyrics preview.
- **Listening Stats & Recap** — Flame streak, real play-count ranked Top Songs & Top Artists, and shareable Story Recap cards.
- **Multi-language & Theming** — Dark, Light, and AMOLED modes with built-in accent palettes + custom RGB color picker.

---

## Project Structure

```text
Swara/
├── apps/
│   └── android-native/   # Pure Kotlin + Jetpack Compose + Media3 Android app
├── docs/                 # Swara landing & download website (GitHub Pages)
└── .github/              # CI workflows, Issue & PR templates
```

---

## Building from Source

### Android
Requirements: JDK 21, Android SDK (API 35).

```bash
cd apps/android-native
./gradlew testDebugUnitTest assembleDebug
```

Output APK: `apps/android-native/app/build/outputs/apk/debug/app-debug.apk`

---

## Contributing
Contributions, bug reports, and feature requests are welcome! Please read [CONTRIBUTING.md](CONTRIBUTING.md) before opening a pull request.

## Website & Releases
- Website & Download: https://swaramusic.github.io/Swara/
- Releases: https://github.com/swaramusic/Swara/releases

## License
Distributed under the **GNU General Public License v3.0 (GPL-3.0)**.
