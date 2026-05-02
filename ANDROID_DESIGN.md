# Design Document — Imagen Android (Azure AI Image Playground for Android)

> A native Android client for the **Azure OpenAI GPT-Image-2** model. A polished, single-activity Jetpack Compose app that brings full text-to-image and multi-image-compositing capabilities to the phone — with conversational sessions, offline-first persistence, instant feel, and gesture-native ergonomics.

---

## 1. Project Overview

Imagen Android is a fully native, offline-first Android application that lets users generate and edit images using **Azure OpenAI GPT-Image-2** directly from their phone. It mirrors and **surpasses** the functionality of the Flask web playground:

- Chat-style conversational sessions with named history
- Up to 10 reference images per request, plus a text prompt → composite image
- Pure text-to-image when no images are attached
- Persistent local sessions, uploads, and generated outputs (Room + filesystem)
- Configurable size and quality
- Live generation timer
- Full-screen preview, save-to-gallery, share-sheet integration
- Phone-native conveniences: voice prompt, paste image from clipboard, share-target receiver, drag-to-reorder reference images, haptic feedback

The app is **single-activity** with **Jetpack Compose** UI, **MVVM + StateFlow** state management, **Room** for session storage, and **internal app storage** for image files. It follows every principle in [ANDROID_APP_DESIGN_PATTERNS.md](ANDROID_APP_DESIGN_PATTERNS.md): single source of truth state, optimistic in-memory + deferred persistence, mode-gated floating UI, back-as-stack, explicit theming, data portability.

---

## 2. Requirements

### 2.1 Functional Requirements (parity with web app + extensions)

| # | Requirement | Notes |
|---|-------------|-------|
| FR-1  | **Image compositing** | 1–10 reference images + prompt → 1 output image via `images.edit`. |
| FR-2  | **Text-to-image** | No images attached → `images.generate`. |
| FR-3  | **Chat-style UI** | Conversational message thread per session. |
| FR-4  | **Session persistence** | Sessions, messages, and image refs survive app restart, process death, and reboot. |
| FR-5  | **File persistence** | Input and output images stored in app-internal storage with stable IDs. |
| FR-6  | **Session management** | Create, rename, delete, switch. Deleting a session removes all its image files. |
| FR-7  | **Image preview & download** | Tap any image → full-screen pinch-zoom viewer with Save to Gallery and Share. |
| FR-8  | **Configurable parameters** | Size (1024², 1024×1536, 1536×1024, auto), Quality (auto/low/medium/high). |
| FR-9  | **Secure credential storage** | Azure endpoint + API key stored in **EncryptedSharedPreferences** (AES-256). First-run setup screen. |
| FR-10 | **Generation timer** | Live ticker while generating; persisted `durationSeconds` per message. |
| FR-11 | **Inline error display** | Errors stored in the message and shown as a red bubble; session is still saved. |
| FR-12 | **Backup & restore** | Export full database + images as a single `.zip` to user-chosen URI; import the same. |
| FR-13 | **Voice prompt** | Mic button uses system `SpeechRecognizer` to dictate the prompt. |
| FR-14 | **Share / paste image input** | Receive images via Android share sheet; paste images from clipboard into the prompt area. |
| FR-15 | **Drag-to-reorder references** | Reorder the up-to-10 input images; first image is treated as the primary reference. |
| FR-16 | **Save to gallery** | One-tap save of any generated image to the device's `Pictures/Imagen` collection via `MediaStore`. |
| FR-17 | **Cancel generation** | While generating, the send button becomes a stop button that cancels the in-flight request. |

### 2.2 Non-Functional Requirements

| # | Requirement | Notes |
|---|-------------|-------|
| NFR-1 | **Single-activity, single-source-of-truth state** | One `MainActivity`, one top-level ViewModel, one immutable `AppState`. |
| NFR-2 | **Offline-first** | App opens instantly. Only the actual generate call requires network. Sessions and images are 100% local. |
| NFR-3 | **No backend** | App talks directly to Azure OpenAI via HTTPS using OkHttp + Kotlin serialization. |
| NFR-4 | **Min SDK 26 (Android 8.0)**, **Target SDK 35 (Android 15)** | Covers ~98% of active devices. |
| NFR-5 | **Dark theme + dynamic Material You + custom themes** | Three themes: Midnight (default dark), Paper (light), Dynamic (Material You from wallpaper, Android 12+). |
| NFR-6 | **60 fps, no jank** | All I/O off the main thread. Image decode via Coil with downsampling. List uses `LazyColumn` with stable keys. |
| NFR-7 | **Edge-to-edge** | Full edge-to-edge with proper insets handling for status bar, nav bar, IME (keyboard). |
| NFR-8 | **Accessibility** | All interactive elements have `contentDescription`. Min 48 dp touch targets. Reduced-motion respected. |
| NFR-9 | **APK size** | Release APK target ≤ 8 MB after R8. |
| NFR-10 | **Data portability** | Backup zip is a documented, version-stamped format that can be re-imported on any device. |

