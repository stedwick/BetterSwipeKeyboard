# AGENTS.md

Guidance for AI coding agents working in this repository. Assumes no prior
knowledge of the project.

This file carries the always-loaded rules: architecture invariants,
security, workflow, and the guardrails that are cheap to state and
expensive to violate. Detailed mechanics live in per-topic reference docs
under `docs/agent-reference/` — open the relevant one (routing table
below) BEFORE working in an area. When you change behavior/conventions,
update this file AND the relevant reference doc.

## Project overview

**Better Swipe Keyboard**: Android soft keyboard (IME), Kotlin + Jetpack
Compose. Features:

- **Swipe (glide) typing**: custom decoder maps a finger trail over the
  QWERTY keys to the most likely dictionary word.
- **Voice input**: mic key dictates via `SpeechRecognizer`, with a
  voice-tuned AI cleanup pass.
- **AI proofreading**: on-device Gemini Nano (ML Kit GenAI), OpenRouter
  fallback, fixes the current sentence after 2 s typing inactivity.

Single module `:app`, package `com.example.betterswipekeyboard`. minSdk
35, target/compileSdk 36, Java 11, Kotlin 2.2.10, AGP 9.3.1, Gradle 9.5.0
(wrapper). Daemon JVM pinned to Java 21 via
`gradle/gradle-daemon-jvm.properties` (foojay auto-provisions).

## Architecture invariants

Deliberately layered — keep it this way. Entry points:
`SwipeKeyboardService` (the IME, owns decoder/proofreaders/all
`InputConnection` interaction) and `MainActivity` (setup screen; only an
Activity can request the `RECORD_AUDIO` runtime permission).

1. Layouts are pure data (`layout/`); panels (emoji, clipboard, voice)
   are layout MODES rendered by `KeyboardScreen`, and must live OUTSIDE
   the letter-gesture `pointerInput` container or their scrolls/taps are
   swallowed.
2. All gestures produce semantic `KeyboardAction`s at container level in
   `ui/keyboard/KeyboardScreen.kt`; keys are purely visual. A swipe
   trail begins at the first letter-key point; a drag crossing fewer
   than two DISTINCT letter keys is a drift-tap (`tapAction()`
   fallback), never decoded.
3. `KeyboardViewModel` reduces actions → `KeyboardState` + optional
   `KeyboardEffect`. Pure logic, unit-tested.
4. **Only `InputConnectionEditor` talks to the text field.** Backspace is
   grapheme-aware (`java.text.BreakIterator`) — never delete one UTF-16
   unit. First backspace after a swipe deletes the whole word
   (`KeyboardState.lastCommitWasSwipe` → `DeleteWordBackward`).
5. Space-bar horizontal drag = cursor control via D-pad key events
   (`InputConnectionEditor.moveCursor`).

## Reference docs (routing table)

| Before touching… | Read |
|---|---|
| `swipe/`, decoder tuning, dictionary generator | `docs/agent-reference/swipe-decoder.md` |
| alternates strip, `StripCells.kt`, swipe commit path | `docs/agent-reference/alternates-strip.md` |
| `proofread/`, prompts, `tools/eval/`, proofread scheduling | `docs/agent-reference/proofreading.md` |
| `ui/keyboard/`, `layout/`, `ime/`, gestures, numpad, popups | `docs/agent-reference/keyboard-ui.md` |
| `clipboard/`, `emoji/`, voice input | `docs/agent-reference/features.md` |
| emulator/adb workflow, API-36 platform issues, trail capture | `docs/agent-reference/environment.md` |
| decoder tuning history, rejected levers, miss autopsies | `docs/decoder-investigation.md` |

## Swipe decoding — guardrails

