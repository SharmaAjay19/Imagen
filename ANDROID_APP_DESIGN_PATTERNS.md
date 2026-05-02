# Building Robust Android Apps: Principles & Methodology

Lessons learned from building a production-quality single-activity Compose app from scratch — covering architecture decisions, what makes an app feel useful, how to test and debug effectively, and how to manage releases.

---

## Part 1: Design Principles

### 1.1 Single Source of Truth for State

All UI state lives in one immutable data class, exposed as a single `StateFlow` from one ViewModel. There is no second place where "the current note" or "which panel is open" is tracked. Every piece of UI reads from the same state object.

**Why**: Eliminates an entire class of bugs where two components disagree about what's active. Makes the app's behavior fully determinable from a single snapshot.

**Consequence**: "Navigation" is not routes or fragments — it's a nullable ID and a couple of booleans. Transitioning between views is just updating fields in the state.

### 1.2 Optimistic In-Memory, Deferred Persistence

When the user types, changes hit the in-memory state instantly. Disk writes are debounced (e.g., 1 second after last keystroke). The UI never waits for I/O.

**Why**: Perceived performance is everything on mobile. The user should never feel lag between their action and the visual response.

**Corollary**: On import or bulk operations, reload from the database after the write completes. The in-memory state is the fast path; the database is the source of truth for cold starts.

### 1.3 Reactive for Display, One-Shot for Action

Data that the UI continuously displays (list of items, theme) should be observed as a Flow. Data needed for a user-triggered action (export all items, bulk import) should be fetched as a one-shot suspend call.

**Why**: Flows keep the UI automatically fresh. But using Flows for everything creates subscription overhead and race conditions with user-initiated state changes (like back-button navigation conflicting with a preference Flow that re-emits the old value).

### 1.4 Mode-Gated Floating UI

Any floating element (toolbar, FAB, bottom sheet) must be gated behind the mode where it's relevant. A toolbar for editing should not render when a navigation panel is open, even if it's technically "below" the panel in the Z-order.

**Why**: On mobile, screen real estate is precious. Overlapping elements that aren't relevant to the current context cause tap target conflicts and visual clutter.

### 1.5 Back Button as a Stack

The Android back button should behave as an undo of the user's navigation depth:
1. Dismiss the most recently opened overlay (tools panel, dialog)
2. Close the next overlay (sidebar)
3. Deselect the current item (return to list)
4. Only then: exit the app

Each level is handled with a priority-ordered `BackHandler` whose `enabled` flag is true only when there's something to pop. If nothing matches, the system default (finish the activity) fires.

### 1.6 Explicit Color Passing Over Implicit Theming

Rather than relying on Material3's complex theming hierarchy and CompositionLocals, define a small custom color scheme with semantically-named fields (background, surface, onBackground, muted, border, accent) and pass it explicitly as a parameter.

**Why**: Fewer surprises. You always know exactly which color a component uses. Adding a new theme variant is adding a new `when` branch, not debugging which Material3 token maps to which surface.

### 1.7 Data Portability as a First-Class Feature

Every app that stores user data should have export (full database to JSON) and import (JSON back to database) from day one. This is not a "nice to have" — it's insurance against data loss, device migration, and user trust.

**Why**: Users will not commit to an app that traps their data. A JSON backup that can be moved via any file-sharing mechanism (email, drive, cable) gives them confidence.

---

## Part 2: What Makes a Utility App Feel Useful

### 2.1 Immediate Value on First Launch

The app should do something useful within 3 seconds of first open. No onboarding screens, no account creation, no permission dialogs unless absolutely necessary for the core function. Seed with a welcome item that demonstrates the features.

### 2.2 Silent Safety Nets

Auto-save on every edit (debounced). Auto-version at intervals (snapshot the previous state before overwriting, capped at a small number). The user should never have to think "did I save?" or "can I undo this?"

### 2.3 Offline-First, Always

A utility app must work fully offline. Cloud sync is a layer on top, never a requirement. The database is local. Preferences are local. The app launches instantly whether or not there's a network connection.

### 2.4 Multi-Modal Input

Wherever text input exists, consider: can the user also speak it? Can they paste an image? Can they use a hardware keyboard shortcut? Voice typing via the system SpeechRecognizer is nearly free to implement and dramatically increases the app's utility for on-the-go use.