---

## 3. Architecture

### 3.1 High-Level

```
┌──────────────────────────────────────────────────────────────────┐
│                     MainActivity (single activity)               │
│ ┌──────────────────────────────────────────────────────────────┐ │
│ │            Compose UI Tree (one screen, mode-driven)         │ │
│ │  • SessionListPanel  • ChatScreen   • PromptComposer         │ │
│ │  • FullScreenImageViewer • SettingsSheet • ConfirmDialogs    │ │
│ └─────────────────────────┬────────────────────────────────────┘ │
│                           │ collects AppState : StateFlow         │
│                           ▼                                       │
│ ┌──────────────────────────────────────────────────────────────┐ │
│ │                   AppViewModel (single)                      │ │
│ │  Holds AppState. Mutates via intent functions.               │ │
│ │  Owns the in-flight generation Job (cancellable).            │ │
│ └─────────┬──────────────────┬──────────────────┬──────────────┘ │
│           ▼                  ▼                  ▼                 │
│  ┌──────────────┐   ┌────────────────┐  ┌────────────────────┐   │
│  │ Repository   │   │ ImageStorage   │  │ AzureImageClient   │   │
│  │ (Room DAOs)  │   │ (file IO)      │  │ (OkHttp + JSON)    │   │
│  └──────┬───────┘   └────────┬───────┘  └─────────┬──────────┘   │
│         ▼                    ▼                    │              │
│   ┌──────────┐      ┌────────────────┐            │HTTPS         │
│   │ Room DB  │      │ filesDir/      │            ▼              │
│   │ sessions │      │   uploads/     │   Azure OpenAI Service    │
│   │ messages │      │   generated/   │   /openai/deployments/    │
│   └──────────┘      └────────────────┘   gpt-image-2/...         │
└──────────────────────────────────────────────────────────────────┘
```

### 3.2 Module / Package Structure

Following pattern §5.1: a small, flat structure for an app that should land around 2–3k lines of Kotlin.

```
app/
├── build.gradle.kts
├── proguard-rules.pro
└── src/main/
    ├── AndroidManifest.xml
    ├── java/com/ajsharm/imagen/
    │   ├── ImagenApp.kt                    # Application class, DI bootstrap
    │   ├── MainActivity.kt                 # Single activity, edge-to-edge, sets Compose root
    │   │
    │   ├── data/
    │   │   ├── ImagenDatabase.kt           # Room database
    │   │   ├── entities/
    │   │   │   ├── SessionEntity.kt
    │   │   │   └── MessageEntity.kt
    │   │   ├── dao/
    │   │   │   ├── SessionDao.kt
    │   │   │   └── MessageDao.kt
    │   │   ├── Repository.kt               # Single repo: sessions + messages + images
    │   │   ├── ImageStorage.kt             # Save/load/delete files in filesDir
    │   │   ├── SecureConfigStore.kt        # EncryptedSharedPreferences wrapper
    │   │   ├── BackupManager.kt            # Zip export/import
    │   │   └── AzureImageClient.kt         # OkHttp call, multipart for edit
    │   │
    │   ├── di/
    │   │   └── ServiceLocator.kt           # Manual DI (no Hilt; keeps APK small)
    │   │
    │   ├── ui/
    │   │   ├── AppViewModel.kt
    │   │   ├── AppState.kt                 # Immutable state data classes
    │   │   ├── AppIntent.kt                # Sealed user-action interface
    │   │   ├── theme/
    │   │   │   ├── ImagenTheme.kt          # Custom color schemes
    │   │   │   └── Type.kt
    │   │   ├── screens/
    │   │   │   ├── ChatScreen.kt
    │   │   │   ├── SessionListPanel.kt     # Modal navigation drawer
    │   │   │   ├── PromptComposer.kt
    │   │   │   ├── MessageItem.kt
    │   │   │   ├── ImageViewerScreen.kt    # Full-screen pinch-zoom
    │   │   │   ├── SettingsSheet.kt
    │   │   │   ├── FirstRunSetup.kt
    │   │   │   └── EmptyState.kt
    │   │   └── components/
    │   │       ├── ReferenceImageStrip.kt
    │   │       ├── GenerationTimer.kt
    │   │       ├── BackHandlers.kt         # Stack-style back composables
    │   │       └── HapticUtils.kt
    │   │
    │   └── util/
    │       ├── DurationFormat.kt
    │       ├── ImageUtils.kt               # downsample, EXIF rotate
    │       └── Result.kt                   # sealed Ok/Err
    │
    └── res/                                # icons, splash, strings, themes.xml
```

