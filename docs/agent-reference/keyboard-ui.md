# Keyboard UI & gestures — reference

Read this before touching `ui/keyboard/`, `layout/`, `ime/`, or the
gesture/commit paths in `KeyboardScreen.kt` and `SwipeKeyboardService`.
`AGENTS.md` carries the architecture invariants; this file carries the
mechanics.

## Entry points

Two entry points (`app/src/main/AndroidManifest.xml`):

- `SwipeKeyboardService` — the `InputMethodService`. An IME service is no
  Activity, so it acts as its own LifecycleOwner/ViewModelStoreOwner/
  SavedStateRegistryOwner, attached to the window's decor view for
  Compose. Owns the `SwipeDecoder`, proofreaders, all `InputConnection`
  interaction.
- `MainActivity` — setup screen: enable/pick IME buttons, OpenRouter key
  field, test field, `RECORD_AUDIO` runtime-permission request (an IME
  service cannot show the permission dialog).

## Data flow (deliberately layered, keep it this way)

1. **Layouts are pure data** (`layout/`): `KeyboardLayout` = rows of
   `Key`s with a `KeyOutput` (Text, Backspace, Enter, Shift,
   SwitchLayout, Microphone): `QwertyLayout`, `SymbolsLayout`,
   `NumericLayout`. Character keys render at one fixed global width
   (`unitKeyWidthPx`, `ui/keyboard/KeyWidth.kt`); short rows centered,
   modifiers take remaining space via weights. Exception: numpad keeps
   its own uniform 1/3-width weights.
2. **Panels are layout modes, not layouts**: `LayoutId.EMOJI` has no
   `KeyboardLayout` — `KeyboardScreen` renders `EmojiPanel`. Panels must
   live **outside** the letter-gesture `pointerInput` container or their
   scrolls/taps are swallowed. On key layouts the container wraps the
   utility row too (a swipe can start anywhere); panels/voice keep it
   outside as plain clickable keys (two render modes of `UtilityRow`).
3. **All gestures produce semantic actions** (`KeyboardAction`), handled
   at container level in `ui/keyboard/KeyboardScreen.kt`; keys are purely
   visual. On letters, a DRAG from anywhere except the space bar may
   start a swipe, but the trail (visual + decode) begins at the first
   letter-key point (`firstLetterContactIndex`, `swipe/TrailTrim.kt` —
   the off-letter prefix would poison letter alignment). A drag that
   never touches a letter: nothing drawn, nothing decoded. A drag
   crossing fewer than two DISTINCT letter keys
   (`distinctLetterKeysCrossed`, `MIN_SWIPE_LETTERS`) is a drift-tap:
   falls back to `tapAction()` (backspace drift deletes once), decoder
   never runs. Gesture-mode utility-row taps re-dispatch via
   `utilityTapAction` (`ui/keyboard/UtilityGesture.kt`, settings via
   `onSettingsClick`). Symbols/numeric: no decoding, non-spacebar drags
   swallowed.
4. **`KeyboardViewModel` reduces actions** → `KeyboardState` + optional
   `KeyboardEffect` (CommitText/CommitWord/DeleteBackward/PerformEnter).
   Pure logic.
5. **Only `InputConnectionEditor` talks to the text field.** Backspace is
   grapheme-aware (`precedingGraphemeLength`, `java.text.BreakIterator`)
   — never delete one UTF-16 unit (surrogate pairs → U+FFFD). Each
   InputConnection call is a synchronous Binder round-trip, so
   held-backspace repeat minimizes them: a "delete streak" (reset by any
   other edit) skips the per-step `getSelectedText` check after step one;
   the repeat clock fires at fixed 50 ms boundaries. First backspace
   after a swipe deletes the whole word, Gboard-style:
   `KeyboardState.lastCommitWasSwipe` (set by CommitWord, cleared by any
   other input action but NOT GestureStarted/Ended) →
   `KeyboardEffect.DeleteWordBackward`; `precedingWordLength` measures
   word + auto-inserted leading space (never a newline). Voice dictation
   bypasses the reducer, never arms word-delete.
6. **Space-bar drag = cursor control** (`SpacebarCursor.kt`,
   `ui/keyboard/SpacebarCursorDrag.kt`): drag emits `MoveCursor` deltas,
   applied as D-pad key events (`InputConnectionEditor.moveCursor`) so
   the target app handles grapheme clusters/selection/clamping.
   Velocity-sensitive `spacebarStepSize` zones: 14.dp/char below
   200 dp/s, 8.dp mid, 4.dp above 800 dp/s (tuning starting points);
   accumulator re-anchored on zone change (`rebaseCursorAnchor`). Cursor
   moves consume no one-shot shift, schedule no auto-proofread. Space
   bar's touch area is inset from the top (`SpacebarTopHitInset`,
   hit-testing only); the slack strip counts as "no key" — drags starting
   there (or any dead space on letters) collect a swipe trail, so
   overshoot word-swipes just above the space bar decode. Junk gap trails
   filtered by `MAX_COMMIT_SCORE`.

`KeyboardGeometry`: collects key bounds (`onGloballyPositioned`),
answers hit-testing/key-center questions.

## Numeric keypad

- `LayoutId.NUMERIC` + `NumericLayout` (`layout/NumericLayout.kt`):
  strict 3x4 dial pad (ITU-T, uniform weight-1f — no wide keys, no ABC
  key, no space bar, no visible punctuation; Philip's spec, don't "fix").
  No swipe decoding; non-spacebar drags swallowed.
- Utility row's fifth key toggles both ways, contextual label ("123" /
  "ABC"); gesture-mode taps via `utilityTapAction(id,
  proofreaderAvailable, layout)`.
- Auto-popup: `SwipeKeyboardService.onStartInputView` applies
  `fieldStartLayout` (`ime/NumericField.kt`, pure, tested) on fresh field
  start (`!restarting`) — numpad for `TYPE_CLASS_NUMBER`/`PHONE`/
  `DATETIME`, back to letters when the next field is non-numeric and the
  numpad shows. Manual toggle never overridden within a field session.
  Routed through `onKeyboardAction` so emoji-suggestion clearing applies.
- Punctuation behind the `0` long-press popup via `keyPopup(layout,
  key)` (`ui/keyboard/LongPressPopup.kt`): `NUMERIC_POPUP` = `# * ( ) / :
  . ,` + space (renders "␣", commits " "; the numpad's only space
  source), 3x3, money keys bottom row. `popupIndexAt` takes the choices
  list — never re-couple it to `PUNCTUATION_POPUP.size`.