### 2.5 Standard Platform Behaviors

Users expect:
- Back button works intuitively (never exits unexpectedly from a deep state)
- Swiping from the left edge navigates back on gesture-nav devices
- Tapping outside a panel/dialog dismisses it
- Long-press reveals additional options
- The keyboard doesn't cover the content being edited

Every violation of these expectations is a reason to uninstall.

### 2.6 Generous File Format Support

When accepting files (import, image pick), support broader MIME types than you think necessary. Devices vary wildly in how they classify files. A `.json` file might register as `text/plain`, `application/octet-stream`, or `application/json` depending on the device manufacturer and file manager.

---

## Part 3: Testing & Debugging Methodology

### 3.1 UI Verification via ADB + UI Automator

For Compose apps, automated UI testing is best done through ADB commands:
- `adb shell uiautomator dump` — exports the view hierarchy as XML
- Parse the XML for expected elements, content descriptions, and bounds
- `adb shell input tap x y` — simulate precise taps
- `adb shell input keyevent KEYCODE_BACK` — simulate back button
- `adb shell screencap` — capture screenshots for visual verification

**Methodology**: For each feature, define assertions in terms of "what elements should be present in the UI hierarchy." Example: "When the sidebar is open, Backup and Import buttons must be present. When editing a note, they must not be."

### 3.2 State-Based Test Scenarios

Since the app is state-driven, tests are state transitions:
1. Set up a starting state (launch app, navigate to a specific point)
2. Perform an action (tap, back press, text input)
3. Assert the resulting state (via UI dump, screenshot, or focus check)

**Key scenarios to always test**:
- Cold launch (fresh install, no data)
- Warm launch (returning with previous state)
- Back button from every reachable state
- Data operations (create, edit, delete, import, export)
- Theme switching (all themes render correctly)
- Rotation / configuration change (state preserved)

### 3.3 Build-Then-Verify Cycle

After any code change:
1. Build (`gradlew assembleDebug`) — check for compile errors
2. Install (`adb install -r`)
3. Force-stop and relaunch (ensures fresh state load)
4. Run through the affected scenarios
5. Check `dumpsys window | grep mCurrentFocus` to verify the app didn't crash/exit

### 3.4 Debugging Overlaps and Layout Issues

When UI elements overlap or appear in wrong contexts:
- Use `adb shell uiautomator dump` and parse bounds to verify element positions
- Check if elements that should be hidden are still present in the hierarchy
- Add a content-description or test tag to suspect elements for easy grepping

### 3.5 Race Condition Detection

Symptoms: pressing back exits the app unexpectedly, state "reverts" after a momentary correct change, UI flickers between states.

Root cause pattern: A Flow (from DataStore or Room) re-emits an old value after you've already updated the state directly. The Flow's emission overwrites your manual state change.

Fix: For state that the user can change via direct action (navigation, selection), load it once at startup (`.first()`), then manage it purely in-memory + persist on change. Don't continuously observe it.

### 3.6 Debugging with Screenshots vs UI Dumps

- **Screenshots** tell you what the user sees — use for visual correctness, theming, layout
- **UI dumps** tell you what's structurally present — use for logic correctness (is this button rendered? is this element clickable?)
- Use both together: a screenshot might look correct but the wrong element could be receiving taps due to overlapping invisible views

---

## Part 4: Build, Release & Version Management

### 4.1 Version Catalog for Dependencies

All dependency versions live in `gradle/libs.versions.toml`. Never hardcode a version in `build.gradle.kts`. This gives you:
- One place to see all versions
- Easy version bumps
- IDE support for catalog references

### 4.2 Semantic Versioning for Releases

Use `vX.Y.Z`:
- **X** (major): Breaking changes, data format migration, complete redesign
- **Y** (minor): New features, significant improvements
- **Z** (patch): Bug fixes, small tweaks

Example progression: v1.0.0 (initial), v1.1.0 (add sections), v1.2.0 (add themes), v1.3.0 (add printing), v1.4.0 (add screenshots to README), v1.5.0 (fix back button + import)

### 4.3 Release Workflow

