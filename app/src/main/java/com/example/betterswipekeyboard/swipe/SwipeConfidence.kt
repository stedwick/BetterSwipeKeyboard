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
 * score anywhere from -1.63 to 1.65):
 *
 *   margin < M    wrong flagged   correct flagged
 *   0.10          7/20            3/234  (1.3%)
 *   0.15         10/20            8/234  (3.4%)
 *   0.20         10/20           11/234  (4.7%)
 *   0.25         11/20           14/234  (6.0%)  <- chosen
 *   0.30         11/20           14/234  (6.0%)
 *   0.35         11/20           19/234  (8.1%)
 *   0.40         12/20           22/234  (9.4%)
 *   0.45         13/20           27/234  (11.5%)
 *
 * 0.25 is the knee of the trade, raised from 0.15 when Philip found the
 * yellow flash too rare: the overall flash rate rises from 7.1% to 9.8%
 * of committed swipes while false positives stay at 6.0%, and the newly
 * flagged correct commits (margins 0.17–0.24) are still close races where
 * "maybe re-swipe" is honest. Past it the trade collapses — 0.25→0.40
 * buys exactly one more wrong commit for eight more false positives,
 * 0.30 flags nothing new over 0.25 at all, and 0.45 breaks into
 * double-digit false positives. The nine wrong commits with healthy
 * margins (≥ 0.37, up to 1.03) are unreachable at any defensible rate.
 * Adding an absolute-score band (score > 1.2) would catch one more wrong
 * commit for one more false positive and a second knob — measured,
 * rejected.
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
