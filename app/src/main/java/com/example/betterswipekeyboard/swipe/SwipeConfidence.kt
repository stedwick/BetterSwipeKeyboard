package com.example.betterswipekeyboard.swipe

/**
 * Two-tier swipe feedback: how a completed swipe should be flashed back to
 * the user (colors + fade live in `KeyboardScreen`; this is the pure
 * classification, unit-tested).
 */
enum class SwipeConfidence {
    /** Committed, no close runner-up: flash nothing. */
    CONFIDENT,

    /** Committed, but the runner-up was close — ambiguous swipe, flash yellow. */
    LOW,

    /** Nothing committed (no candidate below [MAX_COMMIT_SCORE]) — flash red. */
    FAILED,
}

/**
 * Low-confidence margin cutoff: a commit flashes yellow when
 * top2.score - top1.score is below this. Calibrated on the six captured
 * real-hand trail sets (254 committed swipes, 20 of them wrong, intents as
 * ground truth) — margin is the signal, NOT absolute score (wrong commits
 * score anywhere from -2.17 to 1.65). Recalibrated after the last-letter
 * lift-off re-match ([REBASIN_RADIUS_KEYS]): 237 correct + 17 wrong — the
 * re-match turned four formerly wrong commits into CORRECT ones (they are
 * not lost flags, they are fixed swipes) and one formerly correct commit
 * into a wrong one; the flag rates are essentially unchanged (9/17 ≈ 53%
 * of wrong vs 11/20 = 55%, 6.3% vs 6.0% of correct at the knee).
 * Re-measured after the end-key surcharge ([END_KEY_SURCHARGE_WEIGHT],
 * the hello->help fix): 237 correct + 16 wrong — the surcharge pushed the
 * signed-off lazy->last wrong commit (set2#35, pre-lever score 1.647) past
 * MAX_COMMIT_SCORE into silence, a denominator change, not a flag-rate
 * change; the flagged wrong count drops 9 -> 8 only because that flagged
 * commit (margin 0.13) no longer exists. Re-measured after the start-key
 * surcharge ([START_KEY_SURCHARGE_WEIGHT], the go->to fix): 235 correct +
 * 18 wrong — the surcharge flipped the two signed-off q/w aim slips
 * (set4#54, set5#52) from correct to quick->wick wrong commits
 * (denominator changes), and widened margins on four formerly flagged
 * correct commits past 0.25 (their runner-ups pay the start surcharge),
 * improving the false-positive rate 5.9% -> 3.8%:
 *
 *   margin < M    wrong flagged   correct flagged
 *   0.10          6/18            2/235  (0.9%)
 *   0.15          8/18            5/235  (2.1%)
 *   0.20          8/18            8/235  (3.4%)
 *   0.25          8/18            9/235  (3.8%)  <- chosen
 *   0.30          9/18            9/235  (3.8%)
 *   0.35          9/18            13/235 (5.5%)
 *   0.40          9/18            16/235 (6.8%)
 *   0.45          10/18           19/235 (8.1%)
 *
 * 0.25 stays the knee: 0.30 buys exactly one wrong flag (a single
 * 0.27-margin commit) for zero measured false positives — a one-commit
 * margin artifact, not a knee shift, and the false-positive rate at 0.25
 * improved to 3.8%; 0.35 still breaks into 5%+. The wrong commits with
 * healthy margins (≥ 0.44, up to 1.01) remain unreachable at any
 * defensible rate. Adding an absolute-score band (score > 1.2) was
 * measured on the original table and rejected (one more wrong commit for
 * one more false positive and a second knob); re-measure before
 * reconsidering it.
 */
const val LOW_CONFIDENCE_MARGIN = 0.25f

/** Pure, unit-tested: classify a completed swipe from the decoder's top-2. */
fun swipeConfidence(results: List<ScoredWord>): SwipeConfidence {
    val top = results.firstOrNull() ?: return SwipeConfidence.FAILED
    if (top.score >= MAX_COMMIT_SCORE) return SwipeConfidence.FAILED
    val margin = results.getOrNull(1)?.score?.minus(top.score)
    // No runner-up means no competition: confident.
    return if (margin != null && margin < LOW_CONFIDENCE_MARGIN) {
        SwipeConfidence.LOW
    } else {
        SwipeConfidence.CONFIDENT
    }
}
