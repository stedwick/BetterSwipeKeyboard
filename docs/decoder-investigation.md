# Endpoint salience — investigation report

Branch `investig/endpoint-salience` off `acea225`. Investigator: GEOMETRY agent.
Deliverable: options analysis for the two salience failure classes Philip
reported. No production code was changed (all experiments scratch, reverted;
validation runs done against scratch patches and reverted).

## Method (so the numbers are trustworthy)

- Baseline at `acea225`: full suite **178/178 green**; real-trail harness
  **set1 = 5/17, set2 = 26/36** committed-correct.
- I ported `SwipeDecoder` 1:1 to Python (same op order) and verified it
  reproduces the Kotlin decoder's top-5 words and scores **on all 53 trails
  exactly** (both committed counts, every rank). All mechanism screening was
  done in the port (~1 s per full replay); every candidate package was then
  re-validated by scratch-patching the real `SwipeDecoder.kt` and running the
  full 178-test suite. Kotlin numbers below are authoritative; where they
  differ from the port in trivia (Float vs double), Kotlin wins.
- Per-salient-region audit across all 53 trails (curvature vs slowness at
  each region peak, dwell, endpoint-touching, in-intended-word or not) is the
  evidence base for every claim below.

## Anatomy of the two failure classes (measured, not hypothesized)

### Class 1 — mid-trail salient false positive: #17 `dog`→`fog`

Trail #17 (set2): touch-down 0.14kw from D, lift-off 0.64kw from G. The
salient sequence is `fg`:

- region `[0..1]`, peak at index 1, 0.36kw from F, salience 0.59, **dwell
  0 ms**, t=84 ms into a 343 ms swipe. The region's salience is **pure
  slowness from acceleration** — a finger at rest at touch-down is slow by
  physics for the first ~0.5kw of any swipe, and the slowness baseline is the
  swipe's own average speed, so the acceleration phase always reads
  "deliberate". Non-max suppression then collapses the region to its speed
  minimum — which had drifted 0.9kw off the touch-down key onto F.
- Effect: `dog` pays MISSED_SALIENT +0.30 for `f` and gets alignment 1/3;
  `fog` gets 2/3. Baseline: **fog 1.158, dog 1.431** — dog actually wins
  geometry (dist 0.902 vs 1.072) and frequency (−0.137 vs −0.062) and loses
  purely on this artifact (0.57 of salience terms).

Same mechanism, three more instances: accel-phase slowness regions with
dwell ≈ 0 marking `g` (set2 #30 fox→folic), `h` (set1 #12 jumps→humps), `w`
(set1 #9 quick→wick). Audit total: 4 false vs ~8 true accel-phase regions —
indistinguishable by position, only by *evidence* (dwell).

### Class 2 — endpoint hardcode: `salience[0] = salience[n-1] = 0.5`

Audit across 53 trails: ~38 hardcode-only endpoint regions (lone endpoint
point, curvature 0, slowness 0, dwell 0 — zero behavioral evidence). Roughly
**15 land on a key NOT in the intended word**, and *every currently-failing
trail carries at least one*:

- set2 #36 `dog`→`doh`: salient `doh`; `h` is hardcode-only, lift-off 0.44kw
  from H vs 1.07kw from G. Baseline **doh 0.140, dough 0.580, dog 0.960**
  (dog pays +0.30 missed, alignment 2/3 vs doh's 3/3, and its G match pays
  1.07kw ×2 from the endpoint salience multiplier).
- set1 #16 `dog`→`doh`: salient just `h` (hardcode-only, 0.35kw). **doh
  0.873, dog 1.764.**
- set2 #14/#32 `over`→`overt`: salient `ovet`; `t` hardcode-only (lift-off
  0.25/0.30kw from T vs 1.1/1.3kw from R). **overt 0.091 / −0.471, over
  0.257 / −0.237** — over's frequency edge (rank 83 vs 18875) is only 0.17.
- set2 #34 `the`→`three`: salient `tr`; `r` hardcode-only (0.64kw). **three
  0.905, the 0.968** — the (rank 1!) loses by 0.06.

Note the brief's "dough" is actually **"doh"** in the current decoder (rank
29925, wins both dog trails); dough is #3. The analysis is unaffected — both
contain `h`, neither pays for it.

### Why the backtrack penalty doesn't catch `dough` (Philip's question)

`dough`'s matches on #36 are `[2, 19, 29, 32, 32]` — D, O, U, then **G and H
both clamp to the same final trail point** (the trail ends at x=521; G's
center is x=444, the trail never reaches G's column). The reversal leg G→H
therefore contains **zero trail steps** and accumulates zero backtrack,
while the trail's actual leftward drift toward H is consumed by the U→G leg,
whose direction it *matches* (backtrack only punishes opposed motion).
Measured: dough's backtrack (0.203) is even *lower* than dog's (0.251). And
`doh` needs no zigzag at all — its only required turn is at O, which the
trail genuinely performs. Philip's zigzag intuition is sound for `dough`;
the live impostors just don't need zigzags.

### Historical `the`→`thr` (set1 #8, currently →`three` 1.259 vs 1.814)

Different disease: salient `tgr`, where `g` is a **mid-trail curvature**
region (curv 0.93, dwell 50 ms) — a genuine direction change over a crossed
key (corner cut). None of the viable options below touch it without killing
real turns; same class as set2 #13 `jumps`→`juniors` (curv `n`) and set2 #35
`lazy`→`lifesaver` (curv `s`). Listed as future work, not solved here.

## The guards' dependence on the hardcode (why naive B breaks them)

Probe of the synthetic guard trails (reflection into the private methods):

- `trailThrough` trails are constant-speed: salience is 0 everywhere except
  curvature spikes at turn waypoints; **the only endpoint salience is the
  hardcode itself**. Realistic trails: tail salience ≤ 0.25 except the
  hardcoded 0.5. So synthetic trails produce *zero natural endpoint salient
  keys* — the endpoint keys enter the salient sequence solely via the
  hardcode.
- The hardcode does real protective work there: `hell` pays +0.30 for
  missing `o` (hello 0.036 vs hell 0.378), `wipe` pays +0.30 for missing `s`
  (swipe −0.787 vs wipe −0.426), `umpire` pays +0.30 for missing `j` (jumps
  −0.222 vs umpire 0.091).
- Naive deletion (scratch X1, Kotlin-measured) breaks **4 test methods +
  the set2 ratchet**: `hello`→`hell` (×2 classes), `swipe`→`wipe`,
  `jumpw`→`umpire`, set2 26→25. (The brief said 3 guards and "+2 on set2
  (25→27)"; at acea225 I measure set1 5→7, set2 26→**25**. Different base,
  presumably pre-wordfreq — flagging the discrepancy.)

Root cause the guards expose: the decoder has an **unexplained-head
asymmetry** — the existing tail term charges trail arc *after* the last
letter's match, but arc *before the first letter's match is free*. `wipe` on
an S→W→P→E trail ignores the entire S opening and pays nothing for it; the
endpoint missed-charge was the only thing plugging that hole.

## Options

Scored by committed-correct on the 53 real trails (baseline 31/53 = set1 5 +
set2 26) and by full-suite guard status (Kotlin).

### Option A — keep the endpoint anchor (status quo) — 31/53

Keep `salience[0]=salience[n-1]=0.5` as-is.

- Mechanism: none.
- Costs measured: ~15/53 trails carry a false hardcode endpoint key; every
  current error trail has one. Also the endpoint salience value *doubles* the
  distance of the last letter's match (SALIENCE_WEIGHT 2×), so `dog`'s G at
  1.07kw pays 2.14kw — the same measurement noise the KDoc elsewhere says
  must not be charged.
- Keeps: guard protection vs wipe/umpire/hell (0.3–0.6 margins).
- Verdict: the anchor's *presence* is doing real work; its *evidence-free
  attribution and double-charging* is the disease. Status quo is not
  defensible now that the anatomy is known.

### Option B — endpoint evidence redesign (the brief's B, refined) — two variants

**B-naive (delete the hardcode): 32/53, 4 guard fails + ratchet fail.**
Covered above; unfixable in this form because synthetic trails have no
natural endpoint evidence at all.

**B1 — evidence-graded endpoints ("G-final"): 36/53, 3 guard fails (see B3
below for the resolution of those fails).**

1. Endpoint-touching regions (from==0 or to==n−1) keep LCS credit but are
   **exempt from the MISSED_SALIENT charge** — lift-off/touch-down drift is
   not deliberate evidence against a word.
2. Endpoint regions are **re-anchored**: key taken at the trail endpoint
   point (index 0 / n−1), never at the region's salience peak (kills #17's
   F→D misattribution at the source).
3. Mid-trail regions whose peak is **slowness-dominated require dwell ≥
   60 ms** (curvature regions exempt; ≥300 ms dwell still doubles letters) —
   Philip's "slow down slightly over F shouldn't count" rule, generalized.
4. The **salience multiplier does not apply at endpoint match indices** —
   the endpoint key is already salient; doubling its distance double-counts
   known-noisy measurement.

Measured (Kotlin): set1 5→7, set2 26→29. Fixes: over×2, the#34, fox#30,
very#1, lazy#15. Dog gaps shrink to honest residue: #17 fog dead (1.199)
but `doug` ties dog 0.594/0.594; #36 doh −0.008 vs dog 0.143 (was 0.82
gap); set1#16 doh 0.710 vs dog 0.917 (was 0.89). Losses: set2 #31
jumps→nimitz (baseline margin was 0.01; nimitz rank 55206 gets its missing
endpoint keys excused).
Guard fails (Kotlin): `swipe`→`wipe` (margin 0.028), `jumpw`→`umpire`
(0.27), custom-words swipe — the exemption removes the endpoint charge that
was plugging the unexplained-head hole (see B3).

**B2 — trust-radius endpoints ("V+gate"): 35/53, ALL 178 GREEN.**

Like B1 but instead of the miss exemption: a **zero-evidence endpoint
region** (lone hardcoded point) whose attributed key is **>0.35kw from the
endpoint point is dropped entirely** (no LCS, no missed charge); inside the
radius everything behaves exactly as today. Plus re-anchoring and the
mid-trail dwell gate.

Measured (Kotlin): set1 5→7, set2 26→28, **zero trails lost, zero guard
impact** — guards' endpoint keys sit at ~0.0kw, so they are untouched by
construction. Fixes: very#1, lazy#15, fox#30, the#34. Does **not** fix:
over×2 (lift-offs 0.25–0.30kw onto T are *inside* the trust radius —
genuinely ambiguous), dog cases (doh wins on honest geometry: 0.407 vs
0.660 on #36). This is the conservative bookend: safe, but leaves the two
headline error classes half-fixed.

**B3 — B1 + unexplained-head charge ("G-final+head"): 38/53, ALL 178 GREEN
— the recommended package, detailed below.**

### Option C (new) — mid-trail salient evidence alone — 30/53

The dwell gate (B1.3) as the only change: set1 6, set2 24 — kills genuine
in-word slow passes (real intended turns read as slowness with sub-60ms
dwell when the finger rounds a corner at speed). Valuable as a component,
not standalone. Endpoint-scoped (mid-trail only) it loses nothing measured.

### Option D (new) — zigzag / direction-change requirement — REJECTED with data

Implemented Philip's idea as a per-word term: for each interior letter whose
ideal path turns ≥54°, require trail curvature ≥ 0.6× that angle near the
letter's match, else charge 0.8×(missing angle fraction). Result: **set1 4,
set2 25** — it punishes the *intended* words: `mother`→`mortimer` (mother
charged ~1.0 for rounding the T and H corners), `just`→`kisser`, `jumps`
charged for rounding at P. Real users round corners below the evidence bar —
that is exactly why the tunnel exists. A narrowed variant ("unperformed
leg": two consecutive letters sharing one match index with keys ≥0.8kw
apart pay keyDist×w) fires only on dough-style clamps — but dough is #3,
not the winner; `doh` and `overt` need no reversal, so the term touches none
of the live errors. Rejected; the degenerate-leg diagnosis above stands as
the answer to "why doesn't backtrack catch dough".

### Option E (new) — endpoint distance treatment — component only

- **Noise-floor cap** (first/last letter matched within 0.5kw arc of a
  clamp pays at most 0.6kw): measured **idle** on committed counts in every
  package. Also has a dark side: at the first letter it under-charges
  genuinely-wrong keys (let `nimitz`'s N in 1.38kw off the touch-down).
  Not recommended.
- **No salience-doubling at endpoint indices** (B1.4): package-dependent —
  +2 trails in the B1 line, −1 in the B2 line (helps where misses are
  excused, hurts where charges are kept). Keep it inside B1 only.

### Option F (new) — unexplained-head charge — the missing mirror term

Charge arc length **before** the first letter's match beyond a free slack
(`HEAD_ARC_FREE_KEYS` 0.5kw, weight 1.0), mirroring the existing
unexplained-tail term (same KDoc rationale: the intended word's first letter
matches at/near the trail start and pays nothing, even on sloppy
touch-downs, because the basin clamps early and head arc ≈ 0).

Measured on real trails: on B1, set1 7→**9** (fixes quick#9 — `wick`'s W
basin sits past the touch-down; jumps#12 — `humps`' H likewise), **zero
losses on all 53 trails**. And it re-plugs the guard hole B1 opened: `wipe`
on the realistic swipe trail pays ~0.6 for the unexplained S opening,
`umpire` ~1.0 for the J opening. This is what makes B1 guard-safe.

Not useful inside B2 (35→35, one set2 flip-flop) — the trust radius already
handles those cases differently.

## Recommendation: B3 = "G-final + head" — 38/53, all 178 tests green

Five mechanisms, all inside `SwipeDecoder.kt`, all additive to the existing
architecture:

1. **Endpoint miss exemption** — endpoint regions keep LCS credit, charge
   no MISSED_SALIENT. (Kills the false `h`/`t`/`r` charges: dog, over, the.)
2. **Endpoint re-anchoring** — endpoint regions take the key under the
   trail endpoint point, not the accel/decel-dragged salience peak. (Kills
   #17's F.)
3. **Mid-trail dwell gate** — slowness-only mid-trail regions need ≥60 ms
   dwell; turns (curvature) and true hesitations (≥300 ms doubling)
   untouched. (Kills slight-slowdown false positives: very#1, quick's
   competition.)
4. **No salience multiplier at endpoint match indices** — stops
   double-charging lift-off noise in the distance term.
5. **Unexplained-head charge** (0.5kw free, weight 1.0) — mirror of the
   tail term; words that ignore the trail's opening pay for it. (Re-sinks
   wipe/umpire/humps/wick; fixes quick#9, jumps#12.)

Measured end state (Kotlin, full suite **178/178 green**, no guard
re-statement needed):

| | set1 | set2 | total |
|---|---|---|---|
| baseline | 5/17 | 26/36 | 31/53 |
| B1 (no head) | 7/17 | 29/36 | 36/53 (3 guard fails) |
| B2 (V+gate) | 7/17 | 28/36 | 35/53 (green) |
| **B3 (recommended)** | **9/17** | **29/36** | **38/53 (green)** |

Fixed vs baseline: set1 very#1, quick#9, jumps#12, lazy#15; set2 over#14,
fox#30, over#32, the#34. Lost: set2 jumps#31→nimitz (baseline margin 0.01;
rare-word impostor, rank 55206 — a frequency-tuning target, not a geometry
one).

Target errors from the brief:

- **#17 dog→fog**: FIXED as reported — fog falls to 1.199 (out of
  contention); dog now #2 at ~0.60 in a **dead tie with `doug`** (the trail
  genuinely passes 0.4kw over U; only frequency separates dog rank 773 from
  doug 6743). Honest residue, not an artifact.
- **dog→doh #36 + set1#16**: NOT fully fixed — gaps shrink 0.82→0.15 and
  0.89→0.21, but `doh` remains top (lift-offs genuinely land 0.35–0.44kw
  from H vs 0.87–1.07kw from G; doh is the better geometric explanation).
  Flipping these needs the frequency lever: at FREQUENCY_WEIGHT ≈ 0.8 the
  rank gap (773 vs 29925) closes 0.15; anything less leaves doh on top.
  Flag for the planned frequency-tuning session, with these exact margins.
- **over→overt #14/#32**: FIXED (over wins by 0.13–0.20 after the t
  exemption; frequency then holds it).