`SwipeDecoder` (pure Kotlin): SHARK-style scoring — every plausible
dictionary word scored against the trail (no letter reconstruction).
Three ORDERED geometric terms (first-basin letter alignment
`LETTER_DEPART_KEYS` 0.5 with a gated last-letter lift-off re-match
`REBASIN_RADIUS_KEYS` 0.8; SHARK2 tunnel line conformance, free 0.5 /
saturate 2.0 / cull 1.75 key-widths; backtrack penalty), plus salient/LCS
alignment, path-length, frequency prior, per-letter bonus, dwell ≥
300 ms doubling, end-key surcharge `END_KEY_SURCHARGE_WEIGHT` 0.5 (the
re-match/surcharge tension — ≤0.8kw licensed vs >0.5kw charged — is
deliberate: don't move one without the other), start-key surcharge
`START_KEY_SURCHARGE_WEIGHT` 0.7 (mirror of the end surcharge, charged on
the stock first-basin distance; NO start-side re-match exists — the first
letter's scan owns the touch-down basin, so there is no license/charge
tension to preserve here), mid-word dwell skip charge
`MIDWORD_SKIP_WEIGHT` 1.2 per skipped key the finger deliberately stopped
on mid-word (contiguous stay ≥ `MIDWORD_DWELL_MS` 150 ms within
`DWELL_STATIONARY_KEYS` 0.25, attributed within `DWELL_KEY_RADIUS_KEYS`
0.5, first/last `DWELL_EDGE_EXCLUDE_KEYS` 0.75 of arc excluded — endpoint
physics is not letter evidence; three→the is its driving evidence and the
charge is the endpoint surcharges' mid-word mirror: undiluted, outside
every normalization). Commit top word when
`score < MAX_COMMIT_SCORE` (1.8). Feedback flash
(`swipe/SwipeConfidence.kt`): FAILED → RED, close runner-up (margin <
`LOW_CONFIDENCE_MARGIN` 0.25) → YELLOW, confident → nothing.

Tuning rules learned the hard way (the test suite guards these):

- Measure curvature/speed over **arc-length windows** (0.35 key widths),
  never fixed point counts (they see jitter as turns). Salient regions
  use hysteresis (enter 0.45, exit 0.30), collapse to peak point.
- Keep matching **ordered and rigid** — no elastic/DTW warping, no
  neighbor-tolerant LCS (SHARK2 ripped elasticity out; short junk beat
  intended words when matching was tolerant). The one measured exception
  is the last letter's gated lift-off re-match.
- **No trail-length gates.** Straight-trail ties ("ak" vs "ask") are
  decided by frequency + per-letter length bonus. The LCS denominator
  floors at 3 (`ALIGNMENT_MIN_DENOMINATOR`).
- Geometric costs are per-letter/per-point MEANS (FUTO's γ-exponent
  normalization does not apply), plus `LENGTH_BONUS_PER_LETTER` 0.02.
- Constants marked "tuning starting point" come from SHARK2, FUTO, Sivek
  & Riley — validate against real trails (`SwipeTrailCapture`) before
  treating as settled.
- Check dictionary coverage before assuming a missing word is a decoder
  bug. Read `docs/decoder-investigation.md` before proposing decoder
  tuning — most naive levers are measured dead ends.

The dictionary (`words_en.txt`) is GENERATED by
`tools/generate_words_en.py` (wordfreq v3 + SCOWL junk-class filter,
CC BY-SA 4.0 — attribution in asset header, `NOTICE`, setup screen; keep
all three). Never hand-edit `words_en.txt`; edit the generator and
regenerate. Apostrophe words match LETTERS ONLY via `swipeLetters(word)`
and commit the apostrophe verbatim. Custom user words merge at rank 1
(`Dictionary.withCustomWords`) and affect swipe decoding only.

## Swipe alternates strip — invariants

- Always-visible row on EVERY surface; static total height 322.dp
  (`ui/keyboard/SwipeAlternatesStrip.kt`). ONE shared pure placement rule
  (`centeredCells`, `ui/keyboard/StripCells.kt`) for all three states
  (live/failed/committed): finger-up only recolors the center, NEVER
  rearranges the row.
- Strip state has exactly the `lastCommitWasSwipe` lifetime. Green =
  committed center only; blue (`LiveLeaderBlue`) = live leader that
  finger-up would commit; failed-swipe offers render PLAIN.
- The strip lives INSIDE the gesture surface: cells are visual-only
  rects, taps re-dispatched in the gesture loop (never clickable
  children); the `SelectAlternate` reduction is guarded by
  `lastCommitWasSwipe` and RE-ARMS it.
- Tap-typing mirror (display-only): two tiers BELOW the swipe tiers — the
  word mid-tap blue (`tapLiveWord`), the just-ended tap word green
  (`tappedWord`); VERBATIM field text, untappable cells, Enter clears.
  Service-owned: `refreshTapStrip()` re-reads the field after every
  tap/backspace text effect (`TapWord.kt`); swipe reductions clear it.
- Full mechanics: `docs/agent-reference/alternates-strip.md`.

## AI proofreading — invariants

- `selectBackend`: on-device (ML Kit Gemini Nano) wins when available;
  OpenRouter (`google/gemini-2.5-flash-lite`, temperature 0, latency-sorted
  ZDR providers only)
  when an API key is configured; otherwise none.
- Auto-proofread debounced 2 s after last user activity; results applied
  only if the user hasn't typed since (never clobber newer text).
  Failures are logged and swallowed — never depend on the AI. Tapping 3+
  chars suspends auto-proofread (`typedTapStreak`); the next swipe
  restores it.
- Prompt few-shot examples are GENERIC and invented, never derived from
  captured trails/incidents (enforced by `ProofreadPromptTest`'s corpus
  guard). The eval harness (`tools/eval/`) is the quality gate for any
  prompt/model change: ship rule = beat baseline arm A on intent-recovery
  on BOTH sub-corpora, no untouched regression, p95 well under the 15 s
  timeout, ZDR pre-flight first.
- Timing, prompt internals, swipe-path annotation wire format, debugging
  workflow: `docs/agent-reference/proofreading.md`.

## Clipboard, emoji, voice — invariants

- Clipboard: in-memory only, never persisted, never leaves the device;
  `EXTRA_IS_SENSITIVE` clips dropped at the source. Paste commits
  verbatim (no caps, no leading-space rules, no auto-proofread).
- Emoji: offline keyword table generated from CLDR by
  `tools/generate_emoji_keywords.py` — regenerate, don't hand-edit.
- Voice: only final results committed; dictated text proofread with
  `ProofreadMode.VOICE` 2 s after commit; `SpeechRecognizer` sessions
  cancelled when the keyboard hides.
- Mechanics: `docs/agent-reference/features.md`.

## Build and test commands

Requires `local.properties` with `sdk.dir` (SDK 36 minor API level 1
installed). Via the wrapper:

```bash
./gradlew assembleDebug          # build the APK
./gradlew installDebug           # install on a connected device/emulator
./gradlew testDebugUnitTest      # local unit tests (JVM)
./gradlew connectedDebugAndroidTest  # instrumented tests (needs device/emulator)
./gradlew lint                   # Android lint
```

After installing, enable the keyboard in system settings and select it as
the active IME (the setup screen has buttons for both). The emulator's
IME keeps falling back to GBoard — re-run `adb shell ime set
com.example.betterswipekeyboard/.SwipeKeyboardService` (more:
`docs/agent-reference/environment.md`). A fresh emulator boot shows a
BLACK SCREEN (the device is simply asleep): wake it with
`adb shell input keyevent KEYCODE_WAKEUP` + `82` + `KEYCODE_BACK` —
exact procedure in `docs/agent-reference/environment.md`.

## Testing instructions

- Unit tests in `app/src/test/`: **JUnit 4** (+ `org.json`), no mocking
  framework — pure logic and hand-written fakes.
- `app/build.gradle.kts` adds `src/main/assets` to the test source set's
  resources; tests load the real dictionary via
  `javaClass.getResourceAsStream("/words_en.txt")` (see
  `SwipeDecoderTest`). Keep this wiring intact.
- Interesting logic lives in pure, testable units: `KeyboardViewModel`,
  `SwipeDecoder`, `SentenceExtractor`, `ProofreadPrompt`,
  `selectBackend`, `InputConnectionEditor` companions (`withLeadingSpace`,
  `needsSpaceAfterSwipe`). Put new behavior in such a unit and test it
  there, not in the service or Compose UI.
- `app/src/androidTest/` has only the template instrumented test.

## Code style guidelines

- Kotlin `official` style; 4-space indent; Gradle configuration cache on.
- Language of code, comments, docs: **English**.
- Comments explain the *why*, including why a naive approach was
  abandoned (see `SwipeDecoder`'s KDoc).
- Compose UI stateless where possible: `KeyboardScreen` receives
  `KeyboardState`, emits `KeyboardAction`s; `KeyboardState` is the single
  source of truth.
- Keep features separate so parallel branches merge cleanly: prefer new
  files over growing shared ones; edits to shared files small and
  additive. Favor pure functions and small immutable data types
  (`parseCustomWords`, `bottomClearancePx`, `popupTopLeft`).
- Merge gotcha: when a branch based on the old monolithic
  `KeyboardScreen` merges into the panel architecture, deleting the old
  shape can silently drop assignments the new shape needs (two
  `popupAnchor` assignments were almost lost — the popup would never have
  shown). After such a merge, diff against the expected panel shape and
  check every `popupAnchor` site.
- Versions in the catalog `gradle/libs.versions.toml` — never hard-coded
  dependency strings.
- No linter/formatter beyond stock lint; match the surrounding file.

## Security considerations

- The keyboard sees everything typed. Default to the **on-device**
  proofreader; text leaves the device only via OpenRouter, restricted to
  zero-data-retention endpoints (`provider.zdr = true`, `data_collection
  = "deny"`). Preserve these request fields.
- OpenRouter API key stored unencrypted. Never log it or send it anywhere
  besides `openrouter.ai`.
- Permissions: `INTERNET`, `RECORD_AUDIO`; IME protected by
  `BIND_INPUT_METHOD`. `RECORD_AUDIO` requested at runtime from
  `MainActivity` only; dictation audio goes to the system speech
  recognizer, never our code or network.
- Clipboard history never leaves the device (in-memory only, never
  persisted). `EXTRA_IS_SENSITIVE` clips dropped at the source, never
  stored or logged. Preserve both guarantees.

## How the user likes to work

- Vanilla first, fancy later; architecture must extend cleanly.
- Bigger features: plan-mode plan → approval → implement → verify →
  commit.
- **Commit after each feature** (standing instruction); the user tests
  after; fixes get their own follow-up commits.
- Verify empirically (emulator screenshots, adb, real device) and state
  plainly what could not be verified.
- Privacy matters: prefer on-device, ZDR, honest cloud disclosure in the
  UI.
- UI taste: iOS-like keyboard aesthetics, thumb reach, iterating on small
  details.
- When asking him to record or test the swipe test sentences, ALWAYS
  re-print the full sentence list in the reply.
