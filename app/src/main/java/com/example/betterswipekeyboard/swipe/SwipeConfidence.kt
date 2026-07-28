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
 * top2.score - top1.score is below this. Calibrated on the four captured
 * real-hand trail sets (152 committed swipes, 13 of them wrong, intents as
 * ground truth) — margin is the signal, NOT absolute score (wrong commits
 * score anywhere from -1.63 to 1.65):
 *
 *   margin < M    wrong flagged   correct flagged
 *   0.10          7/13            1/139  (0.7%)
 *   0.15          8/13            4/139  (2.9%)  <- chosen
 *   0.20          9/13            6/139  (4.3%)
 *   0.25          10/13           9/139  (6.5%)
 *
 * 0.15 flags most wrong commits while crying wolf on under 3% of correct
 * ones — and those flagged correct commits (margins 0.06–0.15) are
 * genuinely ambiguous coin-flips where "maybe re-swipe" is honest. The
 * three wrong commits with healthy margins (~0.53) are unreachable
 * without an ~18% false-positive rate. Adding an absolute-score band
 * (score > 1.2) would catch one more wrong commit for one more false
 * positive and a second knob — measured, rejected.
 */
const val LOW_CONFIDENCE_MARGIN = 0.15f

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
