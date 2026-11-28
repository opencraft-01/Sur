# PrimeBox

A small, extension-driven media centre for Android. PrimeBox is a **client only** — it
ships with no content and no providers. Everything comes from CS3-compatible `.cs3`
extensions that *you* add.

## Features

- **CloudStream (CS3) extension compatible** — loads existing `.cs3` extension files
  through the same `com.lagradost.cloudstream3` API surface.
- **Repositories** — add a CloudStream extension repository JSON URL, browse its
  extensions, install with optional SHA-256 verification.
- **Direct import** — pick a `.cs3` file with the system file picker.
- **Drop-in folder** — files placed in `/sdcard/Cloudstream3/plugins/` load on start.
- **Material 3 home** — search bar, filter chips, M3 carousel, horizontal poster rows,
  pull-to-refresh.
- **Library tab** — downloads (real, with progress) and watch history side by side.
- **Extended FAB source switcher** — Material 3 bottom sheet to change the active provider.
- Light / dark / system theme and Material You dynamic colour (Android 12+).

## Build

```bash
./gradlew :app:assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk` (arm64-v8a)

### Signing (GitHub Actions)

The workflow reads four repository secrets:

| Secret | Meaning |
| --- | --- |
| `SIGNING_KEY` | base64 of your keystore file (`base64 -w0 my.keystore`) |
| `KEY_STORE_PASSWORD` | keystore password |
| `ALIAS` | key alias |
| `KEY_PASSWORD` | key password |

`SIGNING_KEY` is decoded into `app/primebox.keystore` before the build; local builds
fall back to the keystore committed in the repo. CI builds every push and uploads the
signed APK as the `PrimeBox-APK` artifact.

## Architecture

| Module | Purpose |
| --- | --- |
| `app` | PrimeBox UI (Jetpack Compose + Material 3 1.4), extension engine, player |
| `library` | CS3 extension API: `MainAPI`, `ExtractorApi`, extractors, networking |
| `nicehttp` | HTTP client used by `library` (vendored, was JitPack-only) |

`app/src/main/java/com/lagradost/cloudstream3/` contains the small compatibility shims
(`CloudStreamApp`, `CommonActivity`, `plugins.Plugin`, `actions.VideoClickAction`)
that extensions link against.

## Security notes

- Extensions are arbitrary code loaded with `PathClassLoader` into the app process.
  Only add repositories you trust. Repository hashes are verified **when the repository
  provides a `fileHash`**.
- `usesCleartextTraffic` is enabled because many stream hosts are HTTP-only.
- No analytics, no telemetry, no bundled trackers.

## Not included (size budget)

- Cast, DASH, torrent playback
- Built-in YouTube extraction (NewPipeExtractor removed; extensions that resolve
  YouTube themselves are unaffected)
- Torrent / magnet downloads

## 4.7.0 — streaming-style home + 5-tab navigation

Home: full-bleed auto-sliding hero under a transparent top bar, Top 10 row, deep-navy dark theme (default). Navigation: Home · Search · Downloads · Sources · My Space.

## 4.6.0 — Material 3 UI on top of 4.5.0

All 4.5.0 behaviour is kept (stable provider key, scroll restore, rotating hero, refresh button, empty-download guard, .ts playback).

### Material 3 rewrite

- Every screen uses stock Material 3: `Scaffold`, `NavigationBar`, `TopAppBar` / `LargeTopAppBar`,
  `SearchBar`, carousel, `FilterChip`, `ListItem`, `SegmentedButton`, `ModalBottomSheet`,
  `PullToRefreshBox`, wavy progress, snackbars. The custom "glass" design system is gone.
- Real Material 3 colour scheme (generated tonal palettes from the brand red) + dynamic colour.
- Edge-to-edge with correct status / navigation bar icon colours in light mode.
- Bug fixes: see the release notes in the repository history.