### 3.3 Single Source of Truth — `AppState`

Per pattern §1.1, the entire UI is determined by one immutable data class:

```kotlin
data class AppState(
    // Bootstrap
    val isReady: Boolean = false,
    val configReady: Boolean = false,                  // API key + endpoint set?

    // Library
    val sessions: List<SessionSummary> = emptyList(),

    // Active session
    val currentSessionId: String? = null,
    val messages: List<Message> = emptyList(),

    // Composer
    val draftPrompt: String = "",
    val draftImages: List<DraftImage> = emptyList(),   // up to 10
    val size: ImageSize = ImageSize.S1024,
    val quality: ImageQuality = ImageQuality.AUTO,

    // Generation
    val generation: GenerationStatus = GenerationStatus.Idle,
    val elapsedMillis: Long = 0L,                      // ticked while Generating

    // Navigation overlays (mode flags — pattern §1.4 / §1.5)
    val isSessionPanelOpen: Boolean = false,
    val isSettingsOpen: Boolean = false,
    val viewerImage: String? = null,                   // file path or null
    val pendingDelete: SessionSummary? = null,
    val toast: String? = null,

    // Theme (live, observed continuously)
    val theme: ThemeChoice = ThemeChoice.MIDNIGHT,
)

sealed interface GenerationStatus {
    data object Idle : GenerationStatus
    data class Generating(val startedAt: Long) : GenerationStatus
    data class Error(val message: String) : GenerationStatus
}
```

Navigation is **not** routes — it is `currentSessionId` + a few booleans (pattern §1.1 corollary).

### 3.4 ViewModel Contract

`AppViewModel` exposes:
- `val state: StateFlow<AppState>`
- `fun handle(intent: AppIntent)` — single entry point for every user action
- Owns `private var generationJob: Job?` for cancellation
- Owns `private var saveJob: Job?` for debounced draft persistence

Per pattern §1.3 (Reactive vs One-Shot):
- **Continuously observed:** theme preference, sessions list (reactive Flow from Room — drives the sidebar)
- **One-shot at startup:** `currentSessionId`, draft prompt, draft size/quality (loaded with `.first()` then managed in-memory) — prevents the back-button race condition documented in pattern §3.5

### 3.5 Data Flow for "Generate"

Per pattern §1.2 (Optimistic in-memory, deferred persistence):

```
User taps Send
    │
    ▼
ViewModel.handle(SendPrompt)
    │
    ├── Capture targetSessionId from state (so back-nav can't redirect the result)
    ├── Create message stub locally → emit AppState with Generating(...)
    ├── Start elapsed-ms ticker (collectLatest 100 ms)
    │
    ├── Off main: copy DraftImage URIs into filesDir/uploads/<uuid>.<ext>
    ├── Off main: AzureImageClient.generateOrEdit(prompt, files, size, quality)
    │       └── HTTPS multipart → Azure OpenAI
    │
    ├── On result:
    │     • Save bytes (or download URL) → filesDir/generated/<uuid>.png
    │     • Insert MessageEntity into Room
    │     • Append to AppState.messages IF currentSessionId == targetSessionId
    │     • Emit GenerationStatus.Idle, clear draft
    │
    └── On error:
          • Insert error MessageEntity (status="error")
          • Emit GenerationStatus.Error(msg) — shown as red bubble
          • Session still saved
```

### 3.6 Back Button as a Stack — pattern §1.5

Priority order (each implemented as a `BackHandler(enabled = ...)`):

1. Image viewer open → close viewer
2. Settings sheet open → close sheet
3. Pending-delete confirm → cancel
4. Session panel open → close panel
5. Generating → ask "Cancel generation?"
6. Has draft text or images → ask "Discard draft?"
7. Otherwise → system default (finish activity)

Edge swipe (gesture-nav) is honored automatically because `BackHandler` listens to `OnBackPressedDispatcher`.

---

## 4. Data Model

### 4.1 Room Schema

