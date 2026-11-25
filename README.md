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
- **Netflix-style home** — search bar, category chips, auto-scrolling hero carousel,
  horizontal poster rows.
- **Library tab** — history + downloads (downloads are a placeholder for now).
- **FAB extension switcher** — Material 3 bottom sheet to change the active provider.
- **Sliding nav indicator**, light / dark / system theme.

## Build

```bash
./gradlew :app:assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk`

The release build is signed with `app/primebox.keystore` (password `primebox`,
alias `primebox`) so it can be rebuilt and updated in place. **Replace this keystore
for any real distribution.**

CI builds every push on GitHub Actions and uploads the APK as an artifact.

## Architecture

| Module | Purpose |
| --- | --- |
| `app` | PrimeBox UI (Views + Material 3), extension engine, player |
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
- Real download engine (planned)