1. Ensure all changes are committed on `main`
2. Run a clean build (`gradlew assembleDebug` or `assembleRelease`)
3. Install and verify on a device/emulator
4. Tag and push: `git tag vX.Y.Z && git push origin main --tags`
5. Create a GitHub release with the APK attached and changelog notes
6. Use GitHub CLI for automation: `gh release create vX.Y.Z app.apk --title "..." --notes "..."`

### 4.4 Commit Discipline

- Each commit should be one logical change: "Fix back button navigation" not "various fixes"
- Commit message format: imperative verb + what changed. Examples:
  - `Add proper back button navigation: note→list→exit`
  - `Fix export to include all notes across sections`
  - `Widen import MIME filter for device compatibility`
- Never commit generated files, temp screenshots, or debug artifacts

### 4.5 Git Hygiene

- `.gitignore` must cover: `build/`, `.gradle/`, `local.properties`, IDE files, temp test artifacts
- Don't commit APKs to the repo — attach them to releases instead
- Keep `main` always buildable — never push code that doesn't compile

### 4.6 Debug vs Release Builds

- Debug builds (`assembleDebug`): fast iteration, debuggable, larger APK, signed with debug key
- Release builds (`assembleRelease`): ProGuard/R8 minification, production signing key, smaller APK
- For personal/side projects, debug APKs in releases are acceptable. For Play Store, always use release builds with proper signing.

### 4.7 When to Cut a Release

Release when:
- A bug that affects daily use is fixed
- A user-facing feature is complete and tested
- Multiple small fixes accumulate (batch them into one release with good notes)

Don't release:
- Untested changes
- Partial features
- Refactors with no user-visible impact (these should be committed but not released)

---

## Part 5: Code Organization Principles

### 5.1 Minimal File Count for Small Apps

For apps under ~2000 lines of Kotlin, resist the urge to split into dozens of files. A small number of well-organized files is easier to navigate than 40 files with 30 lines each. Reasonable grouping:
- `data/` — database, entities, DAOs, repositories, storage utilities
- `di/` — dependency injection module(s)
- `ui/` — ViewModel, main screen composable(s), theme
- Root package — Application class, Activity

### 5.2 Co-locate Related Logic

The ViewModel should contain all business logic for the screen it drives. Don't scatter logic across "use case" classes for simple apps. A method like `importBackup()` that parses JSON, writes to DAOs, and refreshes state belongs in the ViewModel — it's one cohesive operation.

### 5.3 Data Classes for Everything Structured

Entities, state objects, backup formats, color schemes — all should be `data class`. This gives you: `copy()` for immutable updates, `equals()` for comparison, `toString()` for debugging, and destructuring for convenience.

### 5.4 Fail Gracefully, Log Nothing to User

JSON parsing, file I/O, and database operations should catch exceptions and return a sensible default (empty list, null, false). Only surface failures to the user via Toast or Snackbar when they initiated the action. Silent background operations (auto-save, auto-version) should never show error UI.

---

## Part 6: Debugging Checklist for Common Issues

| Symptom | Likely Cause | Fix |
|---------|-------------|-----|
| Back button exits from deep state | `BackHandler` enabled condition doesn't cover the current state | Ensure the enabled flag includes all states that should intercept back |
| State reverts after user action | A Flow re-emits old value overwriting manual state change | Load navigation state with `.first()`, observe only display prefs continuously |
| Floating UI overlaps other panels | Element not gated behind the correct mode | Add `if (isCorrectMode)` around the composable |
| File picker shows no files | MIME type too restrictive | Add fallback types: `octet-stream`, `text/*` |
| App crashes after import | Malformed JSON or schema mismatch | Use `ignoreUnknownKeys = true`, default values on all fields |
| Toolbar covers content | No padding/offset for the toolbar height | Add `Modifier.padding(bottom = toolbarHeight)` to content |
| Data missing after export | Exporting from filtered in-memory state | Always query the full database for export, never rely on current view state |
| Theme doesn't apply to all elements | Hardcoded colors somewhere | Search for Color(...) literals; replace with `colors.xxx` references |
| Save indicator stuck on "Saving" | Save job cancelled but status not reset | Ensure the status transitions to IDLE on every path (including cancellation) |