```kotlin
@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String,                        // UUID
    val name: String,
    val createdAt: Long,                               // epoch ms
    val updatedAt: Long
)

@Entity(
    tableName = "messages",
    foreignKeys = [ForeignKey(
        entity = SessionEntity::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("sessionId")]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val timestamp: Long,
    val prompt: String,
    val inputImagePathsJson: String,                   // JSON array of relative paths
    val outputImagePath: String?,                      // relative path under filesDir
    val size: String,
    val quality: String,
    val revisedPrompt: String?,
    val status: String,                                // "success" | "error"
    val error: String?,
    val durationMs: Long
)
```

DAO highlights:
- `SessionDao.observeAll(): Flow<List<SessionWithCount>>` — joins message count for the sidebar.
- `MessageDao.observeBySession(sid): Flow<List<MessageEntity>>` — drives the chat list.
- `SessionDao.deleteCascade(sid)` — Room cascade handles messages; ViewModel deletes referenced files first.

### 4.2 File Layout (internal, app-private)

```
filesDir/
├── uploads/<uuid>.<ext>         # 0..N saved input images (jpg/png/webp; converted on import)
└── generated/<uuid>.png         # 0..N model output images
```

All files are referenced by **relative paths** ("uploads/abc.png") so backups are portable.

### 4.3 Backup Format (`.imagen-backup.zip`)

```
backup.json                      # full DB dump + version stamp
uploads/...
generated/...
```

`backup.json`:
```json
{ "version": 1, "exportedAt": 1745123456789,
  "sessions": [ { "id": "...", "name": "...", "createdAt": ..., "messages": [ ... ] } ] }
```

Import re-creates rows with their original IDs (replace mode) and copies files back.

---

## 5. Azure OpenAI Integration

### 5.1 Networking — `AzureImageClient`

- **Library:** OkHttp (already pulled in by many transitive deps; ~800 KB) + `kotlinx.serialization`. No Retrofit (keeps APK small).
- **Auth:** `api-key: <key>` header, key fetched from `SecureConfigStore`.
- **Base URL:** `${endpoint}/openai/deployments/${deployment}/images`
- **Query:** `?api-version=2025-04-01-preview`

#### 5.1.1 Text-to-image — `POST /generations`

`application/json` body:
```json
{ "model": "gpt-image-2", "prompt": "...", "n": 1, "size": "1024x1024", "quality": "auto" }
```

#### 5.1.2 Image edit / composite — `POST /edits`

`multipart/form-data`:
- `model` = `gpt-image-2`
- `prompt` = text
- `n` = `1`
- `size` = selected size
- `image[]` = each reference image as a `File` part (use `image` for a single file, repeated `image[]` parts for multiple — the Azure SDK accepts a list)

> **Quality is omitted on edit calls** (parity with web app, §5.3.2 of Design.md).

#### 5.1.3 Response parsing

```json
{ "data": [ { "b64_json": "..." | null, "url": "..." | null, "revised_prompt": "..." } ] }
```

- If `b64_json` present → `Base64.decode` → write PNG.
- Else if `url` present → OkHttp `GET` it → write PNG.
- Else → throw, surface as message error.

### 5.2 Cancellation

The generation runs inside a `viewModelScope.launch { ... }` Job. Tapping the in-progress send button (now showing a stop icon) calls `generationJob?.cancel()`. OkHttp call is wrapped to honor `coroutineContext.ensureActive()` and `call.cancel()` on cancellation.

### 5.3 Errors

Mapped to friendly strings:
- `401` → "Invalid API key. Check Settings."
- `404` → "Deployment '<name>' not found at this endpoint."
- `413` → "Images too large. Try fewer or smaller references."
- `429` → "Rate limited. Try again in a moment."
- `5xx` → "Azure service error: <status>. Try again."
- IO/Cancel → "Cancelled." (silent — pattern §5.4)

---

## 6. UI Design

### 6.1 Navigation Model

Single screen — `ChatScreen`. The session list is a **modal navigation drawer** (Material3 `ModalNavigationDrawer`) that slides in from the start edge. Settings is a **bottom sheet** (`ModalBottomSheet`). The image viewer is a **full-screen overlay** composable rendered above everything when `state.viewerImage != null`.

This keeps everything in one Compose tree → trivial state restore, no nav graph, no fragment back stack.

### 6.2 Screen Layout — `ChatScreen` (drawer closed)

