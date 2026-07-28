# AGENTS.md

Guidance for AI coding agents working in this repository. Assumes no prior
knowledge of the project.

## Project overview

**Better Swipe Keyboard** is an Android soft keyboard (IME) app, written
entirely in Kotlin with Jetpack Compose. Its two headline features:

- **Swipe (glide) typing**: a custom decoder maps a finger trail over the
  QWERTY keys to the most likely dictionary word.
- **Voice input**: the microphone key dictates via the built-in
  `SpeechRecognizer`, with a voice-tuned AI cleanup pass after dictation.
- **AI proofreading**: an on-device Gemini Nano proofreader (ML Kit GenAI),
  with an OpenRouter cloud fallback, fixes the current sentence after 2
  seconds of typing inactivity.

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
  OpenRouter API key field, a test text field, and the `RECORD_AUDIO`
  runtime-permission request for voice input (an IME service cannot show
  the system permission dialog — only an Activity can).

Data flow (deliberately layered, keep it this way):

1. **Layouts are pure data** (`layout/`): `KeyboardLayout` = rows of `Key`s,
   each with a `KeyOutput` (Text, Backspace, Enter, Shift, SwitchLayout,
   Microphone). `QwertyLayout` and `SymbolsLayout`. Character keys render at
   one fixed global width computed in `KeyboardScreen` (`unitKeyWidthPx` in
   `ui/keyboard/KeyWidth.kt`); rows with fewer keys are centered instead of
   stretched, and modifier keys take the remaining space via weights.
2. **Panels are layout modes, not layouts**: `LayoutId.EMOJI` has no
   `KeyboardLayout` — `KeyboardScreen` renders `EmojiPanel` instead of the
   letter rows. Any panel (scrollable/tappable content) must live **outside**
   the letter-gesture `pointerInput` container; otherwise panel scrolls/taps
   are swallowed as gestures. On the letters/symbols layouts the container
   wraps the utility row too (so a swipe can start anywhere in the keyboard
   rectangle); panels and the voice screen keep the utility row outside as
   plain clickable keys (two render modes of one `UtilityRow` composable).
3. **All gestures produce semantic actions** (`KeyboardAction`): taps,
   long-presses and swipes are handled at the container level in
   `ui/keyboard/KeyboardScreen.kt` (keys themselves are purely visual), and
   every user intent becomes a `KeyboardAction`. On the letters layout a
   DRAG from anywhere except the space bar may start a swipe (letter
   keys, dead space, modifier keys, utility row), but the trail — visual
   and decode alike — only begins at the first point on a letter key
   (`firstLetterContactIndex` in `swipe/TrailTrim.kt`; the off-letter
   prefix is approach, not word, and would poison the decoder's letter
   alignment). A drag that never touches a letter is not a swipe:
   nothing drawn, nothing decoded. Taps on the gesture-mode
   utility row are re-dispatched semantically in the loop
   (`utilityTapAction` in `ui/keyboard/UtilityGesture.kt`, settings via the
   `onSettingsClick` callback). The symbols layout keeps no decoding: a
   non-spacebar drag there is swallowed.
4. **`KeyboardViewModel` reduces actions** into a new `KeyboardState` plus an
   optional `KeyboardEffect` (CommitText / CommitWord / DeleteBackward /
   PerformEnter). Pure logic, no Android types beyond `ViewModel`.
5. **Only `InputConnectionEditor` talks to the text field** — the service
   applies effects through it. InputConnection handling exists in exactly
   one place. Backspace is grapheme-cluster-aware
   (`precedingGraphemeLength`, `java.text.BreakIterator`): never delete a
   single UTF-16 unit — emoji are surrogate pairs and deleting one unit
   leaves a U+FFFD replacement char. Every InputConnection call is a
   synchronous Binder round-trip into the target app, so the held-backspace
   repeat keeps them minimal: a "delete streak" (reset by any other edit)
   skips the per-step `getSelectedText` check after the first step, and the
   repeat clock in `KeyboardScreen` fires at fixed 50 ms boundaries instead
   of 50 ms after each step's IPC returned. The first backspace after a
   swipe deletes the whole just-swiped word, Gboard-style:
   `KeyboardState.lastCommitWasSwipe` (set by the CommitWord reduction,
   cleared by any other input action — but NOT by GestureStarted/Ended,
   which wrap every gesture) makes the ViewModel emit
   `KeyboardEffect.DeleteWordBackward`, and `precedingWordLength` measures
   the word plus its auto-inserted leading space (never a newline) via the
   word `BreakIterator`. Voice dictation bypasses the reducer, so it never
   arms the word-delete.