- **the→three #34**: FIXED (the 0.33 vs three 0.91). set1#8 NOT fixed —
  mid-trail curvature false positive `g`, different disease (below).

Invariant compliance: ordered rigid first-basin matching untouched; tunnel
untouched; backtrack untouched; per-letter/per-point means untouched; no
trail-length gates (head-arc is a per-word term like the tail term, not a
gate); MAX_COMMIT_SCORE untouched; salience still curvature/slowness with
hysteresis collapse — the package only *grades the evidence* of regions
(dwell, endpoint position) before letting them charge. Dwell-doubling
unchanged.

Implementation cost: ~97 added / 19 changed lines in `SwipeDecoder.kt` only
(that is the exact validated scratch diff, minus its SCRATCH markers):
`computeSlownessDominates` (~30 lines, or refactor `computeSalience` to emit
components — cleaner), `salientKeySequence` returns
`SalientKeys(keys, missEligible)` with re-anchoring and the trust/gate rules,
`score()` gains the miss-masked LCS (3 lines), endpoint salWeight (2 lines)
and the head term (8 lines + KDoc mirroring the tail KDoc). Tests: bump the
ratchets (set1 5→9, set2 26→29 — the ratchet rule says raise on wins);
worth adding small synthetic unit tests for the dwell gate and head arc.
New tuning knobs (same "starting point, validate against real trails" status
as the existing ones): `SLOW_REGION_MIN_DWELL_MS` 60, `HEAD_ARC_FREE_KEYS`
0.5, `HEAD_ARC_WEIGHT` 1.0. Sensitivity measured: dwell gate 45–100 ms
changes nothing on the 53 trails; head 0.5kw/1.0 is the best of
{0.5,0.7}×{0.5,0.7,1.0} tried.

Risks:
- Endpoint miss exemption transfers endpoint decisions to the frequency
  prior; rare-word impostors with good geometry (nimitz #31) can win
  coin-flips until the frequency tuning lands. Net measured effect is still
  +7, but land this *before or with* the frequency work, not after.
- The guards now lean on the head term instead of the endpoint charge for
  impostor protection; both are geometry, but re-run the full suite after
  any future change to either (they are coupled, as this investigation
  showed twice).
- Port/Kotlin parity was verified at every checkpoint (committed counts and
  top-1s matched at baseline, B1, B2, B3); minor Float-vs-double jitter can
  reorder near-ties (e.g. dog #2 in port, #3 in Kotlin on #36).

## What remains open (not solved by any viable option)

1. **Mid-trail curvature false positives** — genuine direction changes over
   crossed keys (corner cuts/wobbles): set1#8 the(`g`), set2#13
   jumps(`n`)→honours, set2#35 lazy(`s`)→lifesaver, set1#13 over(`c`)→ocr.
   Killing them requires distinguishing "turn because the word turns" from
   "turn while crossing" — the word-side turn-matching idea (Option D) is
   the wrong tool (it punishes intended words); a better direction is
   weighting curvature evidence by dwell or by corner radius.
2. **Touch-down aim errors within the candidate radius**: set1#13 starts
   0.30kw from I for "over" (iver/ocr); first-key pruning admits the
   neighbor and geometry follows it. Frequency is the only current lever.
3. **Double-letter dilution**: a doubled letter matched at a clamp pads the
   per-letter mean ("foxx" nearly beat "fox" under B1-without-head). Small;
   watch it when the dwell-double rule is next touched.
4. **the→three set1#8** and the set1 frequency regressions from the
   wordfreq swap (brien/humps/...) — owned by the frequency-tuning session;
   this package makes them frequency-decidable rather than artifact-locked.

## Experiment matrix (committed-correct; port unless noted)

| experiment | set1 | set2 | total | notes |
|---|---|---|---|---|
| baseline (Kotlin-verified) | 5 | 26 | 31 | |
| B-naive: delete hardcode | 7 | 25 | 32 | Kotlin: 4 guard fails + ratchet fail |
| C: dwell gate 60ms alone | 6 | 24 | 30 | |
| endpoint no-miss alone (X3) | 4 | 29 | 33 | |
| X3 + dist cap | 5 | 28 | 33 | |
| gate-everywhere combo (X8) | 8 | 27 | 35 | Kotlin: realistic swipe/jumps guards fail |
| B2: trust 0.35 + reanchor | 6 | 28 | 34 | |
| B2 + mid-trail gate | 7 | 28 | 35 | Kotlin: **all green** |
| D: turn charge ≥54° w0.8 | 4 | 25 | 29 | rejected |
| B1 (G-final, no head) | 7 | 29 | 36 | Kotlin: 3 guard fails |
| **B3: B1 + head 0.5kw w1.0** | **9** | **29** | **38** | Kotlin: **all green** |
| B3 variants: head 0.7/0.7, 0.5/0.5 | 8 | 29 | 37 | |
| B2 + head | 8 | 27 | 35 | |
| B1 with endpoint doubling kept | 6 | 28 | 34 | no-doubling is worth +2 here |

Ranks referenced (words_en.txt): the 1, over 83, three 151, dog 773,
hell 775, hello 1870, doug 6743, wipe 6929, fog 8110, dough 8594,
jumps 8620, swipe 13133, umpire 17518, overt 18875, doh 29925,
nimitz 55206.


---

# ADDENDUM — Overshoot on fast swipes (added scope, same investigation rules)

Philip's observation: on fast swipes he overshoots letters (e.g. `excellent`:
X or C downward toward the spacebar), and the captured `excellent` trails
score 2.2–3.3 even as top-1 (silently dropped by MAX_COMMIT_SCORE 1.8).
Research question: how should the decoder account for fast-swiping overshoot?
Evidence: set1 #2, set2 #2, set2 #20. All numbers measured as in the main
report (Python port verified == Kotlin at every checkpoint; finalists
re-validated in Kotlin against the full 178-test suite).

## Premise check — where the `excellent` scores actually come from

**set1 #2** (baseline top=`exert` 3.58, `excellent` 4.35 rank out):
`excellent`'s T matches at **5.24kw** (first-basin clamp — after N the trail
flicks up to y=31, then dives below the key plane to y=466 and lifts off
there; T is never visited), both L's at 1.23kw (undershot) → dist term 2.09;
the below-plane dive lands in the **tail term, saturated at 2.00** (9.47kw of
post-match wandering); leg 0.51, missed-salient 0.30 (false `k`).
The winner `exert` parks its T at trail point 45 and **ignores the remaining
25.48kw of trail for a flat 2.0** (TAIL_ARC_CAP_KEYS).

**set2 #2** (baseline top=`excellent` 2.22, dropped by the threshold):
leg term 2.08 = conformance-mean **0.033** + **backtrack 2.05**. The
backtrack: 0.76 on C→E (the finger overshoots past C down-right toward the
spacebar — y reaches 428, bottom row + 0.36 row-pitches — then returns
up-left; the tunnel absorbs the *position* error for free, but the outbound
half of the loop is opposed to the leg direction and pays its full arc) plus
1.29 on E→N (a leftward loop toward W). The W loop also creates the false
salient key `w` (+0.30 missed). The per-point saturation cap never binds:
max off-segment distance 0.76kw. **Paying points are the slowest of the
swipe (median 0.26× average speed)** — the "fast swipe" premise is not what
the trail shows; dense slow sampling does not drive the cost either (the
conformance mean is 0.033).

**set2 #20** (baseline top=`recent` 3.80, `excellent` **culled**, score ∞):
a genuine 3.22kw leftward excursion (to x=150, the Q/W corner) on the N→T
leg exceeds CONFORMANCE_CULL_KEYS 1.75 → the intended word is rejected
outright. Winner `recent` parks its N at point 71 (1.64kw — just under the
cull) and **ignores the remaining 16.20kw of trail for a flat 2.0**.

Corpus-wide audit (all 53 trails, baseline):
- Trails with ≥3 points below the key plane (bottom row + 0.5 row-pitch):
  **exactly one** (set1 #2's tail — charged via the tail term, not
  conformance). Below-plane per-point conformance cost is ~zero corpus-wide.
- Intended words culled: **exactly one** (set2 #20); none near-culled
  (maxd ≥ 1.5kw).
- Intended words paying ≥ 0.5 backtrack: **18 of 53** — overshoot-and-return
  reversal is the *common* overshoot signature in this corpus.
- Winners exploiting the saturated tail (ignoring ≥16kw of trail for 2.0):
  exactly the two `excellent` discrimination failures.

**Verdict on the premise: per-point line conformance is not where overshoot
costs live.** The 0.5kw tunnel plus the per-point mean already absorb
position error, vertical dips included. Fast-swiping overshoot shows up as
(a) **backtrack** on overshoot-and-return legs, (b) **tail arc**, which the
prefix-word exploit then under-charges, and (c) rarely, a cull-killing
excursion.

## Mechanism evaluation (committed-correct; baseline 5+26=31)

| mechanism | s1 | s2 | tot | on the excellent trails | verdict |
|---|---|---|---|---|---|
| saturation cap 2.0→1.25 | 5 | 26 | 31 | unchanged | **IDLE** — no point beyond 0.8kw off-segment except on the culled trail |
| robust aggregation (trim worst 10%) | 5 | 26 | 31 | 2.22→2.20 | **IDLE** — conformance means already ≈0.03 |
| velocity-adaptive tunnel | 5 | 26 | 31 | unchanged | **IDLE** — paying points are slow, not fast; premise refuted |
| asymmetric vertical (below-plane dist ×0.5) | 5 | 26 | 31 | unchanged | **IDLE** — no below-plane conformance exists in the corpus |
| tail cap 2.0→4.0 | 5 | 26 | 31 | both sides +2.0 equally | **IDLE** — cap saturates for winner and intended alike; uncapping re-breaks calibration (why the cap exists) |
| cull 1.75→2.5 / removed | 5 | 26 | 31 | winners become *worse* impostors (`recalcitrant` 2.43, `recombinant`, `liszt`) | **REJECTED** — the cull is load-bearing against long-word impostors |
| per-letter dist cap 2.0 | 5 | 25 | 30 | unchanged | **REJECTED** (−1) — under-charges impostors' unvisited letters (served→serviced) |
| per-leg backtrack slack 0.3kw | 5 | 27 | 32 | set2 #2 commits 1.62 | works; worse regression profile (dog #35→lifesaver) |
| **BACKTRACK_WEIGHT 1.0→0.6** | 5 | **27** | **32** | **set2 #2 commits 1.40** | **works, best profile** |

Note: the "REJECTED" verdict on cull relaxation above is for the
0.35-frequency build. The swipe agent's trails3 `mother` evidence (fifth
failure class) forced a deeper look — the cull's protective value turns out
to be configuration-dependent, and a graded cull becomes viable on the
combined build. See **Option H** below.

