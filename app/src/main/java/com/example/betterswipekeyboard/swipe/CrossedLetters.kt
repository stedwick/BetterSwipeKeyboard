package com.example.betterswipekeyboard.swipe

/**
 * The ordered letter keys a swipe trail crossed — proofreader context, not
 * decoder input. The decoder answers "which word best explains this trail";
 * this answers the simpler question "which keys did the finger pass over",
 * so the AI proofreader can restore the word the FINGER described when the
 * decoder's commit is implausible in context (`fog` committed, path `d·o·g`).
 *
 * Deliberately independent of [SwipeDecoder]: the decoder's letter
 * alignment is a private per-candidate structure that maps a WORD's letters
 * onto the trail, not the keys the trail crossed, and exposing it would
 * couple this evidence channel to decoder tuning.
 *
 * Algorithm: per trail point, the NEAREST letter-key center (Voronoi — no
 * radius gate), order preserved, strictly-consecutive repeats collapsed (a
 * dwell is one visit — doubling is the decoder's timed business, not ours).
 * Non-consecutive revisits survive (`mummy`'s m…u…m).
 *
 * Why no radius gate: measured on the four real-trail fixture sets
 * (2026-07-28, intent-letter subsequence recovery over ALL 157 records) —
 * gates of 0.4/0.5/0.6/0.7/0.75kw recover 46/46/64/69/69%, plain
 * nearest-key assignment 75% (7/17, 28/36, 33/37, 50/67 per set). Gates
 * lose exactly the endpoint/short-leg letters the decoder also struggles
 * with (`the` ending nearer r than e). The price of nearest-key is jitter
 * letters between intended ones (avg ~12 letters for a 5-letter word);
 * extra letters are recoverable by the model ("approximate, extras are
 * normal"), missing letters are not — so coverage wins. Run-length and
 * dwell-time filtering were measured and changed nothing: dense trails
 * make every crossing multi-point.
 */
fun crossedLetters(
    trail: List<Vec2>,
    keyCenters: Map<Char, Vec2>,
): String {
    val letters = StringBuilder()
    var last: Char? = null
    for (point in trail) {
        var nearest: Char? = null
        var nearestDistance = Float.MAX_VALUE
        for ((letter, center) in keyCenters) {
            val distance = point.distanceTo(center)
            if (distance < nearestDistance) {
                nearest = letter
                nearestDistance = distance
            }
        }
        if (nearest != null && nearest != last) {
            letters.append(nearest)
            last = nearest
        }
    }
    return letters.toString()
}