6. **Space-bar drag = cursor control** (`SpacebarCursor.kt` +
   `ui/keyboard/SpacebarCursorDrag.kt`): a horizontal drag on the space bar
   emits `MoveCursor` step deltas (net displacement), which
   `InputConnectionEditor.moveCursor` applies as D-pad key events so the
   target app handles grapheme clusters, selection collapse and clamping.
   Scrubbing is velocity-sensitive: the travel per step shrinks with the
   EMA-smoothed finger velocity (`spacebarStepSize` zones — 14.dp/char
   below 200 dp/s, 8.dp mid, 4.dp above 800 dp/s, tuning starting points),
   and the accumulator is re-anchored on every zone change
   (`rebaseCursorAnchor`) so already-emitted steps never re-divide
   retroactively. Cursor moves consume no one-shot shift and schedule no
   auto-proofread.
   The space bar's touch-acceptance area is inset from the top
   (`SpacebarTopHitInset` in `KeyboardScreen.kt`, hit-testing only — the
   visual key and the stored geometry rects are unchanged), and the slack
   strip counts as "no key": drags starting there (or in any dead space of
   the letters layout) collect a swipe trail, so an overshoot word-swipe
   starting just above the space bar decodes instead of being eaten by the
   cursor drag. Junk gap trails are filtered by `MAX_COMMIT_SCORE`.

### Swipe decoding (`swipe/`)

