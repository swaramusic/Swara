# Contributing to Swara

Thank you for your interest in contributing to **Swara**!

## Branching Workflow

We keep `main` strictly for stable releases. All day-to-day development happens through `dev`:

1. Fork the repository under your GitHub account.
2. Branch off **`dev`** using a descriptive prefix:
   - Bug fixes: `git checkout -b fix/short-description dev`
   - New features: `git checkout -b feat/short-description dev`
3. Follow the formatting rules in `.editorconfig` (4-space indentation for Kotlin/Gradle).
4. Verify unit tests and debug build locally before committing (no release keystore required):
   ```bash
   cd apps/android-native
   ./gradlew testDebugUnitTest assembleDebug
   ```
5. Open a Pull Request targeting **`dev`** (not `main`), describing what changed and how you tested it.

## Local Development Requirements

- **JDK 21** (Temurin recommended)
- **Android SDK (API 35)**
- Active Android code lives in `apps/android-native/`.