## Option G (new dimension) — discount the backtrack term

**Mechanism:** BACKTRACK_WEIGHT 1.0 → 0.6 (one constant). Rationale: an
overshoot-and-return excursion is forgiven *positionally* by the tunnel but
charged again *directionally* by backtrack — a fast swiper's self-corrected
momentum loop pays ~1kw per excursion, the same as a deliberately zigzagged
word. Discounting the term 40% keeps the zigzag signal while stopping one
loop from dominating a long word's score. (Alternative shape measured:
per-leg 0.3kw reversal slack — ~10 lines, physically the "overshoot
allowance" framing, but it measures the same or worse and flips
dog #35→lifesaver standalone. The constant is simpler and better.)

**Measured (Kotlin scratch patch, full suite 178/178 green — both my/mummy
zigzag guards included):**
- Alone: set1 5, set2 27 = **32/53 (+1)**; set2 #2 `excellent` 2.22→**1.40,
  commits**.
- **Stacked on the recommended B3 package: set1 9, set2 31 = 40/53 (+2 over
  B3), 178/178 green.** Besides `excellent` (1.47, commits), set2 #17 `dog`
  resolves its B3-era `doug` tie → **`dog` 0.40, commits** — clean synergy:
  B3 clears the salient artifacts, the backtrack discount clears the rest.
- Sensitivity (port): plateau — weights 0.3–0.7 all give 40/53 stacked;
  degradation starts at 0.8. 0.6 is mid-plateau.

**Fixes:** set2 #2 `excellent` (the only one of the three any viable
mechanism saves); set2 #17 `dog`; margin relief on the 18/53 trails whose
intended word pays ≥0.5 backtrack.

**Risks:**
- Zigzag discrimination is this term's designed job (my vs mummy). Guards
  are green at 0.6, but the numeric margin erosion was not measured —
  worth a margin probe before landing.
- Threshold-boundary impostor commits: set1 #3 `mother`→`northerly` commits
  at 1.13 (pre-existing under B3 at 1.38; backtrack relief deepens it) and
  set2 #35 `lazy`→`lifesaver` commits at 1.71 (was silence at 1.90 under
  B3). Both are rare-word/frequency-side impostors — the planned frequency
  tuning is the real fix; flag with exact margins.
- Invariant compliance: the backtrack term is structurally untouched (same
  arc-based opposed-travel measure, only its weight changes); ordered
  first-basin matching, tunnel, cull, per-point/per-letter means, no length
  gates, MAX_COMMIT_SCORE all untouched.
- Implementation cost: one constant (+ ratchet bump set2 29→31 when stacked
  on B3).

## What stays unsolved (honest bottom line)

- **set1 #2 and set2 #20 `excellent` are not savable by any viable
  mechanism**: #2's trail never visits T after N (5.24kw clamp match) and
  undershoots L twice; #20 has a genuine 3.2kw excursion the intended word
  cannot explain, and relaxing the cull admits *worse* impostors
  (`recalcitrant`, `recombinant`). Fixing the winner *identity* needs the
  prefix-word exploit closed, but the tail cap saturates equally for both
  sides, and uncapping re-breaks score calibration on wandering trails
  (correct words at 10+ — the reason the cap exists). Both trails currently
  produce **silence, not wrong text** — acceptable interim behavior; the
  real fix is out of decoder scope (lift-off hygiene / trail capture) or
  accepting silence for genuinely mangled swipes.
- **The four seed mechanisms (saturation cap, velocity-adaptive tolerance,
  robust aggregation, asymmetric vertical tolerance) are all measured IDLE
  on this corpus** — recommend not implementing them (knobs that move no
  trail). Revisit only if future captures show real below-plane or
  fast-segment conformance cost.

## Interaction with the main report and recommendation

Option G is orthogonal to the B3 endpoint package (different scoring term)
and composes cleanly: **B3 + G = 40/53, full suite green, Kotlin-verified**.
Recommended landing order: B3 first (guards re-verified, ratchets 5→9 and
26→29), then Option G as a one-constant follow-up (ratchet 29→31), with the
two threshold-boundary impostor commits (`northerly` 1.13, `lifesaver` 1.71)
recorded as frequency-tuning targets.


---

# ADDENDUM 2 — Option H: graded alternatives to the hard conformance cull (fifth failure class)

Source: swipe agent's trails3 analysis on the combined build (frequency 3.0 +
filtered wordlist): `mother` failed 2 of 3 attempts (the retry scored −0.27
and committed). The mechanism was verified against the decoder structure
here; collateral measured on this investigation's corpus (53 trails, full
56k wordlist — which *contains* `norbert`, `recombinant`, `liszt`; the
combined build's filtered list missed `norbert`, so filter quality is part
of the story).

## The failure class (their evidence, structurally confirmed here)

- The trail's final wander (`h→f→e→t` loops) is assigned wholesale to the
  last leg `e→r` (adjacent keys, ~1.1kw apart — a razor-thin corridor).
  The last letter R clamps to the trail's final point (lift-offs
  0.81/1.29/0.67kw from R — all pass the endpoint gate), so the wander sits
  INSIDE the last leg's match interval and the tail term sees arc 0:
  **frequency, salience, alignment and tail-arc never get a vote.**
- Wander points measure 4.4–4.7kw from the E→R segment >
  CONFORMANCE_CULL_KEYS 1.75 → `legCosts` returns null → score = ∞.
- Longer words partition the same wander into more intervals and survive
  (`norbert`, rank 35485, wrong-committed at 1.26); shorter words end
  before it (`not` won at 2.18 → silence).

