# Environment & platform quirks — reference

Read this when working on the emulator/adb workflow, hitting API-36
platform behavior, or recording swipe trails. These are hard-won
environment facts; they rarely change.

## Android API 36 + gesture-handling gotchas

- `InputMethodService.onShowSoftInput` is gone on API 36 — the show path
  is `onShowInputRequested`.
- `onEvaluateInputViewShown()` must return `true` — its default hides the
  IME when a hardware keyboard is attached (emulators emulate one), and
  the IME *silently never appears*.
- After move events are consumed, finger-up can arrive consumed too: exit
  gesture loops on `!change.pressed`, not `changedToUp()`.
- The restricted `AwaitPointerEventScope` forbids `coroutineScope`/
  `launch`; use `withTimeoutOrNull` loops for timers (see backspace
  repeat).
- The IME window doesn't reliably pad for the system nav/IME strip:
  service calls `WindowCompat.setDecorFitsSystemWindows(window, false)`
  and measures the real inset via a `ViewCompat` listener
  (`max(navigationBars, tappableElement, mandatorySystemGestures)` →
  `ime/BottomInsets.kt` `bottomClearancePx`) passed to `KeyboardScreen`
  as `bottomClearance`, plus fixed 4dp `KeyboardBottomClearance`. The
  listener MUST be on the window's **decor view** (+ `requestApplyInsets`)
  — WindowInsets don't dispatch down to the input view.
- Compose modifier order decides what `imePadding()` pads: AFTER
  `verticalScroll` it extends scroll range; BEFORE it shrinks the
  viewport (what a setup screen wants). Neither auto-scrolls a focused
  field above the IME — trigger `bringIntoView` once the `ime()` inset
  has settled (see `MainActivity`).

## adb/emulator workflow

- The emulator's IME falls back to GBoard after reinstalls/uimode
  changes/force-stops. Re-run `adb shell ime set
  com.example.betterswipekeyboard/.SwipeKeyboardService`.
- After emulator boot, dismiss the "System UI isn't responding" dialog
  (tap Wait) before driving the UI.
- `~/Library/Android/sdk` has `cmdline-tools`. AVDs:
  `Medium_Phone_API_36.0` (daily driver), `Pixel_9_Pro_API_36` (AICore
  confirmed absent on emulators).
- `adb shell input swipe` only draws straight lines, no hold-then-drag —
  the punctuation popup's drag-select needs a real finger. GBoard's
  stylus toolbar appearing instead of our keyboard means the IME fell
  back.
- Voice input needs a Play services image and "host microphone" enabled;
  `isRecognitionAvailable` is false on bare images (→ UNAVAILABLE panel).
  Real dictation and the ML Kit `InputType.VOICE` proofread can only be
  verified on a real device.
- Debug builds can record swipe trails: toggle "Record swipe trails" in
  the setup screen (debug-only, off by default, local only —
  `swipe/SwipeTrailCapture.kt`), then `adb pull
  /sdcard/Android/data/com.example.betterswipekeyboard/files/swipe_trails.jsonl`
  (external storage — platform-tools 37 removed `adb run-as`). Each line
  is one swipe: key geometry, timed trail points, decoder top-5.
