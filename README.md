<div align="center">

# Swara

**Native Android & Desktop Music Player — Fast, Fluid, Pure Kotlin & Tauri**

[![Android CI](https://github.com/swaramusic/Swara/actions/workflows/android-build.yml/badge.svg)](https://github.com/swaramusic/Swara/actions/workflows/android-build.yml)
[![Platform](https://img.shields.io/badge/Platform-Android%20%7C%20Desktop-2563EB.svg)](#apps)
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

### Desktop (`apps/desktop` + `apps/desktop-ui`)
- Cross-platform desktop app powered by **Tauri (Rust)** and **React + TypeScript**.

---

## Project Structure

```text
Swara/
├── apps/
│   ├── android-native/   # Pure Kotlin + Jetpack Compose + Media3 Android app
│   ├── desktop/          # Tauri desktop wrapper (Rust)
│   └── desktop-ui/       # Desktop React/Vite frontend
├── packages/
│   ├── core/             # Shared desktop/web core store & providers
│   └── ui-tokens/        # Shared design tokens
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

### Desktop
Requirements: Node.js 20+, pnpm 9+, Rust toolchain.

```bash
pnpm install
pnpm build
```

---

## Contributing
Contributions, bug reports, and feature requests are welcome! Please read [CONTRIBUTING.md](CONTRIBUTING.md) before opening a pull request.

## Website & Contact
- Website & Download: https://swaramusic.github.io/Swara/
- Support Email: `12swara90@gmail.com`

## License
Distributed under the **GNU General Public License v3.0 (GPL-3.0)**.