**Independence note (as requested):** the endpoint gate is NOT mother's
blocker here — the endpoint work (B3) and the cull are independent levers.
Confirmed on this corpus too: the only culled trail (set2 #20) is culled
identically under baseline and under B3 — the cull fires during scoring,
before any salience term is consulted. Also worth stating: `mother` is not
one bug — it has three distinct failure modes across trails:
northwest/northerly-family impostor on this corpus's set1 #3
(frequency-side, neither cull nor endpoint), the cull on trails3 ×2, and
clean successes (set2 #3/#21, the trails3 retry).

## What the cull is FOR — and its measured protective value

The cull is the **only term that can say "impossible"**. Every other term
is graded, and the per-point conformance cost saturates at 2.0kw by design
— so without the cull, any wild excursion is survivable at a bounded price
and the most elastic long word (the most letters to route the wander
through) wins every mangled trail. FUTO lineage (~1.8kw): kills genuinely
wild mismatches cheaply (also an early-out performance shortcut — minor).

Measured protective value on this corpus, by configuration (removing the
cull = "soft cull", per-point cost simply saturates at the existing cap):

| configuration | with cull | soft cull | what breaks |
|---|---|---|---|
| freq 0.35 (main build) | 31/53 | 31/53 | lazy#35 None→**liszt** (rank 40653) wrong-commit; excellent#20 winner degrades recent→recombinant (both silence) |
| freq 3.0 alone | 43/53 | **41/53** | loses two committed-correct trails: set2#7 nine→nice (0.29), set2#26 pizzas→passed (0.80) — culled impostors resurrected; plus liszt |
| B3 + G + freq 3.0 (combined-build emulation) | 44/53 | **44/53** | zero count change; only flip is wrong→wrong (lazy#35 lifesaver 1.58→liszt 0.98); set2#20 stays silence (recent 2.41 holds off recombinant 2.45 / recalcitrant 2.56 / redundant 2.60 — the impostor cluster is frequency-suppressed) |

Conclusion: the cull's protection is real but **configuration-dependent** —
it is the last line of defense exactly when frequency and the other
evidence terms are weak, and nearly redundant once frequency 3.0 + the
unexplained-head charge + endpoint grading are in.

## Variants evaluated

- **H1 — "cull → existing 2.0 saturation, no rejection"** (the swipe
  agent's seed (a)): the ONLY variant that saves the trails3 mothers.
  Survival converts ∞ into a finite, frequency-votable score. Expected
  margin there: mother rank 537 vs norbert 35485 → frequency-bonus gap at
  weight 3.0 ≈ **1.15** (3.0·ln(35485/537)/ln(56001)) — norbert's 1.26
  win plausibly flips. Needs trails3 confirmation.
- **H2 — cull/corridor scaled by leg length**: analytically dead. A 4.5kw
  excursion on a 1.14kw leg needs a ≥4.5kw threshold (a 2.6× raise), which
  IS no-cull for that leg; any smaller scaling doesn't save mother. The
  problem is not corridor width (the tunnel is free anyway) but that a
  long wander cannot be explained by a 1-key leg at all.
- **H3 — cull only if >X% of the leg's points violate**: dead. The wander
  is sustained (many points), the cull still fires. Single-point jitter
  was never the issue.
- **H4 — survival with a steep post-1.75 cost slope**: dead. The wander's
  many points × any stiff slope dominate the per-point mean; mother either
  still dies or survives too expensive to beat norbert. Gentle survival
  (H1) is the only shape that helps — and gentleness is exactly what
  impostors exploit.

## H1 pros / cons / risks

Pros: converts silent cull-deaths into scored, frequency-votable
competitions (mother ×2 on trails3; the retry already commits at −0.27
when the wander is mild); removes a binary, alignment-fragile decision (a
long wander dumped on a tiny leg) from the pipeline; measured zero
collateral on the combined-build configuration (44=44).

Cons / risks:
- "Impossibility" leaves the model: on mangled trails the decoder always
  produces its best-routing long word; whether that is the intended one is
  decided by frequency. Measured cost when the rest of the evidence is
  weak: liszt at freq 0.35; nine/pizzas at freq 3.0 without B3.
- Resurrected-impostor risk is corpus- and config-dependent — MUST be
  re-measured on trails3 with the real filtered wordlist before landing.
- The cull is also the cheap early-out; survival means full scoring of
  every candidate (not measured; expected minor in Kotlin).
- No interaction with the prefix-parking exploit of Addendum 1 (the tail
  cap is untouched; recent-class prefix winners are unaffected).
- Invariants: ordered first-basin matching, tunnel, backtrack, means
  untouched — but the cull is an invariant-adjacent documented guarantee
  ("any single point past CONFORMANCE_CULL_KEYS rejects the word", KDoc).
  Softening it changes an architectural contract: the KDoc and any
  cull-dependent guard expectations need updating, and a replacement guard
  ("genuinely wild trail must not wrong-commit") should encode that the
  protection now comes from frequency + head charge + endpoint grading.

## Recommendation

Adopt H1 **only after or together with the frequency-3.0 + B3 + G build** —
measured there: zero collateral on this corpus (44/53), plausibly +2
`mother` on trails3 (frequency gap ≈1.15 over norbert). On the
0.35-frequency main build the measured cost is real (liszt wrong-commit) —
keep the hard cull there. Implementation: delete the cull branch in
`legCosts` (scoring falls through to the existing 2.0 per-point
saturation), update the KDoc contract, add the wild-trail guard test, and
re-run trails3 to confirm mother ×2 and watch for norbert/liszt-class
resurrections. If trails3 shows resurrection anyway, the fallback is a
`CULLED_SURCHARGE` (~+0.5) on would-be-culled words — keeps mother votable
while re-raising the impostor bar, at the cost of halving the frequency
margin (untuned; needs trails3 data).


---

# APPENDIX — The next TDD corpus: ten test sentences for trail capture

Designed for coverage, not realism. Each sentence is naturally swipeable
(5–9 common words) and deliberately stresses the failure classes of this
report: (a) between-key lift-off ambiguity, (b) zigzag/visit requirement,
(c) crossed letters, (d) short-leg cull victims (1-key `e→r`-type final
legs), (e) overshoot-prone bottom-row dips, (f) frequency-tie pairs,
(g) two-letter words, (h) easy controls.

Every word verified present in `words_en.txt` (56,207 entries in this
worktree; the only absent item is one-letter "a", which is tapped, not
swiped). Every listed confusable is also in the list (ranks noted where
relevant), so any wrong commit is a decoder problem, not a dictionary gap;
if the combined build's filtered list drops a rare confusable (`doh` 29925,
`norbert` 35485, `liszt` 40653, `folic` 39158, `brien` 49796,
`krazy` 47289, `ewe` 35782) it simply cannot win — all intent words are
common (rank ≤ 13264).

Recording protocol: swipe each sentence 2–3 times at natural speed — the
trails3 `mother`×3 experiment showed the same word flips attempt to
attempt, and reproducibility is half the signal. Sentences 9–10 are
controls: they must decode perfectly on every build; any miss there is a
tuning regression, not a known disease.

1. **"the quick brown fox jumps over the lazy dog"** — (a,c,e,f). The
   pangram is the failure-class showcase: nearly every word failed in the
   captured corpus. Watch: `the`→three/ther/they; `quick`→wick
   (touch-down q/w slip); `brown`→brien (rank 49796 beat it at baseline!);
   `fox`→fix/fog/folic; `jumps`→humps/juniors/jumped/nimitz (+ the jumpw
   neighbor-slip class); `over`→overt/iver/ocr/officer;
   `lazy`→krazy/lifesaver; `dog`→doh/dough/fog/doug.
2. **"my mummy did the minimum"** — (b,g). Zigzag/visit requirement:
   `mummy` needs the M→U→M reversal performed (watch `my` win on straight
   trails and `mummy` lose its second M); `minimum` = six visits, watch
   truncation (minim/minima); `my` two-letter (watch mum/mummy stealing);
   `did`→died; `the`→three.
3. **"his mother never once drank water after dark"** — (d)×4. Four words
   ending in the 1-key `e→r` leg (`mother`, `never`, `water`, `after`) —
   the cull class: any final wander assigned to that leg kills the word
   pre-scoring (score ∞, no frequency vote). Watch: cull-silence;
   `mother`→not/norbert/northwest/northerly/mortimer; `water`→wager
   (16210; shares w,a,e,r); `once`→one; `his`→this/has.
4. **"an excellent example of what to expect"** — (e)×3,(g). Bottom-row
   X/C overshoot (the report's headline trail): `excellent`→exert/recent/
   exceeding or threshold-silence; `example`→examine (5198; shares
   e,x,a,m,…,e); `expect`→except (957; mid-word order swap); `of`→if/off;
   `an`→and (lift-off d-drift); `what`→wheat.
5. **"nine nice mice ran past the fox"** — (f),(a). Three-way minimal set:
   `nine` vs `nice` differ only in the middle key (n vs c, both bottom
   row) — geometry nearly ties, frequency must decide; watch
   `nine`→nicer (e→r long-word pull), `mice` (touch-down m/n),
   `ran`→rain (crossed-i insertion), `past`→pass/passed/pasta,
   `fox`→fix/folic, `the`→three.
6. **"we go up to fix it"** — (g)×5,(f). The two-letter gauntlet: every
   word ≤3 letters — alignment floor, no trail-length gates, frequency
   tie-breaks. Watch: `we`→ewe (35782!), `go`→goo/ho (dwell-double +
   g/h slip), `up`→yup (6393), `to`→too, `it`→if/is, `fix`→fox/fib/fin.
7. **"the dog ran over the hill"** — (a)×4. Reproducibility repeats of
   the exact corpus failures (dog ×2, over ×2 in sets 1+2) in a fresh
   frame: `dog`→doh/dough/fog/doug; `over`→overt/iver; `the`→three/ther;
   `hill`→hell/ill/jill (double-l + h/j slip); `ran`→rain.
8. **"the power will follow a quick swipe"** — (c)×3,(a). Crossed letters:
   `power`'s O→W full-row sweep crosses i,u,y,t,r then reverses W→E→R
   (watch `power`→powder/poser); `follow`→flow (double-l dwell — the
   historical regression); `swipe`→wipe/wiped (crossed i — the head-charge
   guard pair); `will`→well/ill/jill; `quick`→wick; `the`→three.
9. **"how are you doing today"** — CONTROL 1 (h). Common words, smooth
   paths, no bottom-row zigzag. Must stay 100%. Only mild watches:
   `are`'s final r→e 1-key leg, `you`→your (lift-off r-drift).
10. **"we had fun at the lake"** — CONTROL 2 (h). Should stay 100%.
    Watch (must NOT fire): `we`→ewe, `had`→has (d/s neighbors),
    `fun`→gun/fin (f/g slip), `at`→ate/as, `lake`→like/late, `the`→three.

Coverage map: (a) sentences 1, 5, 7; (b) 2; (c) 1, 8; (d) 3; (e) 1, 4;
(f) 1, 5, 6; (g) 2, 4, 6, 10; (h) 9, 10. Headline classes (a, d, e) get
repetition across frames for reproducibility; controls guard the ratchet.


---

# ADDENDUM 3 — The `once`→`one` mirror class: fast deliberate visits vs crossed keys

Source: swipe agent's `once`→`one` diagnosis on Philip's emulator trails
(4 trails). On two, Philip passed THROUGH c fast (0.19–0.25kw from center —
a genuine visit) with no slowdown/turn over the 0.35kw arc window, so c
never registered salient; salients `'oe'` → alignment 2/3 vs 2/4 favors
3-letter `one`, and frequency 3.0 (one rank 36 vs once 254) overruled
once's genuinely better geometry (+0.45 shape vs −0.67 freq+alignment).
When c registers (trails 2/3), once wins comfortably. Their seed idea:
"fast-but-deep-into-key visit" vs "fast edge-graze" could admit the former
(fixing once) without admitting crossed-key impostors (officer/ther class).

**Verdict up front: the proximity distinction is refuted on the wider
corpus — but the investigation produced a better knob (tighten the
attribution radius) and a measured rejection of the alignment-denominator
"tempting fix". The once/one pair itself is an honest ambiguity of the
evidence-free-letter class (same family as dog→doh).**

## Audit A — does peak-to-key-center distance separate false from true regions?

All mid-trail salient regions across the 53 trails, distance from region
peak to attributed key center:

- FALSE (key not in intended word), 13 regions: **0.26–0.67kw** — incl.
  jumps#12 `n` 0.26, pizzas#26 `u` 0.32, excellent#20 `w` 0.37 (and the
  session-1 endpoint-adjacent false `f` on dog#17 at 0.36).
- TRUE (key in intended word): spans the same range and beyond — 15 true
  regions at **0.50–0.69kw** alone (pizzas `z` 0.69, nine `i` 0.65,
  the `h` 0.65, excellent `l` 0.66…).

The distributions overlap completely. Once's true c at 0.19–0.25 and
dog#17's false F at 0.36 sit inside the same band. No distance threshold
separates "fast central visit" from "fast edge graze" on real trails.

## Audit B — what would central-visit admission admit?

Per trail, keys (not in the intended word) passed within ≤0.25kw of center:
**102 across 53 trails ≈ 2 per trail** — and they are the crossed-key
impostors by name: over#14 passes **0.09kw over F** (officer's f — *more*
central than once's true c!), over#13 0.20 over C, dog trails 0.15–0.22
over I, lazy#15 0.05 over S, brown/quick pass centrally through e,g,t,u,y,
w on their top-row sweeps. Long legs pass THROUGH intermediate keys — that
is what crossing means on a keyboard. Admitting fast central visits as
salient would hand the officer/ther/juniors class LCS credit *and* missed
charges against the intended word. **Refuted.**

Collinearity variant (is the impostor key on the intended word's ideal
path?): brown/quick's row-crossings are collinear (0.0–0.23kw off-path),
officer's f is collinear (0.23) — but over#13's impostor keys are 0.6–1.14kw
OFF the intended path because the trail itself wanders. The distinction
breaks exactly on the sloppy trails that need it, it is word-relative
(while salience is computed once per trail), and it duplicates what the
conformance term already encodes — once's geometry WON (+0.45); geometry
was never the problem.

## The actual once failure: frequency overrule on sparse evidence

Their own numbers decompose: once +0.45 geometry, −0.67 frequency +
alignment. The alignment part (2/3 vs 2/4 × 0.8 weight) contributes 0.13;
frequency at weight 3.0 contributes 3.0·ln(254/36)/ln(56001) ≈ 0.54. So
even with the alignment bias removed, once loses by ~0.09 at freq 3.0.
When c registers salient, evidence outvotes frequency and once wins —
which is the system's designed behavior: **evidence beats priors; without
evidence, priors decide, and a fast o-n-e swipe with no c-evidence IS more
likely `one` (rank 36).** Same family as dog→doh (lift-off measurably
nearer H): honest ambiguity, not a decoder artifact. Philip's trails 2/3
show the user-side fix — a slight slowdown on c registers it.

**Alignment-denominator variant measured and REJECTED:** denominator
`max(wordLen, 3)` → `max(salientCount, 3)` (ties same-LCS words regardless
of length, fixing the 2/3-vs-2/4 bias) scores **29/53 (−2)** on this
corpus: it unleashes long-word impostors that explain the salients equally
well (`foxx` beats fox, `there`/`ther` beat the, `britten` beats brown,
`nursery`…). The length-normalized denominator is the parsimony brake and
is load-bearing; once's 0.13 alignment bias is its price. (The
ALIGNMENT_MIN_DENOMINATOR=3 floor for two-letter words is unaffected by
this variant — and stays.)

## The constructive knob: tighten SALIENT_KEY_RADIUS (admit tighter, not more)

The radius that attributes a salient region to a key is 0.7kw — generous.
On this corpus, 10 of 13 false mid-trail regions sit at >0.5kw from the
attributed key, while ~15 true regions do too — but session-1's asymmetry
applies: false keys hurt more than true keys help (why MISSED_SALIENT was
halved 0.6→0.3). Measured (port, baseline config):

| radius | committed | flips |
|---|---|---|
| 0.7 (status quo) | 31/53 | — |
| **0.6** | **33/53 (+2)** | fixes jumps#13 (juniors→jumps — the curv-0.81 false `n` at 0.67 dropped) and the#34 (three→the); one wrong→wrong (dog#17 fog→foy), **zero committed-correct losses** |
| 0.5 | 33/53 (+2) | the 0.6 wins + fox#30 (folic→fox), but loses jumps#31 (jumps→humid — true `p` region dropped) |

0.6 is the cleaner profile: +2, no losses, and it fixes two of the four
documented "mid-trail curvature false positive" trails from the main
report's open-problem list (jumps#13's `n`; the#34's endpoint-adjacent
`r` at 0.64). Guard risk is low by construction (synthetic guard trails
pass through key centers at ~0.0kw, endpoints included) but **needs
Kotlin validation** before landing; also worth stack-testing with B3
(port-indicative: a B3-family config + radius 0.5 reaches ~37/53 and fixes
jumps#13, which full B3 does not).

## Bottom line for the once class

1. once→one is primarily a frequency-calibration item for the frequency
   session (sparse-salient trails let the prior overrule geometry; exact
   margins above), secondarily an honest ambiguity (evidence-free c
   favors one; a slight c-slowdown is the user's disambiguation signal —
   confirmed by their trails 2/3).
2. The salience-side fix is NOT more admission (refuted ×2: proximity
   overlap, ~2 impostor central crossings/trail) but tighter attribution:
   **SALIENT_KEY_RADIUS 0.7→0.6, +2 measured, candidate for Kotlin
   validation** (standalone or stacked on B3).
3. The alignment denominator stays as-is (variant measured, −2).

---

# Implementation log — feature/endpoint-b3g (B3 + G + H1, executed 2026-07-27)

Branch off main 74adeed (frequency prior 3.0, filtered 53k list). All
numbers re-measured against main's harnesses; baseline set1 12/17,
set2 32/36, suite 178 green. Set3 (trails3, 37 trails) added during
this work — see below.

## Commit 1 — 0c68d48 "tune(swipe): grade salient-point evidence before it charges (B3)"

Landed: endpoint re-anchoring (lift-off/touch-down keys taken from the
nearest key to the endpoint, not the hardcoded salience), mid-trail
dwell gate (SLOW_REGION_MIN_DWELL_MS 60, slow-dominated regions only),
no salience multiplier at endpoint match indices, unexplained-head
charge (HEAD_ARC_FREE_KEYS 0.5, cap 2.0, weight 1.0, KDoc mirroring
the tail term). Three new synthetic tests (dwell-gate brief/linger
pair, head-arc charge, plus the guard suite).

The approved endpoint MISSED_SALIENT exemption was measured and
DROPPED before landing: at frequency 3.0 it lost set1#6 (us→is) and
the 'am' guard, while its targets (dog/over) were already held by the
frequency prior — exactly the risk the report flagged.

Result: set1 12→13 (jumps#12 fixed by the head term), set2 32
unchanged, zero losses on 53 trails, 181/181 green. Ratchet set1
bumped 12→13 with why-comment.

## Commit 2 (G, BACKTRACK_WEIGHT 1.0→0.6) — NOT LANDED

Margin probe on the realistic jittered s→w→p→e guard trail (swipe vs
stopped): 1.0 → swipe +0.063; 0.8 → stopped +0.001 (flip ≈ 0.85);
0.6 → stopped +0.061. The crossed-i guard has essentially no margin
at 0.6 — a crossed-letter impostor ('stopped' contains no trail
letters in order… it beats 'swipe' once backtracking gets cheap) wins.
The my/mummy zigzag guard is weight-insensitive and safe (straight
m→y trail: mummy culled outright; zigzag trail: my 1.786 vs mummy
−1.067, identical at 1.0 and 0.6) — but the swipe/stopped guard kills
the change. Reverted; BACKTRACK_WEIGHT stays 1.0.

## Commit 3 (H1, delete the 1.75kw conformance cull) — REJECTED by measurement

Applied fully (cull deleted, legCosts falls through to the 2.0
saturation, KDoc/AGENTS.md rewritten) and measured:

| set | pre-H1 | post-H1 | resurrections |
|---|---|---|---|
| set1 | 13/17 | 12/17 | over#13 → 'ict' (−0.13 commits) |
| set2 | 32/36 | 29/36 | nine#7 → nice (0.26 vs 0.28), pizzas#8/#26 → 'pad', lazy#35 last → 'liszt' (1.25) |
| set3 | 34/37 | 31/37 | mother#3 → 'mortimer' (1.58 commits), nine#7 → nice, lazy#16 → 'lay' (−0.42), pizzas#26 → 'pad' |

And the killing blow: mother STILL loses without the cull — post-H1
probe (topN=40) ranks mother #3 on trail 3-3 (beaten by 'mothed' 2.06
and 'moths' 2.16) and #6 on trail 3-21 (1.569, behind 'not' 1.078,
which still wrong-commits). The swipe agent's "cull kills mother"
diagnosis does NOT reproduce under B3: the saturating conformance mean
plus full-strength backtrack sink mother below geometry-passing junk
whether or not the cull fires. H1 is both unsafe (broad junk
resurrection — exactly what the cull is FOR) and ineffective.

Per the agreed rule, H1 was reverted and is NOT committed. Mother
needs a different lever (per-leg worst-case caps, tail treatment, or
the CULLED_SURCHARGE fallback) — that is Philip's call.

## Commit 3' — 629b451 "test(swipe): add third real-trail capture (trails3) with 34/37 ratchet"

The trails3 harness lands test-only: third @Test replay +
swipe_trails3_philip.jsonl (37 records from the Downloads capture) +
intents.tsv, ratchet MIN_COMMITTED_CORRECT_SET3 = 34 with the
why-comment recording the H1 rejection. Full suite 182/182 green;
accuracy tables: set1 13/17, set2 32/36, set3 34/37.

INTENTS CORRECTION (the trails3 analysis brief was wrong): #9-17 is
NOT the pangram-with-'sold' — it is sentence 1 with 'sold' replacing
'bought' (my/very/excellent/mother/just/sold/us/nine/pizzas), proved
by the decoder tops. The committed intents.tsv encodes the corrected
alignment: #0-8 bought-sentence, #9-17 sold-sentence, #18-26 pangram,
#27-35 pangram again, #36 lone mother retry.

## Final state

- Branch feature/endpoint-b3g: 74adeed + 0c68d48 (B3) + 629b451
  (trails3 harness). Ratchets: set1 13, set2 32, set3 34. Suite 182
  green. REPORT.md untracked; nothing else dirty.
- G and H1 both measured and rejected with numbers on file (above);
  the diff surface for the follow-up apostrophe feature is clean —
  only SwipeDecoder.kt's salience/head sections moved.

---

# ADDENDUM 4 — trails4 (ten-sentence corpus, normal speed): baseline + miss autopsy

Branch `feature/trails4-normal`, harness set 4 committed as 117a7b0.
Baseline at the B3 decoder: **60/67** (89.6%); floor ratcheted at 60.
Alignment: 65 word slots + 2 genuine retries (s4: excellent #23→#24,
example #25→#26; both attempts scored). s8's 'a' tapped, not swiped.
The brief's word counts were off: s4 = 7 words, s9 = 5.

Headline: the appendix's predicted classes did NOT reproduce at normal
speed — (a) dog×3/over×3 all pass with margin, (b) zigzag 5/5,
(c) crossed 6/6, (d) the s3 e→r words all top-1 (mother −0.88!),
(g) two-letter 100%. The 7 misses, root-caused with term breakdowns:

| # | intent→top | margin | root cause | verdict |
|---|---|---|---|---|
| 31 | nine→bounce 0.58 | nine scores **5.03** (cull-off, rank #504) | i-overshoot toward o/u + bottom-row drag: the finger literally draws bounce's shape | decoder arguably RIGHT; mis-swipe candidate (Philip's call) |
| 32 | nice→notice 0.28 | nice scores **6.24** (#849) | same shape story; trail dips to c but also i-overshoots | decoder arguably RIGHT; mis-swipe candidate |
| 23 | excellent→silence | top acetylene 3.36 | genuinely wild first attempt (touch-down nearest A, lift-off at R); whole field >2.7 | defensible silence; retry #24 proves decodability |
| 25 | example→silence | cull-off: example 0.69 vs escape **0.61** | culled by p-UNDERSHOOT (trail never right of x=718, p at 845; m→p corridor violation) — but uncensored it still loses escape by 0.08 | cull surgery pointless HERE: escape uncensors too and wins |
| 35 | past→part −1.64 | 0.061 | geometry favors past by 0.214; frequency (part 149 vs past 406) overrules; salientKeys=[p,a,t] — ZERO middle-key evidence | decoder working as designed on an ambiguous trail |
| 48 | the→that 0.33 | 0.53 | the pays the CAPPED 2.0 unexplained tail — a 2kw+ post-e drag that 'that' partitions into letters | tail term doing its job (the mother/mit guard) |
| 56 | how→hire −0.89 | 1.01 | CONTROL breach: finger turned at i (not o), lifted nearer e; salients=[h,i,e] vs how's {h,o,w}; how pays +0.3 endpoint-e + dist 1.374 vs 0.128 | the dog→dough endpoint disease + mid-trail turn; guarded by 3 tests — big-project territory, not a quick lever |

Measured lever eliminations (saves the next session the detours):
- **Cull deletion/surgery helps none of set4's misses**: nine/nice
  score 5-6 uncull​ed; example uncensors but still loses escape;
  and cull-off costs set4 swipe#55→super (plus the known sets 1-3
  resurrections). The cull stays; it is not this corpus's blocker.
- **MISSED_SALIENT endpoint exemption helps neither how (still loses
  by 0.7) nor dog/over (already held by frequency)** — and it re-loses
  us→is + the am guard (measured at B3). Dead twice.
- No single frequency weight fixes nine (needs Δ4.45) — global freq
  hikes are not the door.

What set4's remaining misses actually are: 3 genuine-shape losses
(nine, nice, how — the finger drew the impostor), 2 wild-trail
silences (the s4 first attempts), 2 ambiguous-trail coin-flips where
the designed tie-breakers fired (past, the). The fixable mass sits in
the endpoint/mid-trail salient-evidence project (how, the class) —
the same disease as dog→dough — not in the cull.

### Post-script: the tail-cap experiment (rejected)

The one provable-cause lever found in the TDD hour: the#48's
unexplained-tail term is pinned at TAIL_ARC_CAP_KEYS (2.0) — the trail
drags ~3.5kw+ after the e. Cap 2.0->1.0 flips the#48 (the wins by
0.47) with zero COUNT collateral on 156 trails — but it cheapens
every prefix-impostor tail: set3 mother#3's 'not' drops 2.45 -> 1.45
(silence becomes a WRONG COMMIT) and #21's 'not' 1.08 -> 0.08. The
cap is load-bearing against the mit/not class. Cap 1.5 flips the#48
by a noise-level 0.028 — an overfit ratchet. Both rejected; constants
stay. the#48 joins the mis-swipe-candidate list (a 3.5kw post-word
drag is not a 'the' trail the decoder should stretch to explain).

---

# ADDENDUM 5 — endpoint-evidence grading: implemented, measured, guard-blocked (2026-07-27)

Branch `feature/endpoint-evidence` off main 7d48da9; harness set 5
(ten-sentence re-record, normal2, 65 records) committed as 103d75b,
baseline 58/65. Set-4's mis-swipe candidates all pass in the
re-record, confirming the trails4 autopsy.

## The set-5 miss autopsy (term breakdowns on file)

- we#36 -> were (-1.88 vs -1.93): the PURE dog->dough disease.
  salientKeys=[w,r]; the r is the hardcoded lift-off anchor on a drift
  endpoint (0.52kw from r); we pays +0.3 and loses alignment.
  Arithmetic without the evidence-free r: we wins by 0.45.
- dog#8 -> doping, his#14 -> hours, minimum#13 -> min, fix#40 -> fox:
  the POST-WORD DRAG class (4 of 7!): capped 2.0 unexplained tail,
  long word partitions the 3.5kw+ drag into letters. Same class as
  the#48 (set4) and mother->not (set3) — the dominant remaining
  disease corpus-wide. Tail-cap lever measured and rejected in
  trails4 (cheapens the not/mit class).
- quick#52 -> wick (0.01): touch-down q/w aim slip, anchor working as
  designed. past#33 -> part (0.11): zero-evidence coin flip, designed.

## The grading experiment (reverted, numbers preserved)

Rule: an ISOLATED lift-off region (nothing measured extends into the
trail — only the hardcoded 0.5 fired) emits no salient key; touch-down
keeps its anchor unconditionally (the symmetric B3 exemption lost
us->is and am precisely because touch-down IS deliberate).
Measured: set3 34->35 (lazy#34 fixed), set5 58->59 (we#36 fixed),
set1/set2/set4 unchanged, no correct trail flipped on 221 trails.
BUT 6 synthetic guards broke (am->an, ask->an x2, hello->hell x2,
dwell-gate guard): constant-speed synthetic trails have zero endpoint
evidence, so their lift-off anchors vanished.

## The deep finding (decides the follow-up design)

45% of CORRECT short-word (<=3 letters) real lift-offs are ALSO
isolated (40/89) — and those words still decode fine (geometry +
frequency carry them). The lift-off anchor is load-bearing ONLY in
constant-speed synthetic trails, i.e. the guards' frame is physically
unrealistic, not the real distribution. Distance-band alternatives
measured and rejected as curve-fits: correct trails land 0.34-0.44kw
off the key routinely (over s=0.41, lazy y=0.41), disease cases sit
at 0.42-0.52 — no principled cut exists.

## What's needed to land this (NOT done — needs its own session)

1. Re-state the 6 guards with realistic decelerating lift-off tails in
   the synthetic builders (trailThrough, dogTrailWithSpeedDip, the
   realistic-trail builder) — assertions unchanged, physics realistic.
2. Capture real am/ask/hello-class short-word trails (deliberate vs
   drift lift-offs) to verify the anchor-free path on real data —
   the corpus currently has NO real trails for the words the guards
   protect, so landing the grading today would be unverifiable risk.
3. The post-word drag class is the biggest measured target (7 trails
   across sets 3/4/5); the tail-cap rejection data is in Addendum 4 —
   any attack must keep prefix-impostors (not/mit) charged.

## Side finding: set-5 contains NO retries (65 records = 66 words - tapped 'a')

Verified: 66 sentence words total (s4=7, s9=5 — the brief's s4=6/s9=6
miscounted); s8's one-letter 'a' was tapped, leaving 65 swipe records,
a gapless pass. onSwipeDecoded fires BEFORE the commit-threshold check
(KeyboardScreen.kt:604), so silence swipes are recorded — a failed
attempt can only vanish if it never touched a letter key
(trailStart < 0). No duplicate-intent pairs; no within-sentence gap
>2s (set-4's two confirmed retries show 5.5s/3.4s pauses). The
remembered retries are the set-4 session (excellent #23/#24, example
#25/#26 — both wild first attempts, silence by design, retry
succeeded); set-5's equivalent cases (minimum #13 silence, dog #8
wrong-commit doping) were likely tapped out, which leaves no record.

# ADDENDUM 6 — lift-off grading LANDED (feature/endpoint-evidence, 2026-07-27)

Everything Addendum 5 said was needed is now done, in three commits:

1. **d921c1a** — harness set 6: Philip's short-word capture (40 records,
   paragraph "I am well and we go up the hill to ask if you will fix it.
   Hello, it is fun.", 19 swipeable words x 2 passes). Pass 1 = deliberate
   stop on each last letter, pass 2 = natural drift lift-offs. #34/#35
   labeled '-' (echo swipes of 'it' by timestamp+i->t geometry — probable,
   Philip to confirm). Baseline 34/38: pass 1 18/19 (and->amd), pass 2
   16/19 (we->were 0.03, you->yoy, hello->help 0.01) — the predicted
   deliberate-vs-drift contrast, captured.
2. **b512bbf** — the 6 guards re-stated with realistic decelerating
   lift-off tails in all three synthetic builders (assertions unchanged).
   Two physics lessons from the measurement loop:
   - The tail points must keep CREEPING forward (3+2+1px, gaps 24/40/64ms,
     ~128ms < DWELL_DOUBLE_MS). A fully STATIONARY tail reaches into the
     final leg through the 0.35kw salience window (window arc runs out, so
     the whole tail duration lands in mid-leg windows) and merges the last
     turn's region into the end region — it ate "hello"'s double-L dwell.
   - The realistic builder's 0.3 speed floor had hello's L-turn dwell
     sitting EXACTLY on the 300ms doubling threshold (any duration
     perturbation flips it — the tail's +3% shaved it to 259, guard flipped
     hello->help). Floor now 0.25; L dwell = 347 with margin. Side-effect:
     a few more regions cross 300ms and double (jumps's u, follow's second
     o) — assertions all hold, keys stay compatible.
3. **4b59d35** — the grading itself: an isolated lift-off region
   (region.from == n-1, i.e. no measured salience chains into the last
   point) emits no salient key. Touch-down keeps its anchor
   unconditionally. KDoc + AGENTS.md updated; ratchets bumped honestly.

## Measured result (full suite 226 green after each commit)

| set | before | after | what moved |
|-----|--------|-------|------------|
| 1 | 13/17 | 13/17 | unchanged |
| 2 | 32/36 | 32/36 | unchanged |
| 3 | 34/37 | **35/37** | lazy #34 fixed (lay's free y-anchor dropped); mother x2 shuffle impostors (#3 silence via 'misinterpret', #21 wrong-commits 'norbert') — cull class, untouched |
| 4 | 60/67 | 60/67 | unchanged (drag-class misses, parked for PLAN-DRAG) |
| 5 | 58/65 | **59/65** | we #36 + quick #52 fixed; had #60 FLIPPED to has (by 0.10) — the symmetric cost: also no deceleration, but the drift ended 0.38kw from the RIGHT key (d), so the dropped anchor was luck helping a thin margin, not evidence |
| 6 | 34/38 | **35/38** | we #22 fixed (same r-anchor disease). Pass split: deliberate 18/19 unchanged, drift 16->17/19 |

Net +3 across sets, no ratchet lowered. The deliberate-vs-drift contrast
verifies the design: deliberate stops decelerate, so their end regions
are non-isolated and keep their keys (pass 1 untouched); drift lift-offs
lose the free anchor (pass 2 gains).

## Remaining misses, all sets (for the next session's prioritization)

- Post-word drag class (PLAN-DRAG.md, parked for review): set3 mother x2,
  set4 the #48 / past #35 / nine #31 / nice #32, set5 dog #8 / minimum
  #13 / his #14 / fix #40.
- Mid-trail bottom-row dip (the excellent-overshoot family): set6 and #2
  dips to 0.40kw from M (and V) mid-trail, so m-words (amd/ahmed) win —
  the trail genuinely visits the m region, this is geometry, not salience.
- Frequency coin-flips: set5 past #33 (part 0.11), set6 you #30 (yoy).
- set5 had #60 (has 0.10) — the grading's known symmetric cost; set6
  hello #36 (help) is the same shape (isolated drift lift-off near the
  right key) but was already a miss pre-grading — the margin widened,
  the outcome didn't change.
- set4 excellent #23 + example #25 (wild first attempts, silence by
  design) + how #56 (control-sentence breach).

## Addendum 6: the 'keyboard' overshoot-and-return fix (last-letter lift-off re-match)

Data: 14 captured 'keyboard' swipes (`swipe_trails_word_keyboard.jsonl` in
Philip's Downloads — the word was practically untypable by swipe).

### Autopsy

- Stock decoder: 7/14 commit. The 7 misses are all SILENT with 'keyboard'
  as top-1 at 2.295–3.167 — just over MAX_COMMIT_SCORE 1.8.
- The signature is overshoot-and-return on the final D: first-basin
  matching locks d at the first approach mid-trail (charged
  0.286–0.939kw); the finger's genuine return visit (often the trail's
  closest approach to the key) sits in a LATER basin the first-basin rule
  can never reach, and the post-match arc pays unexplained-tail on top.
- This is NOT the excellent-class pile-up (multi-term accumulation landing
  1.8–2.7 with no dominant term): here one structural mis-match produces
  most of the cost, and fixing the match drops totals by 0.9–1.5 in one
  move.

### The lever

The last letter — and only it, so no stolen match can cascade — may
re-match into the basin still open at the trail's last point. Gates, all
measured: a basin must have closed (depart-and-return), the re-match must
beat the stock match, and it must land within REBASIN_RADIUS_KEYS 0.8kw.
~30 lines + constant in SwipeDecoder.kt; KDoc carries the reasoning.

### Measured (production decoder; all six fixture sets + the 14 trails)

- keyboard set: **12/14 commit** (was 7/14). Residuals #0 (1.905) and #7
  (1.909) are a DIFFERENT class: backtrack-dominated (r→d leg btrk
  1.45/0.99kw over 22/18 points) — the finger zigzags, and no endpoint
  lever fixes a zigzag. Carried by the failed-swipe alternates strip
  (phase 2, same plan).
- Fixtures: **234/260 → 237/260** (13/32/34/60/62/36). Flips: +dog #8,
  +his #14, +fix #40 (set5), +and #2 (set6) — all overshoot-and-return
  lift-offs; −lazy #34 (set3, lazy→last): a LOWERED ratchet, explicitly
  signed off by Philip — the trail's lift-off basin sits 0.41kw from Y vs
  0.49kw from T (genuine geometric ambiguity) and 'last' (rank 136)
  outranks 'lazy' (rank 4711), so frequency arbitrates exactly as the
  signed-off straight-trail rule prescribes.
- Radius grid {0.5, 0.7, 0.8, 1.0}: 0.5/0.7 miss dog #8 (236/260); 1.0
  flips set1#15 lazy→kay (breaks the set-1 13-ratchet). 0.8 is the
  max-win point with no extra loss.
- Confidence recalibration (a real re-run of the margin table, not
  hand-moved numbers): 9/17 wrong flagged, 15/237 correct (6.3%) at the
  unchanged 0.25 knee — denominator changes, not flag-rate changes (four
  wrong commits became correct swipes; the wrong pool shrank 20→17).
- Synthetic guards (`swipe/SwipeRebasinTest.kt`): overshoot-and-return
  commits the intended word clear of its plural; a drift lift-off near a
  foreign key summons nothing; a wild excursion still culls.
- The investigation's instrumented replica remains parity-exact with the
  production decoder at the production radius (max top-5 score delta
  4.8e-7 over the 14 trails).

### New measured dead ends

- **Ungated re-matching** (no basin-closed / closer-than-stock / radius
  gates, or re-matching non-final letters): impostors re-claim foreign
  end-keys. The gates exist because the geometric ambiguity is real —
  lazy→last happened WITH the gates.
- **Salience/dwell evidence gates at the re-match point**: re-silence the
  genuine overshoots — the finger slides through the return without
  lingering, so evidence-gating rejects exactly the class it was meant to
  rescue.

### The plural contest (synthetic-probe lesson)

On clean synthetic overshoot trails the live competitor is the PLURAL
("keyboards"): it matches d as a MIDDLE letter at the first pass and parks
s at the trail-end clamp (tail free, distance ~1.1kw spread over 9
letters). On a collinear overshoot (past d along the leg) the re-match
trades the tail for backtrack (~0.68kw on the return leg) and gains only
~0.15 — the plural still wins; on a side overshoot (return not opposed to
the leg) the re-match gains ~0.84 and keyboard wins 1.44 clear. Lessons:
overshoot geometry decides which term eats the win, and longer-word
competitors parking at the clamp are the pressure to check in guard
design. On the real 14 trails the plural never stole (messy prefixes
scale the extra-letter cost up).

### Superseded "remaining misses" notes (previous session's list)

- set5 dog #8 / his #14 / fix #40 and set6 and #2 were filed under
  "post-word drag class (PLAN-DRAG)" / "mid-trail bottom-row dip" — all
  four are FIXED by the re-match: their real disease was the last-letter
  overshoot-and-return (and #2's m-region dip note was geometry-true but
  not the killer). PLAN-DRAG's remaining members: set3 mother x2, set4
  the #48 / past #35 / nine #31 / nice #32, set5 minimum #13.


---

# ADDENDUM 7 — hello→help: the end-key surcharge (LANDED 2026-07-30)

Source: Philip's report — swiping 'hello' very often commits 'help' although
his finger never touches P. Data: 13 fresh captured trails
(`swipe_trails_word_hello.jsonl`, Downloads). Investigation branch
`investigate/hello-help` (report-only), implementation approved as Option A.

## Capture contents and the regression verdict

13/13 trails intended 'hello'. Pre-lever commits: **help x6, hell x2,
hello x5** — 8/13 wrong, hello-vs-help margins 0.02-0.13. The capture
reproduces exactly under the current decoder (max score delta 0.0), so it
was recorded WITH the lift-off re-match merged — and the re-match is still
ruled out three ways: (a) A/B REBASIN_RADIUS_KEYS 0.8 vs 0.0 — zero flips,
every score identical; (b) per-candidate the re-match never fires for
hello/help/hell (`basinsClosed=0` for p on all 13 — the trail approaches O
monotonically, distance-to-P falls through the whole final approach, no
depart-and-return basin ever closes, gate 1 can't open); (c) set6#36
archived the same flip (margin 0.01) BEFORE the re-match branch existed.
**Pre-existing disease, not a regression.**

## The mechanism (term-by-term autopsy, all 13 trails identical in shape)

Representative trail #0 (help -1.4112 beats hello -1.2795): both words
tunnel the trail perfectly (conformance ~0, backtrack ~0, tail 0, head 0).
The entire geometric case against 'help' is:

- the unvisited p's first-basin distance (0.81-0.95kw — O and P centers are
  1.0kw apart) **diluted by the per-letter mean to ~0.22**;
- a +0.30 missed-salient for o, present only when the lift-off region is
  non-isolated (8/13 trails; the other 5 have no o evidence at all);
- alignment +0.04 and length bonus +0.02.

Against ~0.59 of geometry stands the frequency prior: help rank 163 vs
hello rank 1905 = a **constant +0.676** (`3.0*ln(1905/163)/ln(maxRank)`).
Frequency wins by ~0.1 on six trails.

Why the geometric terms can't charge the neighbor key harder: the
first-basin p match happens EARLY, while the trail (heading up-left to O)
is still near the L->P corridor — the leg's points stay inside the free
tunnel (conformance 0), and the actual O pass lands in the free 1.5kw tail
slack (measured post-match arc 0.8-1.2kw). Direction can't separate
adjacent same-row keys either: L->O and L->P differ only in +-0.5
horizontal, so the up-left final motion has POSITIVE projection on both
legs (0.86 vs 0.38) and backtrack is ~0 for both words (opposed-vs-L->P
arc measured 0.00-0.04kw on all 13).

The hell wins (#10/#12) are the same coin from the other side: those
lift-offs are ISOLATED (graded anchor emits nothing -> salient=[h,e,l]),
so hell wins on frequency (rank 800), the alignment denominator (3/4 vs
3/5) and dilution, even though hello's o basin is genuinely visited at
0.11-0.13kw.

**Class**: frequency-overrule-on-sparse-end-evidence (sibling of once->one
Addendum 3, past->part), sharpened by "the differing key is an unvisited
NEIGHBOR of the visited end key, and its only cost is a diluted distance
charge".

**Why the guards didn't catch it**: both hello guards (realistic-trail +
custom-words test) drive through key centers with a decelerating forward
creep — a deliberate-stop model (set6 pass-1 shape), where the non-isolated
end region makes o salient and help pays +0.9 missed-salient (hello margin
0.35). The failing shape is the drift lift-off (pass-2): weak/no o
evidence and a p basin at 0.81-0.95kw. No synthetic guard modeled a drift
lift-off ending short of the last key.

## The lever (LANDED): end-key surcharge

`max(0, lastLetterMatchDist - TUNNEL_RADIUS_KEYS) * END_KEY_SURCHARGE_WEIGHT`
(0.5), added UNDILUTED after the lift-off re-match (the re-matched distance
is what gets charged). Framing: the tunnel grants position freedom
mid-word, but a word's claim to END on the trail should cost when the
trail ends off its last key.

Measured (production-patched, this worktree):

- hello trails: **10/13** (all six help commits flip; residuals #10/#12
  hell + #11 help — the isolated-lift-off family, carried by the
  alternates strip + crossed-letters proofreader evidence: 13/13 trails
  crossed o, none crossed p). set6#36 unchanged (its drift genuinely
  ended <=0.5kw from P — decoder defensible, correctly not charged).
