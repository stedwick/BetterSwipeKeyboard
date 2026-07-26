# AGENTS.md

Guidance for AI coding agents working in this repository. Assumes no prior
knowledge of the project.

## Project overview

**Better Swipe Keyboard** is an Android soft keyboard (IME) app, written
entirely in Kotlin with Jetpack Compose. Its two headline features:

- **Swipe (glide) typing**: a custom decoder maps a finger trail over the
  QWERTY keys to the most likely dictionary word.
- **AI proofreading**: an on-device Gemini Nano proofreader (ML Kit GenAI),
  with an OpenRouter cloud fallback, fixes the current sentence after 1
  second of typing inactivity.

Single Gradle module `:app`, package `com.example.betterswipekeyboard`.
minSdk 35, targetSdk/compileSdk 36, Java 11 source/target compatibility,
Kotlin 2.2.10, AGP 9.3.1, Gradle 9.5.0 (wrapper). The Gradle daemon JVM is
pinned to a Java 21 toolchain via `gradle/gradle-daemon-jvm.properties`
(foojay resolver auto-provisions it).

## Runtime architecture

The app has two entry points declared in `app/src/main/AndroidManifest.xml`:

- `SwipeKeyboardService` — the `InputMethodService` hosting the keyboard.
  Since an IME service is not an Activity, it acts as its own
  `LifecycleOwner` / `ViewModelStoreOwner` / `SavedStateRegistryOwner` and
  attaches these to the keyboard window's decor view so Compose works. It
  owns the `SwipeDecoder`, the proofreaders, and all `InputConnection`
  interaction.
- `MainActivity` — a setup screen: buttons to enable/pick the IME, an
  OpenRouter API key field, and a test text field.

Data flow (deliberately layered, keep it this way):

1. **Layouts are pure data** (`layout/`): `KeyboardLayout` = rows of `Key`s,
   each with a `KeyOutput` (Text, Backspace, Enter, Shift, SwitchLayout,
   Microphone). `QwertyLayout` and `SymbolsLayout`.
2. **All gestures produce semantic actions** (`KeyboardAction`): taps,
   long-presses and swipes are handled at the container level in
   `ui/keyboard/KeyboardScreen.kt` (keys themselves are purely visual), and
   every user intent becomes a `KeyboardAction`.
3. **`KeyboardViewModel` reduces actions** into a new `KeyboardState` plus an
   optional `KeyboardEffect` (CommitText / CommitWord / DeleteBackward /
   PerformEnter). Pure logic, no Android types beyond `ViewModel`.
4. **Only `InputConnectionEditor` talks to the text field** — the service
   applies effects through it. InputConnection handling exists in exactly
   one place.

### Swipe decoding (`swipe/`)

- `SwipeDecoder` (pure Kotlin, no Android deps): SHARK-style scoring — every
  plausible dictionary word is scored against the trail instead of
  reconstructing letters from the trail. Salient points (high curvature or
  low speed) mark deliberate motion; an LCS alignment between salient keys
  and the candidate word drives the score, plus distance, trail-length and
  word-frequency terms. A dwell ≥ 300 ms on a key doubles its letter. Lower
  score = better; `KeyboardScreen` commits the top word when
  `score < MAX_COMMIT_SCORE` (1.75).
- `Dictionary`: frequency-ordered word list from
  `app/src/main/assets/words_en.txt` (`word<TAB>rank` lines, ~20k words,
  lower rank = more frequent), indexed by first letter.
- `KeyboardGeometry`: collects key bounds from the Compose UI
  (`onGloballyPositioned`) and answers hit-testing / key-center questions.

### AI proofreading (`proofread/`)

- `Proofreader` interface; `MlKitProofreader` (on-device Gemini Nano via
  `com.google.mlkit:genai-proofreading`, keyboard-tuned input type, kicks off
  model download itself) and `OpenRouterProofreader` (OkHttp + org.json,
  model `google/gemini-2.5-flash-lite`, few-shot prompt in `ProofreadPrompt`,
  requests restricted to zero-data-retention providers).
- `selectBackend`: on-device wins when available; cloud when an API key is
  configured; otherwise none.
- Auto-proofread is debounced 1 s after the last text change
  (`SwipeKeyboardService.scheduleAutoProofread`); `SentenceExtractor` pulls
  the current sentence from text before the cursor, and the result is only
  applied if the user hasn't typed since (never clobber newer text).
  Proofread failures are logged and swallowed — the keyboard must never
  depend on the AI.
- The OpenRouter API key is stored in plain SharedPreferences by
  `ApiKeyStore` (acceptable for a personal app; noted in code as
  not production-grade).

## Build and test commands

Requires `local.properties` with `sdk.dir` pointing at an Android SDK (SDK
36 with minor API level 1 must be installed). All commands via the wrapper:

```bash
./gradlew assembleDebug          # build the APK
./gradlew installDebug           # install on a connected device/emulator
./gradlew testDebugUnitTest      # local unit tests (JVM)
./gradlew connectedDebugAndroidTest  # instrumented tests (needs device/emulator)
./gradlew lint                   # Android lint
```

After installing, the keyboard must be enabled in system settings and
selected as the active IME (the app's setup screen has buttons for both).

## Testing instructions

- Local unit tests in `app/src/test/` use **JUnit 4** (plus `org.json` for
  the OpenRouter JSON-building tests). No mocking framework — tests rely on
  pure logic and hand-written fakes.
- `app/build.gradle.kts` adds `src/main/assets` to the test source set's
  resources, so tests load the real dictionary via
  `javaClass.getResourceAsStream("/words_en.txt")` (see `SwipeDecoderTest`).
  Keep this wiring intact.
- By design, the interesting logic lives in pure, testable units:
  `KeyboardViewModel` (action → state/effect reduction), `SwipeDecoder`
  (trail → words), `SentenceExtractor`, `ProofreadPrompt`,
  `selectBackend`, and the `InputConnectionEditor` companion helpers
  (`withLeadingSpace`, `needsSpaceAfterSwipe`). When adding behavior, put
  the logic in such a pure unit and test it there rather than in the
  service or Compose UI.
- `app/src/androidTest/` contains only the template instrumented test.

## Code style guidelines

- Kotlin `official` code style (`kotlin.code.style=official` in
  `gradle.properties`); standard 4-space indentation, Gradle configuration
  cache enabled.
- Language of code, comments and docs: **English**.
- Comments explain the *why*, including why a non-obvious approach was
  chosen or a naive one abandoned (see the extensive KDoc on
  `SwipeDecoder`). Follow that example for algorithmic or gesture code.
- Compose UI is stateless where possible: `KeyboardScreen` receives a
  `KeyboardState` and emits `KeyboardAction`s; `KeyboardState` is the single
  source of truth for the keyboard UI.
- Versions live in the version catalog `gradle/libs.versions.toml` —
  add dependencies there, not with hard-coded strings in build files.
- No linter/formatter beyond stock lint is configured; match the style of
  the surrounding file.

## Security considerations

- The keyboard sees everything the user types. Default to the **on-device**
  proofreader; text only leaves the device via the OpenRouter fallback,
  and then restricted to zero-data-retention endpoints
  (`provider.zdr = true`, `data_collection = "deny"`). Preserve these
  request fields.
- The OpenRouter API key is user-entered and stored unencrypted
  (SharedPreferences). Never log it or send it anywhere besides
  `openrouter.ai`.
- The app requests only the `INTERNET` permission; the IME service is
  protected by `BIND_INPUT_METHOD`.