```
┌─ status bar (transparent, content drawn behind) ──────────────┐
│ ╭────────────────────────────────────────────────╮            │
│ │ ☰   Session name           ⋮      ⚙           │ TopAppBar  │
│ ╰────────────────────────────────────────────────╯            │
│                                                                │
│   ┌───────────────────────────────────────────┐               │
│   │ User bubble  (right-aligned, accent bg)   │               │
│   │   [thumb][thumb][thumb] +2                │               │
│   │   "make these into a watercolor"          │               │
│   │   12:04 · 1024×1024 · auto                │               │
│   └───────────────────────────────────────────┘               │
│   ┌───────────────────────────────────────────┐               │
│   │ Assistant bubble                           │               │
│   │   ┌───────────────────────┐               │               │
│   │   │     generated PNG     │ tap to zoom   │               │
│   │   └───────────────────────┘               │               │
│   │   Revised prompt · Generated in 24s        │               │
│   │   [Save] [Share] [Use as input]            │               │
│   └───────────────────────────────────────────┘               │
│                                                                │
│ ╭─ PromptComposer (sticky, IME-aware) ─────────────────────╮  │
│ │ ┌ Reference images strip (horizontal, drag-reorder) ─┐  │  │
│ │ │ [+] [img×]  [img×]  [img×]                          │  │  │
│ │ └─────────────────────────────────────────────────────┘  │  │
│ │ ┌─────────────────────────────────────────────────────┐  │  │
│ │ │ Describe what you want…                       🎤   │  │  │
│ │ └─────────────────────────────────────────────────────┘  │  │
│ │  Size [1024×1024 ▾]   Quality [auto ▾]            ▶▶ │  │
│ ╰──────────────────────────────────────────────────────────╯  │
└────────────── nav bar (inset) ────────────────────────────────┘
```

### 6.3 Mode-Gated Floating UI — pattern §1.4

| Element                  | Visible when                                                |
|--------------------------|-------------------------------------------------------------|
| `PromptComposer`         | A session is active, **and** drawer closed, **and** viewer closed |
| FAB "New session"        | Drawer open, no session selected                            |
| Stop button              | `generation is Generating`                                  |
| Live timer chip          | `generation is Generating`                                  |
| "Use as input" action    | Message has `outputImagePath` and not currently generating  |

### 6.4 Theme — pattern §1.6

A small custom `ColorScheme` class with **explicit fields**, passed via a `LocalImagenColors` `CompositionLocal` from the root `ImagenTheme(theme)`:

```kotlin
data class ImagenColors(
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val onBackground: Color,
    val onSurface: Color,
    val muted: Color,
    val border: Color,
    val accent: Color,
    val accentOn: Color,
    val userBubble: Color,
    val assistantBubble: Color,
    val error: Color,
    val success: Color,
)
```

Three palettes shipped:
- **Midnight** (default) — mirrors the web app's GitHub-dark vibe (`#0d1117` / `#161b22` / `#58a6ff`)
- **Paper** — warm light theme
- **Dynamic** — Material You's `dynamicDarkColorScheme(context)` mapped onto the `ImagenColors` shape (Android 12+)

A new theme = a new `when` branch — never a Material-token spelunk.

### 6.5 Components