- Fixture floors UNCHANGED: **13/32/34/60/62/36** across w=0.4-0.7.
  Exposure audit: 42/260 intended words pay small surcharges at w=0.5,
  zero flips <=0.7 (competitors pay their own surcharges).
- **Binding constraint**: set5 dog#8 — its lift-off-re-matched g sits at
  0.76kw and pays (0.76-0.5)*w: margin 0.202 -> 0.072 at w=0.5
  (production-re-confirmed 0.0715), 0.02 at 0.7, FLIPS at 0.8 (set5 61).
  0.5 is mid-plateau with headroom.
- Documented tension: the re-match licenses last-letter matches up to
  REBASIN_RADIUS_KEYS 0.8kw while the surcharge charges past 0.5kw — at
  0.5 the max surcharge on a re-matched letter is 0.15, tolerable.
- Confidence calibration re-run (full table in LOW_CONFIDENCE_MARGIN's
  KDoc): wrong pool 17->16 — the surcharge pushed the signed-off
  lazy->last wrong commit (set2#35, pre-lever 1.647, margin 0.13) past
  MAX_COMMIT_SCORE into silence; ratchets 9/17 -> 8/16 and 15/237 ->
  14/237 (5.9%), denominator changes, not flag-rate changes. The constant
  stays 0.25: 0.30 buys one flag (a single 0.27-margin commit) for zero
  measured FPs — a one-commit artifact, not a knee shift.

## Newly measured dead ends (from the investigation's options report)

- **Frequency surgery (Option C)**: global weight 2.8 -> hello 5/13 AND
  set5 quick#52->wick re-breaks (61/62 — the frequency prior's own signed-
  off win); 2.6 -> 6/13, same break. Pair-specific rank surgery needs
  Delta>=0.13-0.6 against a genuine corpus gap (help IS more frequent) —
  unprincipled. Rejected.
