# Imagen — Azure AI Image Playground for Android

A native Android client for the **Azure OpenAI GPT-Image-2** model. Generate images from text, or composite up to 10 reference images with a prompt — all from your phone, with persistent local sessions, voice prompts, share-sheet ingestion, save-to-gallery, and a polished dark UI.

![Min SDK](https://img.shields.io/badge/min%20SDK-26-blue)
![Target SDK](https://img.shields.io/badge/target%20SDK-35-blue)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF)
![Compose](https://img.shields.io/badge/Jetpack-Compose-4285F4)

> Built end-to-end against [ANDROID_APP_DESIGN_PATTERNS.md](ANDROID_APP_DESIGN_PATTERNS.md) and [ANDROID_DESIGN.md](ANDROID_DESIGN.md): single-activity, single-source-of-truth state, offline-first, mode-gated UI, back-as-a-stack.

---

## Features

- **Text-to-image generation** — describe what you want and get an image back.
- **Image editing / compositing** — attach 1–10 reference images with a prompt for a single composite output.
- **Chat-style sessions** — conversational message history, named sessions, create / rename / delete / switch.
- **Offline-first persistence** — sessions in Room, images in app-private storage. Survives restarts and process death.
- **Configurable output** — size (1024×1024, 1024×1536, 1536×1024, auto) and quality (auto / low / medium / high).
- **Live generation timer** — see elapsed seconds while generating; final duration recorded per message.
- **Cancel mid-generation** — the send button becomes a stop button while a request is in flight.
- **Full-screen image viewer** — pinch-to-zoom, pan, save to gallery, share via system sheet.
- **Save to Pictures/Imagen** — one tap writes the image to MediaStore on Android 10+.
- **Backup & restore** — export the entire database + images as a versioned `.zip`; import on any device.
- **Voice prompt** — system speech recognizer dictates straight into the prompt field.
- **Share target** — share images from any app into Imagen to seed reference images.
- **Use as input** — feed a generated image back as a reference for the next prompt.
- **Three themes** — Midnight (default), Paper (light), Dynamic (Material You on Android 12+).
- **Edge-to-edge, IME-aware, gesture-native** — back-as-a-stack pops viewer → settings → confirm → drawer → cancel-gen → exit.
- **Encrypted credentials** — endpoint and API key stored in `EncryptedSharedPreferences` (AES-256, Android Keystore master key).

---

## Screenshots

> Drop screenshots into `screenshots/` and they will appear here.

| First-run setup | Chat with composite | Full-screen viewer |
|:-:|:-:|:-:|
| _add screenshot_ | _add screenshot_ | _add screenshot_ |

---

## Architecture at a Glance

```
MainActivity (single)
    └─ Compose tree (mode-driven; one screen, overlays via state)
        └─ AppViewModel.state : StateFlow<AppState>
              ├─ Repository (Room: SessionDao, MessageDao)
              ├─ ImageStorage (filesDir/uploads, filesDir/generated)
              ├─ SecureConfigStore (EncryptedSharedPreferences)
              ├─ PrefsStore (DataStore: theme, size, quality, current session)
              ├─ AzureImageClient (OkHttp + kotlinx.serialization)
              └─ BackupManager (zip export/import)
```

- **One immutable `AppState`** — every UI element reads from this one snapshot. Navigation = a nullable session id + a few booleans.
- **Optimistic in-memory + deferred persistence** — typing, image add/remove, size/quality changes hit memory instantly; disk writes are debounced.
- **Reactive for display, one-shot for navigation/draft** — theme + sessions are observed Flows; `currentSessionId` is loaded once with `.first()` to avoid back-button race conditions.
- **Mode-gated overlays** — composer, FAB, stop button, and chip actions are gated behind explicit modes.
- **Back as a stack** — priority `BackHandler`s for: viewer → settings → pending-delete → drawer → cancel-generation → system default.

For the full design, see [ANDROID_DESIGN.md](ANDROID_DESIGN.md).

---

## Project Structure

```
app/
├── build.gradle.kts                  # Module Gradle config
├── proguard-rules.pro
└── src/main/
    ├── AndroidManifest.xml           # Single activity, share-target filters, FileProvider
    ├── java/com/ajsharm/imagen/
    │   ├── ImagenApp.kt              # Application — bootstraps DI
    │   ├── MainActivity.kt           # Single activity, edge-to-edge, BackHandler stack
    │   ├── data/
    │   │   ├── ImagenDatabase.kt     # Room entities + DAOs
    │   │   ├── Repository.kt         # Sessions + messages + image lifecycle
    │   │   ├── ImageStorage.kt       # Save/load/delete; downsample + EXIF rotate
    │   │   ├── SecureConfigStore.kt  # EncryptedSharedPreferences
    │   │   ├── PrefsStore.kt         # DataStore (theme + drafts + current session)
    │   │   ├── AzureImageClient.kt   # OkHttp /generations + /edits
    │   │   └── BackupManager.kt      # Zip export/import
    │   ├── di/ServiceLocator.kt      # Manual DI (no Hilt)
    │   ├── ui/
    │   │   ├── AppState.kt           # Single immutable state
    │   │   ├── AppIntent.kt          # Sealed user-action interface
    │   │   ├── AppViewModel.kt       # All business logic
    │   │   ├── theme/ImagenTheme.kt  # Custom ImagenColors + 3 themes
    │   │   └── screens/              # Chat, sessions, composer, viewer, settings, setup
    │   └── util/DurationFormat.kt
    └── res/                          # icons, themes, network/data rules, file_paths
build.gradle.kts                      # Root project config
settings.gradle.kts
gradle/libs.versions.toml             # Single source for dependency versions
gradle.properties
ANDROID_DESIGN.md                     # Detailed design document
ANDROID_APP_DESIGN_PATTERNS.md        # Principles this app follows
```

---

## Build & Run

### Prerequisites
- **Android Studio Koala (2024.1.1) or newer**
- **JDK 17**
- A device or emulator running **Android 8.0 (API 26)** or newer
- An Azure OpenAI resource with a **gpt-image-2** deployment

### Quick start

```powershell
# 1. Clone
git clone https://github.com/SharmaAjay19/ImagenAndroid.git
cd ImagenAndroid

# 2. Generate the Gradle wrapper (one time, if not already present)
gradle wrapper --gradle-version 8.9

# 3. Build the debug APK
./gradlew :app:assembleDebug

# 4. Install on a connected device or running emulator
./gradlew :app:installDebug
```

Or just open the project in Android Studio and press **Run**.

### First-run setup

On first launch, the app shows the **First-Run Setup** screen. Fill in:

| Field             | Example                                                          |
|-------------------|------------------------------------------------------------------|
| Endpoint          | `https://YOUR_RESOURCE.cognitiveservices.azure.com/`             |
| API key           | _your Azure OpenAI key_                                          |
| Deployment name   | `gpt-image-2`                                                    |
| API version       | `2025-04-01-preview`                                             |

Credentials are stored in `EncryptedSharedPreferences` and never leave the device. You can change them later in **Settings**.

---

## Usage

1. **Open the side drawer** (☰ or swipe from the start edge) and tap **New session**.
2. **(Optional) Add reference images** — tap **+** in the composer; the photo picker supports up to 10 images. Or share images into Imagen from any other app.
3. **Type a prompt** — or tap the **mic** for voice dictation.
4. **(Optional) Adjust size / quality** in the composer.
5. **Tap send** (or `Ctrl+Enter` on a hardware keyboard). Watch the live timer; tap **stop** to cancel.
6. **Tap any image** for full-screen pinch-zoom, then **Save** to Pictures/Imagen or **Share** via the system sheet.
7. **Tap “Use as input”** on a generated image to feed it into the next prompt.
8. **Backup** any time from the drawer or Settings — produces an `.imagen-backup-<timestamp>.zip` you can move anywhere.

---

## Tech Stack

| Layer        | Technology                                                       |
|--------------|------------------------------------------------------------------|
| Language     | Kotlin 2.0                                                       |
| UI           | Jetpack Compose (Material 3 BOM 2024.10)                         |
| State        | `StateFlow` + single immutable `AppState`                        |
| Persistence  | Room 2.6 (SQLite) + DataStore Preferences + filesDir             |
| Networking   | OkHttp 4.12 + kotlinx.serialization 1.7                          |
| Image I/O    | Coil 2.7 (display); ExifInterface (rotation); BitmapFactory (downsample) |
| Security     | androidx.security `EncryptedSharedPreferences` (AES-256-GCM)     |
| DI           | Manual `ServiceLocator` — no Hilt, keeps APK small               |

No frontend build tools, no third-party UI kits, no analytics SDKs.

---

## Permissions

| Permission                     | When requested      | Why                                  |
|--------------------------------|---------------------|--------------------------------------|
| `INTERNET`                     | Manifest (always)   | Azure OpenAI API calls               |
| `ACCESS_NETWORK_STATE`         | Manifest (always)   | Detect offline state                 |
| `RECORD_AUDIO`                 | First mic-button tap | Voice prompt dictation               |
| `POST_NOTIFICATIONS` (API 33+) | Optional, future use | "Generation complete" notifications |

No storage permissions are needed — the photo picker and `MediaStore` work without them on Android 13+.

---

## Security

- **API key** stored only in `EncryptedSharedPreferences` (AES-256-GCM, master key in the Android Keystore).
- **HTTPS only** — `cleartextTrafficPermitted="false"` in `network-security-config.xml`.
- **No ADB / cloud backup** of preferences or files — `allowBackup=false` and explicit `data_extraction_rules`.
- **No telemetry, no analytics, no third-party trackers.**
- **Path-traversal guard** on backup import — entries outside `filesDir/uploads` or `filesDir/generated` are rejected.

---

## Backup Format

A `.zip` with:

```
backup.json          # versioned JSON dump of sessions + messages
uploads/<uuid>.png   # all referenced input images
generated/<uuid>.png # all generated outputs
```

`backup.json` is human-readable, version-stamped, and re-importable on any device. The backup contains **no** API keys or endpoint URLs.

---

## Troubleshooting

| Symptom                                         | Likely fix                                                                                  |
|--------------------------------------------------|---------------------------------------------------------------------------------------------|
| `Invalid API key. Check Settings.`              | Endpoint or key wrong — open Settings and re-save.                                          |
| `Deployment not found at this endpoint.`        | Verify the deployment name (default `gpt-image-2`) exists in your Azure OpenAI resource.    |
| `Images too large…`                             | Use fewer or smaller references (the app downsamples to ≤ 2048 px, but quality matters).    |
| `Rate limited.`                                 | Wait a few seconds and retry.                                                               |
| Back button exits unexpectedly                   | Should not happen — please file an issue with reproduction steps.                           |
| Generated image looks rotated                    | EXIF orientation is honored on import; please file an issue with the source file attached. |

---

## Roadmap

- **v1.0** — Parity with the web playground (sessions, generate, edit, persist, themes, backup).
- **v1.1** — Voice prompt + share target.
- **v1.2** — Drag-to-reorder references + "Use as input".
- **v1.3** — Material You dynamic theme polish.
- **v1.4** — Long-press message actions (re-run, copy prompt, delete).

---

## License

MIT
