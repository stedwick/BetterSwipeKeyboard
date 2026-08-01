# Swipe decoder — reference

Read this before touching `swipe/`, the dictionary generator
(`tools/generate_words_en.py`), or anything decoder-related. The term
list summary and the tuning guardrails live in `AGENTS.md` ("Swipe
decoding"); this file carries the full mechanics. Calibration
measurements, miss autopsies and rejected levers:
`docs/decoder-investigation.md` (the decoder's engineering log).

## Scoring internals

Three ORDERED geometric terms make the word's ideal key-to-key path
explain the trail in sequence (this is what separates same-start/end
words like "my" vs "mummy"):

1. Ordered letter alignment: each letter matches at the minimum of the
   FIRST approach basin after the previous letter's match
   (`LETTER_DEPART_KEYS` 0.5 ends a basin). Never a global argmin —
   jitter decides which of two visits to the same key wins, and a stolen
   match cascades every following letter off the trail ("follow"
   regressed to "flow" until first-basin matching). Crossed letters
   ("swipe"'s i) match cheaply on the passing trail. The LAST letter
   alone may re-match at a later basin — the one still open at lift-off,
   within `REBASIN_RADIUS_KEYS` 0.8 — because no letter follows it to
   cascade: this fixes the overshoot-and-return signature (the finger
   passes the final key, comes back to it, and first-basin matching could
   never see the return visit; 7 of 14 captured "keyboard" swipes, 12/14
   now commit). Gates measured on the captured sets: a basin must have
   closed (depart-and-return), the re-match must beat the stock match,
   and the radius holds — ungated re-matching let impostors claim foreign
   end-keys.
2. Line conformance (SHARK2's tunnel): trail points between two matched
   letters must follow the key-to-key segment; free inside 0.5
   key-widths, linear to a 2.0 saturation cap, hard cull at 1.75
   key-widths (FUTO's legacy decoder). A correctly traced word scores
   ~zero at ANY trail length — that is why no trail-length gate exists.
3. Backtrack penalty: trail steps opposing the current leg's direction
   cost their length — a zigzag word's reversal leg (M→U→M) on a
   straight trail.

Plus: salient points (high curvature or low speed) mark deliberate
motion; an LCS alignment between salient keys and the word, a
trail-vs-ideal path-length term (Swype's per-word "expected path
length"), a unigram frequency prior, and a small per-letter length bonus
(FUTO's β·L). A dwell ≥ 300 ms on a key doubles its letter.

Salient evidence is graded before it can charge: a mid-trail region
dominated by SLOWNESS (not curvature) counts as a deliberate key visit
only if the finger lingered ≥ 60 ms (a slight slowdown over a crossed key
is aim noise — "dog" hesitating over F must not become "fog"); endpoint
regions are anchored to the actual first/last trail point (their
hardcoded 0.5 salience is evidence-free, so the distance term skips the
salience multiplier there); an ISOLATED lift-off region — no measured
salience reaches the last point, i.e. the finger lifted mid-flight
without decelerating — emits no key at all, so a drift endpoint's nearest
key ("dough"'s h, "we're"'s r) can't charge the intended word a missed
salient it never earned (touch-down keeps its anchor unconditionally:
the finger starts at rest on an aimed key); and words whose first letter
matches mid-trail pay an unexplained-head charge mirroring the tail term
(0.5kw free — touch-down aim is much better than lift-off aim).

The LAST letter also pays an end-key surcharge
(`END_KEY_SURCHARGE_WEIGHT` 0.5): its match distance beyond the tunnel
radius, charged AGAIN undiluted, after the lift-off re-match — the
per-letter mean shrugs an unvisited neighbor of the visited end key off
to ~0.2 ("help"'s p next to "hello"'s o), and the frequency prior then
decides the word (rank 163 vs 1905 = a constant +0.68 for help; 6/13
captured hello trails committed "help"). Measured: 10/13 hello, fixture
floors held 13/32/34/60/62/36 at w=0.4-0.7; the binding constraint is
set5 dog#8 (re-matched g at 0.76kw — margin 0.072 at 0.5, flips at 0.8),
and the re-match tension (re-match licenses ≤0.8kw, surcharge charges
past 0.5kw) is deliberate.

The FIRST letter pays the mirror image: a start-key surcharge
(`START_KEY_SURCHARGE_WEIGHT` 0.7) — its touch-down match distance beyond
the tunnel radius, charged AGAIN undiluted. The head term cannot see this
miss (first letter matched at trail index 0 ⇒ head arc is 0), the
per-letter mean shrugs an unvisited NEIGHBOR of the touched start key off
to ~0.2-0.5 ("to"'s t next to "go"'s g, never approached closer than
0.73kw), and the frequency prior then decides (rank 2 vs 96 = a constant
+1.06 for "to"; 6/10 captured go trails committed "to"). Measured: five
of the six go losses fixed (set7 18 → 23/24; the residual's t basin is
only 0.73kw off — its 0.23kw excess can't beat a 0.272 margin short of
w≈1.2, unreachable). The signed-off cost (Philip, 2026-08): the two q/w
touch-down aim slips flip quick→wick (set4#54, set5#52 — the trail
physically starts ON the W key, 0.09/0.29kw from its center, so "wick" is
the honest read and no weight separates the pair). No start-side re-match
tension: the first letter's scan starts at index 0 and fully explores the
touch-down basin, so the license/charge tension the end side documents
has no start-side counterpart.

Two-tier feedback flash (pure classification in
`swipe/SwipeConfidence.kt`, jQuery-highlight-style fade over ~400 ms,
purely cosmetic): a FAILED swipe (no candidate below the cutoff, nothing
committed) flashes the trail RED (`FailedSwipeFlash`); a commit with a
close runner-up (top2−top1 margin < `LOW_CONFIDENCE_MARGIN` 0.25,
calibrated on the six captured trail sets — flags 8/18 wrong commits at
3.8% false positives; recalibrated after the re-match (wrong pool 20→17),
again after the end-key surcharge (17→16: the signed-off lazy→last wrong
commit set2#35 was pushed past `MAX_COMMIT_SCORE` into silence), and again
after the start-key surcharge (16→18: the two signed-off quick→wick flips
joined the wrong pool, one of them flagged; correct-commit flags 14→9 —
four margins widened past 0.25, the two quicks left the correct pool, one
new flag — denominator changes throughout, not flag-rate changes)) flashes YELLOW
(`LowConfidenceFlash`) as "maybe re-swipe"; confident commits flash
nothing. Segment alpha in `ui/keyboard/TrailFade.kt`.

## Dictionary and word list

- `Dictionary`: frequency-ordered list from
  `app/src/main/assets/words_en.txt` (`word<TAB>rank`, ~55k words, lower
  rank = more frequent), indexed by first letter. Generated by
  `tools/generate_words_en.py` from **wordfreq v3** (~2021 multi-corpus
  snapshot; top 60k filtered to `^[a-z]{2,}$` plus one-apostrophe
  `^[a-z]+'[a-z]+$`, minus vowel-less 3+ letter abbreviation junk — 'y'
  counts as a vowel). License **CC BY-SA 4.0**: attribution in the
  asset's comment header (skipped by `Dictionary.load`), repo-root
  `NOTICE`, and a setup-screen credit — keep all three. Manual supplement
  (keyboard vocabulary like "swipe") merged by the generator's
  `SUPPLEMENT` at each word's wordfreq rank. Edit the generator and
  regenerate — never hand-edit `words_en.txt`.
- Apostrophe words are swipeable (~1.75k, exactly ONE apostrophe between
  letters, so first()/last() is always a letter). Decoder matches LETTERS
  ONLY — `swipeLetters(word)` (`swipe/WordLetters.kt`) strips the
  apostrophe: zero geometry contribution, but the letter count feeds
  every per-letter mean — and commits the apostrophe VERBATIM. Frequency
  is the only tie-breaker between same-letter candidates: m-o-t-h-e-r-s →
  "mother's", i-t-s → "it's" > "its", "dogs" > "dog's". Philip signed off
  these arbitrations.
- Junk-class filter (generator's second source: **SCOWL**
  english/american-words + proper-names): two word-CLASS rules, never a
  rank cutoff (rank-adjacent keepers prove no threshold works):
  - **Rare proper names**: in SCOWL names ∧ NOT in SCOWL words ∧ zipf <
    2.8 — drops surname junk while keeping "siri"/"alexa"/"jose".
    Apostrophe tokens EXEMPT (a possessive's letters must match the trail
    in order, so frequency crushes them whenever geometry coincides).
  - **Nonce respellings**: len ≥ 4, not a SCOWL word, zipf < 3.1, and a
    same-length ONE-SUBSTITUTION neighbor in SCOWL words with zipf ≥ word
    + 2.0 ("krazy"→"crazy"). Substitution-only is deliberate (insertion/
    deletion neighbors would kill "json"→"son"). `KEEP_EXCEPTIONS` saves
    mandated modern words ("cron", "vimeo", "binance", "yeet", "thanos").
  - Known survivors: "doh", "dix", "folic" — decoder-side territory, not
    dictionary. wordfreq case-folds, so capitalization is NOT an
    available name signal (measured, dead end).
- Custom user words merge via `Dictionary.withCustomWords` at rank 1
  (geometry still dominates); parsed by `parseCustomWords` (split on any
  non-letter run, apostrophe between letters stays intra-word, hyphens
  break), stored newline-joined in SharedPreferences by `CustomWordStore`.
  Service rebuilds the decoder in `onStartInputView` on change;
  `KeyboardScreen` gets a `decoderProvider` read at gesture time. Custom
  words affect swipe decoding only.

## crossedLetters

`crossedLetters` (`swipe/CrossedLetters.kt`, pure): ordered letter keys a
trail crossed — nearest key center per point (Voronoi, NO radius gate —
measured: gates lose exactly the endpoint letters that matter), order
preserved, consecutive repeats collapsed. Proofreader context, NOT
decoder input; independent of `SwipeDecoder`'s alignment. Ratchet-tested
in `CrossedLettersRealTrailTest`.