- **Evidence resurrection (Option D)**: crossed-last-letter credit (L2) ->
  4/13, breaks set5 (61) + set6 (35, re-opens am->an); isolated-lift-off
  anchor at 0.5kw (L3) -> 6/13 (fixes the hell pair) but re-breaks set6
  we#22->were (the grading session's signed-off win); L1+L3 -> 13/13 but
  set1 12 / set2 31 / set5 61. A 0.3kw distance-band anchor would separate
  hello#10/#12 (0.18/0.25kw) from we#22 (0.5kw) but is exactly the
  curve-fit rejected in Addendum 5 ("no principled cut exists"). Rejected.
- **Final-leg direction/backtrack (Lever A of the report)**: measured
  dead on the geometry — adjacent same-row legs are near-parallel, the
  trail's final motion has positive projection on both (see above).

## Guards landed

`SwipeEndKeySurchargeTest` (new file, merge-clean rule): (1) drift
lift-off toward the neighbor commits hello — the synthetic shape was
verified to commit help PRE-lever (help -1.693 vs hello -1.532; a guard
that never failed pins nothing), tuned into the real-trail envelope
(lift-off 0.22kw from O / 1.18kw from P, p basin 0.90kw, salient
[h,e,l,o]); (2) genuine neighbor-end stays help (through P's center —
surcharge exactly zero, margin >1.0, both pre- and post-lever).

## Not verified (carried from the report into the commits)

- **No genuine 'help' swipe captured** — "a real help trail never pays the
  surcharge" is geometric inference pinned only by synthetic guard 2.
  Philip may record help/hell trails to close it; if a real help trail
  pays, the radius/weight needs a revisit.
