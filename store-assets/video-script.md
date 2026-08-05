# Better Swipe Keyboard — Store Listing Video Script

Target length: ~30 seconds. Screen recordings from the emulator (the
mid-swipe captures in `screenshots/` double as shot references). Record
in light mode, portrait, on the phone emulator. No voiceover needed —
text overlays carry it. Upload to YouTube as **unlisted**, ads off, not
age restricted, then paste the URL into Play Console.

## Shots

| Time | Visual | Overlay text |
|---|---|---|
| 0–3 s | Title card: app icon on dark navy background | "Your keyboard should keep up with you." |
| 3–9 s | Screen recording: fast swipe typing, trail visible, word lands mid-sentence | "Swipe. It's accurate." |
| 9–13 s | Close on the alternates strip: committed word in green, tap an alternate to swap it in | "Didn't mean that? One tap fixes it." |
| 13–18 s | Voice dictation into the test field (mic key → panel → dictated sentence appears) | "Speak naturally." |
| 18–24 s | AI proofread pass fixes a garbled sentence 2 s after typing stops | "AI proofreading — on-device by default." |
| 24–28 s | Quick cuts (~1 s each): emoji panel, clipboard panel, dark theme swipe | — |
| 28–32 s | End card: logo | "Better Swipe Keyboard — private by design." |

## Recording notes

- The keyboard hides the "System UI isn't responding" dialog: dismiss it
  (tap Wait) before recording.
- The emulator falls back to GBoard after reinstalls — re-run
  `adb shell ime set com.philpdx.keyboard/com.example.betterswipekeyboard.SwipeKeyboardService`
  before every take.
- For the swipe shots, a real finger (or the SwipePoseInjector
  instrumented test) is needed — `adb shell input swipe` only draws
  straight lines.
- Grainy emulator recordings read fine at 1080×2400; no device frame
  needed for Play.
