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
 * of wrong vs 11/20 = 55%, 6.3% vs 6.0% of correct at the knee):
 *
 *   margin < M    wrong flagged   correct flagged
 *   0.10          5/17            2/237  (0.8%)
 *   0.15          8/17            7/237  (3.0%)
 *   0.20          8/17            11/237 (4.6%)
 *   0.25          9/17            15/237 (6.3%)  <- chosen
 *   0.30          9/17            15/237 (6.3%)
 *   0.35          9/17            20/237 (8.4%)
 *   0.40          10/17           24/237 (10.1%)
 *   0.45          11/17           29/237 (12.2%)
 *
 * 0.25 is the knee of the trade, raised from 0.15 when Philip found the
 * yellow flash too rare: the recalibration leaves the trade's shape
 * unchanged — 0.30 flags nothing new over 0.25, 0.25→0.40 buys exactly one
 * more wrong commit for nine more false positives, and 0.45 breaks into
 * double-digit false positives. The eight wrong commits with healthy
 * margins (≥ 0.37, up to 0.53) are unreachable at any defensible rate.
 * Adding an absolute-score band (score > 1.2) was measured on the original
 * table and rejected (one more wrong commit for one more false positive
 * and a second knob); re-measure before reconsidering it.
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