- **#11 residual accepted** (stays help at w=0.5, margin was 0.575) —
  isolated-lift-off family, carried by strip + proofreader.
- Yellow-flash false-positive uptick on corrected hello commits (margins
  0.06-0.18 < 0.25) — cosmetic; the calibration table was re-run (above)
  and the constant stayed.


# ADDENDUM 8 — go→to: the start-key surcharge (LANDED 2026-08-01)

The mirror image of Addendum 7, on the START key. Branch
`feature/start-key-evidence`; plan approved as PLAN.md draft 2 (scratch,
uncommitted).

## Symptom and evidence

Philip swipes 'to go to'. On the G→O swipe (intended 'go') the finger
never comes within 0.73 key-widths of T, yet the decoder committed 'to'
in 6 of 10 attempts. Evidence: `swipe_trails7_to_go_to_philip.jsonl` —
24 trails (keyWidth 169), classified by touch-down: 14 to-intended
(start 0.02-0.20kw from T) and 10 go-intended (start 0.05-0.31kw from G;
0-based lines 1,4,7,10,13,16,18,20,21,22). Measured replay baseline:
**18/24** — 'to' wins go-lines 4,7,18,20,21,22 (all margins ≤0.28), 'go'
wins 1,10,13,16. Harness landed test-only first (M0, ratchet 18).

## Diagnosis (verified against the code, not re-derived)

Geometry genuinely favors 'go' by ~0.8-1.0 (start-basin distance
+0.2-0.5; alignment + missed-salient +0.57 with salients [g,o]), but 'to'
collects a CONSTANT +1.063 frequency bonus (rank 2 vs 96,
`FREQUENCY_WEIGHT` 3.0) and wins. Three structural dilutions keep
geometry weak:

1. the ~1.0kw start-key miss is halved by the per-letter mean;
2. no start-side counterpart to `END_KEY_SURCHARGE_WEIGHT` existed — a
   first letter matched at trail index 0 also escapes the unexplained-head
   charge (head arc = 0), and the tunnel gives the off-line T→O leg 0.5kw
   free;
3. `ALIGNMENT_MIN_DENOMINATOR` 3 caps go's perfect two-key alignment at
   2/3 (−0.533 instead of −0.8).

Measured baseline per-term breakdown on all 10 go trails: conformance,
backtrack, tail and head are 0 for BOTH words — the whole contest is
basin/alignment/missed-salient vs the frequency constant.

## The lever (LANDED): start-key surcharge

`max(0, firstLetterMatchDist - TUNNEL_RADIUS_KEYS) * START_KEY_SURCHARGE_WEIGHT`,
added UNDILUTED, charged on the stock first-basin distance
(SwipeDecoder.kt:325-327, constant :786 with the full measured KDoc). No
start-side re-match exists or is needed: the first letter's scan starts
at index 0 and fully explores the touch-down basin, so no later closer
basin can exist that stock matching cannot reach — the end-side
license/charge tension has no start-side counterpart (measured proof in
the M5 audit below).

Weight grid (replica parity-exact vs production at w=0 and w=0.7 — all
seven per-set counts and every trail's top-1 word; the w=0.3-0.6/0.8
cells are replica-only, w=0.7 row production-confirmed via the accuracy
harness):

| weight | set1 | set2 | set3 | set4 | set5 | set6 | set7 |
|---|---|---|---|---|---|---|---|
| 0 (baseline) | 13 | 32 | 34 | 60 | 62 | 36 | 18 |
| 0.3 | 13 | 32 | 34 | 60 | 61 | 36 | 19 |
| 0.4-0.6 | 13 | 32 | 34 | 60 | 61 | 36 | 21 |
| 0.7-0.8 | 13 | 32 | 34 | 59 | 61 | 36 | 23 |

- **Binding constraints** — the only two intended words in all 284 trails
  whose >0.5kw start miss is not already outvoted: set5#52 quick (margin
  0.034 vs a 0.805/w differential, flips at w≈0.04) and set4#54 quick
  (margin 0.236 vs 0.366/w, flips at w≈0.64). Both are genuine q/w
  touch-down aim slips: the trail physically starts ON the W key (0.09kw
  and 0.29kw from its center), so 'wick' is the honest read — their
  signature is identical to the impostor's and no weight separates them.
- set7#21 never flips: t basin only 0.73kw off → excess 0.23kw vs a 0.272
  margin → needs w≈1.2, unreachable.
- **Kill-criterion stop, honestly reported**: the plan's criterion was
  "≥4 of the 6 go losses flipped WITHOUT dropping a set 1-6 ratchet".
  Unreachable — every weight ≥0.3 already drops set5 62→61 (its binding
  constraint flips at w≈0.04). The stop was reported with the grid;
  **Philip chose w=0.7 explicitly** ("do the big fix"), signing off both
  quick→wick flips as accepted costs. Ratchets: set4 60→59 and set5 62→61
  LOWERED with sign-off comments naming the trails; set7 18→23 (earned).

## Flips at w=0.7 (all seven sets, 284 trails)

- **5 FIX**: set7 go-lines #4/#7/#18/#20/#22 (to→go). Mechanism: 'to'
  pays (tBasinDist − 0.5kw) × 0.7 ≈ 0.16-0.46 undiluted; 'go' pays 0
  (touch-down 0.05-0.31kw from G, inside the free radius). #21 residual
  stays 'to' (above).
- **2 LOSS**: set4#54 and set5#52 (quick→wick), the signed-off costs.
- **ZERO wrong→wrong.** Designed coin-flips checked by name and unmoved:
  past/part ×2, and no straight-trail two-letter tie moved (the signed-
  off straight-trail rule is untouched — this lever only fires when
  geometry is diluted below the prior, which is exactly the class the
  plan scoped).

## Confidence calibration re-run (honest)

