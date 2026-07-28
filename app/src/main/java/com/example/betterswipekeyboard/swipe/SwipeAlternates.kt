package com.example.betterswipekeyboard.swipe

/**
 * Pure, unit-tested: the up-to-3 runner-up words offered in the alternates
 * strip after a swipe commit. Top-1 is what got committed, so it is never
 * repeated; runners-up at or above [MAX_COMMIT_SCORE] are the same decoder
 * junk the commit gate itself rejects and are never offered.
 */
fun swipeAlternates(results: List<ScoredWord>): List<String> =
    results.drop(1).filter { it.score < MAX_COMMIT_SCORE }.take(3).map { it.word }
