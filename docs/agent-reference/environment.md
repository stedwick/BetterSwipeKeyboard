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

- `adb` is NOT on PATH in fresh shells: it lives at
  `$sdk.dir/platform-tools/adb` (`~/Library/Android/sdk/platform-tools/adb`);
  the emulator binary is `~/Library/Android/sdk/emulator/emulator`
  (`-avd Medium_Phone_API_36.0`). Never pipe the emulator's stdout
  through `head` — SIGPIPE kills it on its next write; redirect to a
  log file instead.
- A fresh emulator boot shows a BLACK SCREEN — the device is simply
  asleep (`dumpsys power` → `mWakefulness=Asleep`). Verified wake
  procedure (cold-boot proven: `sys.boot_completed=1` + Asleep + black
  screencap → Awake + home screen after these):
  `adb shell input keyevent KEYCODE_WAKEUP` (screen on), then
  `adb shell input keyevent 82` (MENU — dismisses the keyguard when one
  is shown), then `adb shell input keyevent KEYCODE_BACK` (82 on the
  already-unlocked launcher opens its wallpaper/home-settings menu;
  BACK closes it and is a no-op otherwise). Verify with
  `adb exec-out screencap -p > /tmp/x.png`.
- `adb emu kill` is a HARD power-off: it once left PackageManager with
  the keyboard's package installed but its service declaration lost
  (`ime set` answers "Unknown input method";
  `cmd package query-services -a android.view.InputMethod` lists only
  GBoard). Recovery: `./gradlew installDebug` again, then `ime enable` +
  `ime set`. Prefer the emulator UI's power button for shutdown.
- The emulator's IME falls back to GBoard after reinstalls/uimode
  changes/force-stops. Re-run `adb shell ime set
  com.philpdx.keyboard/com.example.betterswipekeyboard.SwipeKeyboardService`
  (the package slot follows the applicationId, but the class name must
  be the FULL code-package class —
  `com.philpdx.keyboard/.SwipeKeyboardService` expands the dot against
  the applicationId and fails with "Unknown input method").
- Emulator stuck on a GRAY screen with adb showing `offline` and QEMU
  near-zero CPU right after "Loading snapshot 'default_boot'": the
  boot snapshot is corrupt. Kill the QEMU process and relaunch with
  `-no-snapshot-load` (cold boot); verified fix 2026-08.
- `am instrument` KILLS the target app's foreground activity when the
  session starts (and again when it ends) — injected gestures must
  re-open the target screen in a startup-delay gap, or they land on
  the home screen.
- Mid-swipe store screenshots: `SwipePoseInjector` (androidTest,
  debug-only) injects a scripted curved trail via UiAutomation and
  parks the finger (`-e path 'x,y;...' -e delayMs -e moveMs -e holdMs
  -e postMs`). Recipe + compositor scripts: `store-assets/`
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
  /sdcard/Android/data/com.philpdx.keyboard/files/swipe_trails.jsonl`
  (external storage — platform-tools 37 removed `adb run-as`). Each line
  is one swipe: key geometry, timed trail points, decoder top-5.