Full table in `LOW_CONFIDENCE_MARGIN`'s KDoc and
`SwipeConfidenceCalibrationTest`. Committed pool 253 (sets 1-6; set7 is
OUTSIDE the calibration domain — open item below): 237+16 → **235 correct
+ 18 wrong**. Wrong flagged 8/16 → **8/18**: the two new wick commits
split (set5#52 margin 0.021 flagged; set4#54 0.530 not), and set4#32
'notice' LOST its flag (0.068→0.473 — its runner-up now pays a
surcharge). Correct flagged 14/237 (5.9%) → **9/235 (3.8%)**: −4 widened
past 0.25 (set1#6, set1#12, set3#6, set5#31), −2 quicks left the correct
pool, +1 set6#39 fun (0.406→0.238). Ratchets: `MIN_WRONG_FLAGGED` stays
8, `MAX_CORRECT_FLAGGED` 14→9; `LOW_CONFIDENCE_MARGIN` stays 0.25.

## Newly measured dead ends

- **L2 (touch-down re-basin analog of the lift-off re-match)**: M5
  exposure audit over all 284 trails found 21 geometric later-closer-
  basin hits — but a start-side re-match trades first-basin distance for
  HEAD ARC (matching later un-zeros the head), e.g. set6#32: distance
  gain 0.205 vs head charge 9.73 → net −9.53. The end-side re-match
  exists because the trail can END before the last letter's scan reaches
  its basin; the first letter's scan STARTS at index 0, so stock matching
  already owns the touch-down basin and there is no license to hand out.
  Dead end — no license counterpart.
- **L3 (unexplained-head charge firing when the first letter matches at
  index 0 but touch-down is far)**: grid strictly dominated by L1 — same
  binding quick losses at 0.7/1.0 with no additional go fixes, plus a
  wrong→wrong flip at 1.0. Rejected.

## Guards landed

`SwipeStartKeySurchargeTest` (new file, merge-clean rule): (1) go guard —
synthetic go-shaped trail (waypoints (490,125),(690,92),(840,54) on the
set7 geometry) verified to commit 'to' PRE-lever at w=0 (to −2.293 vs go
−2.161 — a guard that never failed pins nothing), commits go post-lever
(go −2.161 vs to −2.048); (2) genuine-to guard — T→O through both
centers pays exactly zero surcharge and stays 'to' (−3.379, margin 1.12,
both pre- and post-lever).

## Not verified (carried into the commit)

- **The 0.5-0.73kw start band is pinned only by synthetic guard 2** — no
  captured trail starts 0.5-0.73kw off its intended first key; if a real
  one ever flips, the radius/weight needs a revisit (same shape as
  Addendum 7's open help-trail item).
- **set7#21 residual accepted** (stays 'to', margin 0.272 vs max
  reachable surcharge 0.16) — carried by the alternates strip +
  crossed-letters proofreader evidence.
- **4 of the 5 fixed go commits flash yellow** (post-fix margins <0.25) —
  cosmetic, but set7 is OUTSIDE the confidence-calibration domain (253
  committed = sets 1-6 only); whether to fold set7 into the calibration
  is an open decision for Philip.
- **Grid cells w=0.3-0.6 and 0.8 are replica-only** — the replica was
  parity-exact vs production at w=0 and w=0.7 (all seven counts, every
  trail's top-1); the intermediate cells were not re-run in Kotlin.
- No new on-device verification: unit replay only, per the plan's
  harness-first discipline.

---

# ADDENDUM 9 — joker/lots/movies: the tail-slack fix (TAIL_ARC_FREE_KEYS 1.5→0.5, LANDED 2026-08-02)

Branch `fix/tail-slack-0.5`. Eighth capture landed harness-first at the
pre-fix baseline (commit ac16a76, set-8 fixture + intents + ratchet 30),
then the one-constant decoder change bumped the ratchets. Every number
below is the REAL decoder (the investigation probe was parity-exact —
parity delta 0.0 over 1420 scores — and is deleted; the ratchets are the
audit against production code).

## Symptom and evidence

`swipe_trails8_joker_lots_movies_philip` (142 records, 96 scored): the
joker/lots/movies paragraph — 36 joker + 24 lots + 16 movies scored
swipes — plus five passes of "i'm joker and watch lots of movies".
#0-40 are a→s / a→d / s→e warm-up calibration drags (intent unknown,
`-`); #57-59, #109, #141 are mis-swipes whose honest geometric read IS
a different word, `-` per the set2/set6 precedent (joe/jobs/movie: the
trail never comes within 1.03kw of K on #57-59, #109 is pruned by the
first/last-letter gate, #141 ends ON e). Baseline at the 1.5 slack:
**30/96** — joker 3/36, lots 2/24, movies 5/16, others 20/20.

## Diagnosis (verified term-by-term on the flips)

joke/joe/movie park their last letter one key early on joker/movies
trails: the differing key (r vs e, s vs e — adjacent home/top-row keys)
is an unvisited NEIGHBOR of the visited end key, the same shape family
as hello→help (Addendum 7). But the end-key surcharge cannot fire here:
the parked e IS the visited key, matched well inside the tunnel radius,
so the match distance beyond the tunnel is zero. The convicting evidence
is the trail arc from the parked e to the lift-off (~1.07kw e→r,
~1.05kw e→s) — and that hop rode FREE inside the 1.5kw tail slack. The
tail term, built for prefix-impostors and long drags (mit/mother,
the#48), was blind to the one-key hop that separates a word from its
prefix+neighbor impostor; the frequency prior then decided (joe rank
1689 and joke 2077 vs joker 10462 = constant +0.50/+0.44; movie 691 vs
movies 1661 = +0.24). Measured on all 41 flipped trails: the intended
word's score moves by EXACTLY 0.000 (its own last letter matches at the
trail end — tail arc ~0), the impostor worsens +0.24…+1.00 (median
~+0.6) — the newly-charged band, undiluted.

## The lever (LANDED): tail slack 1.5 → 0.5

One constant. At 0.5 the slack covers lift-off jitter/drift ONLY:
genuine overshoot-AND-return is owned by the last-letter re-match
(REBASIN_RADIUS_KEYS 0.8 — it re-basins the last letter to the lift-off
point, leaving ~0 tail arc), and overshoot WITHOUT return now pays up to
~1.0 on arc that used to ride free. Grid (probe replay, real-code
verified at 0.5; joker/movies fix counts are mis-swipe-insensitive — the
excluded trails never commit joker/movie(s) at any grid point, so the
probe's /40≡fixture's /36 and /17≡/16):

| slack | joker | movies | lots | sets 1-7 flips |
|---|---|---|---|---|
| 1.5 (base) | 3 | 5 | 2 | — |
| 1.0 | 19 | 7 | 2 | set2#31 fixed |
| 0.75 | 24 | 11 | 2 | set2#31 fixed, zero losses |
| **0.5** | **32** | **15** | 2 | set2#31 + set5#60 fixed, zero losses |

Monotone with a wide plateau — 0.5 is no knife-edge, and 0.75 is the
measured fallback if the overshoot-band captures (below) show reliance.
Why Addendum 4's tail rejection does not apply: the post-script rejected
moving the CAP (2.0→1.0/1.5 cheapened EVERY prefix-impostor tail — 'not'
on mother#3 drops 2.45→1.45, silence becomes a wrong commit). The cap is
load-bearing and UNTOUCHED; this change moves the SLACK, the free band
before the cap engages. The levers guard different classes: the slack
licenses short lift-off hops, the cap bounds long drags — prefix-impostor
tails run 2kw+ (capped both before and after), so the mit/not guard is
unaffected (verified: zero flips on sets 1-7 beyond the two intended).
Complement, not conflict, with Addendum 7: help's O pass on hello trails
sat in the old slack (measured post-match arc 0.8-1.2kw) and now pays
0.3-0.7 more — the surcharge's hold on the hello class deepens
(set6#36 stays help, margin 0.273→0.466; no hello fixture moved).

## Flip audit (real decoder, all 429 captured records / 426 scored)

**+41 gains, 0 losses.** 29 joker (#41-47, #50-55, #60-64, #66, #68,
#71-73, #102, #110, #117, #124, #131, #132), 10 movies (#93, #96-99,
#107, #115, #122, #129, #138), set2#31 jumped→jumps (jumped's d→s hop
charged; jumped still holds a +0.17 frequency edge — geometry won it),
set5#60 has→had (has's s→d hop charged +0.30 — reverts the lift-off
grading's documented symmetric cost: the dropped end anchor had been
luck helping a thin margin, and the tail term now charges the geometry
instead). Unscored/wrong→wrong flips, all benign: 8 warm-up drags
(as→add ×6, as→and, as→are) and set8#113 lots life→less (wrong→wrong).
Set totals 13/**33**/34/59/**62**/36/23 + set-8 **69**/96; ratchets
raised for the three moved floors, all others held.

## Residue at 0.5 (documented, accepted)

- joker #65/67/69/70: joe/joke commit, joker rank #3 behind both —
  thin-frequency wins (+0.50/+0.44) outrun joker's geometric edge; the
  strip offers joker. Frequency-limited, not geometry-limited.
- movies #94: movie by **0.010** (movie paid +0.31 of tail, closing
  0.32 of the 0.33 gap — not the last 0.01).
- The five excluded mis-swipes (#57-59, #109, #141): honest geometric
  reads (joe/jobs/movie), user-shape errors, `-` by the settled rule.
- lots 2/24 UNCHANGED — see the dead end below; 'less' ends on the
  trail's end key (tail arc 0), so no tail lever can touch it.

## The lots/less dead end (all levers measured, none survive)

Frequency-shaped, not geometry-shaped: less rank 295 vs lots 1363 = a
constant +0.42, and the trails genuinely pass nearer E than O/T (lots
intent rank #3-19, 'los'/'loss' also ahead). Measured and rejected:
- **Dedouble levers** (drop the doubled end salient): swing exactly ~0.5
  where they fire (#75: less−lots gap 0.583→0.083; #82: 0.765→0.265) —
  but the firing trails' gaps (0.58-0.77) still absorb the swing, and
  the CLOSE trails (#113/#120/#127, gaps 0.13-0.19) have no doubled
  salient to remove. Zero net flips.
- **Alignment-denominator variant** (`max(wordLen, salientCount, 3)`):
  real (joker +8 over the tail-0.5 state, zero fixture cost) but fully
  subsumed by the slack fix — pocketed for a future miss that needs it
  (out of scope for this branch).
- **Dwell-doubling alone**: not worth it.
The handles are custom words + the alternates strip + the proofreader.

## Confidence calibration re-run (honest)

Full table in `LOW_CONFIDENCE_MARGIN`'s KDoc (updated). Sets 1-6,
253 committed: 235+18 → **237 correct + 16 wrong**. Wrong flagged 8/18 →
**5/16**: the two fixed swipes (set2#31, set5#60 — both flagged wrong
commits) left the pool, and set2#13 'juniors' (still wrong) had its
margin widened 0.070→0.258 past the cutoff when its close runner-up
'jumped' started paying its own d→s hop — genuinely less close, not a
masked miss. Correct flagged 9/235 (3.8%) → **11/237 (4.6%)** — the
ceiling RISES under the re-match precedent (fixed swipes and genuinely
narrowed races, not false positives): 'had' joins flagged (0.198);
**set2#26 'pizzas' (+0.793) and set5#32 'ran' (+0.736) are the corpus's
measured 0.5-1.5kw-band exposure** — correct commits that now pay their
own overshoot-and-drift tails, margins narrowed to 0.066/0.013, both
STILL committed correctly, both correctly yellow-flagged; set6#0 'am'
lost its cry-wolf flag (0.125→0.800). Ratchets re-derived:
`MIN_WRONG_FLAGGED` 8→5, `MAX_CORRECT_FLAGGED` 9→11; the constant stays
0.25 (0.30 buys one wrong flag — the 0.258 'juniors' — for zero false
positives, the same one-commit artifact as the last two tables). set8 is
OUTSIDE the calibration domain (sets 1-6) — same open decision as set7.

## Guards landed

- `SwipeRealTrailAccuracyTest` set 8: fixture + intents + ratchet —
  harness-first at the pre-fix 30/96 (commit ac16a76), raised to 69/96
  with the fix; why-comment carries the per-word split and residue.
- Existing floors: set2 32→33, set5 61→62 (with why-comments).
- `SwipeConfidenceCalibrationTest` bounds re-derived (above), full
  per-commit autopsy in the test + KDoc history.
- `SwipeRebasinTest`'s three synthetic overshoot guards (`:110`
  overshoot-and-return commits keyboard with its >1.0 margin pin,
  `:146` lift-off drift near a foreign key, `:159` wild excursion culls):
  RUN, all green, the margin pin UNMOVED — the re-match owns
  overshoot-and-return, so these trails are tail-0 and slack-insensitive
  (measured, not assumed). Full suite 378 green.

## Not verified (carried into the commit)

- **The 0.5-1.5kw overshoot-without-return band has no captured coverage
  by design** — no trail in the 429-record corpus flips on it, but that
  is absence of evidence. The two band-exposure commits (pizzas, ran)
  moved score-only and stayed correct. Gates the landing: a dedicated
  overshoot-heavy capture set (help/hello/loop/poll/look + short fast
  words, ~30-40 trails) replayed at 0.5 as `swipe_trails9_*` BEFORE
  merge; if genuine overshoot-and-drift flips to prefix-impostors, fall
  back to 0.75 (measured profile above) or drop the change.
- **lots stays 2/24 by design** (frequency-shaped; handles are custom
  words + strip + proofreader).
- **Grid cells 1.0/0.75 are probe-only**; 0.5 is real-code verified.
- No new on-device verification: unit replay only, per the plan's
  harness-first discipline (QA build + emulator pass precede merge).

# ADDENDUM 10 — three→the: the mid-word dwell skip charge (LANDED 2026-08-02)

## Symptom and evidence

Philip: 'three' is nearly impossible to swipe — it almost always commits
'the'. New capture set 9 (`swipe_trails9_the_three_philip.jsonl`, 15
trails recorded for this investigation): 8 three-intended, 7
the-intended. On QWERTY R sits between T and E on the top row, so the
'the' path T→H→E passes 0.6-1.0kw directly over R — 'three'
(T→H→R→E→E) is geometrically near-superset of the same trail. The only
real differentiators: the intended-three trails STOP on R mid-word
(measured contiguous stays 200-417ms; the intended-the trails never stop
anywhere mid-word) and the EE ending.

## Diagnosis (verified term-by-term on the misses)

Pre-fix replay: 7/8 three-trails committed 'the', 1 committed 'there'.
'three' WINS the non-frequency contest on all 8 trails (geometry +
alignment + surcharges favor it by 0.28-0.81) and loses the word on
frequency alone by 0.58-1.11: 'the' is rank 1 vs 'three' rank 157 = a
constant +1.39 prior edge, larger than any geometric separation the pair
can produce. Example #1 pre-fix: the −2.708 vs three −2.014 — and
three's salient alignment is a PERFECT 5/5 (`t,h,r,e,e` all salient);
the salient channel already sees the R visit but prices it at 0.3/key,
the crossed-key aim-noise grade set by the 0.6→0.3 halving. That price
is right for a crossing and wrong for a 200-417ms stop: the decoder had
no channel that grades mid-word dwell as deliberate evidence. (The
≥300ms dwell channel exists but only DOUBLES a letter — it cannot say
"this skipped key was visited".)

## The lever (LANDED): contiguous-stay dwell evidence + per-key skip charge

`dwelledKeys()` collects keys the finger deliberately stopped on
mid-word: a contiguous stay ≥ `MIDWORD_DWELL_MS` 150ms within
`DWELL_STATIONARY_KEYS` 0.25kw of some trail point, attributed to the
nearest key within `DWELL_KEY_RADIUS_KEYS` 0.5kw, first/last
`DWELL_EDGE_EXCLUDE_KEYS` 0.75 of arc excluded (endpoint physics —
touch-down settle, lift-off deceleration — is not letter evidence; the
start/end surcharges own those keys). A word that skips such a key pays
`MIDWORD_SKIP_WEIGHT` 1.2 per key, undiluted, outside every
normalization — the mid-word mirror of the start/end-key surcharges.

"Contiguous" is load-bearing: a steady CROSSING, however slow, never
holds a 150ms+ stay inside a 0.25kw radius (the finger keeps leaving the
radius); a genuine hesitation does. Rejected variant, measured: TOTAL
dwell (sum of all time near a key) — the synthetic-trail builders
fake-dwell under any absolute total threshold, 5 guards failed with
winners fruitless/supported/jumpers. The contiguous-stay idiom is the
same one the doubling dwell uses in `salientKeySequence`.

## Grid and plateau (real decoder, full corpus per cell)

Measured plateau: T ∈ {150, 175}ms × w ∈ {1.0, 1.2} — zero losses over
the 426-trail corpus, set9 14/15. The edges: T=125 breaks set5#45
over→ocr (its c-crossing holds a contiguous 125-149ms stay — the
corpus's tightest genuine crossing); T=200 puts set9#5's 200ms R stop on
the edge; w=1.0 leaves set9#5 decided by 0.006; w=1.2 clears it and
higher buys nothing. Radius 0.4 loses #5's R stop (0.43kw off-center),
0.6 starts admitting mere crossings on adjacent keys; arc exclusion
insensitive over 0.5-1.0, 0.75 mid-plateau. Landed: T=150, w=1.2.

## Guard re-timing (builders, not decoder)

The synthetic `realisticTrail` builders in `SwipeDecoderRealisticTrailTest`
and `SwipeDecoderCustomWordsTest` faked mid-word dwells under the new
evidence: at the 8ms base gap a mere slow crossing accumulates a
contiguous ≥150ms stay. Re-timed to a 3ms base gap with the speed floor
0.25→0.15 so turn dwells stay above `DWELL_DOUBLE_MS` (the floor's job,
unchanged) while crossings fall under 150ms (the new requirement).
Precedent: Addenda 5/6 re-timed the same builders when salience evidence
landed. Verified: swipe/follow/jumps/hello all decode correctly under
the re-timed builder with the lever on; no crossed-key dwells fire.

## Flip audit (real decoder, all 441 captured records / 426 corpus + 15 new)

- **set9 7/15 → 14/15**: the seven strongest three-trails flip
  the→three (#0,1,5,6,7,8,13) — 'the' now pays 1.2 for skipping the
  deliberately dwelled R. All 7 the-trails unchanged (they never stop
  mid-word, so no charge fires).
- **set8 69/96 → 79/96**: the lots/less class — Addendum 9's documented
  frequency dead end ("no tail lever can touch it") — resolves on dwell
  evidence: intended-lots trails stop on O and T mid-word, 'less' skips
  BOTH and pays 2.4 undiluted. 9× less→lots + 1× loss→lots
  (#75,76,77,79,80,82,83,84,90,135). The 12 residual lots misses keep
  honest less geometry (intent rank out/#3-5). One unscored mis-swipe
  flips swipe→super (#40), benign.
- **Signed-off coin-flips preserved**: past/part stays 'part' at T=150,
  quick→wick unchanged, set2#31 jumped→jumps and set5#60 has→had
  tail-slack wins unchanged. Zero losses anywhere; sets 1-7 floors held
  at their pre-change values (13/33/34/59/62/36/23).

## Residue (documented, accepted)

- **set9 #14 → 'there'** (wrong→wrong flip): 'there' CONTAINS r, so it
  escapes the skip charge and outranks 'three' on frequency by 0.08;
  three is #2 (−1.62 vs −1.70), carried by the alternates strip. Its R
  stay is 159ms — 9ms over the threshold, the corpus's thinnest dwell
  margin. No genuine 'there'-intended swipe has been captured, so the
  there-class containment escape is measured only on this trail.
- **lots residual 12/24**: trails whose honest geometric read IS less
  (intent rank out or #3-5) — user-shape errors, not decoder failures
  (the set2/set6 precedent).

## Guards landed

- `SwipeRealTrailAccuracyTest` set 9: fixture + intents (intents
  inferred from the swipe geometry, confirmed by Philip) + ratchet —
  harness-first at the pre-fix 7/15, raised to 14/15 with the fix;
  why-comment carries the flip list and the #14 residue.
- set8 ratchet 69→79 with the lots-class why-comment.
- `SwipeConfidenceCalibrationTest` UNCHANGED — passed unmodified, no
  margin crossed 0.25 (measured, not assumed).
- Full suite 394 green.

## Not verified (carried into the commit)

- **No emulator/on-device run** — unit replay only, per harness-first
  discipline (QA build + emulator pass precede merge).
- **Thin margins at the T floor**: #14's R stay is 9ms over 150ms and
  set5#45's c-crossing is 1-25ms under it; a future capture set with
  faster genuine stops (or slower genuine crossings) could force T to
  175 (measured profile above — set9 stays 14/15, set8 +8 not +10 at
  175, so the fallback costs 2 lots flips).
- **The 'there' containment escape** has exactly one captured instance
  (#14, three-intended). A there/they/them capture set would tell
  whether words CONTAINING the dwelled key need their own grade.