#### 6.5.1 `MessageItem`
- User bubble: prompt text + `LazyRow` of input thumbnails (Coil), each tappable for the viewer.
- Assistant bubble: generated image (rounded 16 dp, fixed aspect ratio container so the bubble doesn't pop on load), optional revised prompt, "Generated in 24s" badge, action row (`Save to Gallery`, `Share`, `Use as input`).
- Error bubble: red-tinted, icon + reason.
- Long-press → bottom sheet with `Copy prompt`, `Delete message`, `Re-run`.

#### 6.5.2 `ReferenceImageStrip`
- Horizontal scrolling row of 96×96 dp thumbnails with a corner ✕.
- Trailing `+` tile opens the multi-image picker (`PickMultipleVisualMedia`, capped at remaining slots).
- Drag-to-reorder via `Modifier.pointerInput { detectDragGesturesAfterLongPress }` + animated `Modifier.animateItemPlacement()`.
- Accepts files via the share intent and clipboard paste.

#### 6.5.3 `GenerationTimer`
- A pure Compose ticker: `LaunchedEffect(generation) { while (active) { delay(100); update } }`.
- Renders as a chip: `⏱ 12.4 s` next to the stop button.

#### 6.5.4 `ImageViewerScreen`
- Full-screen, opaque background.
- Pinch-to-zoom + pan via `Modifier.pointerInput { detectTransformGestures }` (scale 0.5..6×).
- Top app bar: back, share, save-to-gallery, "Use as input".
- Swipe-down to dismiss with progressive alpha fade.

#### 6.5.5 `SessionListPanel` (drawer)
- Header: app title + "New session" primary button.
- `LazyColumn` of sessions, newest first, each item showing name, message count, last-updated relative time.
- Long-press → context sheet: Rename, Delete, Duplicate.
- Footer actions: **Backup**, **Import**, **Settings** (always present here, never floating during chat — pattern §1.4).

#### 6.5.6 `SettingsSheet`
- Endpoint URL, API key (masked), API version, deployment name → saved to `SecureConfigStore`.
- Theme picker (Midnight / Paper / Dynamic).
- Default size + quality.
- Backup / Import shortcuts.
- "Test connection" button → calls a tiny `images.generate` ping with `quality=low`.
- About / version.

### 6.6 First-Run Setup
If `SecureConfigStore.endpoint` or `apiKey` is missing, the app shows `FirstRunSetup` (full-screen) instead of `ChatScreen`. After save, the app seeds a welcome session with one assistant-style intro message that explains the controls (pattern §2.1 — "immediate value on first launch").

### 6.7 Multi-Modal Input — pattern §2.4
- 🎤 mic button → `SpeechRecognizer`, partial results stream into `draftPrompt`.
- Clipboard paste of an image (`ClipboardManager.primaryClip`) → added to references.
- Share-target intent filter (`image/*`, `multiple`) → opens app, prefills references in the active or new session.
- Hardware keyboard `Ctrl+Enter` → send.

### 6.8 Standard Platform Behaviors — pattern §2.5
Honored explicitly:
- Back button (see §3.6).
- Edge swipe back (free with `BackHandler`).
- Tap outside drawer/sheet/dialog dismisses it.
- IME (keyboard) does **not** cover the prompt — `Modifier.imePadding()` + `WindowCompat.setDecorFitsSystemWindows(false)`.
- Long-press on any message and any thumbnail reveals more.
- `RECORD_AUDIO` and `POST_NOTIFICATIONS` requested at point of use, never up front.

---

## 7. Permissions & Manifest

| Permission                   | Why                                          | When requested |
|------------------------------|----------------------------------------------|----------------|
| `INTERNET`                   | Azure API calls                              | Manifest only  |
| `ACCESS_NETWORK_STATE`       | Show "offline" badge                         | Manifest only  |
| `RECORD_AUDIO`               | Voice prompt                                 | First mic tap  |
| `POST_NOTIFICATIONS` (33+)   | (Optional) "Generation complete" while bg    | First long gen |

No `READ_EXTERNAL_STORAGE` / `READ_MEDIA_IMAGES` needed — `PickVisualMedia` uses the photo picker which doesn't require permissions on Android 13+.

Intent filters:
- `ACTION_SEND` / `ACTION_SEND_MULTIPLE` with `image/*` — receive shared images.
- Deep link: `imagen://session/<id>` (low priority, optional).

---

## 8. Persistence Strategy — pattern §1.2 / §3.5

| Datum                       | Where               | When written                                         | When read                      |
|-----------------------------|---------------------|------------------------------------------------------|--------------------------------|
| Sessions, messages          | Room                | On message insert / session CRUD                     | Observed continuously (Flow)   |
| Theme                       | DataStore           | On change                                            | Observed continuously (Flow)   |
| `currentSessionId`          | DataStore           | Debounced 1 s after change                           | **Once at startup** (`.first()`) |
| Draft prompt + draft images | DataStore + filesDir| Debounced 1 s after typing / image add               | Once at startup                |
| Default size + quality      | DataStore           | On change                                            | Once at startup                |
| Endpoint, API key           | EncryptedSharedPrefs| On save in Settings                                  | Once at startup, then in-memory|
| Generated / uploaded images | filesDir            | Immediately on receive / on send                     | Lazily via Coil                |

The pattern: **navigation/draft state — load once, then in-memory + persist on change** (avoids the back-exit race).

---

## 9. Testing & Debugging Plan — pattern §3

### 9.1 Build-then-verify loop
After every change:
```
gradlew :app:assembleDebug  →  adb install -r  →  am force-stop  →  am start  →  uiautomator dump
```

### 9.2 State-based scenario suite
- **Cold launch, no config** → FirstRunSetup shown.
- **Cold launch, has config, no sessions** → empty state with "Create your first session".
- **Generate text-to-image** → message appended, image saved, timer recorded.
- **Generate with 3 reference images** → multipart edit, output saved.
- **Cancel mid-generation** → message stub removed, status Idle.
- **Switch session during generation** → result lands in originating session, not the visible one. (pattern §1.2 corollary; handled by capturing `targetSessionId` up front.)
- **Back from each level** → matches §3.6 priority.
- **Process death** during generation → on relaunch, the in-flight stub becomes a `status="error"` message with reason "Interrupted".
- **Rotate** in every overlay → state preserved.
- **Theme switch** → all bubbles, chrome, viewer recolor.
- **Backup → wipe data → import** → byte-identical sessions and images.
- **No-network generation** → friendly inline error.
- **Invalid API key** → 401 mapped to "Check Settings."

### 9.3 Espresso/Compose UI tests
Minimal but high-value:
- `ChatScreenTest`: composer disabled when `generation = Generating`.
- `BackHandlerTest`: state transitions through the back stack in order.
- `GenerationCancelTest`: stop button cancels the Job.

### 9.4 Unit tests
- `AzureImageClientTest` (MockWebServer) — generations, edits, error mapping, cancellation.
- `BackupManagerTest` — round-trip zip integrity.
- `ImageStorageTest` — write, read, delete, orphan-cleanup.
- `RepositoryTest` — cascade delete removes files.

---

## 10. Build & Release — pattern §4

### 10.1 Gradle setup
- `gradle/libs.versions.toml` is the **only** place versions live (pattern §4.1).
- Kotlin 2.0 + Compose Compiler plugin.
- Min SDK 26, Target SDK 35, Compile SDK 35.
- `buildFeatures { compose = true; buildConfig = true }`.

### 10.2 Key dependencies
```toml
[versions]
kotlin = "2.0.21"
compose-bom = "2024.10.01"
room = "2.6.1"
coil = "2.7.0"
okhttp = "4.12.0"
serialization = "1.7.3"
datastore = "1.1.1"
security = "1.1.0-alpha06"

[libraries]
androidx-activity-compose
androidx-lifecycle-viewmodel-compose
compose-material3
compose-material-icons-extended
androidx-room-runtime
androidx-room-ktx
androidx-room-compiler   # ksp
coil-compose
okhttp
kotlinx-serialization-json
androidx-datastore-preferences
androidx-security-crypto
```

No Hilt — DI is a tiny `ServiceLocator` initialized in `Application.onCreate` (pattern §5.1: minimal file count).

### 10.3 Release pipeline
1. All changes committed to `main` with disciplined imperative messages (pattern §4.4).
2. `gradlew clean :app:assembleRelease` (R8 enabled).
3. Signed with the project release keystore (referenced via `~/.gradle/gradle.properties`, never committed).
4. Install on a physical device, run the §9.2 scenarios.
5. Tag: `git tag v1.0.0 && git push origin main --tags`.
6. `gh release create v1.0.0 app-release.apk --title "Imagen 1.0.0" --notes "..."`.

### 10.4 Versioning
SemVer (pattern §4.2). Indicative roadmap:
- **v1.0.0** — parity with web app (sessions, generate, edit, persist, settings, themes, backup).
- **v1.1.0** — voice prompt + share target.
- **v1.2.0** — drag-to-reorder + "Use as input".
- **v1.3.0** — Material You dynamic theme.
- **v1.4.0** — message long-press actions (re-run, copy prompt).

### 10.5 .gitignore essentials
```
/build, /.gradle, /local.properties
/*.iml, /.idea
*.keystore, keystore.properties
/app/release/
```

---

## 11. Security Considerations — pattern + OWASP

| Risk                              | Mitigation                                                                 |
|-----------------------------------|----------------------------------------------------------------------------|
| API key leak                      | `EncryptedSharedPreferences` (AES-256-GCM, master key in Android Keystore).|
| Plaintext network                 | OkHttp enforced HTTPS only; cleartext disabled in `network-security-config`.|
| Backup containing secrets         | Backup zip contains **no** API key or endpoint — only sessions + images.   |
| ADB backup of secrets             | `android:allowBackup="false"`, `android:dataExtractionRules` excludes prefs.|
| Path traversal on import          | All paths validated against canonical `filesDir` before write.             |
| Malicious oversized image         | Coil downsamples; pre-validate dimensions and MIME before sending.         |
| Webview / XSS                     | No WebView is used.                                                        |
| Tap-jacking on Settings           | `android:filterTouchesWhenObscured="true"` on the API-key field.           |

---

## 12. Performance Budget

| Metric                            | Target                  |
|-----------------------------------|-------------------------|
| Cold start to first frame         | ≤ 600 ms (mid-tier dev) |
| Drawer open animation             | 60 fps                  |
| Chat scroll with 200 messages     | 60 fps (LazyColumn + stable keys + Coil memory cache) |
| Image decode on chat thumb        | Off-main, ≤ 50 ms       |
| In-flight memory (10 references)  | ≤ 60 MB                 |
| Release APK                       | ≤ 8 MB                  |

Coil is configured with: `respectCacheHeaders(false)`, generous `MemoryCache`, disk cache 256 MB.

---

## 13. Common-Pitfalls Checklist (specialized §6 of patterns)

| Symptom                                                | Likely Cause                                           | Fix                                                                                  |
|--------------------------------------------------------|--------------------------------------------------------|--------------------------------------------------------------------------------------|
| Back button exits while drawer/viewer is open           | `BackHandler` enabled flag missed that mode             | Add an explicit `BackHandler` per overlay; verify the priority order (§3.6).         |
| Theme reverts after navigating sessions                 | Continuously-observed `currentSessionId` Flow re-emits  | Load `currentSessionId` once with `.first()`, persist on change (pattern §3.5).      |
| Composer covers content                                 | Missing `Modifier.imePadding()` on the chat list        | Use `imePadding()` + bottom padding equal to composer height.                        |
| Generation result lands in wrong session                | Captured `currentSessionId` at result time              | Capture `targetSessionId` at intent time; only append to UI if still active.         |
| Image picker rejects valid files                        | MIME filter too strict                                  | Use `PickVisualMedia.ImageOnly`; on share-receive accept `image/*` and `*/*`.        |
| Save indicator stuck                                    | Save job cancelled without status reset                 | `try { ... } finally { state = state.copy(saving = false) }`.                        |
| OOM on large image input                                | Decoded full-resolution                                 | `BitmapFactory` with `inSampleSize` to ≤ 2048 px before upload.                      |
| Drawer flicker on open                                  | Drawer state not hoisted into `AppState`                | Hoist `isSessionPanelOpen` into `AppState`, drive `DrawerValue` from it.             |
| Generated image looks rotated                           | EXIF orientation ignored                                | Apply `ExifInterface` rotation when copying uploads.                                  |

---

## 14. How to Build & Run

```powershell
# 1. Clone
git clone https://github.com/SharmaAjay19/ImagenAndroid.git
cd ImagenAndroid

# 2. Open in Android Studio (Koala or newer) — auto-syncs Gradle

# 3. Build
./gradlew :app:assembleDebug

# 4. Install on a connected device or emulator (API 26+)
./gradlew :app:installDebug

# 5. Launch the app, complete First-Run Setup with:
#      - Endpoint: https://YOUR_RESOURCE.cognitiveservices.azure.com/
#      - API key:  YOUR_KEY
#      - Deployment: gpt-image-2
#      - API version: 2025-04-01-preview
```

That's it. No backend, no server, no config files.

---

## 15. Out of Scope (v1)

- Multiple Azure accounts / profile switching.
- Cloud sync (sessions stay on-device; backup zip is the migration story).
- In-painting / mask editor (single composite output only, matching the web app).
- Tablet master-detail layout (drawer works fine on tablets; revisit at v2).
- Play Store distribution (sideloaded APKs from GitHub releases for v1).

---

## 16. Summary

Imagen Android is a phone-class peer to the web playground that follows every principle in [ANDROID_APP_DESIGN_PATTERNS.md](ANDROID_APP_DESIGN_PATTERNS.md):

- **One activity, one ViewModel, one immutable `AppState`** — no nav graph, no fragments, no surprises.
- **Optimistic in-memory + deferred persistence** — the UI never waits on disk.
- **Reactive for display, one-shot for navigation/draft** — kills the back-exit race class of bugs.
- **Mode-gated floating UI** — the composer is gone when it shouldn't be there.
- **Back-as-a-stack** — every overlay pops in priority order.
- **Explicit theming via custom `ImagenColors`** — three themes, zero Material-token mysteries.
- **Data portability from day one** — backup/restore as a versioned zip.
- **Multi-modal input** — voice, paste, share-target, hardware Ctrl+Enter.
- **Tested by ADB scenario suite** — every state transition exercised.

The result should feel **noticeably better** than the web app: instantaneous launches, gesture-native navigation, voice prompts, share-sheet ingestion, and one-tap save-to-gallery — all on top of the same proven Azure GPT-Image-2 capabilities.