- `SwipeDecoder` (pure Kotlin, no Android deps): SHARK-style scoring — every
  plausible dictionary word is scored against the trail instead of
  reconstructing letters from the trail. Three ORDERED geometric terms make
  the word's ideal key-to-key path explain the trail in sequence (this is
  what separates same-start/end words like "my" vs "mummy"):
  1. Ordered letter alignment: each letter matches at the minimum of the
     FIRST approach basin after the previous letter's match
     (`LETTER_DEPART_KEYS` 0.5 ends a basin). Never a global argmin —
     jitter decides which of two visits to the same key wins, and a stolen
     match cascades every following letter off the trail ("follow"
     regressed to "flow" until first-basin matching). Crossed letters
     ("swipe"'s i) match cheaply on the passing trail.
  2. Line conformance (SHARK2's tunnel): trail points between two matched
     letters must follow the key-to-key segment; free inside 0.5
     key-widths, linear to a 2.0 saturation cap, hard cull at 1.75
     key-widths (FUTO's legacy decoder). A correctly traced word scores
     ~zero at ANY trail length — that is why no trail-length gate exists.
  3. Backtrack penalty: trail steps opposing the current leg's direction
     cost their length — a zigzag word's reversal leg (M→U→M) on a
     straight trail.
  Plus: salient points (high curvature or low speed) mark deliberate
  motion; an LCS alignment between salient keys and the word, a trail-vs-
  ideal path-length term (Swype's per-word "expected path length"), a
  unigram frequency prior, and a small per-letter length bonus (FUTO's
  β·L). A dwell ≥ 300 ms on a key doubles its letter. Salient evidence is
  graded before it can charge: a mid-trail region dominated by SLOWNESS
  (not curvature) counts as a deliberate key visit only if the finger
  lingered ≥ 60 ms (a slight slowdown over a crossed key is aim noise —
  "dog" hesitating over F must not become "fog"); endpoint regions are
  anchored to the actual first/last trail point (their hardcoded 0.5
  salience is evidence-free, so the distance term skips the salience
  multiplier there), and an ISOLATED lift-off region — no measured
  salience reaches the last point, i.e. the finger lifted mid-flight
  without decelerating — emits no key at all, so a drift endpoint's
  nearest key ("dough"'s h, "we're"'s r) can't charge the intended word
  a missed salient it never earned (touch-down keeps its anchor
  unconditionally: the finger starts at rest on an aimed key); and words
  whose first letter matches mid-trail pay
  an unexplained-head charge mirroring the tail term (0.5kw free — touch-
  down aim is much better than lift-off aim). Lower score =
  better; `KeyboardScreen` commits the top word when
  `score < MAX_COMMIT_SCORE` (1.8, calibrated on captured real-hand
  trails — correct swipes at normal speed land up to ~1.8). Two-tier
  feedback flash (pure classification in `swipe/SwipeConfidence.kt`,
  jQuery-highlight-style fade over ~400 ms, purely cosmetic): a FAILED
  swipe (no candidate below the cutoff, nothing committed) flashes the
  trail RED (`FailedSwipeFlash`); a commit with a close runner-up
  (top2−top1 margin < `LOW_CONFIDENCE_MARGIN` 0.15, calibrated on the
  four captured trail sets — flags 8/13 wrong commits at ~3% false
  positives) flashes YELLOW (`LowConfidenceFlash`) as "maybe re-swipe";
  confident commits flash nothing. Segment alpha in
  `ui/keyboard/TrailFade.kt`.
- Tuning rules learned the hard way (the test suite guards these):
  - Measure curvature/speed over **arc-length windows** (0.35 key widths),
    never fixed point counts — real finger trails are dense and jittery, and
    point-count windows see jitter as turns. Salient regions use hysteresis
    (enter 0.45, exit 0.30) and collapse to their peak point.
  - Keep matching **ordered and rigid** — no elastic/DTW-style warping and
    no neighbor-tolerant LCS: SHARK2 tried elasticity and ripped it out
    (it destroys discrimination in a crowded template space), and our own
    history agrees (short junk like "role", "keynote" beat intended words
    when matching was tolerant).
  - **No trail-length gates.** The old two-letter gate (≤ 3.5 key widths)
    was deleted: two-letter words compete like any other. Straight-trail
    ties (e.g. "ak" vs "ask" on a straight A→K line — both tunnel
    perfectly) are decided by word frequency plus the small per-letter
    length bonus, per the signed-off rule: on a genuinely straight trail
    the obvious frequent short word wins. The LCS alignment denominator
    floors at 3 (`ALIGNMENT_MIN_DENOMINATOR`) so two-letter words no
    longer get a free perfect alignment — that structural bias is why
    "ak" once beat "ask".
  - Geometric costs are per-letter / per-point MEANS (length-normalized by
    construction; FUTO's γ-exponent normalization is for summed CTC costs
    and does not apply), plus the per-letter bonus
    `LENGTH_BONUS_PER_LETTER` (0.02) against residual short-word bias.
  - Constants marked "tuning starting point" in `SwipeDecoder` come from
    SHARK2 (tunnel radius), FUTO's legacy decoder (cull) and Sivek & Riley
    (saturation) — validate them against real trails recorded with
    `SwipeTrailCapture` before treating them as settled.
  - The old google-10000 list (2006 web n-grams) was replaced by wordfreq
    data (see below); check dictionary coverage before assuming a missing
    word is a decoder bug.
  - `docs/decoder-investigation.md` is the decoder's engineering log: every
    captured-trail miss autopsy with term breakdowns, and the REJECTED
    levers with the measurements that killed them (tail-cap, cull deletion,
    drag stage-0, endpoint exemption). Read it before proposing decoder
    tuning — most naive levers are already measured dead ends.
- `Dictionary`: frequency-ordered word list from
  `app/src/main/assets/words_en.txt` (`word<TAB>rank` lines, ~55k words,
  lower rank = more frequent), indexed by first letter. The asset is
  generated by `tools/generate_words_en.py` from **wordfreq v3**
  (multi-corpus ~2021 snapshot: Wikipedia, OpenSubtitles, SUBTLEX, Google
  Books, OSCAR, Twitter, Reddit; top 60k filtered to plain words
  `^[a-z]{2,}$` plus one-apostrophe tokens `^[a-z]+'[a-z]+$`, minus
  vowel-less 3+ letter abbreviation junk like "pwr"/"thx" — 'y' counts as a
  vowel so "sky"/"gym" stay). Data license **CC BY-SA 4.0** — attribution
  lives in the asset's comment header (comment lines are skipped by
  `Dictionary.load`), the repo-root `NOTICE` file, and a credit line on the
  setup screen; keep all three. A manual supplement (keyboard vocabulary
  like "swipe") is merged by the generator's `SUPPLEMENT` list at each
  word's wordfreq rank (unknown words append at the tail) — edit the
  generator and regenerate, never hand-edit `words_en.txt`.
- Apostrophe words (possessives/contractions) are swipeable: the generator
  admits tokens with exactly ONE apostrophe between letters ("mother's",
  "don't", ~1.75k of them) so first()/last() is always a letter and the
  endpoint gates are unaffected. The decoder matches them LETTERS ONLY —
  `swipeLetters(word)` (swipe/WordLetters.kt) strips the apostrophe, which
  has no key and contributes zero geometry (no distance, conformance,
  salience or length cost; letter count feeds every per-letter term so
  the means stay undiluted) — and commits the apostrophe VERBATIM
  ("Mother's" under one-shot shift already works). Frequency is the only
  tie-breaker between same-letter candidates: swiping m-o-t-h-e-r-s gives
  "mother's" (4.37 > mothers 4.32), i-t-s gives "it's" over "its", and
  plurals win where they genuinely outrank ("dogs" > "dog's"). Philip
  signed off these arbitrations.
- Junk-class filter (the generator's second source is **SCOWL**
  english/american-words + proper-names levels, fetched from the
  rdeits/SCOWL-mirror or a local dir): two word-CLASS rules, never a rank
  cutoff (rank-adjacent keepers like "pizzas" 18158 vs "wick" 18235 prove
  no threshold separates them):
  - **Rare proper names**: in SCOWL names ∧ NOT in SCOWL words ∧ zipf <
    2.8 — drops "brien"/"vey"/"iver"-class surname junk (~2.5k words)
    while keeping names people type ("siri", "alexa", "jose", "maria").
    Apostrophe tokens are EXEMPT (Philip's call after a decoder
    competition audit: a possessive's letters must match the trail in
    order, so frequent words crush them on frequency whenever geometry
    coincides — the bare-name steal mechanism doesn't transfer).
  - **Nonce respellings**: len ≥ 4, not a SCOWL word, zipf < 3.1, and a
    same-length ONE-SUBSTITUTION neighbor exists in SCOWL words with zipf
    ≥ word + 2.0 ("krazy"→"crazy"). Substitution-only is deliberate —
    insertion/deletion neighbors would kill real words ("json"→"son").
    A small documented `KEEP_EXCEPTIONS` list saves mandated modern words
    the rule would eat ("cron", "vimeo", "binance", "yeet", "thanos", ...).
  - Known survivors (real SCOWL words no principled rule drops): "doh",
    "dix", "folic" — they still steal an occasional swipe; decoder-side
    territory, not dictionary. wordfreq's API case-folds, so
    capitalization is NOT an available name signal (measured, dead end).
- Custom user words (names, jargon) merge in via
  `Dictionary.withCustomWords` at rank 1 (top frequency, geometry still
  dominates scoring); parsed from free-form input by `parseCustomWords`
  (split on any non-letter run — but an apostrophe BETWEEN letters stays
  intra-word, so custom possessives like "spielberg's" work; hyphens
  break),
  stored newline-joined in SharedPreferences by `CustomWordStore`. The
  service rebuilds the decoder in `onStartInputView` when the stored string
  changed; `KeyboardScreen` receives a `decoderProvider` so it reads the
  current decoder at gesture time. Custom words affect swipe decoding only.
- `KeyboardGeometry`: collects key bounds from the Compose UI
  (`onGloballyPositioned`) and answers hit-testing / key-center questions.

### Clipboard history (`clipboard/`)

- `ClipboardHistory` (pure Kotlin, injectable clock): in-memory ring buffer,
  50 entries max, 1-hour lazy expiry, case-sensitive exact-match dedup
  (re-copying moves a clip to the top). Blank and >10k-char clips rejected.
  Deliberately not persisted — process death clears it, and nothing lands
  on disk or in backups.
- `SwipeKeyboardService` observes `ClipboardManager` with an
  `OnPrimaryClipChangedListener` (the platform grants the default IME
  clipboard access even when the keyboard is hidden) and feeds accepted
  clips to `KeyboardViewModel.addClip`; the ViewModel mirrors them into
  `KeyboardState.clipboard`. Clips marked
  `ClipDescription.EXTRA_IS_SENSITIVE` are never stored; only item text is
  read (never `coerceToText`).
- The 📋 utility key toggles `LayoutId.CLIPBOARD`; `ClipboardPanel.kt`
  renders outside the letter-gesture `pointerInput` scope (same pattern as
  panels elsewhere). Tap emits `PasteClip` (returns to letters); long-press
  deletes. Paste reduces to `KeyboardEffect.PasteText`, which commits
  verbatim — never uppercased by caps, no leading-space rules, and no
  auto-proofread scheduling (a paste must stay exactly as copied).

### Emoji suggestions (`emoji/`)

- `EmojiSuggester` (pure Kotlin, no Android deps): matches the last few
  words before the cursor against a static keyword → emoji table and
  returns up to 8 emoji for the suggestion row atop the emoji panel.
  Offline and instant — plain HashMap lookups, no AI/network.
- The table is `app/src/main/assets/emoji_keywords_en.txt`
  (`keyword<TAB>emoji,emoji,...`, best first), generated by
  `tools/generate_emoji_keywords.py` from Unicode CLDR annotations
  (English). Regenerate to update; hand-tuned aliases ("taking off",
  "lol") live in the script's `ALIASES` dict. CLDR keys drop the VS16
  variation selector where the panel keeps it ("✈" vs "✈️") — the
  generator canonicalizes to the panel form, preserve that.
- Matching rules (unit-tested in `EmojiSuggesterTest`): last 3 letter
  tokens, last-two-word bigram first, exact then naive singular
  ("planes" → "plane"), most-recent-word-first ranking, dedup, cap 8.
- The service owns the suggester and mirrors results into
  `KeyboardState.emojiSuggestions` (same pattern as the clipboard):
  refreshed when the emoji panel opens and after any text change while
  it is open, cleared on switching away. `EmojiPanel`'s suggestion row
  hides when empty; the panel height is fixed, so the row never resizes
  the IME window. Suggestion taps commit via the same
  `KeyboardAction.InsertText` path as grid taps.
- `EmojiPanel` is ONE scroll surface: a single `LazyVerticalGrid` holds
  the suggestion block (label + row, only while suggestions exist), the
  "Categories" label, the category bar, and all sections as items; only
  the ABC/backspace bottom bar is pinned. Category jumps therefore
  depend on whether suggestions are visible — use
  `categoryJumpIndex(categories, hasSuggestions, index)` (pure, tested
  in `EmojiDataTest`), never `categoryStartIndex` plus a hardcoded
  offset; the two must stay in sync with the grid's item order.

### AI proofreading (`proofread/`)

- `Proofreader` interface; `MlKitProofreader` (on-device Gemini Nano via
  `com.google.mlkit:genai-proofreading`, keyboard-tuned input type, kicks off
  model download itself) and `OpenRouterProofreader` (OkHttp + org.json,
  model `google/gemini-2.5-flash-lite`, few-shot prompt in `ProofreadPrompt`,
  requests restricted to zero-data-retention providers).
- `selectBackend`: on-device wins when available; cloud when an API key is
  configured; otherwise none.
- Auto-proofread is debounced 2 s after the last *user activity*, not just
  the last text change (`SwipeKeyboardService.scheduleAutoProofread`):
  `KeyboardScreen` emits `KeyboardAction.GestureStarted`/`GestureEnded`
  around every gesture (a swipe produces no actions until finger-up), a
  touch cancels the timer, and finger-up reschedules it only when the
  dirty flag says text changed since the last proofread attempt (no
  wasted API calls after no-op gestures). Typed-mode scheduling during
  a gesture is deferred to finger-up (`gestureActive`), so a held
  backspace doesn't cancel + relaunch the job on every repeat step.
- `SentenceExtractor.currentWindow` pulls the current fragment *plus the
  previous sentence* from text before the cursor, so a continuation
  fragment ("and bought ...") can be merged back into a sentence an
  earlier pass terminated during a mid-thought pause. The window never
  crosses the last newline before the cursor: a newline is a deliberate
  user boundary (paragraphs, lists), so text before it is neither
  analyzed nor editable, the newline itself can never be removed by a
  replacement, and continuation merging is possible only WITHIN a
  paragraph. The whole window is the proofread input and the replacement
  span; the result is only applied if the user hasn't typed since (never
  clobber newer text).
  Proofread failures are logged and swallowed — the keyboard must never
  depend on the AI.
- The merge behavior is taught only on the OpenRouter path (few-shot
  examples in `ProofreadPrompt`). The ML Kit API takes plain text only —
  no system prompt, no few-shot — so on-device merging is best-effort
  model behavior and parity between backends is not guaranteed.
- The typed prompt also teaches the swipe decoder's measured error
  classes (`ProofreadPrompt.SWIPE_EXAMPLES`): post-word drags (his→hours),
  tail truncations (mother→not), same-path swaps (nine→bounce), edge
  key-slips (quick→wick) and rare-word frequency-tie steals (fox→folic),
  plus negative examples guarding against 'correcting' plausible words.
  Examples deliberately avoid the ten-sentence retest corpus so the
  retest measures class generalization, not memorization. OpenRouter
  path only, same ML Kit caveat as merging.
- The OpenRouter API key is stored in plain SharedPreferences by
  `ApiKeyStore` (acceptable for a personal app; noted in code as
  not production-grade).

### Voice input

- The microphone key emits `KeyboardAction.ToggleVoice`; the **service**
  decides start/stop (permission + `SpeechRecognizer.isRecognitionAvailable`
  checks are Android concerns) and drives `VoiceState` (OFF / LISTENING /
  PERMISSION_REQUIRED / UNAVAILABLE) in `KeyboardState` via ViewModel
  setters. While not OFF, the key rows are replaced by a minimal
  `VoicePanel`, and `KeyboardScreen`'s container gesture loop swallows all
  touches — the `KeyboardGeometry` rects are stale while the panel is up and
  must not produce phantom text.
- `SpeechRecognizer` is created lazily and destroyed on the main thread;
  `onFinishInputView`/`onWindowHidden` cancel any active session so the mic
  is never held after the keyboard hides. Only final results are committed
  (via `editor.commitWord`, so leading-space and voice-proofread scheduling
  come free); partials only update the panel.
- Dictated text is proofread with `ProofreadMode.VOICE` 2 s after the
  transcript commit, through the same debounce and never-clobber guard as
  typed text. ML Kit's `ProofreadingRequest` has **no custom-prompt hook** —
  the only tuning is `ProofreaderOptions.InputType` per client, so
  `MlKitProofreader` holds a second client configured with
  `InputType.VOICE` (tuned for homophone/same-sound errors). The
  OpenRouter path instead uses the voice few-shot prompt
  (`ProofreadPrompt.VOICE_SYSTEM`/`VOICE_EXAMPLES`). `selectBackend` is
  unchanged: voice proofreading uses whichever backend is active, and none
  when there is no backend.

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
- Keep features as separate as possible so parallel branches merge cleanly:
  prefer new files over growing shared ones (e.g. `PunctuationPopup.kt`,
  `ClipboardPanel.kt`, `BottomInsets.kt`), and when a shared file must
  change, make the edit small and additive. Favor (mostly) functional style
  — pure functions and small immutable data types over stateful objects
  (e.g. `parseCustomWords`, `bottomClearancePx`, `popupTopLeft`): pure code
  is trivially unit-testable and its merge conflicts stay textual, not
  behavioral.
- Merge gotcha learned the hard way: when a branch based on the old
  monolithic `KeyboardScreen` merges into the panel architecture, deleting
  the old shape can silently drop assignments the new shape still needs
  (two `popupAnchor` assignments were almost lost this way — the popup
  would never have shown). After such a merge, diff the result against
  the expected panel shape and check every `popupAnchor` site.
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
- The app requests the `INTERNET` and `RECORD_AUDIO` permissions; the IME
  service is protected by `BIND_INPUT_METHOD`. `RECORD_AUDIO` is requested
  at runtime from `MainActivity` only; dictation audio goes to the system
  speech recognizer, never to our own code or network layer.
- Clipboard history is recorded from `ClipboardManager` but never leaves
  the device (no network, in-memory only, never persisted). Clips flagged
  `ClipDescription.EXTRA_IS_SENSITIVE` (password managers, password
  fields) are dropped at the source and never stored or logged. Preserve
  both guarantees.

## Android API 36 + gesture-handling gotchas

- `InputMethodService.onShowSoftInput` no longer exists on API 36 — the show
  path is `onShowInputRequested`. Don't copy older IME tutorials blindly.
- `onEvaluateInputViewShown()` must return `true`: its default hides the
  soft keyboard when a hardware keyboard is attached (which emulators
  emulate), and the IME then *silently never appears*.
- After move events are consumed, the finger-up can arrive consumed too:
  exit gesture loops on `!change.pressed`, not `changedToUp()`.
- The restricted `AwaitPointerEventScope` forbids `coroutineScope`/`launch`;
  use `withTimeoutOrNull` loops for timers (see backspace repeat).
- The IME window's SoftInputWindow does not reliably pad for the system
  nav/IME strip (hide-keyboard chevron, IME switcher) — `navigationBars`
  alone left the strip overlapping the bottom row on a Galaxy Z Fold 5. The
  service calls `WindowCompat.setDecorFitsSystemWindows(window, false)` and
  measures the real bottom inset via a `ViewCompat` listener
  (`max(navigationBars, tappableElement, mandatorySystemGestures)` →
  `ime/BottomInsets.kt` `bottomClearancePx`), passed to `KeyboardScreen` as
  `bottomClearance`; a small fixed 4dp (`KeyboardBottomClearance`) is added
  on top purely as an aesthetic gap (12dp left too much dead space above
  the strip). The listener MUST be registered on the
  window's **decor view** (plus `ViewCompat.requestApplyInsets`): the IME
  window does not dispatch WindowInsets down to the input view, so a
  listener on the ComposeView never fires.
- Compose modifier order decides what `imePadding()` pads: AFTER
  `verticalScroll` it becomes part of the scrollable content (just extends
  the scroll range); BEFORE it, it shrinks the viewport — which is what a
  setup screen wants. And neither auto-scrolls a focused text field above
  the IME: relocation on focus fires before the animated inset lands, so
  trigger `bringIntoView` only once the `ime()` inset has settled (see
  `MainActivity`).

## Environment quirks (adb/emulator workflow)

- The emulator's IME keeps falling back to GBoard after reinstalls, uimode
  changes, and force-stops. Re-run
  `adb shell ime set com.example.betterswipekeyboard/.SwipeKeyboardService`.
- After an emulator boot, dismiss the "System UI isn't responding" dialog
  (tap Wait) before driving the UI.
- `~/Library/Android/sdk` has `cmdline-tools` installed (for avdmanager/
  sdkmanager). AVDs: `Medium_Phone_API_36.0` (daily driver),
  `Pixel_9_Pro_API_36` (created to confirm AICore is absent on emulators).
- `adb shell input swipe` only draws straight lines and cannot do
  hold-then-drag, so the punctuation popup's drag-select needs a real
  finger to verify. GBoard's stylus toolbar appears instead of our keyboard
  whenever the IME fell back — that's the tell.
- Voice input needs a speech recognizer (Play services image) and the AVD's
  "host microphone" enabled; `SpeechRecognizer.isRecognitionAvailable` is
  false on bare images, which surfaces as the keyboard's UNAVAILABLE panel.
  Real dictation (and the ML Kit `InputType.VOICE` proofread, since AICore
  is absent on emulators) can only be verified on a real device.
- Debug builds can record real swipe trails for decoder tuning: toggle
  "Record swipe trails" in the app's setup screen (debug-only, off by
  default, local only — see `swipe/SwipeTrailCapture.kt`), then pull
  `adb pull /sdcard/Android/data/com.example.betterswipekeyboard/files/swipe_trails.jsonl`
  (external app-specific storage — platform-tools 37 removed `adb run-as`,
  so internal storage is unreachable on production devices). Each line is
  one swipe: key geometry, timed trail points, decoder top-5.

## How the user likes to work

- Vanilla first, fancy later; architecture must extend cleanly.
- Bigger features: plan-mode plan → approval → implement → verify → commit.
- **Commit after each feature** (standing instruction); the user tests
  after; fixes get their own follow-up commits.
- Verify empirically (emulator screenshots, adb, real device) and state
  plainly when something could not be verified.
- Privacy matters: prefer on-device, ZDR, honest cloud disclosure in the UI.
- UI taste: iOS-like keyboard aesthetics, thumb reach, iterating on small
  details (labels, colors, popup styling, long-press hints).
- When asking him to record or test the swipe test sentences, ALWAYS
  re-print the full sentence list in the reply — he doesn't want to scroll
  back up to find them.

