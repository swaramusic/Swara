<div align="center">

<img src="docs/logo.png" width="88" height="88" alt="Swara Logo" />

# Swara

**Fast, Fluid, Open-Source Android Music Player — Pure Kotlin, Jetpack Compose & Media3**

[![Download APK](https://img.shields.io/badge/Download-Latest%20APK-2563EB?style=for-the-badge&logo=android&logoColor=white)](https://github.com/swaramusic/Swara/releases/latest)
[![Website](https://img.shields.io/badge/Website-Swara%20Pages-1C9B90?style=for-the-badge)](https://swaramusic.github.io/Swara/)

[![Android CI](https://github.com/swaramusic/Swara/actions/workflows/android-build.yml/badge.svg)](https://github.com/swaramusic/Swara/actions/workflows/android-build.yml)
[![License](https://img.shields.io/badge/License-GPL--3.0-1C9B90.svg)](#license)
[![Platform](https://img.shields.io/badge/Android-8.0%2B-3DDC84.svg?logo=android&logoColor=white)](#installation)

</div>

---

## Overview

**Swara** is a lightweight, native Android music player built from the ground up with **Kotlin**, **Jetpack Compose (Material Design 3)**, and **AndroidX Media3 (ExoPlayer)**. Designed for speed, low memory footprint, and smooth 60fps interactions, Swara brings streaming, offline downloads, real-time audio effects, and synced lyrics into a clean, ad-free experience.

---

## Features

### 1. Native Audio Engine & Background Playback
- **AndroidX Media3 (ExoPlayer + `MediaSessionService`):** True native background playback with lock-screen controls, notification media art, and Bluetooth/headset button support.
- **Dynamic Island Overlay:** Floating miniplayer pill that stays accessible outside the app, complete with an animated squiggly duration bar and quick play/pause/skip controls.
- **Audio Quality Selector:** Choose between `High` (Ultra), `Medium` (Normal), and `Low` (Data Saver) stream bitrates for both streaming and downloads.

### 2. Home, Explore & Region-Aware Discovery
- **Personalized Home:** Quick access to **Your Likes** (with instant Shuffle), 2×2 recent quick picks, **Trending by Genre/Mood** chips, **Today's Pick — Hot For You**, and curated horizontal shelves.
- **Explore & Search:** Instant search across songs, albums, and artists with persistent search history, Top Charts, Trending Playlists, Top Artists, New Releases, and **Identify Song** by lyrics snippet.
- **Region & Language Support:** Switch between 12+ UI languages and region-specific charts/trending feeds directly in Settings.

### 3. Audio Effects & Sound Customization
- **5-Band Native Equalizer:** Fine-tune frequencies manually or pick built-in EQ presets with Bass Boost.
- **Sound Styles & Speed/Pitch:** One-tap sound profiles including *Normal*, *Sped Up*, *Slowed + Reverb*, *Nightcore*, *Lo-fi*, *8D Spatial*, *Concert Hall*, *Bass Heavy*, *Vaporwave*, and *Vocal Clarity*, plus Room Reverb control.
- **Sleep Timer:** Automatically pause playback after your chosen countdown.

### 4. Library, Offline Downloads & Playlist Importer
- **Collections:** Manage Liked Songs, Recently Played (with Clear History), Offline Downloads, and Followed Artists.
- **Local Audio Import:** Import and play local `FLAC`, `MP3`, `WAV`, `M4A`, and `OGG` files directly from your device.
- **Playlist Importer:** Paste public **Spotify** or **YouTube Music** playlist links to convert and save them into your Swara library.
- **Storage & Song Cache Manager:** Monitor downloaded song storage size, clear downloads, toggle song caching, adjust max cache size (128 MB – 2.0 GB), and clear cache anytime.

### 5. Now Playing, Vinyl Mode & Synced Lyrics
- **Clean Square Artwork & Vinyl Mode:** Crisp rounded-square album cover by default; tap the cover to switch to a spinning Vinyl Record.
- **Squiggly Animated Seekbar:** Smooth wave-animated progress bar with tap & drag seeking.
- **Synced Lyrics & Queue:** Live time-synced lyrics view with auto-scroll and an interactive reorderable/removable playback queue.

### 6. Listening Stats & Customization
- **Listening Stats & Recap:** Track your listening streak, total plays, total listening minutes, Top Songs, Top Artists, and generate shareable Story Recap cards.
- **Themes & Accent Colors:** Choose between **Dark**, **Light**, and **AMOLED** modes with built-in accent palettes or a custom RGB color picker.
- **In-App Update Checker:** Automatic startup check against GitHub Releases plus manual check in **Settings → About Swara**.

---

## Tech Stack

| Layer | Technology |
|---|---|
| **Language** | Kotlin 2.x |
| **UI Framework** | Jetpack Compose + Material Design 3 |
| **Audio & Media** | AndroidX Media3 (ExoPlayer, MediaSessionService) |
| **Image Loading** | Coil (Memory + Disk Cache) |
| **Networking** | OkHttp + NewPipeExtractor |
| **Build & Optimization** | Gradle + R8 Minification & Resource Shrinking |

---

## Installation

1. Download the latest APK from **[GitHub Releases](https://github.com/swaramusic/Swara/releases/latest)** or the **[Swara Website](https://swaramusic.github.io/Swara/)**.
2. Open the downloaded `.apk` file on your Android device (**Android 8.0 Oreo / API 26+**).
3. Allow *"Install from this source"* if prompted, then tap **Install**.

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

Requirements: **JDK 21** and **Android SDK (API 35)**.

```bash
cd apps/android-native
./gradlew testDebugUnitTest assembleRelease
```

Output APK: `apps/android-native/app/build/outputs/apk/release/app-release.apk`

---

## Security & Privacy

- **Zero Ads & Zero Trackers:** No analytics SDKs, no ad networks, and no user data selling.
- **HTTPS-Only Network Policy:** Cleartext HTTP traffic is disabled (`cleartextTrafficPermitted="false"`), and `allowBackup="false"` protects local preferences.
- **Open Source:** Every line of code is publicly auditable under GPL-3.0.

---

## Contributing

Contributions, bug reports, and feature suggestions are welcome! Please read [CONTRIBUTING.md](CONTRIBUTING.md) before opening a pull request.

> **Branch workflow:** All pull requests must target the **`dev`** branch — never `main` directly.
> `main` is updated only on official releases. See [CONTRIBUTING.md](CONTRIBUTING.md) for the full flow.

## Website & Releases

- **Website & Download:** https://swaramusic.github.io/Swara/
- **Releases:** https://github.com/swaramusic/Swara/releases

## License

Distributed under the **GNU General Public License v3.0 (GPL-3.0)**.
