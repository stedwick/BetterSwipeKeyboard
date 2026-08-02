# Swipe alternates strip — reference

Read this before touching `ui/keyboard/StripCells.kt`,
`ui/keyboard/SwipeAlternatesStrip.kt`, `swipe/SwipeAlternates.kt`, or the
swipe commit path in `KeyboardScreen.kt`. `AGENTS.md` carries the core
invariants; this file carries the geometry and the full mechanics.

## Geometry and placement

- Always-visible row between utility row and content, on EVERY surface
  (blank on panels/voice) so total height is static: `UtilityRowHeight +
  6 + AlternatesStripHeight (40.dp) + 6 + KeyboardContentHeight` = 322.dp
  (`ui/keyboard/SwipeAlternatesStrip.kt`). Empty: gray italic
  "Alternatives will appear here".
- ONE shared pure placement rule for all three states (live, failed,
  committed) so finger-up only recolors the center, never rearranges the
  row (Philip's rule): `centeredCells` (`ui/keyboard/StripCells.kt`)
  behind `stripCells`/`failedOfferCells`/`liveOfferCells` — center
  middle, best runner-up nearest center, alternating sides by rank:
  `[a3, a1, CENTER, a2, a4]`. Count tracks width
  (`alternateCountForWidth`: 2 below 600.dp, 4 at/above). `KeyboardScreen`
  computes the cell list once, shared by rendering and hit-testing.

## Full mechanics

- Committed state: the commit site passes two runner-up lists on
  `CommitWord` (both CAPS-TRANSFORMED at commit time, because one-shot
  shift is already consumed by tap time and caps cannot be re-derived
  later; both with exactly the `lastCommitWasSwipe` lifetime — every
  action that clears the flag clears the strip, and voice transitions and
  fresh field starts clear it too):
  - `alternates` = `swipeAlternates(results)` (pure,
    `swipe/SwipeAlternates.kt` — drops top-1, rejects runners-up ≥
    `MAX_COMMIT_SCORE`, caps at 4, i.e. the decoder's topN=5 minus the
    commit) → `KeyboardState.swipeAlternates`.
  - `stripOffers` = the WIDER near-miss-band runner-up list (top-1
    excluded) the live strip showed → `KeyboardState.swipeStripOffers`.
  `stripCells` places THE WIDE list so every survivor keeps its mid-swipe
  slot; offers missing from the narrow `swipeAlternates` (score between
  `MAX_COMMIT_SCORE` and the near-miss band — they show mid-swipe but are
  commit-gate junk post-commit) render as invisible, untappable
  `isPlaceholder` cells reserving their slots, never as a re-lay-out.
- The strip lives INSIDE the gesture surface like the utility row: cells
  are purely visual, register rects, and taps are re-dispatched in the
  gesture loop as `KeyboardAction.SelectAlternate` (a clickable child
  would be swallowed by the container `pointerInput`). The reduction is
  guarded by `lastCommitWasSwipe` (a stale strip never deletes text) and
  RE-ARMS the flag, so chained swaps and word-delete on the replacement
  work; the tapped word moves into `swipedWord` (the strip's center) and
  leaves the alternates — the replaced-away old word disappears (no
  swap-back, Philip's call). The service applies
  `KeyboardEffect.ReplaceSwipedWord` as `deleteWordBackward()` +
  `commitWord()`, so leading-space rules reapply and the text ends up
  exactly as if the alternate had been swiped. The replacement has no
  trail: nothing new enters `SwipedWordLog`, and the replaced word's
  entry invalidates itself at proofread reconciliation. In a COMMITTED
  strip, the green center and the invisible band-mismatch placeholders
  are the only untappable cells.
- FAILED swipe (nothing committed): when top-1 sits in the near-miss band
  (`< NEAR_MISS_OFFER_MAX_SCORE` 3.2 — measured on the six fixture sets
  plus the 14 captured keyboard trails: 2/2 rescue, 3/274 impostors, KDoc
  table in `swipe/SwipeAlternates.kt`), the decode branch emits
  `KeyboardAction.OfferFailedSwipe(offers, letters)` with
  `failedSwipeOffers(results, maxOffers)` (top-1 INCLUDED, capped at the
  width-adaptive cell count; empty band → no action → placeholder) and
  the trail's crossed letters. The reduction stores them in
  `KeyboardState.failedSwipe`, clears the swipedWord/swipeAlternates pair
  AS A PAIR (a stale green center among the offers would lie), keeps
  `lastCommitWasSwipe` untouched (the gesture committed nothing — the
  last COMMIT still owns the word-delete) and consumes no one-shot shift.
  `failedSwipe` is cleared everywhere the pair is cleared, plus by
  CommitWord. The strip's single computation site prefers
  `failedOfferCells(offers, maxAlternates)` (top-1 in the CENTER slot,
  PLAIN — no green, no blue: nothing was committed and nothing would
  auto-commit — flanked by the rest in the same shared layout, so
  finger-up never rearranges the row) over `stripCells(...)`; unlike a
  committed strip's green center (tap = no-op), the failed strip's center
  slot IS tappable. The red failed-swipe flash still fires (the yellow
  low-confidence flash is disjoint: commit-only), and offer cells render
  lowercase even under armed shift — caps applies at commit time.
- An offer tap is re-dispatched failed-first in the gesture loop as
  `KeyboardAction.CommitWord(picked, letters, offers - picked,
  stripOffers = offers - picked)`: the normal commit path supplies
  leading-space rules, caps (consuming the still-armed one-shot shift),
  word-delete arming, the green-center strip with the remaining offers IN
  THE SAME SLOTS (all offers are in-band, so the wide list IS the
  remaining offers), and SwipedWordLog recording with the failed trail's
  path evidence. After the tap the strip looks exactly like a decoder
  commit.
- LIVE suggestions while swiping: the trail-append loop fires throttled
  background decodes of the trimmed trail-so-far (pure gate
  `shouldRunLiveDecode` in `swipe/LiveDecodeThrottle.kt` — ≥10 trail
  points, ≥120 ms and ≥6 new points since the last decode, tuning
  starting points; skip while a decode is still running, never preempt).
  The decode runs on `Dispatchers.Default` from the composable's existing
  `rememberCoroutineScope` (the fade-job scope), and a generation counter
  (`liveGen`, bumped by every new decode and by gesture-end teardown,
  which also cancels the job) drops stale results. Results land in
  `liveOffers` (`LiveOffers(words, leaderWouldCommit)` in
  `ui/keyboard/StripCells.kt`) — TRANSIENT Compose state like
  `trailPoints`, never `KeyboardState`, so 8 Hz updates cause no reducer
  churn. The words reuse `failedSwipeOffers` (the 3.2 near-miss band,
  capped at maxAlternates + 1: top-1 center plus the flanks);
  `leaderWouldCommit` is top-1's score < `MAX_COMMIT_SCORE` — the honest
  rule: light blue only for what a finger-up would commit right now
  (`isLiveLeader` on the CENTER cell, rendered `LiveLeaderBlue`
  0xFF0A84FF bold in `SwipeAlternatesStrip`, never green — green stays
  reserved for the committed center; a top-1 that would not commit
  renders plain, same slot). `altCells` prefers failedSwipe → liveOffers
  → the commit strip; `liveOffers` is cleared at every gesture end.
  CANCELED-swipe persistence: when the final decode's near-miss band is
  empty but live offers exist, the FAILED branch emits
  `OfferFailedSwipe(liveOffers.words, letters)` through the existing
  path, so a canceled swipe's last suggestions stay tappable — WITHOUT
  the blue (`failedOfferCells` never sets `isLiveLeader`: after finger-up
  nothing auto-commits, so the mark would lie).

## Tap-typing word mirror

- Two tiers BELOW the swipe tiers in `altCells` (KeyboardScreen.kt; swipe
  wins belt-and-braces — the reducer clears the tap fields on every swipe
  reduction): `state.tapLiveWord` renders as a lone blue center via
  `liveOfferCells(LiveOffers(listOf(it), leaderWouldCommit = true))` — the
  leader flag is borrowed PURELY for the `LiveLeaderBlue` rendering, nothing
  commits on finger-up in the tap flow — and `state.tappedWord` as a lone
  green center via `stripCells(it, emptyList(), emptyList(), …)`. No flanks,
  ever.
- Field truth, not reducer memory: pure extractors in `TapWord.kt`
  (package root) — `currentWordPrefix` (trailing run of Unicode letters +
  `'`) and `tappedWordBeforeBoundary` (the word behind a trailing boundary
  run, never crossing `\n`). The service's `refreshTapStrip()` reads
  `textBeforeCursor(TAP_STRIP_CHARS = 48)` and calls
  `viewModel.setTapStrip(live, committed)` (exactly one non-null; both null
  clears) at the END of the CommitText / DeleteBackward / DeleteWordBackward
  effect branches ONLY — never after PasteText (verbatim) or swipe commits
  (swipe owns the strip). The InsertText/Backspace REDUCTIONS deliberately
  don't touch the tap fields (the hook overwrites from field truth anyway);
  `clearSwipeFlag` must not either (it runs mid-reduction, before the field
  read — and fires for DeleteClip, which changes no text).
- Display-only invariants: VERBATIM field text (caps as typed, no
  mirrorCaps, no dictionary, no completions). Cells are untappable: the
  green center by construction (`isCenter`), the blue center because its
  `SelectAlternate` dispatch dies on the reduction's `lastCommitWasSwipe`
  guard. Enter clears in the reduction (newline = hard boundary, no field
  read follows PerformEnter); MoveCursor, SwitchLayout, PasteClip,
  setVoiceState and clearSwipeAlternates (field start) clear too.
