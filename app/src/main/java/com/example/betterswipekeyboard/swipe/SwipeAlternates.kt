package com.example.betterswipekeyboard.swipe

/**
 * Pure, unit-tested: the up-to-4 runner-up words offered in the alternates
 * strip after a swipe commit. Top-1 is what got committed, so it is never
 * repeated; runners-up at or above [MAX_COMMIT_SCORE] are the same decoder
 * junk the commit gate itself rejects and are never offered. The decoder
 * decodes topN=5, so 4 runners-up always exist — the strip shows 2 of them
 * on narrow screens and all 4 on wide ones (see alternateCountForWidth).
 */
fun swipeAlternates(results: List<ScoredWord>): List<String> =
    results.drop(1).filter { it.score < MAX_COMMIT_SCORE }.take(4).map { it.word }

/**
 * Score ceiling for the near-miss offer band: a FAILED swipe (top-1 at/above
 * [MAX_COMMIT_SCORE], nothing committed) offers its top candidates as one-tap
 * strip insertions only when top-1 is below this bound. Measured on the
 * production decoder over the six fixture sets (274 swipes) plus the 14
 * captured keyboard trails:
 *   rescue — 2/2: the two silent keyboard trails offer "keyboard" at top-1
 *     (1.905 and 2.639);
 *   impostors — 3/274 (1.1%): set1#2 "exert"@2.92 (1 offer), set3#3
 *     "misinterpret"@2.55 (5 in-band, all wrong), set5#13 "min"@2.53;
 *   a 2.5 cap would rescue only 1/2 and the nearest impostor sits 0.03 away.
 * Above the band the top candidates are unrelated words — showing them as
 * tappable would train distrust of the strip.
 */
const val NEAR_MISS_OFFER_MAX_SCORE = 3.2f

/**
 * Offers for the strip after a FAILED swipe: the top candidates INCLUDING
 * top-1 (nothing was committed — there is no center cell to drop it for),
 * all below [NEAR_MISS_OFFER_MAX_SCORE], capped at [maxOffers] (the strip's
 * width-adaptive cell count). Null when the band is empty, so callers leave
 * the placeholder exactly as before.
 */
fun failedSwipeOffers(results: List<ScoredWord>, maxOffers: Int): List<String>? =
    results.filter { it.score < NEAR_MISS_OFFER_MAX_SCORE }
        .take(maxOffers)
        .map { it.word }
        .ifEmpty { null }
